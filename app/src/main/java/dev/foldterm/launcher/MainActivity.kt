package dev.foldterm.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
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

private const val REQ_BIND_WIDGET = 41
private const val REQ_CONFIGURE_WIDGET = 42

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermissionsResult() }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.exportSettings(it) } }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importSettings(it) } }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        dev.foldterm.launcher.data.CrashLog.install(this)
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
                    is UiRequest.BindWidget -> {
                        pendingWidget = req.id
                        val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, req.id)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, req.info.provider)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, req.info.profile)
                        try {
                            @Suppress("DEPRECATION")
                            startActivityForResult(i, REQ_BIND_WIDGET)
                        } catch (e: Exception) {
                            vm.onWidgetBound(req.id, false)
                        }
                    }
                    UiRequest.ExportSettings -> exportLauncher.launch("foldterm-settings.json")
                    UiRequest.ImportSettings -> importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    is UiRequest.ConfigureWidget -> {
                        pendingWidget = req.id
                        try {
                            vm.widgetsHost.host.startAppWidgetConfigureActivityForResult(this@MainActivity, req.id, 0, REQ_CONFIGURE_WIDGET, null)
                        } catch (e: Exception) {
                            // some widgets declare a configure activity that can't be launched; keep the widget anyway
                            vm.onWidgetConfigured(req.id, true)
                        }
                    }
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

    private var pendingWidget = -1

    @Deprecated("Widget bind/configure flows only support request-code results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget) ?: pendingWidget
        val ok = resultCode == RESULT_OK
        when (requestCode) {
            REQ_BIND_WIDGET -> vm.onWidgetBound(id, ok)
            REQ_CONFIGURE_WIDGET -> vm.onWidgetConfigured(id, ok)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.widgetsHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        vm.widgetsHost.stopListening()
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
