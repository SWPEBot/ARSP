package com.google.android.factory.factory.ui.mainscreen

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.factory.base.hwdescriptor.DebugPageContract
import com.google.android.factory.base.logging.Log
import com.google.android.factory.base.ui.KeepScreenOn
import com.google.android.factory.factory.R
import com.google.android.factory.factory.actions.hwdescriptor.HwDescUtils
import com.google.android.factory.factory.actions.interfaces.AnalyzedAction
import com.google.android.factory.factory.data.interfaces.FactoryContext
import com.google.android.factory.factory.proto.TestStatus
import com.google.android.factory.factory.ui.base.icon.TestStatusIcon
import com.google.android.factory.factory.ui.base.keyevent.KeyEventEffect
import com.google.android.factory.factory.ui.base.keyevent.KeyEventFilter
import com.google.android.factory.factory.ui.base.keyevent.KeyEventTextButton
import com.google.android.factory.factory.ui.mainscreen.BottomStatusBar
import com.google.android.factory.factory.ui.mainscreen.devicedata.DeviceDataEditorDialog
import com.google.android.factory.factory.ui.mainscreen.devicedata.DeviceDataEditorDialogState
import com.google.android.factory.factory.ui.mainscreen.dialog.EditArgsDialog
import com.google.android.factory.factory.ui.mainscreen.dialog.EditArgsDialogState
import com.google.android.factory.factory.ui.mainscreen.dialog.PopupQrcodeDialog
import com.google.android.factory.factory.ui.mainscreen.factorybug.FactoryBugViewModel
import com.google.android.factory.factory.ui.mainscreen.interfaces.TestListViewModelInterface
import com.google.android.factory.factory.ui.mainscreen.scaffold.Scaffold
import com.google.android.factory.factory.ui.mainscreen.snackbar.SnackBarHost
import com.google.android.factory.factory.ui.mainscreen.testhistory.TestHistoryDialog
import com.google.android.factory.factory.ui.mainscreen.testhistory.TestHistoryDialogViewModel
import com.google.android.factory.factory.ui.theme.Typography
import java.io.File
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The root UI component of the app. */
@Composable
private fun TestListSelector(
  expanded: Boolean,
  testListViewModel: TestListViewModelInterface,
  onDismissRequest: () -> Unit,
) {
  val allTestLists by testListViewModel.allTestListsFlow().collectAsStateWithLifecycle(emptyList())

  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    offset = DpOffset(128.dp, 0.dp),
  ) {
    for (testList in allTestLists) {
      // TODO: chungsheng - Add support for other repository sources, now assume all sources are app
      // assets.
      DropdownMenuItem(
        { Text(testList.path.fileName?.toString() ?: "") },
        onClick = {
          testListViewModel.switchTestList(testList)
          onDismissRequest()
        },
      )
    }
  }
}

@Composable
private fun EngineeringModeDialog(
  onDismissRequest: () -> Unit,
  onEnabledEngineeringMode: (password: String, onResult: (success: Boolean) -> Unit) -> Unit,
) {
  var password by rememberSaveable { mutableStateOf("") }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }
  var mismatchedCount by rememberSaveable { mutableIntStateOf(0) }

  Dialog(
    onDismissRequest,
    DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
  ) {
    Surface(shape = RoundedCornerShape(4.dp)) {
      Column(Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val passwordDisplay = stringResource(R.string.main_screen_password)
        TextField(
          value = password,
          onValueChange = { password = it },
          label = { Text(passwordDisplay) },
          singleLine = true,
          placeholder = { Text(passwordDisplay) },
          visualTransformation =
            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          trailingIcon = {
            val image =
              if (passwordVisible) {
                Icons.Filled.Visibility
              } else {
                Icons.Filled.VisibilityOff
              }
            val description =
              stringResource(
                if (passwordVisible) R.string.main_screen_hide_password
                else R.string.main_screen_show_password
              )
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
              Icon(imageVector = image, contentDescription = description)
            }
          },
        )
        if (mismatchedCount != 0) {
          Text(stringResource(R.string.main_screen_incorrect_password, mismatchedCount))
        }
        Row {
          Button({
            onEnabledEngineeringMode(
              password,
              { success -> if (success) onDismissRequest() else mismatchedCount++ },
            )
          }) {
            Text(stringResource(R.string.main_screen_ok))
          }
          Button({ onDismissRequest() }) { Text(stringResource(R.string.main_screen_cancel)) }
        }
      }
    }
  }
}

@Composable
private fun EngineeringModeIndicator(onDismissRequest: (() -> Unit)?) {
  Row(
    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error),
    Arrangement.Center,
    Alignment.CenterVertically,
  ) {
    Text(
      stringResource(R.string.main_screen_engineering_mode),
      color = MaterialTheme.colorScheme.onError,
    )
    if (onDismissRequest != null) {
      IconButton(
        onDismissRequest,
        colors =
          IconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
            disabledContentColor = MaterialTheme.colorScheme.onErrorContainer,
          ),
      ) {
        Icon(
          Icons.Filled.Close,
          contentDescription = stringResource(R.string.main_screen_disable_engineering_mode),
        )
      }
    }
  }
}

@Composable
private fun ColumnScope.TestItemControlDropdownMenuItems(
  testListViewModel: TestListViewModelInterface,
  analyzedAction: AnalyzedAction,
  onDismissRequest: () -> Unit,
) {
  val actionStatus by
    testListViewModel
      .getActionStatusFlowById(analyzedAction.id)
      .collectAsStateWithLifecycle(TestStatus.TEST_STATUS_UNSPECIFIED)
  val engineeringMode by testListViewModel.engineeringModeEnabled.collectAsStateWithLifecycle(false)
  val actionIdSetThatCanBeRestarted by
    testListViewModel.actionIdSetThatCanBeRestarted.collectAsStateWithLifecycle()
  val hasAllTestsRunBefore = remember { analyzedAction.id in actionIdSetThatCanBeRestarted }

  val runningActions by testListViewModel.runningActions.collectAsStateWithLifecycle()
  val allActiveChildrenCanBeAborted = remember {
    // We do not want to allow user to skip Barrier or other tests with disableAbort == true.
    runningActions.filter { analyzedAction.isAncestorOf(it.action) }.all { !it.action.disableAbort }
  }

  // If the test is not in engineering mode and the test is not the first test to run, we do not
  // want to allow user to skip the test. This will be the only item in the dropdown menu, so we
  // can just return.
  if (!engineeringMode && !hasAllTestsRunBefore) {
    DropdownMenuItem(
      text = { Text(stringResource(R.string.test_list_cannot_skip)) },
      onClick = {},
      enabled = false,
    )
    return
  }

  val isGroup = analyzedAction is AnalyzedAction.Group
  val hasOwnRestartFlag =
    (analyzedAction as? AnalyzedAction.Group)?.shouldRestartAllSubActions == true
  val isInheritingRestart = analyzedAction.restartParent != analyzedAction

  // Displays the simplified "Run/Restart" menu for "Atomic Units" (Single Actions,
  // Groups with restart flags, or items within an Atomic Group).
  // A restart shouldn't be allowed if:
  // 1. The parent group set shouldRestartAllSubActions.
  // 2. The test is active and shouldn't be aborted.
  val isAtomicUnit = !isGroup || hasOwnRestartFlag || isInheritingRestart

  if (actionStatus != TestStatus.TEST_STATUS_ACTIVE || allActiveChildrenCanBeAborted) {
    if (isAtomicUnit) {
      // CASE 1: Atomic Units - Only show Run/Restart for the designated restartParent.
      if (actionStatus != TestStatus.TEST_STATUS_PASSED || engineeringMode) {
        DropdownMenuItem(
          text = {
            Text(
              stringResource(
                if (
                  actionStatus == TestStatus.TEST_STATUS_UNSPECIFIED ||
                    actionStatus == TestStatus.TEST_STATUS_UNTESTED
                ) {
                  R.string.test_list_run_test
                } else {
                  R.string.test_list_restart_test
                },
                // Show the label of the actual restart target (the Atomic Group or itself)
                analyzedAction.restartParent.label,
              )
            )
          },
          onClick = {
            testListViewModel.restartActionTree(analyzedAction.restartParent.id)
            onDismissRequest()
          },
        )
      }
    } else {
      // CASE 2: Generic Groups - Show full management menu.
      if (engineeringMode) {
        // Limited to engineering mode to prevent restarting passed tests.
        DropdownMenuItem(
          text = {
            Text(
              stringResource(
                if (
                  actionStatus == TestStatus.TEST_STATUS_UNSPECIFIED ||
                    actionStatus == TestStatus.TEST_STATUS_UNTESTED
                ) {
                  R.string.test_list_run_all
                } else {
                  R.string.test_list_restart_all
                },
                analyzedAction.label,
              )
            )
          },
          onClick = {
            testListViewModel.restartActionTree(analyzedAction.id)
            onDismissRequest()
          },
        )
      }

      DropdownMenuItem(
        text = {
          Text(stringResource(R.string.test_list_restart_have_not_passed, analyzedAction.label))
        },
        onClick = {
          testListViewModel.restartActionHasNotPassed(analyzedAction.id)
          onDismissRequest()
        },
      )

      if (engineeringMode) {
        // Limited to engineering mode to prevent skipping failed tests.
        DropdownMenuItem(
          text = { Text(stringResource(R.string.test_list_run_untested, analyzedAction.label)) },
          onClick = {
            testListViewModel.runActionTree(analyzedAction.id)
            onDismissRequest()
          },
        )
      }
    }
  }

  // Clear state option for the specific target
  // 1. The test is in engineering mode.
  // 2. And the test is not active.
  if (engineeringMode && actionStatus != TestStatus.TEST_STATUS_ACTIVE) {
    DropdownMenuItem(
      text = {
        val label =
          if (isGroup) {
            stringResource(R.string.test_list_clear_all, analyzedAction.label)
          } else {
            stringResource(R.string.test_list_clear_test, analyzedAction.label)
          }
        Text(label)
      },
      onClick = {
        testListViewModel.clearActionTreeState(analyzedAction.id)
        onDismissRequest()
      },
    )
  }

  // Options to stop all active tests.
  val enableStop =
    actionStatus == TestStatus.TEST_STATUS_ACTIVE &&
      (engineeringMode || allActiveChildrenCanBeAborted)

  HorizontalDivider(Modifier, Dp.Hairline)

  DropdownMenuItem(
    text = { Text(stringResource(R.string.test_list_stop_all)) },
    onClick = {
      testListViewModel.stopAllActions()
      onDismissRequest()
    },
    enabled = enableStop,
  )

  DropdownMenuItem(
    text = {
      Text(stringResource(R.string.test_list_abort_active_and_continue, analyzedAction.label))
    },
    onClick = {
      // Note: This follow the ActionOnFailure behavior so the next action is not guaranteed to be
      // run. Thus don't need to worry about bypassing failed tests accidentally
      testListViewModel.abortActionTreeAndContinue(analyzedAction.id)
      onDismissRequest()
    },
    enabled = enableStop,
  )
}

@Composable
private fun TestListLabelWithDropdownMenu(
  testListViewModel: TestListViewModelInterface,
  editArgsDialogState: EditArgsDialogState,
  analyzedAction: AnalyzedAction,
  onHistoryDialogRequest: () -> Unit,
) {
  var isDropdownMenuExpanded by rememberSaveable { mutableStateOf(false) }
  val onDismissRequest = { isDropdownMenuExpanded = false }

  Column {
    TextButton(onClick = { isDropdownMenuExpanded = !isDropdownMenuExpanded }) {
      Text(analyzedAction.label, maxLines = 1)
    }
    DropdownMenu(expanded = isDropdownMenuExpanded, onDismissRequest = onDismissRequest) {
      TestItemControlDropdownMenuItems(testListViewModel, analyzedAction, onDismissRequest)
      if (analyzedAction is AnalyzedAction.Action) {
        HorizontalDivider(Modifier, Dp.Hairline)
        DropdownMenuItem(
          { Text("Edit argument of \"${analyzedAction.label}\"") },
          onClick = {
            editArgsDialogState.openDialog(analyzedAction)
            onDismissRequest()
          },
        )
      }
      HorizontalDivider(Modifier, Dp.Hairline)
      DropdownMenuItem(
        { Text(stringResource(R.string.test_list_test_history)) },
        onClick = {
          onHistoryDialogRequest()
          onDismissRequest()
        },
      )
    }
  }
}

private const val STATUS_ICON_SIZE = 16
private const val ICON_SIZE = 40
private const val INDENT_SIZE = 20
private const val ROW_HEIGHT = 40

@Composable
private fun RunIfTestListItem(
  testListViewModel: TestListViewModelInterface,
  testHistoryDialogViewModel: TestHistoryDialogViewModel,
  editArgsDialogState: EditArgsDialogState,
  analyzedAction: AnalyzedAction.Action,
) {
  val currentActionStatus by
    testListViewModel
      .getActionStatusFlowById(analyzedAction.id)
      .collectAsStateWithLifecycle(TestStatus.TEST_STATUS_UNSPECIFIED)

  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(ROW_HEIGHT.dp)) {
    Box(modifier = Modifier.width(ICON_SIZE.dp), contentAlignment = Alignment.Center) {
      Icon(
        Icons.Filled.QuestionMark,
        contentDescription = "run if icon",
        modifier = Modifier.size(STATUS_ICON_SIZE.dp),
      )
    }
    TestStatusIcon(currentActionStatus, modifier = Modifier.size(STATUS_ICON_SIZE.dp))
    TestListLabelWithDropdownMenu(
      testListViewModel,
      editArgsDialogState,
      analyzedAction,
      { testHistoryDialogViewModel.openDialog(analyzedAction) },
    )
  }
}

@Composable
private fun TestListItem(
  testListViewModel: TestListViewModelInterface,
  testHistoryDialogViewModel: TestHistoryDialogViewModel,
  editArgsDialogState: EditArgsDialogState,
  analyzedAction: AnalyzedAction,
) {
  val currentActionStatus by
    testListViewModel
      .getActionStatusFlowById(analyzedAction.id)
      .collectAsStateWithLifecycle(TestStatus.TEST_STATUS_UNSPECIFIED)
  var isGroupExpandedByUser by rememberSaveable { mutableIntStateOf(0) }
  val runningActions by testListViewModel.runningActions.collectAsStateWithLifecycle()
  val isChildActive = runningActions.any { analyzedAction.isAncestorOf(it.action) }
  val isGroupExpanded = if (isGroupExpandedByUser == 0) isChildActive else isGroupExpandedByUser > 0
  Column {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(ROW_HEIGHT.dp)) {
      if (analyzedAction is AnalyzedAction.Group) {
        IconButton(
          onClick = { isGroupExpandedByUser = if (isGroupExpanded) -1 else 1 },
          modifier = Modifier.size(ICON_SIZE.dp),
        ) {
          Icon(
            if (isGroupExpanded) Icons.Filled.KeyboardArrowDown
            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isGroupExpanded) "expanded group" else "collapsed group",
          )
        }
      } else {
        Spacer(modifier = Modifier.width(ICON_SIZE.dp))
      }
      TestStatusIcon(currentActionStatus, modifier = Modifier.size(STATUS_ICON_SIZE.dp))
      TestListLabelWithDropdownMenu(
        testListViewModel,
        editArgsDialogState,
        analyzedAction,
        { testHistoryDialogViewModel.openDialog(analyzedAction) },
      )
    }
    if (analyzedAction is AnalyzedAction.Group && isGroupExpanded) {
      if (analyzedAction.runIfAction != null) {
        RunIfTestListItem(
          testListViewModel,
          testHistoryDialogViewModel,
          editArgsDialogState,
          analyzedAction.runIfAction!!,
        )
      }
      Row(modifier = Modifier.padding(start = INDENT_SIZE.dp).height(IntrinsicSize.Min)) {
        VerticalDivider(Modifier.width(2.dp))
        Column {
          for (subAction in analyzedAction.subActions) {
            TestListItem(
              testListViewModel,
              testHistoryDialogViewModel,
              editArgsDialogState,
              subAction,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AppDropdownMenu(
  factoryContext: FactoryContext,
  testListViewModel: TestListViewModelInterface,
  factoryBugViewModel: FactoryBugViewModel,
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  deviceDataEditorDialogState: DeviceDataEditorDialogState,
  _testHistoryDialogViewModel: TestHistoryDialogViewModel,
) {
  val context = LocalContext.current
  val currentTestList by testListViewModel.currentTestList.collectAsStateWithLifecycle()
  val rootAction = currentTestList?.root
  var testListSelectorExpanded by rememberSaveable { mutableStateOf(false) }
  var engineeringModeDialogExpanded by rememberSaveable { mutableStateOf(false) }
  val engineeringMode by testListViewModel.engineeringModeEnabled.collectAsStateWithLifecycle(false)
  val adbTerminalIntent =
    remember(context) { context.packageManager.getLaunchIntentForPackage("com.google.android.desktop.terminal") }

  val coroutineScope = rememberCoroutineScope()
  var isOriginalPolicyEnforcing by remember { mutableStateOf(false) }
  val hwDescLauncher =
    rememberLauncherForActivityResult(contract = DebugPageContract()) {
      coroutineScope.launch(Dispatchers.IO) {
        HwDescUtils.restoreEnforcingPolicy(factoryContext.adbClient, isOriginalPolicyEnforcing)
        HwDescUtils.tearDownHwDescEnvironment(factoryContext.adbClient)
      }
    }

  KeyEventEffect(filter = KeyEventFilter(Key.Zero, ctrl = true, alt = true)) {
    engineeringModeDialogExpanded = true
  }

  if (engineeringModeDialogExpanded) {
    EngineeringModeDialog(
      { engineeringModeDialogExpanded = false },
      { password, onResult -> testListViewModel.enableEngineeringMode(password, onResult) },
    )
  }
  DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
    rootAction?.let {
      TestItemControlDropdownMenuItems(testListViewModel, it, onDismissRequest)
      HorizontalDivider(Modifier, Dp.Hairline)
    }

    DropdownMenuItem(
      {
        Text(stringResource(R.string.test_list_switch_test_list))
        TestListSelector(testListSelectorExpanded, testListViewModel) {
          testListSelectorExpanded = false
          onDismissRequest()
        }
      },
      onClick = { testListSelectorExpanded = !testListSelectorExpanded },
      enabled = engineeringMode,
    )

    DropdownMenuItem(
      { Text(stringResource(R.string.test_list_toggle_engineering_mode)) },
      onClick = {
        if (engineeringMode) {
          testListViewModel.disableEngineeringMode()
          onDismissRequest()
        } else {
          engineeringModeDialogExpanded = true
        }
      },
      enabled = currentTestList?.options?.engineeringPasswordSha1?.isNotEmpty() ?: false,
    )
    HorizontalDivider(Modifier, Dp.Hairline)
    DropdownMenuItem(
      { Text("Open device data editor") },
      onClick = {
        deviceDataEditorDialogState.isOpened.value = true
        onDismissRequest()
      },
    )
    DropdownMenuItem(
      { Text("Open adb terminal") },
      onClick = {
        adbTerminalIntent?.let(context::startActivity)
        onDismissRequest()
      },
      enabled = adbTerminalIntent != null,
    )
    HorizontalDivider(Modifier, Dp.Hairline)
    DropdownMenuItem(
      { Text("Open Hardware Descriptor Debug page") },
      onClick = {
        coroutineScope.launch(Dispatchers.IO) {
          // TODO: b/503230989 - Remove the setup logic once runtime mapping table patch is removed.
          val verifyAction =
            rootAction?.find { it is AnalyzedAction.Action && it.actionName == "HwDescVerify" }
              as? AnalyzedAction.Action
          if (verifyAction == null) {
            Log.error("No HwDescVerify action found")
            return@launch
          }
          val mappingTableSourceArg =
            verifyAction.args.fieldsMap.get("mappingTableSource")?.stringValue ?: ""
          val mappingTablePatchSourceArg =
            verifyAction.args.fieldsMap.get("mappingTablePatchSource")?.stringValue ?: ""
          val ecComponentManifestSourceArg =
            verifyAction.args.fieldsMap.get("ecComponentManifestSource")?.stringValue ?: ""
          val ishComponentManifestSourceArg =
            verifyAction.args.fieldsMap.get("ishComponentManifestSource")?.stringValue ?: ""

          isOriginalPolicyEnforcing =
            HwDescUtils.setupHwDescEnvironment(
                factoryContext = factoryContext,
                mappingTableSourceArg = mappingTableSourceArg,
                mappingTablePatchSourceArg = mappingTablePatchSourceArg,
                ecComponentManifestSourceArg = ecComponentManifestSourceArg,
                ishComponentManifestSourceArg = ishComponentManifestSourceArg,
              )
              .getOrElse {
                Log.error("Failed to setup HwDesc environment: ${it.message}")
                return@launch
              }

          withContext(Dispatchers.Main) { hwDescLauncher.launch(Unit) }
        }
        onDismissRequest()
      },
    )
    // Factory bug item
    val isGeneratingFactoryBug by
      factoryBugViewModel.isGeneratingFactoryBug.collectAsStateWithLifecycle()
    HorizontalDivider(Modifier, Dp.Hairline)
    DropdownMenuItem(
      { Text("Save factory bug to /tmp") },
      onClick = {
        factoryBugViewModel.saveFactoryBugTo(File("/tmp"))
        onDismissRequest()
      },
      enabled = !isGeneratingFactoryBug,
    )
    val storageDevices by
      factoryContext.systemInfo
        .pollingStorageDevices(2.seconds)
        .collectAsStateWithLifecycle(emptyList())
    storageDevices
      .filter { it.isRemovable && it.mountPoint != null }
      .forEach {
        DropdownMenuItem(
          { Text("Save factory bug to ${it.description}") },
          onClick = {
            factoryBugViewModel.saveFactoryBugTo(it.mountPoint!!.toFile())
            onDismissRequest()
          },
          enabled = !isGeneratingFactoryBug,
        )
      }
  }
}

/**
 * A selector to choose which test to be shown on the screen. This is only used when there are
 * multiple tests running at once.
 */
@Composable
private fun RowScope.OnScreenTestSelector(testListViewModel: TestListViewModelInterface) {
  val runningActions by testListViewModel.runningActions.collectAsStateWithLifecycle()
  val onScreenRunningActionContext by
    testListViewModel.onScreenRunningActionContext.collectAsStateWithLifecycle()
  val selectedTabIndex = max(0, runningActions.indexOfFirst { it == onScreenRunningActionContext })

  LaunchedEffect(runningActions, onScreenRunningActionContext) {
    if (runningActions.isEmpty()) {
      testListViewModel.onScreenRunningActionContext.value = null
    }
    if (!runningActions.contains(onScreenRunningActionContext)) {
      testListViewModel.onScreenRunningActionContext.value = runningActions.firstOrNull()
    }
  }

  Surface(modifier = Modifier.weight(4f), shape = RoundedCornerShape(6.dp)) {
    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
      if (runningActions.isEmpty()) {
        // TabRow does not accept zero tab. Add an empty tab here.
        Tab(
          selected = true,
          onClick = {},
          text = { Text(stringResource(R.string.main_screen_no_test_running)) },
          enabled = false,
        )
      }
      for ((index, runningAction) in runningActions.withIndex()) {
        Tab(
          selected = selectedTabIndex == index,
          onClick = { testListViewModel.onScreenRunningActionContext.value = runningAction },
          text = { Text(runningAction.action.label) },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
  factoryContext: FactoryContext,
  testListViewModel: TestListViewModelInterface,
  factoryBugViewModel: FactoryBugViewModel,
  onTestListViewExpandClick: () -> Unit,
  deviceDataEditorDialogState: DeviceDataEditorDialogState,
  testHistoryDialogViewModel: TestHistoryDialogViewModel,
) {
  val currentTestList by testListViewModel.currentTestList.collectAsStateWithLifecycle()
  val rootAction = currentTestList?.root
  val context = LocalContext.current
  var isDropdownMenuExpanded by rememberSaveable { mutableStateOf(false) }

  TopAppBar(
    navigationIcon = {
      IconButton(onClick = { isDropdownMenuExpanded = true }) {
        Icon(
          Icons.AutoMirrored.Filled.List,
          contentDescription = stringResource(R.string.top_bar_expand_test_list),
        )
      }
      AppDropdownMenu(
        factoryContext,
        testListViewModel,
        factoryBugViewModel,
        isDropdownMenuExpanded,
        onDismissRequest = { isDropdownMenuExpanded = false },
        deviceDataEditorDialogState,
        testHistoryDialogViewModel,
      )
    },
    title = {
      Row(
        Modifier.height(TopAppBarDefaults.TopAppBarExpandedHeight),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.weight(1f)) {
          TextButton(onClick = onTestListViewExpandClick) {
            Text(
              rootAction?.label ?: "",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = Typography.titleLarge,
            )
          }
        }
        OnScreenTestSelector(testListViewModel)
      }
    },
    actions = {
      TextButton(
        onClick = {
          val intent = Intent(context, OssLicensesMenuActivity::class.java)
          context.startActivity(intent)
        }
      ) {
        Text(stringResource(R.string.top_bar_license))
      }
      LanguagePicker()
      IconButton(onClick = { rootAction?.let { testListViewModel.runActionTree(it.id) } }) {
        Icon(
          Icons.Filled.PlayArrow,
          contentDescription = stringResource(R.string.top_bar_start_auto_test),
        )
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
  )
}

@Composable
private fun TestListView(
  testListViewModel: TestListViewModelInterface,
  testHistoryDialogViewModel: TestHistoryDialogViewModel,
  editArgsDialogState: EditArgsDialogState,
) {
  val currentTestList by testListViewModel.currentTestList.collectAsStateWithLifecycle()
  val engineeringMode by testListViewModel.engineeringModeEnabled.collectAsStateWithLifecycle(false)

  Column {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
      currentTestList?.let {
        for (subAction in it.root.subActions) {
          TestListItem(
            testListViewModel,
            testHistoryDialogViewModel,
            editArgsDialogState,
            subAction,
          )
        }
      }
    }
    AnimatedVisibility(engineeringMode) {
      EngineeringModeIndicator(
        onDismissRequest =
          if (currentTestList?.options?.engineeringPasswordSha1?.isNotEmpty() ?: false) {
            { testListViewModel.disableEngineeringMode() }
          } else {
            null
          }
      )
    }
  }
}

@Composable
private fun MainScreenContent(testListViewModel: TestListViewModelInterface) {
  val onScreenRunningActionContext by
    testListViewModel.onScreenRunningActionContext.collectAsStateWithLifecycle()
  val engineeringMode by testListViewModel.engineeringModeEnabled.collectAsStateWithLifecycle(false)

  Column(modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      onScreenRunningActionContext?.let { it.actionUI(it.actionViewModel) }
    }
    if (engineeringMode) {
      onScreenRunningActionContext?.let {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
          KeyEventTextButton(
            filter = KeyEventFilter(Key.Enter, ctrl = true),
            onClick = { testListViewModel.markActionAsPassed(it.action.id) },
            text = stringResource(R.string.main_screen_mark_passed),
          )
          Spacer(modifier = Modifier.width(16.dp))
          KeyEventTextButton(
            filter = KeyEventFilter(Key.Escape, ctrl = true),
            onClick = { testListViewModel.markActionAsFailed(it.action.id) },
            text = stringResource(R.string.main_screen_mark_failed),
          )
        }
      }
    }
  }
}

@Composable
private fun MainScreen(
  factoryContext: FactoryContext,
  testListViewModel: TestListViewModelInterface,
  factoryBugViewModel: FactoryBugViewModel,
  deviceDataEditorDialogState: DeviceDataEditorDialogState,
  editArgsDialogState: EditArgsDialogState,
  testHistoryDialogViewModel: TestHistoryDialogViewModel,
) {
  var testListViewExpanded by rememberSaveable { mutableStateOf(true) }
  val isGeneratingFactoryBug by
    factoryBugViewModel.isGeneratingFactoryBug.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      TopBar(
        factoryContext,
        testListViewModel,
        factoryBugViewModel,
        onTestListViewExpandClick = { testListViewExpanded = !testListViewExpanded },
        deviceDataEditorDialogState,
        testHistoryDialogViewModel,
      )
    },
    bottomBar = { BottomStatusBar(factoryContext, isGeneratingFactoryBug) },
    testListView = {
      TestListView(testListViewModel, testHistoryDialogViewModel, editArgsDialogState)
    },
    testListViewExpanded = testListViewExpanded,
    logsPanel = {
      LiveLogsPanel(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        testListViewModel = testListViewModel,
      )
    },
  ) {
    MainScreenContent(testListViewModel)
  }
}

@Composable
fun MainScreenWithOverlay(
  factoryContext: FactoryContext,
  testListViewModel: TestListViewModelInterface,
) {
  val mainScreenScope = rememberCoroutineScope()
  val deviceDataEditorDialogState = remember {
    DeviceDataEditorDialogState(factoryContext, mainScreenScope)
  }
  val editArgsDialogState = remember { EditArgsDialogState() }
  val testHistoryDialogViewModel = remember { TestHistoryDialogViewModel(mainScreenScope) }
  val factoryBugViewModel = remember { FactoryBugViewModel(factoryContext, mainScreenScope) }

  Box(Modifier.fillMaxSize()) {
    KeepScreenOn()
    MainScreen(
      factoryContext,
      testListViewModel,
      factoryBugViewModel,
      deviceDataEditorDialogState,
      editArgsDialogState,
      testHistoryDialogViewModel,
    )
    PopupQrcodeDialog(factoryContext)
    DeviceDataEditorDialog(deviceDataEditorDialogState)
    SnackBarHost(factoryBugViewModel)
    EditArgsDialog(editArgsDialogState, factoryContext)
    TestHistoryDialog(testHistoryDialogViewModel)
  }
}
