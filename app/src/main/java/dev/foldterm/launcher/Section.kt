package dev.foldterm.launcher

/**
 * Sections shown in the sidebar, in the user's order. Home is always shown; Settings is
 * pinned separately at the bottom; sections added in later versions are appended.
 */
fun sidebarSections(order: List<String>, hidden: Set<String>): List<Section> {
    val all = Section.entries.filter { it != Section.SETTINGS }
    val ordered = order.mapNotNull { id -> all.firstOrNull { it.id == id } } + all.filter { s -> order.none { it == s.id } }
    return ordered.distinct().filter { it == Section.HOME || it.id !in hidden }
}

/** Every configurable section (everything but Settings) in the user's order, hidden ones included. */
fun orderedSections(order: List<String>): List<Section> {
    val all = Section.entries.filter { it != Section.SETTINGS }
    return (order.mapNotNull { id -> all.firstOrNull { it.id == id } } + all.filter { s -> order.none { it == s.id } }).distinct()
}

enum class Section(val id: String, val title: String, val glyph: String) {
    HOME("home", "Home", "⌂"),
    CHAT("chat", "Chat", "»"),
    MAIL("mail", "Mail", "@"),
    CALENDAR("cal", "Calendar", "▦"),
    NOTIFS("notifs", "Alerts", "!"),
    PEOPLE("people", "People", "☺"),
    PHOTOS("photos", "Photos", "▣"),
    MEDIA("media", "Media", "♫"),
    CONTROLS("controls", "Controls", "◐"),
    USAGE("usage", "Screen time", "⏱"),
    FOCUS("focus", "Focus", "◎"),
    HEALTH("health", "Health", "♥"),
    WEATHER("weather", "Weather", "☁"),
    NEWS("news", "News", "≡"),
    NOTES("notes", "Notes", "✎"),
    WIDGETS("widgets", "Widgets", "◫"),
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
                    "note", "memo", "scratch" -> NOTES
                    "widget" -> WIDGETS
                    "wttr", "forecast" -> WEATHER
                    "music", "player", "nowplaying" -> MEDIA
                    "notifications", "alerts", "notification" -> NOTIFS
                    "quick", "qs", "control" -> CONTROLS
                    "screentime", "screen", "time" -> USAGE
                    "pomodoro", "zen" -> FOCUS
                    else -> null
                }
        }
    }
}
