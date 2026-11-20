package dev.foldterm.launcher

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Invisible trampoline for split screen.
 *
 * Android ignores FLAG_ACTIVITY_LAUNCH_ADJACENT when it comes from the home screen, because
 * home can't take part in split screen. This activity lives in its own resizable task:
 *  1. it launches the RIGHT app adjacent to itself, which makes the system enter split screen;
 *  2. it launches the LEFT app, which opens in this activity's half;
 *  3. it finishes, leaving LEFT | RIGHT.
 */
class SplitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val left = intent.parcelable(EXTRA_LEFT)
        val right = intent.parcelable(EXTRA_RIGHT)
        if (left == null || right == null) { finish(); return }

        lifecycleScope.launch {
            runCatching {
                startActivity(
                    right.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
                    ),
                )
            }
            delay(RIGHT_SETTLE_MS)
            runCatching { startActivity(left.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            delay(250)
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun Intent.parcelable(key: String): Intent? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Intent::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(key)

    companion object {
        const val EXTRA_LEFT = "left"
        const val EXTRA_RIGHT = "right"
        private const val RIGHT_SETTLE_MS = 550L
    }
}
