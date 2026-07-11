package com.google.android.factory.factory.actions

import androidx.compose.runtime.Composable
import com.google.android.factory.factory.actions.apwriteprotect.ApWriteProtectArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.apwriteprotect.ApWriteProtectArgs_GeneratedConverter
import com.google.android.factory.factory.actions.apwriteprotect.ApWriteProtectViewModel
import com.google.android.factory.factory.actions.audio.AudioLoopbackCheckArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.audio.AudioLoopbackCheckArgs_GeneratedConverter
import com.google.android.factory.factory.actions.audio.AudioLoopbackCheckViewModel
import com.google.android.factory.factory.actions.audio.PlayAudioArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.audio.PlayAudioArgs_GeneratedConverter
import com.google.android.factory.factory.actions.audio.PlayAudioViewModel
import com.google.android.factory.factory.actions.base.FactoryActionViewModel
import com.google.android.factory.factory.actions.base.NoArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.base.NoArgs_GeneratedConverter
import com.google.android.factory.factory.actions.battery.BatteryCheckArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.battery.BatteryCheckArgs_GeneratedConverter
import com.google.android.factory.factory.actions.battery.BatteryCheckViewModel
import com.google.android.factory.factory.actions.battery.BatteryCurrentArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.battery.BatteryCurrentArgs_GeneratedConverter
import com.google.android.factory.factory.actions.battery.BatteryCurrentScreen
import com.google.android.factory.factory.actions.battery.BatteryCurrentViewModel
import com.google.android.factory.factory.actions.camera.CameraArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.camera.CameraArgs_GeneratedConverter
import com.google.android.factory.factory.actions.camera.CameraScreen
import com.google.android.factory.factory.actions.camera.CameraViewModel
import com.google.android.factory.factory.actions.checkpoint.CheckPointArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.checkpoint.CheckPointArgs_GeneratedConverter
import com.google.android.factory.factory.actions.checkpoint.CheckPointViewModel
import com.google.android.factory.factory.actions.checkpoint.IdleArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.checkpoint.IdleArgs_GeneratedConverter
import com.google.android.factory.factory.actions.checkpoint.IdleViewModel
import com.google.android.factory.factory.actions.common.NoUIScreen
import com.google.android.factory.factory.actions.common.SimpleMessageScreen
import com.google.android.factory.factory.actions.common.SubTaskScreen
import com.google.android.factory.factory.actions.connectivity.BluetoothArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.connectivity.BluetoothArgs_GeneratedConverter
import com.google.android.factory.factory.actions.connectivity.BluetoothViewModel
import com.google.android.factory.factory.actions.connectivity.WifiArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.connectivity.WifiArgs_GeneratedConverter
import com.google.android.factory.factory.actions.connectivity.WifiViewModel
import com.google.android.factory.factory.actions.device.ScanDeviceDataArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.device.ScanDeviceDataArgs_GeneratedConverter
import com.google.android.factory.factory.actions.device.ScanDeviceDataScreen
import com.google.android.factory.factory.actions.device.ScanDeviceDataViewModel
import com.google.android.factory.factory.actions.device.VerifyDeviceDataArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.device.VerifyDeviceDataArgs_GeneratedConverter
import com.google.android.factory.factory.actions.device.VerifyDeviceDataViewModel
import com.google.android.factory.factory.actions.downloadfactorydrive.DownloadFactoryDriveArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.downloadfactorydrive.DownloadFactoryDriveArgs_GeneratedConverter
import com.google.android.factory.factory.actions.downloadfactorydrive.DownloadFactoryDriveViewModel
import com.google.android.factory.factory.actions.downloadfactorydrive.SyncFactoryDriveArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.downloadfactorydrive.SyncFactoryDriveArgs_GeneratedConverter
import com.google.android.factory.factory.actions.downloadfactorydrive.SyncFactoryDriveViewModel
import com.google.android.factory.factory.actions.dsm.Cs35l56CalibrationArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.dsm.Cs35l56CalibrationArgs_GeneratedConverter
import com.google.android.factory.factory.actions.dsm.Cs35l56CalibrationViewModel
import com.google.android.factory.factory.actions.dsm.Rt1320CalibrationArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.dsm.Rt1320CalibrationArgs_GeneratedConverter
import com.google.android.factory.factory.actions.dsm.Rt1320CalibrationViewModel
import com.google.android.factory.factory.actions.dsm.Tas2563CalibrationArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.dsm.Tas2563CalibrationArgs_GeneratedConverter
import com.google.android.factory.factory.actions.dsm.Tas2563CalibrationViewModel
import com.google.android.factory.factory.actions.dsm.Wsa8845CalibrationArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.dsm.Wsa8845CalibrationArgs_GeneratedConverter
import com.google.android.factory.factory.actions.dsm.Wsa8845CalibrationViewModel
import com.google.android.factory.factory.actions.ecwriteprotect.EcWriteProtectArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.ecwriteprotect.EcWriteProtectArgs_GeneratedConverter
import com.google.android.factory.factory.actions.ecwriteprotect.EcWriteProtectViewModel
import com.google.android.factory.factory.actions.fan.FanSpeedActionArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.fan.FanSpeedActionArgs_GeneratedConverter
import com.google.android.factory.factory.actions.fan.FanSpeedScreen
import com.google.android.factory.factory.actions.fan.FanSpeedViewModel
import com.google.android.factory.factory.actions.fingerprint.EnableFPWriteProtectViewModel
import com.google.android.factory.factory.actions.fingerprint.FingerprintSensorElanArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.fingerprint.FingerprintSensorElanArgs_GeneratedConverter
import com.google.android.factory.factory.actions.fingerprint.FingerprintSensorElanViewModel
import com.google.android.factory.factory.actions.fingerprint.FingerprintSensorFocaltechViewModel
import com.google.android.factory.factory.actions.fingerprint.FpmcuInitializeEntropyViewModel
import com.google.android.factory.factory.actions.fingerprint.UpdateFpFirmwareArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.fingerprint.UpdateFpFirmwareArgs_GeneratedConverter
import com.google.android.factory.factory.actions.fingerprint.UpdateFpFirmwareViewModel
import com.google.android.factory.factory.actions.fingerprint.VerifyFpKeyViewModel
import com.google.android.factory.factory.actions.gbbflags.SetGbbFlagsArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.gbbflags.SetGbbFlagsArgs_GeneratedConverter
import com.google.android.factory.factory.actions.gbbflags.SetGbbFlagsViewModel
import com.google.android.factory.factory.actions.gsc.LockGscArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.gsc.LockGscArgs_GeneratedConverter
import com.google.android.factory.factory.actions.gsc.LockGscViewModel
import com.google.android.factory.factory.actions.gsc.VerifyEkViewModel
import com.google.android.factory.factory.actions.gsc.VerifyGscBeforeLockViewModel
import com.google.android.factory.factory.actions.hwdescriptor.HwDescProbeProvisionArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.hwdescriptor.HwDescProbeProvisionArgs_GeneratedConverter
import com.google.android.factory.factory.actions.hwdescriptor.HwDescProbeProvisionViewModel
import com.google.android.factory.factory.actions.hwdescriptor.HwDescScreen
import com.google.android.factory.factory.actions.hwdescriptor.HwDescVerifyArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.hwdescriptor.HwDescVerifyArgs_GeneratedConverter
import com.google.android.factory.factory.actions.hwdescriptor.HwDescVerifyViewModel
import com.google.android.factory.factory.actions.installotherapp.InstallOtherAppArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.installotherapp.InstallOtherAppArgs_GeneratedConverter
import com.google.android.factory.factory.actions.installotherapp.InstallOtherAppViewModel
import com.google.android.factory.factory.actions.interfaces.ActionArgsConverter
import com.google.android.factory.factory.actions.interfaces.ActionArgsConverterWarning
import com.google.android.factory.factory.actions.interfaces.FactoryAction
import com.google.android.factory.factory.actions.keyboard.KeyboardArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.keyboard.KeyboardArgs_GeneratedConverter
import com.google.android.factory.factory.actions.keyboard.KeyboardBacklightScreen
import com.google.android.factory.factory.actions.keyboard.KeyboardBacklightViewModel
import com.google.android.factory.factory.actions.keyboard.KeyboardViewModel
import com.google.android.factory.factory.actions.led.LedActionArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.led.LedActionArgs_GeneratedConverter
import com.google.android.factory.factory.actions.led.LedScreen
import com.google.android.factory.factory.actions.led.LedViewModel
import com.google.android.factory.factory.actions.lidswitch.LidSwitchArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.lidswitch.LidSwitchArgs_GeneratedConverter
import com.google.android.factory.factory.actions.lidswitch.LidSwitchViewModel
import com.google.android.factory.factory.actions.lightbar.LightbarColorArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.lightbar.LightbarColorArgs_GeneratedConverter
import com.google.android.factory.factory.actions.lightbar.LightbarColorScreen
import com.google.android.factory.factory.actions.lightbar.LightbarColorViewModel
import com.google.android.factory.factory.actions.memory.DramIdentificationArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.memory.DramIdentificationArgs_GeneratedConverter
import com.google.android.factory.factory.actions.memory.DramIdentificationViewModel
import com.google.android.factory.factory.actions.otaupdate.OtaFinalizeViewModel
import com.google.android.factory.factory.actions.otaupdate.OtaUpdateViewModel
import com.google.android.factory.factory.actions.postmanufacturing.EnablePostManufacturingAppViewModel
import com.google.android.factory.factory.actions.postmanufacturing.EnableRepairModeViewModel
import com.google.android.factory.factory.actions.power.SuspendResumeStressViewModel
import com.google.android.factory.factory.actions.productionmode.IdentifySoCViewModel
import com.google.android.factory.factory.actions.productionmode.IntelEnableProductionModeArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.productionmode.IntelEnableProductionModeArgs_GeneratedConverter
import com.google.android.factory.factory.actions.productionmode.IntelEnableProductionModeViewModel
import com.google.android.factory.factory.actions.provision.ProvisionDeviceIdViewModel
import com.google.android.factory.factory.actions.provision.ProvisionGscArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.provision.ProvisionGscArgs_GeneratedConverter
import com.google.android.factory.factory.actions.provision.ProvisionGscViewModel
import com.google.android.factory.factory.actions.provision.ProvisionVpdArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.provision.ProvisionVpdArgs_GeneratedConverter
import com.google.android.factory.factory.actions.provision.ProvisionVpdViewModel
import com.google.android.factory.factory.actions.provision.RemoveVpdKeysArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.provision.RemoveVpdKeysArgs_GeneratedConverter
import com.google.android.factory.factory.actions.provision.RemoveVpdKeysViewModel
import com.google.android.factory.factory.actions.provision.UpdateCbiArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.provision.UpdateCbiArgs_GeneratedConverter
import com.google.android.factory.factory.actions.provision.UpdateCbiViewModel
import com.google.android.factory.factory.actions.provision.VpdValidationArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.provision.VpdValidationArgs_GeneratedConverter
import com.google.android.factory.factory.actions.provision.VpdValidationViewModel
import com.google.android.factory.factory.actions.qrcode.AddPopupQrcodeArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.qrcode.AddPopupQrcodeArgs_GeneratedConverter
import com.google.android.factory.factory.actions.qrcode.AddPopupQrcodeViewModel
import com.google.android.factory.factory.actions.qrcode.ClearPopupQrcodeViewModel
import com.google.android.factory.factory.actions.removable.UsbCFunctionalTestArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.removable.UsbCFunctionalTestArgs_GeneratedConverter
import com.google.android.factory.factory.actions.removable.UsbCFunctionalTestScreen
import com.google.android.factory.factory.actions.removable.UsbCFunctionalTestViewModel
import com.google.android.factory.factory.actions.removable.UsbReadWriteArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.removable.UsbReadWriteArgs_GeneratedConverter
import com.google.android.factory.factory.actions.removable.UsbReadWriteScreen
import com.google.android.factory.factory.actions.removable.UsbReadWriteViewModel
import com.google.android.factory.factory.actions.rotation.TabletRotationArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.rotation.TabletRotationArgs_GeneratedConverter
import com.google.android.factory.factory.actions.rotation.TabletRotationScreen
import com.google.android.factory.factory.actions.rotation.TabletRotationViewModel
import com.google.android.factory.factory.actions.sensor.AccelerometerArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.sensor.AccelerometerArgs_GeneratedConverter
import com.google.android.factory.factory.actions.sensor.AccelerometerCalibrationArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.sensor.AccelerometerCalibrationArgs_GeneratedConverter
import com.google.android.factory.factory.actions.sensor.AccelerometerCalibrationViewModel
import com.google.android.factory.factory.actions.sensor.AccelerometerLidAngleArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.sensor.AccelerometerLidAngleArgs_GeneratedConverter
import com.google.android.factory.factory.actions.sensor.AccelerometerLidAngleViewModel
import com.google.android.factory.factory.actions.sensor.AccelerometerViewModel
import com.google.android.factory.factory.actions.sensor.GyroscopeAngleArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.sensor.GyroscopeAngleArgs_GeneratedConverter
import com.google.android.factory.factory.actions.sensor.GyroscopeAngleViewModel
import com.google.android.factory.factory.actions.sensor.GyroscopeCalibrationArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.sensor.GyroscopeCalibrationArgs_GeneratedConverter
import com.google.android.factory.factory.actions.sensor.GyroscopeCalibrationViewModel
import com.google.android.factory.factory.actions.sensor.LightSensorScreen
import com.google.android.factory.factory.actions.sensor.LightSensorTestArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.sensor.LightSensorTestArgs_GeneratedConverter
import com.google.android.factory.factory.actions.sensor.LightSensorViewModel
import com.google.android.factory.factory.actions.sensor.ThermalSensorArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.sensor.ThermalSensorArgs_GeneratedConverter
import com.google.android.factory.factory.actions.sensor.ThermalSensorViewModel
import com.google.android.factory.factory.actions.shell.ExecShellArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.shell.ExecShellArgs_GeneratedConverter
import com.google.android.factory.factory.actions.shell.ExecShellViewModel
import com.google.android.factory.factory.actions.shutdown.FullRebootViewModel
import com.google.android.factory.factory.actions.shutdown.RebootViewModel
import com.google.android.factory.factory.actions.shutdown.ShutdownArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.shutdown.ShutdownArgs_GeneratedConverter
import com.google.android.factory.factory.actions.shutdown.ShutdownViewModel
import com.google.android.factory.factory.actions.storage.ExpandUserdataArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.storage.ExpandUserdataArgs_GeneratedConverter
import com.google.android.factory.factory.actions.storage.ExpandUserdataTestViewModel
import com.google.android.factory.factory.actions.storage.ExpandUserdataViewModel
import com.google.android.factory.factory.actions.storage.StorageHealthViewModel
import com.google.android.factory.factory.actions.storage.StorageSimpleStressArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.storage.StorageSimpleStressArgs_GeneratedConverter
import com.google.android.factory.factory.actions.storage.StorageSimpleStressViewModel
import com.google.android.factory.factory.actions.stylus.StylusArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.stylus.StylusArgs_GeneratedConverter
import com.google.android.factory.factory.actions.stylus.StylusScreen
import com.google.android.factory.factory.actions.stylus.StylusViewModel
import com.google.android.factory.factory.actions.systemhealth.DisableSystemHealthLoggingViewModel
import com.google.android.factory.factory.actions.systemhealth.EnableSystemHealthLoggingArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.systemhealth.EnableSystemHealthLoggingArgs_GeneratedConverter
import com.google.android.factory.factory.actions.systemhealth.EnableSystemHealthLoggingViewModel
import com.google.android.factory.factory.actions.tabletmode.TabletModeArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.tabletmode.TabletModeArgs_GeneratedConverter
import com.google.android.factory.factory.actions.tabletmode.TabletModeViewModel
import com.google.android.factory.factory.actions.touchpad.TouchpadArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.touchpad.TouchpadArgs_GeneratedConverter
import com.google.android.factory.factory.actions.touchpad.TouchpadScreen
import com.google.android.factory.factory.actions.touchpad.TouchpadViewModel
import com.google.android.factory.factory.actions.touchscreen.TouchscreenArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.touchscreen.TouchscreenArgs_GeneratedConverter
import com.google.android.factory.factory.actions.touchscreen.TouchscreenScreen
import com.google.android.factory.factory.actions.touchscreen.TouchscreenViewModel
import com.google.android.factory.factory.actions.umpire.UploadCsrArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.umpire.UploadCsrArgs_GeneratedConverter
import com.google.android.factory.factory.actions.umpire.UploadCsrViewModel
import com.google.android.factory.factory.actions.umpire.UploadFactoryReportViewModel
import com.google.android.factory.factory.actions.umpire.UploadFileScreen
import com.google.android.factory.factory.actions.umpire.UploadReportArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.umpire.UploadReportArgs_GeneratedConverter
import com.google.android.factory.factory.actions.updateapp.InstallPreflashAppArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.updateapp.InstallPreflashAppArgs_GeneratedConverter
import com.google.android.factory.factory.actions.updateapp.InstallPreflashAppViewModel
import com.google.android.factory.factory.actions.updateapp.UpdateAppFromUmpireArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.updateapp.UpdateAppFromUmpireArgs_GeneratedConverter
import com.google.android.factory.factory.actions.updateapp.UpdateAppFromUmpireViewModel
import com.google.android.factory.factory.data.interfaces.FactoryContext
import com.google.android.factory.factory.actions.hardware.HardwareProbeArgs_GeneratedArgsEditor
import com.google.android.factory.factory.actions.hardware.HardwareProbeArgs_GeneratedConverter
import com.google.android.factory.factory.actions.hardware.HardwareProbeScreen
import com.google.android.factory.factory.actions.hardware.HardwareProbeViewModel
import com.google.protobuf.Struct

/**
 * A builder for an action. Use [createActionBuilder] to create an [ActionBuilder].
 *
 * @property generatedArgsEditorUI The UI to edit the action's arguments.
 * @property createArgsToStruct The function to create the action's arguments, overrides default
 *   value and convert back to Struct. This is used by the args editing dialog to provide default
 *   values.
 * @property actionUI The UI to be rendered when the action is selected.
 * @property buildActionViewModel The function to build the action's view model.
 */
data class ActionBuilder(
  val generatedArgsEditorUI:
    @Composable
    (initArgs: Struct, onArgsChange: (args: Struct) -> Unit) -> Unit,
  val createArgsToStruct: (args: Struct) -> Struct,
  val getCreateArgsWarning: (args: Struct) -> ActionArgsConverterWarning?,
  val actionUI: @Composable (viewModel: FactoryAction) -> Unit,
  val buildActionViewModel: (factoryContext: FactoryContext) -> FactoryAction,
)

/**
 * Creates an [ActionBuilder] for a given action UI and action view model. This helps to convert the
 * specific action view model to the common [FactoryAction] interface, and wrap the initialization
 * of the view model with the factory context.
 *
 * @param actionUI The UI to be rendered when the action is selected.
 * @param buildActionViewModel The function to build the action's view model.
 * @return An [ActionBuilder] for the given action UI and action view model.
 */
private inline fun <
  reified ArgsT,
  reified ActionArgsConverterT : ActionArgsConverter<ArgsT>,
  reified ViewModelT : FactoryActionViewModel<ArgsT>,
> createActionBuilder(
  argsConverter: ActionArgsConverterT,
  noinline generatedArgsEditorUI:
    @Composable
    (initArgs: Struct, onArgsChange: (args: Struct) -> Unit) -> Unit,
  crossinline actionUI: @Composable (viewModel: ViewModelT) -> Unit,
  crossinline actionViewModelConstructor: () -> ViewModelT,
) =
  ActionBuilder(
    generatedArgsEditorUI = generatedArgsEditorUI,
    createArgsToStruct = { args ->
      argsConverter.toProtoStruct(argsConverter.createArgs(args).first)
    },
    getCreateArgsWarning = { args -> argsConverter.createArgs(args).second },
    actionUI =
      @Composable { viewModel: FactoryAction ->
        if (viewModel is ViewModelT) {
          actionUI(viewModel)
        } else {
          throw IllegalArgumentException("Factory action is not an instance of the expected type.")
        }
      },
    buildActionViewModel = { factoryContext ->
      actionViewModelConstructor().also { it.initialize(factoryContext, argsConverter) }
        as FactoryAction
    },
  )

object ActionAggregator {

  val actionBuilders =
    mapOf<String, ActionBuilder>(
      // battery
      "BatteryCurrent" to
        createActionBuilder(
          BatteryCurrentArgs_GeneratedConverter(),
          ::BatteryCurrentArgs_GeneratedArgsEditor,
          ::BatteryCurrentScreen,
          ::BatteryCurrentViewModel,
        ),
      // Audio
      "AudioLoopbackCheck" to
        createActionBuilder(
          AudioLoopbackCheckArgs_GeneratedConverter(),
          ::AudioLoopbackCheckArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::AudioLoopbackCheckViewModel,
        ),
      "PlayAudio" to
        createActionBuilder(
          PlayAudioArgs_GeneratedConverter(),
          ::PlayAudioArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::PlayAudioViewModel,
        ),
      // batterycheck
      "BatteryCheck" to
        createActionBuilder(
          BatteryCheckArgs_GeneratedConverter(),
          ::BatteryCheckArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::BatteryCheckViewModel,
        ),
      // camera
      "Camera" to
        createActionBuilder(
          CameraArgs_GeneratedConverter(),
          ::CameraArgs_GeneratedArgsEditor,
          ::CameraScreen,
          ::CameraViewModel,
        ),
      // checkpoint
      "CheckPoint" to
        createActionBuilder(
          CheckPointArgs_GeneratedConverter(),
          ::CheckPointArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::CheckPointViewModel,
        ),
      "Idle" to
        createActionBuilder(
          IdleArgs_GeneratedConverter(),
          ::IdleArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::IdleViewModel,
        ),
      // device
      "ScanDeviceData" to
        createActionBuilder(
          ScanDeviceDataArgs_GeneratedConverter(),
          ::ScanDeviceDataArgs_GeneratedArgsEditor,
          ::ScanDeviceDataScreen,
          ::ScanDeviceDataViewModel,
        ),
      "VerifyDeviceData" to
        createActionBuilder(
          VerifyDeviceDataArgs_GeneratedConverter(),
          ::VerifyDeviceDataArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::VerifyDeviceDataViewModel,
        ),
      // downloadfactorydrive
      "DownloadFactoryDrive" to
        createActionBuilder(
          DownloadFactoryDriveArgs_GeneratedConverter(),
          ::DownloadFactoryDriveArgs_GeneratedArgsEditor,
          ::SubTaskScreen,
          ::DownloadFactoryDriveViewModel,
        ),
      "SyncFactoryDrive" to
        createActionBuilder(
          SyncFactoryDriveArgs_GeneratedConverter(),
          ::SyncFactoryDriveArgs_GeneratedArgsEditor,
          ::SubTaskScreen,
          ::SyncFactoryDriveViewModel,
        ),
      // fan
      "FanSpeed" to
        createActionBuilder(
          FanSpeedActionArgs_GeneratedConverter(),
          ::FanSpeedActionArgs_GeneratedArgsEditor,
          ::FanSpeedScreen,
          ::FanSpeedViewModel,
        ),
      // fingerprint
      "EnableFPWriteProtect" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::EnableFPWriteProtectViewModel,
        ),
      "FingerprintSensorFocaltech" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::FingerprintSensorFocaltechViewModel,
        ),
      "FingerprintSensorElan" to
        createActionBuilder(
          FingerprintSensorElanArgs_GeneratedConverter(),
          ::FingerprintSensorElanArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::FingerprintSensorElanViewModel,
        ),
      "HardwareProbe" to
        createActionBuilder(
          HardwareProbeArgs_GeneratedConverter(),
          ::HardwareProbeArgs_GeneratedArgsEditor,
          ::HardwareProbeScreen,
          ::HardwareProbeViewModel,
        ),
      "FpmcuInitializeEntropy" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::FpmcuInitializeEntropyViewModel,
        ),
      "UpdateFpFirmware" to
        createActionBuilder(
          UpdateFpFirmwareArgs_GeneratedConverter(),
          ::UpdateFpFirmwareArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::UpdateFpFirmwareViewModel,
        ),
      "VerifyFpKey" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::VerifyFpKeyViewModel,
        ),
      // keyboard
      "Keyboard" to
        createActionBuilder(
          KeyboardArgs_GeneratedConverter(),
          ::KeyboardArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::KeyboardViewModel,
        ),
      "KeyboardBacklight" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::KeyboardBacklightScreen,
          ::KeyboardBacklightViewModel,
        ),
      // memory
      "DramIdentification" to
        createActionBuilder(
          DramIdentificationArgs_GeneratedConverter(),
          ::DramIdentificationArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::DramIdentificationViewModel,
        ),
      // Led
      "Led" to
        createActionBuilder(
          LedActionArgs_GeneratedConverter(),
          ::LedActionArgs_GeneratedArgsEditor,
          ::LedScreen,
          ::LedViewModel,
        ),
      // lidswitch
      "LidSwitch" to
        createActionBuilder(
          LidSwitchArgs_GeneratedConverter(),
          ::LidSwitchArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::LidSwitchViewModel,
        ),
      // Lightbar
      "LightbarColor" to
        createActionBuilder(
          LightbarColorArgs_GeneratedConverter(),
          ::LightbarColorArgs_GeneratedArgsEditor,
          ::LightbarColorScreen,
          ::LightbarColorViewModel,
        ),
      // otaupdate
      "OtaUpdate" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::OtaUpdateViewModel,
        ),
      "OtaFinalize" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::OtaFinalizeViewModel,
        ),
      // power
      "SuspendResumeStress" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::SuspendResumeStressViewModel,
        ),
      // provision
      "ProvisionVPD" to
        createActionBuilder(
          ProvisionVpdArgs_GeneratedConverter(),
          ::ProvisionVpdArgs_GeneratedArgsEditor,
          ::SubTaskScreen,
          ::ProvisionVpdViewModel,
        ),
      "ProvisionGSC" to
        createActionBuilder(
          ProvisionGscArgs_GeneratedConverter(),
          ::ProvisionGscArgs_GeneratedArgsEditor,
          ::SubTaskScreen,
          ::ProvisionGscViewModel,
        ),
      "ProvisionDeviceId" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::SubTaskScreen,
          ::ProvisionDeviceIdViewModel,
        ),
      "UpdateCbi" to
        createActionBuilder(
          UpdateCbiArgs_GeneratedConverter(),
          ::UpdateCbiArgs_GeneratedArgsEditor,
          ::SubTaskScreen,
          ::UpdateCbiViewModel,
        ),
      "RemoveVpdKeys" to
        createActionBuilder(
          RemoveVpdKeysArgs_GeneratedConverter(),
          ::RemoveVpdKeysArgs_GeneratedArgsEditor,
          ::SubTaskScreen,
          ::RemoveVpdKeysViewModel,
        ),
      // qrcode
      "AddPopupQrcode" to
        createActionBuilder(
          AddPopupQrcodeArgs_GeneratedConverter(),
          ::AddPopupQrcodeArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::AddPopupQrcodeViewModel,
        ),
      "ClearPopupQrcode" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::ClearPopupQrcodeViewModel,
        ),
      // postmanufacturing
      "EnableRepairMode" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::EnableRepairModeViewModel,
        ),
      "EnablePostManufacturingApp" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::EnablePostManufacturingAppViewModel,
        ),
      // shell
      "ExecShell" to
        createActionBuilder(
          ExecShellArgs_GeneratedConverter(),
          ::ExecShellArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::ExecShellViewModel,
        ),
      // shutdown
      "Shutdown" to
        createActionBuilder(
          ShutdownArgs_GeneratedConverter(),
          ::ShutdownArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::ShutdownViewModel,
        ),
      "Reboot" to
        createActionBuilder(
          ShutdownArgs_GeneratedConverter(),
          ::ShutdownArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::RebootViewModel,
        ),
      "FullReboot" to
        createActionBuilder(
          ShutdownArgs_GeneratedConverter(),
          ::ShutdownArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::FullRebootViewModel,
        ),
      // rotation
      "TabletRotation" to
        createActionBuilder(
          TabletRotationArgs_GeneratedConverter(),
          ::TabletRotationArgs_GeneratedArgsEditor,
          ::TabletRotationScreen,
          ::TabletRotationViewModel,
        ),
      // tabletmode
      "TabletMode" to
        createActionBuilder(
          TabletModeArgs_GeneratedConverter(),
          ::TabletModeArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::TabletModeViewModel,
        ),
      // storage
      "ExpandUserdata" to
        createActionBuilder(
          ExpandUserdataArgs_GeneratedConverter(),
          ::ExpandUserdataArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::ExpandUserdataViewModel,
        ),
      "StorageHealth" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::StorageHealthViewModel,
        ),
      "StorageSimpleStress" to
        createActionBuilder(
          StorageSimpleStressArgs_GeneratedConverter(),
          ::StorageSimpleStressArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::StorageSimpleStressViewModel,
        ),
      // Stress test to verify the ExpandUserdata
      "ExpandUserdataTest" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::ExpandUserdataTestViewModel,
        ),
      // productionmode
      "IntelEnableProductionMode" to
        createActionBuilder(
          IntelEnableProductionModeArgs_GeneratedConverter(),
          ::IntelEnableProductionModeArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::IntelEnableProductionModeViewModel,
        ),
      "IdentifySoC" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::IdentifySoCViewModel,
        ),
      // stylus
      "Stylus" to
        createActionBuilder(
          StylusArgs_GeneratedConverter(),
          ::StylusArgs_GeneratedArgsEditor,
          ::StylusScreen,
          ::StylusViewModel,
        ),
      // apwriteprotect
      "ApWriteProtect" to
        createActionBuilder(
          ApWriteProtectArgs_GeneratedConverter(),
          ::ApWriteProtectArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::ApWriteProtectViewModel,
        ),
      // ecwriteprotect
      "EcWriteProtect" to
        createActionBuilder(
          EcWriteProtectArgs_GeneratedConverter(),
          ::EcWriteProtectArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::EcWriteProtectViewModel,
        ),
      // dsm
      "Cs35l56Calibration" to
        createActionBuilder(
          Cs35l56CalibrationArgs_GeneratedConverter(),
          ::Cs35l56CalibrationArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::Cs35l56CalibrationViewModel,
        ),
      "Rt1320Calibration" to
        createActionBuilder(
          Rt1320CalibrationArgs_GeneratedConverter(),
          ::Rt1320CalibrationArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::Rt1320CalibrationViewModel,
        ),
      "Tas2563Calibration" to
        createActionBuilder(
          Tas2563CalibrationArgs_GeneratedConverter(),
          ::Tas2563CalibrationArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::Tas2563CalibrationViewModel,
        ),
      "Wsa8845Calibration" to
        createActionBuilder(
          Wsa8845CalibrationArgs_GeneratedConverter(),
          ::Wsa8845CalibrationArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::Wsa8845CalibrationViewModel,
        ),
      // hwdescriptor
      "HwDescProbeProvision" to
        createActionBuilder(
          HwDescProbeProvisionArgs_GeneratedConverter(),
          ::HwDescProbeProvisionArgs_GeneratedArgsEditor,
          ::HwDescScreen,
          ::HwDescProbeProvisionViewModel,
        ),
      "HwDescVerify" to
        createActionBuilder(
          HwDescVerifyArgs_GeneratedConverter(),
          ::HwDescVerifyArgs_GeneratedArgsEditor,
          ::HwDescScreen,
          ::HwDescVerifyViewModel,
        ),
      // updateapp
      "InstallPreflashApp" to
        createActionBuilder(
          InstallPreflashAppArgs_GeneratedConverter(),
          ::InstallPreflashAppArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::InstallPreflashAppViewModel,
        ),
      "UpdateAppFromUmpire" to
        createActionBuilder(
          UpdateAppFromUmpireArgs_GeneratedConverter(),
          ::UpdateAppFromUmpireArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::UpdateAppFromUmpireViewModel,
        ),
      // Sensor
      "Accelerometer" to
        createActionBuilder(
          AccelerometerArgs_GeneratedConverter(),
          ::AccelerometerArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::AccelerometerViewModel,
        ),
      "AccelerometerCalibration" to
        createActionBuilder(
          AccelerometerCalibrationArgs_GeneratedConverter(),
          ::AccelerometerCalibrationArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::AccelerometerCalibrationViewModel,
        ),
      "AccelerometerLidAngle" to
        createActionBuilder(
          AccelerometerLidAngleArgs_GeneratedConverter(),
          ::AccelerometerLidAngleArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::AccelerometerLidAngleViewModel,
        ),
      "GyroscopeAngle" to
        createActionBuilder(
          GyroscopeAngleArgs_GeneratedConverter(),
          ::GyroscopeAngleArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::GyroscopeAngleViewModel,
        ),
      "GyroscopeCalibration" to
        createActionBuilder(
          GyroscopeCalibrationArgs_GeneratedConverter(),
          ::GyroscopeCalibrationArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::GyroscopeCalibrationViewModel,
        ),
      "LightSensor" to
        createActionBuilder(
          LightSensorTestArgs_GeneratedConverter(),
          ::LightSensorTestArgs_GeneratedArgsEditor,
          ::LightSensorScreen,
          ::LightSensorViewModel,
        ),
      "UsbReadWrite" to
        createActionBuilder(
          UsbReadWriteArgs_GeneratedConverter(),
          ::UsbReadWriteArgs_GeneratedArgsEditor,
          ::UsbReadWriteScreen,
          ::UsbReadWriteViewModel,
        ),
      "ThermalSensor" to
        createActionBuilder(
          ThermalSensorArgs_GeneratedConverter(),
          ::ThermalSensorArgs_GeneratedArgsEditor,
          ::SimpleMessageScreen,
          ::ThermalSensorViewModel,
        ),
      "VpdValidation" to
        createActionBuilder(
          VpdValidationArgs_GeneratedConverter(),
          ::VpdValidationArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::VpdValidationViewModel,
        ),
      "SetGbbFlags" to
        createActionBuilder(
          SetGbbFlagsArgs_GeneratedConverter(),
          ::SetGbbFlagsArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::SetGbbFlagsViewModel,
        ),
      "VerifyEk" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::VerifyEkViewModel,
        ),
      "VerifyGscBeforeLock" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::VerifyGscBeforeLockViewModel,
        ),
      "LockGSC" to
        createActionBuilder(
          LockGscArgs_GeneratedConverter(),
          ::LockGscArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::LockGscViewModel,
        ),
      "Bluetooth" to
        createActionBuilder(
          BluetoothArgs_GeneratedConverter(),
          ::BluetoothArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::BluetoothViewModel,
        ),
      "InstallOtherApp" to
        createActionBuilder(
          InstallOtherAppArgs_GeneratedConverter(),
          ::InstallOtherAppArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::InstallOtherAppViewModel,
        ),
      "EnableSystemHealthLogging" to
        createActionBuilder(
          EnableSystemHealthLoggingArgs_GeneratedConverter(),
          ::EnableSystemHealthLoggingArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::EnableSystemHealthLoggingViewModel,
        ),
      "DisableSystemHealthLogging" to
        createActionBuilder(
          NoArgs_GeneratedConverter(),
          ::NoArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::DisableSystemHealthLoggingViewModel,
        ),
      "Wifi" to
        createActionBuilder(
          WifiArgs_GeneratedConverter(),
          ::WifiArgs_GeneratedArgsEditor,
          ::NoUIScreen,
          ::WifiViewModel,
        ),
      "UploadFactoryReport" to
        createActionBuilder(
          UploadReportArgs_GeneratedConverter(),
          ::UploadReportArgs_GeneratedArgsEditor,
          ::UploadFileScreen,
          ::UploadFactoryReportViewModel,
        ),
      "UsbCFunctionalTest" to
        createActionBuilder(
          UsbCFunctionalTestArgs_GeneratedConverter(),
          ::UsbCFunctionalTestArgs_GeneratedArgsEditor,
          ::UsbCFunctionalTestScreen,
          ::UsbCFunctionalTestViewModel,
        ),
      "UploadCsr" to
        createActionBuilder(
          UploadCsrArgs_GeneratedConverter(),
          ::UploadCsrArgs_GeneratedArgsEditor,
          ::UploadFileScreen,
          ::UploadCsrViewModel,
        ),
      "Touchpad" to
        createActionBuilder(
          TouchpadArgs_GeneratedConverter(),
          ::TouchpadArgs_GeneratedArgsEditor,
          ::TouchpadScreen,
          ::TouchpadViewModel,
        ),
      "Touchscreen" to
        createActionBuilder(
          TouchscreenArgs_GeneratedConverter(),
          ::TouchscreenArgs_GeneratedArgsEditor,
          ::TouchscreenScreen,
          ::TouchscreenViewModel,
        ),
    )
}
