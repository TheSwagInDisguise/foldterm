package dev.foldterm.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.WindowInfoTracker
import dev.foldterm.launcher.ui.LauncherRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermissionsResult() }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A translucent surface lets the wallpaper (theme: windowShowWallpaper) stay visible;
        // the UI paints its own opaque background when wallpaper mode is off.
        window.setFormat(PixelFormat.TRANSLUCENT)
        enableEdgeToEdge()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { vm.updateFold(it) }
            }
        }
        lifecycleScope.launch {
            vm.uiRequests.collect { req ->
                when (req) {
                    is UiRequest.Permissions -> permissionLauncher.launch(req.perms)
                    UiRequest.RoleHome -> requestHomeRole()
                }
            }
        }

        setContent { LauncherRoot(vm) }

        if (vm.prefs.consumeFirstRun()) {
            vm.out("first run: requesting permissions — `grant` to retry, `access` for notifications", dev.foldterm.launcher.shell.LineKind.DIM)
            vm.requestAllPermissions()
        }
    }

    private fun requestHomeRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing home while already home: close overlays, then return to the home pane.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
