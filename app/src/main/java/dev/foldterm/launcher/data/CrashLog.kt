package dev.foldterm.launcher.data

import android.content.Context
import android.os.Build
import dev.foldterm.launcher.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Saves the stack trace of an uncaught crash to a file so it can be shown on the next
 * launch (`crashlog` in the prompt) — there's no logcat on the phone.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    @Volatile private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(app.filesDir, FILE).writeText(
                    "foldterm ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        "time ${System.currentTimeMillis()} · thread ${thread.name}\n\n$sw",
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun read(ctx: Context): String? = runCatching { File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(ctx: Context) { runCatching { File(ctx.filesDir, FILE).delete() } }
}
