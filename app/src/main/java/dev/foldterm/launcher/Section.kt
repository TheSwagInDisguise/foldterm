package dev.foldterm.launcher

enum class Section(val id: String, val title: String, val glyph: String) {
    HOME("home", "Home", "⌂"),
    CHAT("chat", "Chat", "»"),
    MAIL("mail", "Mail", "@"),
    CALENDAR("cal", "Calendar", "▦"),
    PEOPLE("people", "People", "☺"),
    PHOTOS("photos", "Photos", "▣"),
    HEALTH("health", "Health", "♥"),
    NEWS("news", "News", "≡"),
    SETTINGS("settings", "Settings", "⚙");

    companion object {
        fun find(s: String): Section? {
            val q = s.trim().lowercase().removePrefix("~/").removePrefix("/")
            return entries.firstOrNull { it.id == q || it.title.lowercase() == q }
                ?: when (q) {
                    "~", "", "..", "h" -> HOME
                    "calendar", "agenda" -> CALENDAR
                    "contacts", "ppl" -> PEOPLE
                    "messages", "sms" -> CHAT
                    "email", "inbox" -> MAIL
                    "gallery", "pics" -> PHOTOS
                    "prefs", "config" -> SETTINGS
                    else -> null
                }
        }
    }
}
