package dev.foldterm.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.foldterm.launcher.data.Note
import dev.foldterm.launcher.data.Prefs

/**
 * "Share → FoldTerm": links go to read-later, anything else becomes a note.
 * Writes straight to storage; the launcher picks it up when it next resumes.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (text.isNotEmpty()) {
            val prefs = Prefs(this)
            val url = Regex("https?://\\S+").find(text)?.value
            if (url != null) {
                val title = subject.ifBlank { text.replace(url, "").trim().ifBlank { url.substringAfter("//").substringBefore('/') } }
                prefs.addBookmark(url, title)
                Toast.makeText(this, "Saved to FoldTerm › links", Toast.LENGTH_SHORT).show()
            } else {
                val now = System.currentTimeMillis()
                prefs.saveNotes(listOf(Note(now, (if (subject.isNotBlank()) "$subject\n" else "") + text, now)) + prefs.loadNotes())
                Toast.makeText(this, "Saved to FoldTerm › notes", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
