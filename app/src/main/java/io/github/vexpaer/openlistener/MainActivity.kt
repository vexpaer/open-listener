package io.github.vexpaer.openlistener

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.vexpaer.openlistener.service.MicrophoneRecognitionService
import io.github.vexpaer.openlistener.ui.OpenListenerRoot
import io.github.vexpaer.openlistener.ui.OpenListenerViewModel
import io.github.vexpaer.openlistener.ui.UiEffect
import io.github.vexpaer.openlistener.ui.theme.OpenListenerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: OpenListenerViewModel by viewModels()
    private var foreground by mutableStateOf(false)
    private var permissionRequestPending = false
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionRequestPending = false
        if (result[Manifest.permission.RECORD_AUDIO] == true || hasMicrophonePermission()) startVisibleService()
        else model.microphoneDenied()
    }
    private val documentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        model.saveDocument(if (result.resultCode == RESULT_OK) result.data?.data else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by model.preferences.collectAsState()
            OpenListenerTheme(preferences.theme, preferences.reducedMotion) {
                OpenListenerRoot(viewModel = model, foreground = foreground)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.effects.collect { effect ->
                    try {
                        when (effect) {
                            UiEffect.RequestMicrophoneStart -> requestMicrophone()
                            is UiEffect.StartActivity -> startActivity(effect.intent)
                            is UiEffect.CreateDocument -> documentLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = effect.format.mime
                                putExtra(Intent.EXTRA_TITLE, effect.name)
                            })
                        }
                    } catch (error: Exception) { model.showError(error) }
                }
            }
        }
    }
    override fun onStart() { super.onStart(); foreground = true }
    override fun onResume() { super.onResume(); model.onActivityResumed() }
    override fun onStop() { foreground = false; super.onStop() }

    private fun hasMicrophonePermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun requestMicrophone() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { model.activityNotVisible(); return }
        if (hasMicrophonePermission()) startVisibleService()
        else if (!permissionRequestPending) {
            permissionRequestPending = true
            permissionLauncher.launch(buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray())
        }
    }
    private fun startVisibleService() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { model.activityNotVisible(); return }
        try {
            ContextCompat.startForegroundService(this, Intent(this, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_START))
        } catch (error: Exception) { model.showError(error) }
    }
}
