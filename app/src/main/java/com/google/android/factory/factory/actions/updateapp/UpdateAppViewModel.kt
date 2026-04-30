package com.google.android.factory.factory.actions.updateapp

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.input.key.Key
import com.google.android.factory.base.logging.Log
import com.google.android.factory.base.utils.createChannel
import com.google.android.factory.base.utils.shutdownChannel
import com.google.android.factory.factory.actions.base.FactoryActionViewModel
import com.google.android.factory.factory.actions.common.SimpleMessageScreenController
import com.google.android.factory.factory.actions.common.SimpleMessageScreenViewModelInterface
import com.google.android.factory.factory.actions.common.withCountdownOrNull
import com.google.android.factory.factory.actions.interfaces.FactoryActionResult
import com.google.android.factory.factory.processor.ksp.GenerateTestArgsUtils
import com.google.android.factory.factory.proto.DownloadPayloadRequest
import com.google.android.factory.factory.proto.PayloadType
import com.google.android.factory.factory.proto.UmpireDUTCommandsGrpc
import com.google.android.factory.factory.ui.base.keyevent.KeyEventFilter
import io.grpc.ManagedChannel
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

open class BaseUpdateAppArgs(
  open var waitInstallSecs: Int,
  open var force: Boolean,
  open var updateTimeoutSecs: Int,
)

/**
 * Arguments of [InstallPreflashAppViewModel].
 *
 * @property waitInstallSecs A delay before the install.
 * @property force If set, update even if the hash is the same. Could cause infinite loop.
 * @property updateTimeoutSecs Timeout for updating APK. The countdown begins after the installation
 *   starts.
 */
@GenerateTestArgsUtils
data class InstallPreflashAppArgs(
  override var waitInstallSecs: Int = 5,
  override var force: Boolean = false,
  override var updateTimeoutSecs: Int = 5,
) : BaseUpdateAppArgs(waitInstallSecs, force, updateTimeoutSecs)

/**
 * Arguments of [UpdateAppFromUmpireViewModel].
 *
 * @property waitInstallSecs A delay before the install.
 * @property force If set, update even if the hash is the same. Could cause infinite loop.
 * @property updateTimeoutSecs Timeout for updating APK. The countdown begins after the installation
 *   starts.
 * @property serverUrl The server URL to connect to.
 * @property caName The CA bundle, which should be installed as assets of the apk.
 * @property timeoutSecs Number of seconds to wait for the factory server to become reachable
 *   before connecting to it.
 */
@GenerateTestArgsUtils
data class UpdateAppFromUmpireArgs(
  override var waitInstallSecs: Int = 5,
  override var force: Boolean = false,
  override var updateTimeoutSecs: Int = 5,
  var serverUrl: String = "",
  var caName: String = "",
  var timeoutSecs: Int = 60,
) : BaseUpdateAppArgs(waitInstallSecs, force, updateTimeoutSecs)

private fun parseServerUrls(serverUrl: String): List<String> =
  serverUrl
    .split(',', ';', '\n')
    .map { it.trim() }
    .filter { it.isNotEmpty() }

private data class ServerEndpoint(val host: String, val port: Int)

private fun parseServerEndpoint(serverUrl: String): ServerEndpoint {
  val uri = URI(if ("://" in serverUrl) serverUrl else "grpc://$serverUrl")
  val host = uri.host
  val port = uri.port
  if (host.isNullOrEmpty() || port < 0) {
    throw IllegalArgumentException("Invalid server URL: $serverUrl")
  }
  return ServerEndpoint(host, port)
}

abstract class BaseUpdateAppViewModel<Args : BaseUpdateAppArgs>(
  protected val screenController: SimpleMessageScreenController = SimpleMessageScreenController()
) : FactoryActionViewModel<Args>(), SimpleMessageScreenViewModelInterface by screenController {

  override val expectedResumeTimes = 1

  abstract protected suspend fun prepareApk(): File?

  override suspend fun runActionImpl(): FactoryActionResult {
    val newApkFile = prepareApk()
    if (newApkFile == null) {
      Log.error("The apk file is not prepared.")
      return FactoryActionResult.Failure
    }
    val localVersion = factoryContext.systemInfo.calculateAppMd5Hash()
    val updateVersion = factoryContext.systemInfo.calculateFileMd5Hash(newApkFile.toPath())

    when (resumeCounter) {
      0 -> {
        if (localVersion == updateVersion && !args.force) {
          Log.info("No need to update from the preflash apk.")
          return FactoryActionResult.Success
        }
        val isCancelled =
          factoryContext.withCountdownOrNull(
            args.waitInstallSecs,
            { sec -> screenController.title = "Update APK in $sec seconds..." },
            {
              screenController.awaitableButtonController.awaitClick("Cancel", KeyEventFilter(Key.C))
              true
            },
          ) ?: false
        if (isCancelled) {
          Log.error("Update APK action is cancelled.")
          return FactoryActionResult.Failure
        }
        withTimeoutOrNull(args.updateTimeoutSecs.seconds) {
          screenController.title = "Updating APK..."
          // If success, this function shouldn't return.
          executeUpdateApp(newApkFile.absolutePath)
        }
        Log.error(
          "Update APK timeout in ${args.updateTimeoutSecs} seconds. Please make sure the apk has the correct signature."
        )
        return FactoryActionResult.Failure
      }
      1 -> {
        if (localVersion != updateVersion) {
          Log.error("Failed to update APK to $updateVersion.")
          return FactoryActionResult.Failure
        } else {
          Log.info("Successfully updated APK to $updateVersion.")
          return FactoryActionResult.Success
        }
      }
      else -> {
        Log.error("Unexpected resume counter: $resumeCounter")
        return FactoryActionResult.Failure
      }
    }
  }

  // TODO(pohengchen): Collect error message from the launcher if installation failed.
  suspend fun executeUpdateApp(apkPath: String) {
    val intent =
      Intent().apply {
        component =
          ComponentName(
            FACTORY_APP_LAUNCHER_PACKAGE_NAME,
            FACTORY_APP_LAUNCHER_UPDATE_ACTIVITY_NAME,
          )
        putExtra("apk_path", apkPath)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    factoryContext.applicationContext.startActivity(intent)
    // Make sure the factory app is closed so that even the installation failed, the factory app is
    // restarted.
    awaitCancellation()
  }

  override suspend fun tearDown() {}

  companion object {
    const val FACTORY_APP_APK_NAME = "factory_app.apk"
    const val FACTORY_APP_LAUNCHER_PACKAGE_NAME = "com.google.android.factory.factorylauncher"
    const val FACTORY_APP_LAUNCHER_UPDATE_ACTIVITY_NAME =
      "com.google.android.factory.factorylauncher.UpdateFactoryAppActivity"
  }
}

class InstallPreflashAppViewModel : BaseUpdateAppViewModel<InstallPreflashAppArgs>() {
  override suspend fun prepareApk(): File? {
    val apkFile = factoryContext.factoryPartition.fetchFactoryAppApkPath()?.toFile()
    if (apkFile != null) {
      val adbClient = factoryContext.adbClient
      val dumpedApkFile = File(factoryContext.applicationContext.cacheDir, FACTORY_APP_APK_NAME)
      adbClient.openAdbShell().use { shell ->
        shell.runAndCheck("cp ${apkFile.absolutePath} ${dumpedApkFile.absolutePath}")
        shell.runAndCheck("chown u10_system:u10_system  ${dumpedApkFile.absolutePath}")
        shell.runAndCheck("chcon u:object_r:system_app_data_file:s0 ${dumpedApkFile.absolutePath}")
      }
      return dumpedApkFile
    } else {
      return null
    }
  }
}

class UpdateAppFromUmpireViewModel : BaseUpdateAppViewModel<UpdateAppFromUmpireArgs>() {
  private var channel: ManagedChannel? = null

  private suspend fun waitForServerReady(serverUrl: String): String? {
    val serverUrls = parseServerUrls(serverUrl)
    if (serverUrls.isEmpty()) {
      Log.error("The server URL is empty.")
      return null
    }
    if (args.timeoutSecs <= 0) {
      return serverUrls.first()
    }

    Log.info(
      "Wait for factory servers ${serverUrls.joinToString()} " +
        "for ${args.timeoutSecs} seconds."
    )
    screenController.title = "Waiting for Umpire server..."
    var readyServerUrl: String? = null
    val ready =
      withTimeoutOrNull(args.timeoutSecs.seconds) {
        while (true) {
          for (url in serverUrls) {
            val endpoint =
              try {
                parseServerEndpoint(url)
              } catch (e: IllegalArgumentException) {
                Log.error(e.message ?: "Invalid server URL: $url")
                continue
              }
            val canConnect =
              withContext(factoryContext.ioDispatcher) {
                runCatching {
                    Socket().use {
                      it.connect(InetSocketAddress(endpoint.host, endpoint.port), 1000)
                    }
                  }
                  .isSuccess
              }
            if (canConnect) {
              readyServerUrl = url
              return@withTimeoutOrNull true
            }
          }
          delay(1.seconds)
        }
      }
    if (ready != true) {
      Log.error("Factory servers ${serverUrls.joinToString()} are unreachable.")
      return null
    }

    val selectedServerUrl = readyServerUrl ?: serverUrls.first()
    Log.info("Factory server $selectedServerUrl is reachable.")
    return selectedServerUrl
  }

  override suspend fun prepareApk(): File? {
    val downloadFile = File(factoryContext.applicationContext.cacheDir, FACTORY_APP_APK_NAME)
    val serverUrl =
      if (args.serverUrl.isEmpty()) {
        Log.info("The server URL is empty, fetch from factory config.")
        val factoryConfig = factoryContext.factoryPartition.fetchFactoryConfig()
        factoryConfig.umpireServerUri
      } else {
        args.serverUrl
      }

    val readyServerUrl = waitForServerReady(serverUrl) ?: return null
    screenController.title = "Downloading APK from Umpire..."
    try {
      channel =
        createChannel(
          factoryContext.applicationContext,
          args.caName,
          readyServerUrl,
          { e -> throw RuntimeException("Failed to create channel to Umpire: ${e.message}", e) },
        )
      if (channel == null) {
        Log.error("The created channel to Umpire $readyServerUrl is null.")
        return null
      }
      val stub = UmpireDUTCommandsGrpc.newFutureStub(channel)
      val response =
        stub
          .downloadPayload(
            DownloadPayloadRequest.newBuilder()
              .setPath(downloadFile.absolutePath)
              .setPayloadType(PayloadType.PAYLOAD_TYPE_APK)
              .build()
          )
          .await()
      if (!response.success) {
        Log.error("Failed to download apk from Umpire $readyServerUrl: ${response.messages}")
        return null
      }
      Log.info("Downloaded apk from Umpire $readyServerUrl.")
      return downloadFile
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.error("Error sending DownloadPayload request to $readyServerUrl: ${e.message}")
    } finally {
      channel?.let { shutdownChannel(it) }
      channel = null
    }
    return null
  }
}
