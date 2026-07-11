package com.google.android.factory.factory.actions.removable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.factory.factory.viewmodel.display.ExternalDisplayTestPhase
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Monitor

private val ColorPass = Color(0xFF2E7D32)
private val ColorFail = Color(0xFFC62828)
private val ColorWaiting = Color(0xFF1565C0)
private val ColorRunning = Color(0xFFE65100)

@Composable
fun UsbCFunctionalTestScreen(viewModel: UsbCFunctionalTestViewModel, modifier: Modifier = Modifier) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()
  val onKeyPressed = { event: KeyEvent ->
    if (event.type == KeyEventType.KeyDown) {
      viewModel.checkIsRightDisplayProof(event.key)
      true
    } else {
      false
    }
  }

  val requester = remember { FocusRequester() }
  LaunchedEffect(Unit) { requester.requestFocus() }

  UsbCExternalDisplayPresentationHost(ui.hdmiTestPhase, viewModel::checkIsRightDisplayProof)

  Column(
    modifier = modifier
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .verticalScroll(scrollState)
      .onKeyEvent(onKeyPressed)
      .focusRequester(requester)
      .focusable(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    OverallStatusBanner(ui = ui)

    Row(
      modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      PortCard(modifier = Modifier.weight(1f).fillMaxHeight(), state = ui.port1)
      PortCard(modifier = Modifier.weight(1f).fillMaxHeight(), state = ui.port2)
      PortCard(modifier = Modifier.weight(1f).fillMaxHeight(), state = ui.hdmiPort, isHdmi = true)
    }

    // TypeC CC1/CC2 orientation — full-width live-monitor card, shown when CC test is enabled.
    ui.ccPhase?.let { phase ->
      TypeCCcCard(phase = phase, port1 = ui.port1, port2 = ui.port2, hdmiPort = ui.hdmiPort)
    }

    if (!ui.hdmiPort.isFinished && ui.hdmiPort.status != "SKIPPED") {
      HdmiInstructionCard(
        phase = ui.hdmiTestPhase,
        isConnected = ui.hdmiPort.isPresent,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// TypeC CC1/CC2 orientation card
// ─────────────────────────────────────────────────────────────────────────────


/**
 * Full-width live-monitor card for the TypeC CC1/CC2 orientation test.
 *
 * Layout: [emoji icon 28sp] | [headline (ExtraBold) + detail (bodySmall)]
 *
 * | Phase              | Background    | Icon | Headline                                  |
 * |--------------------|---------------|------|-------------------------------------------|
 * | DETECTING_FIRST    | theme card    | 🔍   | CC detected / Detecting…                  |
 * | WAITING_UNPLUG     | solid orange  | ⚡   | ACTION — Unplug TypeC cable               |
 * | WAITING_REPLUG     | solid orange  | 🔄   | Cable removed — Re-plug in OPPOSITE       |
 * | DONE (pass)        | solid green   | ✅   | CC1 ✓  CC2 ✓  Both CC pins verified       |
 * | DONE (fail)        | solid red     | ❌   | Same CC pin detected twice                |
 */
@Composable
private fun TypeCCcCard(
  phase: TypeCCcPhase,
  port1: UsbCPortState = UsbCPortState(),
  port2: UsbCPortState = UsbCPortState(),
  hdmiPort: UsbCPortState = UsbCPortState(),
  modifier: Modifier = Modifier,
) {
  if (phase is TypeCCcPhase.IDLE) return

  val secondaryContainer   = MaterialTheme.colorScheme.secondaryContainer
  val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer

  data class CcContent(
    val color: Color,
    val icon: String,
    val headline: String,
    val detail: String,
    val onSolidBg: Boolean = true,  // true → white text; false → themed text
  )

  // Helper: live progress summary of the 3 port tests.
  fun progressDetail(suffix: String = ""): String {
    fun tick(done: Boolean) = if (done) "✓" else "⏳"
    return "USB 2.0 ${tick(port1.isFinished)}   USB 3.0 ${tick(port2.isFinished)}   HDMI ${tick(hdmiPort.isFinished)}$suffix"
  }

  val content: CcContent = when (phase) {
    is TypeCCcPhase.DETECTING_FIRST -> {
      val suffix = if (port1.isFinished && port2.isFinished && hdmiPort.isFinished)
        "  —  All done! Unplug cable to flip CC ↩" else ""
      val detail = progressDetail(suffix)
      if (phase.detected != null) {
        CcContent(
          color    = ColorWaiting,
          icon     = "🔍",
          headline = "${phase.detected} detected — Live Monitoring",
          detail   = detail,
        )
      } else {
        CcContent(
          color     = secondaryContainer,
          icon      = "🔍",
          headline  = "Detecting CC orientation…",
          detail    = detail,
          onSolidBg = false,
        )
      }
    }

    is TypeCCcPhase.WAITING_UNPLUG -> {
      val secondCcLabel = if (phase.firstCc == "CC1") "CC2" else "CC1"
      CcContent(
        color    = ColorRunning,
        icon     = "⚡",
        headline = "${phase.firstCc} detected — ACTION REQUIRED: UNPLUG the TypeC cable",
        detail   = "Remove the cable now so the opposite CC pin ($secondCcLabel) can be tested.",
      )
    }

    is TypeCCcPhase.WAITING_REPLUG -> {
      val secondCcLabel = if (phase.firstCc == "CC1") "CC2" else "CC1"
      CcContent(
        color    = ColorRunning,
        icon     = "🔄",
        headline = "Cable removed ✓ — Re-plug in OPPOSITE orientation to test $secondCcLabel",
        detail   = "${phase.firstCc} verified. Flip the TypeC cable 180° and re-plug to test $secondCcLabel.",
      )
    }

    is TypeCCcPhase.DONE -> if (phase.passed) {
      CcContent(
        color    = ColorPass,
        icon     = "✅",
        headline = "CC Orientation Test PASSED",
        detail   = "${phase.firstCc} ✓   ${phase.secondCc} ✓   Both CC pins verified successfully.",
      )
    } else {
      CcContent(
        color    = ColorFail,
        icon     = "❌",
        headline = "CC Orientation Test FAILED",
        detail   = "Same CC pin detected twice (${phase.firstCc} → ${phase.secondCc}). Cable may not have been flipped.",
      )
    }

    else -> return
  }

  val bgColor by animateColorAsState(content.color, tween(600), label = "ccCard")
  val textColor  = if (content.onSolidBg) Color.White else onSecondaryContainer
  val textMuted  = if (content.onSolidBg) Color.White.copy(alpha = 0.85f) else onSecondaryContainer.copy(alpha = 0.75f)

  Card(
    modifier  = modifier.fillMaxWidth(),
    shape     = RoundedCornerShape(12.dp),
    colors    = CardDefaults.cardColors(containerColor = bgColor),
    elevation = CardDefaults.cardElevation(defaultElevation = if (content.onSolidBg) 4.dp else 2.dp),
  ) {
    Row(
      modifier             = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment    = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(content.icon, fontSize = 28.sp)
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          content.headline,
          style      = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.ExtraBold,
          color      = textColor,
        )
        Text(
          content.detail,
          style = MaterialTheme.typography.bodySmall,
          color = textMuted,
        )
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OverallStatusBanner(ui: UsbCFunctionalTestUiState) {
  // CC is "done" when the phase is DONE, or when the CC test is disabled (ccPhase == null).
  val ccDone   = ui.ccPhase == null || ui.ccPhase is TypeCCcPhase.DONE
  val ccPassed = ui.ccPhase == null || (ui.ccPhase as? TypeCCcPhase.DONE)?.passed == true

  val allDone = ui.port1.isFinished && ui.port2.isFinished && ui.hdmiPort.isFinished && ccDone
  val allPassed = ui.port1.isPassed && ui.port2.isPassed && (ui.hdmiPassed == true) && ccPassed

  // "In-progress" = at least one item is active/finished but not all done yet.
  val anyStarted = ui.port1.isFinished || ui.port2.isFinished ||
                   ui.hdmiPort.isFinished || ui.hdmiPort.isPresent ||
                   (ui.ccPhase as? TypeCCcPhase.DETECTING_FIRST)?.detected != null ||
                   ui.ccPhase is TypeCCcPhase.WAITING_UNPLUG ||
                   ui.ccPhase is TypeCCcPhase.WAITING_REPLUG ||
                   ui.ccPhase is TypeCCcPhase.DONE

  val targetColor = when {
    allDone && allPassed  -> ColorPass
    allDone               -> ColorFail
    anyStarted            -> ColorRunning
    else                  -> ColorWaiting
  }
  val bgColor by animateColorAsState(targetColor, tween(600), label = "banner")
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = bgColor,
  ) {
    Text(
      text = ui.overallInstruction,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = Color.White,
    )
  }
}

@Composable
private fun HdmiInstructionCard(
  phase: ExternalDisplayTestPhase?,
  isConnected: Boolean,
  modifier: Modifier = Modifier,
) {
  data class HdmiHint(
    val color: Color,
    val icon: @Composable () -> Unit,
    val title: String,
    val body: String,
  )

  val hint =
    when (phase) {
      is ExternalDisplayTestPhase.TESTING ->
        HdmiHint(
          color = ColorRunning.copy(alpha = 0.14f),
          icon = { Icon(Icons.Filled.Keyboard, contentDescription = null, tint = ColorRunning) },
          title = "Display test in progress",
          body = "Look at the external display, then press the same number shown on that screen using the keyboard.",
        )
      else ->
        HdmiHint(
          color = if (isConnected) ColorWaiting.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
          icon = {
            Icon(
              if (isConnected) Icons.Filled.Monitor else Icons.Filled.Cable,
              contentDescription = null,
              tint = if (isConnected) ColorWaiting else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          title = if (isConnected) "Display detected" else "Connect external display",
          body =
            if (isConnected) {
              "The USB-C display is connected. Wait for the number prompt to appear on the external screen."
            } else {
              "Connect one external display to the USB-C port under test. After the screen lights up, follow the number prompt shown there."
            },
        )
    }

  Card(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = hint.color),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      hint.icon()
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = hint.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(text = hint.body, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Port card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PortCard(modifier: Modifier = Modifier, state: UsbCPortState, isHdmi: Boolean = false) {
  val cardColor by animateColorAsState(
    targetValue = when {
      state.isFinished && state.isPassed  -> ColorPass
      state.isFinished && !state.isPassed -> ColorFail
      state.isFormatting                  -> ColorRunning.copy(alpha = 0.14f)
      state.isPresent                     -> ColorWaiting.copy(alpha = 0.10f)
      else                                -> MaterialTheme.colorScheme.surfaceVariant
    },
    animationSpec = tween(600),
    label = "cardColor",
  )

  // When the card is solid-colored (pass/fail) use white text; otherwise use default.
  val onCard = if (state.isFinished) Color.White else Color.Unspecified
  val onCardMuted = if (state.isFinished) Color.White.copy(alpha = 0.80f) else Color.Gray

  Card(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = cardColor),
    elevation = CardDefaults.cardElevation(
      defaultElevation = if (state.isFinished) 6.dp else 2.dp,
    ),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Label + connection dot
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        ConnectionDot(isConnected = state.isPresent, finished = state.isFinished)
        Spacer(Modifier.width(6.dp))
        Text(
          text = state.label,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = onCard,
        )
      }

      // Sub-status
      when {
        state.isFinished -> {}  // sub-status suppressed — result shown in large status text below
        state.isFormatting -> Text(
          "⚙ FORMATTING...",
          style = MaterialTheme.typography.labelSmall,
          color = ColorRunning,
          fontWeight = FontWeight.SemiBold,
        )
        else -> Text(
          text = if (state.isPresent) "● CONNECTED" else "○ WAITING...",
          style = MaterialTheme.typography.labelSmall,
          color = if (state.isPresent) ColorPass else Color.Gray,
          fontWeight = FontWeight.SemiBold,
        )
      }

      HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = if (state.isFinished) Color.White.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outlineVariant,
      )

      if (!isHdmi) {
        SpeedRow("Write", state.writeSpeed, onCard, onCardMuted)
        SpeedRow("Read",  state.readSpeed,  onCard, onCardMuted)
      }

      // Progress bar (only while running, not when finished)
      if (!state.isFinished && (state.isFormatting || state.isPresent)) {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).height(4.dp),
          color = if (state.isFormatting) ColorRunning else ColorWaiting,
        )
      }

      HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = if (state.isFinished) Color.White.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outlineVariant,
      )

      val statusColor = when {
        state.isFinished   -> Color.White
        state.isFormatting -> ColorRunning
        state.isPresent    -> ColorRunning
        else               -> Color.Gray
      }
      Text(
        state.status,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = statusColor,
      )
    }
  }
}

@Composable
private fun SpeedRow(label: String, speed: Double?, onCard: Color, onCardMuted: Color) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = if (onCard == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else onCard,
      modifier = Modifier.width(36.dp),
    )
    Text(
      speed?.let { "%.1f MB/s".format(it) } ?: "--",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = if (speed == null) onCardMuted else onCard,
    )
  }
}

@Composable
private fun ConnectionDot(isConnected: Boolean, finished: Boolean = false) {
  val color by animateColorAsState(
    targetValue = when {
      finished     -> Color.White.copy(alpha = 0.85f)
      isConnected  -> ColorPass
      else         -> Color.LightGray
    },
    animationSpec = tween(400),
    label = "dot",
  )
  Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
}
