package com.google.android.factory.factory.domain.actions

import com.google.android.factory.base.logging.Log
import com.google.android.factory.factory.actions.ActionBuilder
import com.google.android.factory.factory.actions.interfaces.AnalyzedAction
import com.google.android.factory.factory.actions.interfaces.FactoryActionResult
import com.google.android.factory.factory.data.interfaces.ActionState
import com.google.android.factory.factory.data.interfaces.FactoryContext
import com.google.android.factory.factory.domain.logging.actionInvocationId
import com.google.android.factory.factory.domain.logging.withActionStep
import com.google.android.factory.factory.proto.ActionOnFailure
import com.google.android.factory.factory.proto.TestStatus
import com.google.protobuf.Struct
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Handles the logic of running an action tree. */
class ActionTreeRunner(
  private val factoryContext: FactoryContext,
  private val actionBuilders: Map<String, ActionBuilder>,
  private val deferredActionRunner: DeferredActionRunner,
) {
  private val _runningActions: MutableStateFlow<List<RunningActionContext>> =
    MutableStateFlow(emptyList())
  val runningActions: StateFlow<List<RunningActionContext>> = _runningActions.asStateFlow()

  private var job: Job? = null

  /** Traverses the action tree and runs the actions in order. */
  suspend fun run(action: AnalyzedAction) = coroutineScope {
    stop()
    job = launch {
      factoryContext.testListRepository.setRunningActionTreeRootId(action.id)
      val unused = traverseAndRunActionTree(action)
      factoryContext.testListRepository.setRunningActionTreeRootId("")
    }
    job?.join()
  }

  /** Stops the action tree. This will stop all running actions and mark them as failed. */
  suspend fun stop() {
    if (job == null || job!!.isActive == false) return

    withContext(NonCancellable) {
      Log.info("Stop all actions")
      job?.cancelAndJoin()
      _runningActions.update { emptyList() }
      val activeActionIds =
        factoryContext.actionStateRepository
          .getActionIdsFlowByStatus(listOf(TestStatus.TEST_STATUS_ACTIVE))
          .first()
      val actionStates =
        activeActionIds
          .map {
            async {
              factoryContext.actionStateRepository.getActionStateById(it).apply {
                status = TestStatus.TEST_STATUS_FAILED
              }
            }
          }
          .awaitAll()
      factoryContext.actionStateRepository.upsertActionStates(*actionStates.toTypedArray())
      factoryContext.testListRepository.setRunningActionTreeRootId("")
    }
  }

  private suspend fun traverseAndRunActionTree(
    action: AnalyzedAction
  ): Pair<TestStatus, ActionOnFailure> {
    when (action) {
      is AnalyzedAction.Action -> return Pair(runAction(action), action.actionOnFailure)
      is AnalyzedAction.NestedGroup -> return runActionSequencialGroup(action)
      is AnalyzedAction.LeafGroup -> {
        if (action.isParallel == true) {
          return runActionParallelGroup(action)
        }
        return runActionSequencialGroup(action)
      }
    }
  }

  private suspend fun runActionParallelGroup(
    action: AnalyzedAction.Group
  ): Pair<TestStatus, ActionOnFailure> {
    Log.info("Run action group in parallel ${action.id}")
    val actionState = factoryContext.actionStateRepository.getActionStateById(action.id)
    factoryContext.actionStateRepository.upsertActionStates(
      actionState.apply { status = TestStatus.TEST_STATUS_ACTIVE }
    )

    if (action.runIfAction != null) {
      val actionStatus = runAction(action.runIfAction!!)
      if (actionStatus != TestStatus.TEST_STATUS_PASSED) {
        coroutineScope {
          action
            .map {
              launch {
                val state = factoryContext.actionStateRepository.getActionStateById(it.id)
                factoryContext.actionStateRepository.upsertActionStates(
                  state.apply { status = TestStatus.TEST_STATUS_SKIPPED }
                )
              }
            }
            .joinAll()
        }
        return Pair(TestStatus.TEST_STATUS_SKIPPED, action.actionOnFailure)
      }
    }

    val results =
      coroutineScope {
        action.subActions
          .map { child -> async { traverseAndRunActionTree(child) } }
          .awaitAll()
      }

    val overallStatus =
      if (results.all { it.first == TestStatus.TEST_STATUS_PASSED }) {
        TestStatus.TEST_STATUS_PASSED
      } else {
        TestStatus.TEST_STATUS_FAILED
      }

    actionState.status = overallStatus
    factoryContext.actionStateRepository.upsertActionStates(actionState)
    return Pair(overallStatus, action.actionOnFailure)
  }

  private suspend fun runActionSequencialGroup(
    action: AnalyzedAction.Group
  ): Pair<TestStatus, ActionOnFailure> {
    Log.info("Run action group ${action.id}")
    val actionState = factoryContext.actionStateRepository.getActionStateById(action.id)
    factoryContext.actionStateRepository.upsertActionStates(
      actionState.apply { status = TestStatus.TEST_STATUS_ACTIVE }
    )

    if (action.runIfAction != null) {
      val actionStatus = runAction(action.runIfAction!!)
      if (actionStatus != TestStatus.TEST_STATUS_PASSED) {
        coroutineScope {
          action
            .map {
              launch {
                val state = factoryContext.actionStateRepository.getActionStateById(it.id)
                factoryContext.actionStateRepository.upsertActionStates(
                  state.apply { status = TestStatus.TEST_STATUS_SKIPPED }
                )
              }
            }
            .joinAll()
        }
        return Pair(TestStatus.TEST_STATUS_SKIPPED, action.actionOnFailure)
      }
    }

    actionState.status = TestStatus.TEST_STATUS_PASSED
    for (child in action.subActions) {
      val (childStatus, actionOnFailure) = traverseAndRunActionTree(child)
      if (childStatus == TestStatus.TEST_STATUS_FAILED) {
        actionState.status = TestStatus.TEST_STATUS_FAILED
        when (actionOnFailure) {
          ActionOnFailure.ACTION_AUTO,
          ActionOnFailure.ACTION_NEXT -> continue
          ActionOnFailure.ACTION_PARENT -> {
            factoryContext.actionStateRepository.upsertActionStates(actionState)
            return Pair(actionState.status, action.actionOnFailure)
          }
          ActionOnFailure.ACTION_STOP -> {
            factoryContext.actionStateRepository.upsertActionStates(actionState)
            return Pair(actionState.status, ActionOnFailure.ACTION_STOP)
          }
          ActionOnFailure.UNRECOGNIZED -> {
            Log.error("Unsupported ActionOnFailure: ${actionOnFailure}")
            return Pair(actionState.status, ActionOnFailure.ACTION_AUTO)
          }
        }
      }
    }
    factoryContext.actionStateRepository.upsertActionStates(actionState)
    return Pair(actionState.status, action.actionOnFailure)
  }

  private suspend fun runAction(action: AnalyzedAction.Action): TestStatus {
    Log.info("Run action ${action.id}")
    val actionState = factoryContext.actionStateRepository.getActionStateById(action.id)
    val actionBuilder = actionBuilders[action.actionName]
    if (actionBuilder == null) {
      Log.error("Action builder not found for action ${action.actionName}: ${action.id}")
      factoryContext.actionStateRepository.upsertActionStates(
        actionState.apply { status = TestStatus.TEST_STATUS_FAILED }
      )
      return actionState.status
    }
    val argsOverride =
      factoryContext.actionStateRepository.getArgsOverride(action.id) ?: action.args
    return when (actionState.status) {
      TestStatus.TEST_STATUS_UNSPECIFIED,
      TestStatus.TEST_STATUS_UNTESTED,
      TestStatus.TEST_STATUS_ACTIVE -> {
        runUnfinishedAction(action, argsOverride, actionState, actionBuilder)
      }
      TestStatus.TEST_STATUS_FAILED,
      TestStatus.TEST_STATUS_FAILED_AND_WAIVED,
      TestStatus.TEST_STATUS_SKIPPED,
      TestStatus.TEST_STATUS_PASSED -> {
        actionState.status
      }
      TestStatus.UNRECOGNIZED -> {
        Log.error("Unsupported test status: ${actionState.status}")
        actionState.status
      }
    }
  }

  private suspend fun runUnfinishedAction(
    action: AnalyzedAction.Action,
    args: Struct,
    actionState: ActionState,
    actionBuilder: ActionBuilder,
  ): TestStatus {
    if (action.iteration > 1 || action.retry > 0) {
      Log.info(
        "Action ${action.id} will run ${action.iteration} iterations and retry at most ${action.retry} times"
      )
    }
    while (
      actionState.passedCounter < action.iteration && actionState.failedCounter <= action.retry
    ) {
      val result = runActionSingleIteration(action, args, actionState, actionBuilder)
      if (action.iteration > 1 || action.retry > 0) {
        Log.info(
          "Action ${action.id} iteration ${actionState.passedCounter + actionState.failedCounter} result: ${result}"
        )
      }
      factoryContext.actionStateRepository.upsertActionStates(
        actionState.apply {
          resumeCounter = -1 // Set to -1 because it will be incremented to 0 in the next iteration.
          if (result == TestStatus.TEST_STATUS_PASSED) {
            passedCounter++
          } else {
            failedCounter++
          }
        }
      )
    }
    if (action.iteration > 1 || action.retry > 0) {
      Log.info(
        "Action ${action.id} passedCounter: ${actionState.passedCounter}, failedCounter: ${actionState.failedCounter}"
      )
      if (actionState.passedCounter == action.iteration) {
        Log.info("Action ${action.id} passed because it reached the iteration goal")
      } else {
        Log.info("Action ${action.id} failed because it reached the retry limit")
      }
    }
    factoryContext.actionStateRepository.upsertActionStates(
      actionState.apply {
        resumeCounter = 0
        status =
          if (actionState.passedCounter == action.iteration) {
            TestStatus.TEST_STATUS_PASSED
          } else {
            TestStatus.TEST_STATUS_FAILED
          }
      }
    )
    return actionState.status
  }

  private suspend fun runActionSingleIteration(
    action: AnalyzedAction.Action,
    args: Struct,
    actionState: ActionState,
    actionBuilder: ActionBuilder,
  ): TestStatus =
    withActionStep(action.id, args) {
      val actionViewModel = actionBuilder.buildActionViewModel(factoryContext)
      factoryContext.actionStateRepository.upsertActionStates(
        actionState.apply {
          if (status != TestStatus.TEST_STATUS_ACTIVE) {
            status = TestStatus.TEST_STATUS_ACTIVE
            resumeCounter = 0
          } else {
            resumeCounter++
          }
        }
      )
      _runningActions.update {
        it +
          RunningActionContext(
            action,
            actionBuilder.actionUI,
            actionViewModel,
            currentStep.actionInvocationId!!,
          )
      }
      val result =
        deferredActionRunner.runAction(action.id) {
          val result = actionViewModel.runAction(action, args, actionState.resumeCounter)
          when (result) {
            FactoryActionResult.Success -> TestStatus.TEST_STATUS_PASSED
            FactoryActionResult.Failure -> TestStatus.TEST_STATUS_FAILED
          }
        }
      _runningActions.update { it.filter { runningAction -> runningAction.action.id != action.id } }
      result
    }
}
