package com.google.android.factory.factory.actions.removable

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.factory.factory.viewmodel.display.ExternalDisplayTestPhase

fun ExternalDisplayTestPhase?.matchesDisplayProof(key: Key): Boolean? {
  val currentPhase = this as? ExternalDisplayTestPhase.TESTING ?: return null
  val pressedDigit = key.toDigit() ?: return null
  return pressedDigit == currentPhase.displayProof
}

@Composable
fun UsbCExternalDisplayPresentationHost(
  phase: ExternalDisplayTestPhase?,
  onKeyDown: (Key) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(phase, context, lifecycleOwner) {
    var presentation: UsbCExternalDisplayPresentation? = null
    if (phase is ExternalDisplayTestPhase.TESTING) {
      presentation =
        UsbCExternalDisplayPresentation(
          outerContext = context,
          lifecycleOwner = lifecycleOwner,
          display = phase.externalDisplay,
          text = "Press number ${phase.displayProof} to pass the test",
          onKeyDown = onKeyDown,
        )
      presentation.show()
    }

    onDispose { presentation?.dismiss() }
  }
}

private class UsbCExternalDisplayPresentation(
  private val outerContext: Context,
  private val lifecycleOwner: LifecycleOwner,
  display: Display,
  private val text: String,
  private val onKeyDown: (Key) -> Unit,
) : Presentation(outerContext, display) {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val composeView = ComposeView(context)
    val decorView = window!!.decorView
    decorView.setViewTreeLifecycleOwner(lifecycleOwner)
    (outerContext as? ComponentActivity)?.let { decorView.setViewTreeSavedStateRegistryOwner(it) }

    setContentView(composeView.apply { setContent { UsbCExternalDisplayPresentationText(text) } })
  }

  override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
    onKeyDown(Key(event.keyCode))
    return true
  }
}

@Composable
private fun UsbCExternalDisplayPresentationText(text: String) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val dynamicFontSize = with(LocalDensity.current) { (maxWidth / 30f).toSp() }
    Text(text, style = MaterialTheme.typography.bodyLarge, fontSize = dynamicFontSize)
  }
}

private fun Key.toDigit(): Int? {
  return when (this) {
    Key.Zero -> 0
    Key.One -> 1
    Key.Two -> 2
    Key.Three -> 3
    Key.Four -> 4
    Key.Five -> 5
    Key.Six -> 6
    Key.Seven -> 7
    Key.Eight -> 8
    Key.Nine -> 9
    else -> null
  }
}
