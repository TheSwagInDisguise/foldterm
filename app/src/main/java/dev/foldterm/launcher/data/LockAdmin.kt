package dev.foldterm.launcher.data

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Device admin limited to the force-lock policy, used for double-tap-to-lock. */
class LockAdmin : DeviceAdminReceiver() {
    companion object {
        fun component(ctx: Context) = ComponentName(ctx, LockAdmin::class.java)
        private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
        fun enabled(ctx: Context) = runCatching { dpm(ctx).isAdminActive(component(ctx)) }.getOrDefault(false)
        fun lock(ctx: Context): Boolean = enabled(ctx) && runCatching { dpm(ctx).lockNow(); true }.getOrDefault(false)
        fun enableIntent(ctx: Context) = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(ctx))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets FoldTerm lock the screen when you double-tap the home screen. It can't do anything else.")
        fun disable(ctx: Context) { runCatching { dpm(ctx).removeActiveAdmin(component(ctx)) } }
    }
}
