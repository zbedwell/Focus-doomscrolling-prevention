package com.zack.focus

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zack.focus.ui.theme.FocusTheme

class OverlayGate(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(packageName: String, onGoBack: () -> Unit) {
        if (overlayView != null) return

        mainHandler.post {
            if (overlayView != null) return@post

            val owner = OverlayLifecycleOwner().also { it.start() }
            lifecycleOwner = owner

            val view = ComposeView(context).apply {
                // Paint black immediately so no app content is visible before Compose renders.
                setBackgroundColor(android.graphics.Color.BLACK)
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    FocusTheme {
                        GateOverlayContent(
                            packageName = packageName,
                            onGoBack = onGoBack,
                            onContinue = {
                                hide()
                                val intent = Intent(context, GateActivity::class.java)
                                    .putExtra(GateActivity.EXTRA_PACKAGE, packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP }

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm
            overlayView = view
            runCatching {
                wm.addView(view, params)
            }.onFailure {
                // BadTokenException — overlay permission revoked; reset state cleanly
                owner.stop()
                overlayView = null
                windowManager = null
            }
        }
    }

    fun hide() {
        mainHandler.post {
            val view = overlayView ?: return@post
            val wm = windowManager ?: return@post
            lifecycleOwner?.stop()
            lifecycleOwner = null
            runCatching { wm.removeView(view) }
            overlayView = null
            windowManager = null
        }
    }
}

@Composable
private fun GateOverlayContent(
    packageName: String,
    onGoBack: () -> Unit,
    onContinue: () -> Unit
) {
    val appLabel = FocusStore.BLOCKED_APP_LABELS[packageName] ?: packageName

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Take a breath.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "You opened $appLabel, an app you chose to block.",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Is this really what you want right now?",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = MotivationMessages.getRandom(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onGoBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Go Back")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("I Still Want to Continue")
            }
        }
    }
}

private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore = store

    private val controller = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry

    fun start() {
        controller.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
