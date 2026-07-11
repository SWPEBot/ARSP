package com.google.android.factory.factory.viewmodel.umpire

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.google.android.factory.base.logging.Log
import com.google.android.factory.base.utils.createChannel
import com.google.android.factory.base.utils.shutdownChannel
import com.google.android.factory.factory.domain.logging.Logging
import com.google.android.factory.factory.domain.otaupdate.OtaUpdate
import com.google.android.factory.factory.processor.ksp.GenerateTestArgsUtils
import com.google.android.factory.factory.proto.Component
import com.google.android.factory.factory.proto.GetOtaPackageRequest
import com.google.android.factory.factory.proto.GetUpdateVersionRequest
import com.google.android.factory.factory.proto.SyncDeviceTimeRequest
import com.google.android.factory.factory.proto.SyncDeviceTimeResponse
import com.google.android.factory.factory.proto.TestObject
import com.google.android.factory.factory.proto.UmpireDUTCommandsGrpc
import com.google.android.factory.factory.proto.UpdateFactoryAppRequest
import com.google.android.factory.factory.proto.UpdateFactoryAppResponse
import com.google.android.factory.factory.viewmodel.base.TestStateListener
import com.google.android.factory.factory.viewmodel.base.TestViewModel
import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.stub.AbstractStub
import java.io.File
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Arguments of [SyncFactoryServerViewModel].
 *
 * @property updateToolkit Whether to check factory update.
 * @property updateWithoutPrompt Update without prompting when an update is available.
 * @property waitUpdateSecs Number of seconds to wait before the update. This only applies when
 *   [updateWithoutPrompt] is true.
 * @property downloadOtaPackage Whether to download OTA package.
 * @property serverUrl Set and keep new factory server URL.
 * @property caName The CA bundle, which should be installed as assets of the apk.
 * @property developer If set, then the test won't fail/pass automatically.
 * @property disableLogging Disable logging and delete logs.
 * @property syncDeviceTime Whether to synchronize the device time/timezone.
 */
@GenerateTestArgsUtils
data class SyncFactoryServerArgs(
  var updateToolkit: Boolean = false,
  var updateWithoutPrompt: Boolean = false,
  var waitUpdateSecs: Int = 5,
  var downloadOtaPackage: Boolean = false,
  var serverUrl: String = "",
  var caName: String = "",
  var developer: Boolean = false,
  var disableLogging: Boolean = false,
  var syncDeviceTime: Boolean = false,
)

data class SyncFactoryServerUiState(
  var proxyError: String = "",
  var updateAppResponse: UpdateFactoryAppResponse? = null,
  var syncTimeResponse: SyncDeviceTimeResponse? = null,
  var promptForUpdateState: Int = PROMPT_INITIAL,
  var localVersion: String? = null,
  var updateVersion: String? = null,
  var countDownSecsForUpdate: Int = 0,
) {
  companion object {
    const val PROMPT_INITIAL = 0
    const val PROMPT_WAITING = 1
    const val PROMPT_ACCEPTED = 2
    const val PROMPT_DENIED = 3
  }
}

class SyncFactoryServerViewModel(
  application: Application,
  testStateListener: TestStateListener,
  test: TestObject,
) :
  TestViewModel<SyncFactoryServerArgs>(
    application,
    testStateListener,
    test,
    SyncFactoryServerArgs(),
    SyncFactoryServerArgs_GeneratedConverter(),
  ) {
  private val context
    get() = getApplication<Application>().applicationContext

  private val grpcExecutor = Executors.newSingleThreadExecutor()
  private val grpcContext = grpcExecutor.asCoroutineDispatcher()
  private var runTestJob: Job? = null
  private var tearDownJob: Job? = null
  private var channel: ManagedChannel? = null

  private val _uiState = MutableStateFlow(SyncFactoryServerUiState())
  val uiState = _uiState.asStateFlow()

  init {
    addCloseable {
      grpcContext.cancel()
      grpcExecutor.shutdown()
    }
  }

  private suspend fun <S : AbstractStub<*>> createStub(
    args: SyncFactoryServerArgs,
    getStub: (channel: Channel) -> S,
  ): S {
    val logError = { err: Exception ->
      _uiState.update { it.copy(proxyError = "$err ${err.cause}") }
      Log.info("proxyError: ${_uiState.value.proxyError}")
      Unit
    }

    channel?.let {
      return getStub(it)
    }

    while (true) {
      val serverUrl =
        try {
          if (args.serverUrl.isEmpty()) {
            Log.info("The server URL is empty, fetch from factory config.")
            factoryContext.factoryPartition.fetchFactoryConfig().umpireServerUri
          } else {
            args.serverUrl
          }
        } catch (e: Exception) {
          Log.error("Failed to fetch factory config", e)
          ""
        }

      if (serverUrl.isNotEmpty()) {
        val tempChannel = createChannel(context, args.caName, serverUrl, logError)
        if (tempChannel != null) {
          channel = tempChannel
          return getStub(tempChannel)
        }
        Log.error("Failed to create channel to $serverUrl, retrying...")
      } else {
        Log.info("Waiting for server URL from factory config...")
        _uiState.update { it.copy(proxyError = "Waiting for server URL from factory config...") }
      }
      delay(2.seconds)
    }
  }

  private suspend fun createFutureStub(
    args: SyncFactoryServerArgs
  ): UmpireDUTCommandsGrpc.UmpireDUTCommandsFutureStub =
    // TODO: b/510246694 - Migrate to use FactoryActionViewModel, but don't use future stub for gRPC
    // client; it's for Java. Use coroutine stub for Kotlin.
    createStub(args) { UmpireDUTCommandsGrpc.newFutureStub(it) }

  private suspend fun <R> callWithRetry(
    args: SyncFactoryServerArgs,
    block: suspend (stub: UmpireDUTCommandsGrpc.UmpireDUTCommandsFutureStub) -> R
  ): R {
    while (true) {
      try {
        val stub = createFutureStub(args)
        return block(stub)
      } catch (err: Exception) {
        val errorMsg = "Connection failed: ${err.message}. Retrying in 5s..."
        _uiState.update { it.copy(proxyError = errorMsg) }
        Log.error(errorMsg, err)
        // Invalidate channel to force re-fetch of URL and re-creation of channel
        channel?.let { shutdownChannel(it) }
        channel = null
        delay(5.seconds)
      }
    }
  }

  private suspend fun downloadOtaPackage(args: SyncFactoryServerArgs) {
    // TODO: chungsheng - Move the OTA package path to domain/.
    val downloadPath =
      File(OtaUpdate.UMPIRE_DOWNLOAD_DIR, OtaUpdate.OTA_PACKAGE_ZIP_NAME).absolutePath

    val response = callWithRetry(args) { stub ->
      stub.getOtaPackage(GetOtaPackageRequest.newBuilder().setPath(downloadPath).build()).await()
    }

    Log.info("otaPackageResponse: $response")
    if (!response.success) {
      failTest(args.developer)
    }
  }

  /** Updates the ALMAK app from the factory server. */
  private suspend fun updateToolkit(args: SyncFactoryServerArgs) {
    val localVersion = factoryContext.systemInfo.calculateAppMd5Hash()
    Log.info("localVersion: $localVersion")
    _uiState.update { it.copy(localVersion = localVersion) }

    val updateVersion = callWithRetry(args) { stub ->
      stub
        .getUpdateVersion(
          GetUpdateVersionRequest.newBuilder().setComponent(Component.COMPONENT_TOOLKIT).build()
        )
        .await()
        .version
    }

    Log.info("updateVersion: $updateVersion")
    _uiState.update { it.copy(updateVersion = updateVersion) }
    when {
      updateVersion.isEmpty() -> {
        Log.info("No update in the server.")
        return
      }
      // version from DOME contains parentheses. Ex. local version: 439b6af62634d94b2642b6f69dfc57a0
      // update version: (439b6af62634d94b2642b6f69dfc57a0)
      localVersion in updateVersion -> {
        Log.info("No need to update from the server.")
        return
      }
    }
    if (!args.updateWithoutPrompt || args.waitUpdateSecs > 0) {
      _uiState.update { it.copy(promptForUpdateState = SyncFactoryServerUiState.PROMPT_WAITING) }
      val countDownTimer =
        if (args.updateWithoutPrompt) {
          viewModelScope.async {
            for (index in 0 until args.waitUpdateSecs) {
              _uiState.update { it.copy(countDownSecsForUpdate = args.waitUpdateSecs - index) }
              delay(1.seconds)
            }
            _uiState.update {
              it.copy(
                countDownSecsForUpdate = 0,
                promptForUpdateState = SyncFactoryServerUiState.PROMPT_ACCEPTED,
              )
            }
          }
        } else {
          null
        }

      val state = _uiState.first {
        it.promptForUpdateState in
          arrayOf(SyncFactoryServerUiState.PROMPT_ACCEPTED, SyncFactoryServerUiState.PROMPT_DENIED)
      }
      countDownTimer?.cancel()
      if (state.promptForUpdateState == SyncFactoryServerUiState.PROMPT_DENIED) {
        failTest(args.developer)
      }
    }

    _uiState.update { it.copy(updateAppResponse = null) }

    val response = callWithRetry(args) { stub ->
      stub.updateFactoryApp(UpdateFactoryAppRequest.newBuilder().build()).await()
    }

    _uiState.update { it.copy(updateAppResponse = response) }
    Log.info("response: $response")
    if (!response.success) {
      failTest(args.developer)
    }
  }

  /** Synchronizes the device time using the Umpire server's timezone. */
  private suspend fun syncDeviceTime(args: SyncFactoryServerArgs) {
    _uiState.update { it.copy(syncTimeResponse = null) }
    val request = SyncDeviceTimeRequest.newBuilder().build()

    val response = callWithRetry(args) { stub ->
      stub.syncDeviceTime(request).await()
    }

    _uiState.update { it.copy(syncTimeResponse = response) }
    Log.info("syncTimeResponse: $response")
    if (!response.success) {
      failTest(args.developer)
    }
  }

  override fun runTest() {
    super.runTest()
    val args = args.value
    val cachedTearDownJob = tearDownJob?.also { tearDownJob = null }
    runTestJob =
      launchTestJob(grpcContext, manual = args.developer) {
        cachedTearDownJob?.join()

        if (args.updateToolkit) {
          updateToolkit(args)
        } else {
          Log.info("Skip updating toolkit.")
        }
        if (args.downloadOtaPackage) {
          downloadOtaPackage(args)
        } else {
          Log.info("Skip downloading OTA package.")
        }
        if (args.disableLogging) {
          Log.info("Logging disabled; there should be no more logs.")
          Logging.disable()
        }
        if (args.syncDeviceTime) {
          syncDeviceTime(args)
        } else {
          Log.info("Skip synchronizing device time.")
        }

        passTest(args.developer)
      }
  }

  override fun tearDown() {
    super.tearDown()
    val cachedRunTestJob = runTestJob?.also { runTestJob = null }
    val cachedChannel = channel?.also { channel = null }
    tearDownJob =
      viewModelScope.launch(grpcContext) {
        cachedRunTestJob?.cancelAndJoin()
        cachedChannel?.also { shutdownChannel(it) }
        _uiState.update { SyncFactoryServerUiState() }
      }
  }

  fun setPromptForUpdate(value: Int) {
    _uiState.update { it.copy(promptForUpdateState = value) }
  }
}
