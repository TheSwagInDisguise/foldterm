package dev.foldterm.launcher

import dev.foldterm.launcher.data.AppUsage
import dev.foldterm.launcher.data.CalEvent
import dev.foldterm.launcher.data.ContactSummary
import dev.foldterm.launcher.data.DayPoint
import dev.foldterm.launcher.data.HourPoint
import dev.foldterm.launcher.data.MediaSessionInfo
import dev.foldterm.launcher.data.NewsItem
import dev.foldterm.launcher.data.Note
import dev.foldterm.launcher.data.Notif
import dev.foldterm.launcher.data.NotifKind
import dev.foldterm.launcher.data.NotificationStore
import dev.foldterm.launcher.data.NowPlaying
import dev.foldterm.launcher.data.NowPlayingStore
import dev.foldterm.launcher.data.StepState
import dev.foldterm.launcher.data.TaskItem
import dev.foldterm.launcher.data.UsageDay
import dev.foldterm.launcher.data.UsageReport
import dev.foldterm.launcher.data.Weather
import dev.foldterm.launcher.ui.Fmt

/**
 * Sample data for screenshots and demos (launch with the extra `demo=true`).
 * Nothing here touches real accounts; it only fills the in-memory state.
 */
fun LauncherViewModel.loadDemo() {
    demo = true
    clearLog()
    out("foldterm — type `help`, or try: split chrome | notes · 100 usd in aud · brief", dev.foldterm.launcher.shell.LineKind.DIM)
    val day = Fmt.startOfDay(System.currentTimeMillis())
    val h = 3_600_000L
    val m = 60_000L
    fun ev(id: Long, title: String, start: Long, len: Long, where: String, color: Long, cal: String = "Work", notes: String = "") =
        CalEvent(id, title, start, start + len, where, color.toInt(), false, cal, notes)
    val red = 0xFFE5534B; val blue = 0xFF539BF5; val orange = 0xFFE0823D; val green = 0xFF57AB5A; val violet = 0xFFB083F0
    events.value = listOf(
        ev(1, "Daily standup", day + 9 * h + 30 * m, 15 * m, "Zoom · https://zoom.us/j/1", red),
        ev(2, "Design review - Fold-X home screen", day + 10 * h + 30 * m, h, "Conf Room B", red),
        ev(3, "1:1 with Priya", day + 11 * h + 45 * m, 30 * m, "Priya's office", red),
        ev(4, "Lunch with Maya", day + 12 * h + 30 * m, h, "Cafe Luna, 214 Main St", blue, "Personal"),
        ev(5, "Quarterly roadmap review", day + 14 * h, h, "Zoom", red),
        ev(6, "Dentist cleaning", day + 15 * h + 15 * m, 45 * m, "Bright Smiles Dental, 88 Oak Ave", orange, "Health", "Bring insurance card."),
        ev(7, "Emma's piano lesson", day + 16 * h + 30 * m, 45 * m, "Harmony Music School", green, "Family"),
        ev(8, "Dinner with the Parkers", day + 19 * h, 2 * h, "Osteria Rosa, 12 Union Sq", green, "Family"),
        ev(9, "Daily standup", day + 33 * h + 30 * m, 15 * m, "Zoom", red),
        ev(10, "Gym", day + 31 * h, h, "Fitness First", violet, "Personal"),
        ev(11, "Product demo", day + 38 * h, 90 * m, "Main hall", red),
        ev(12, "Farmers market", day + 57 * h, 2 * h, "Riverside Park", green, "Family"),
    )
    calendarNames.value = listOf("Family", "Health", "Personal", "Work")
    contacts.value = listOf(
        ContactSummary(101, "a", "Aisha Rahman", true, "Crescent Pharmacy", "(202) 555-0134", "aisha.rahman@crescent.example", birthday = "12-24"),
        ContactSummary(102, "b", "Alex Rivera", false, "Harbor Bakery", "(202) 555-0177", "alex@harbor.example"),
        ContactSummary(103, "c", "Dana Okafor", false, "Lumen Design Studio", "(202) 555-0160", "dana@lumen.example"),
        ContactSummary(104, "d", "Marcus Webb", true, "Fold-X", "(202) 555-0101", "marcus@foldx.example"),
        ContactSummary(105, "e", "Maya Okafor", true, "Lumen Design Studio", "(202) 555-0199", "maya@lumen.example"),
        ContactSummary(106, "f", "Mom", true, null, "(202) 555-0121", null),
        ContactSummary(107, "g", "Priya Nair", true, "Fold-X", "(202) 555-0145", "priya@foldx.example"),
        ContactSummary(108, "h", "Sam Chen", false, "Trailhead Co.", "(202) 555-0188", "sam@trailhead.example"),
    )
    prefs.ice.value = setOf(101L, 102L)
    val now = System.currentTimeMillis()
    fun n(key: String, pkg: String, app: String, title: String, text: String, ago: Long, kind: NotifKind) =
        Notif(key, pkg, app, title, text, text, now - ago, kind, null, null)
    NotificationStore.items.value = listOf(
        n("1", "com.whatsapp", "WhatsApp", "Maya", "Running 5 late, grab us a table?", 2 * m, NotifKind.CHAT),
        n("2", "com.google.android.gm", "Gmail", "Priya Nair", "Re: Q4 launch plan - final review", 18 * m, NotifKind.MAIL),
        n("3", "com.google.android.apps.messaging", "Messages", "Mom", "Dinner Sunday? Dad's making lasagne 🍝", 40 * m, NotifKind.CHAT),
        n("4", "com.google.android.gm", "Gmail", "Marcus Webb", "+1 to Priya. Flagging the support rota", 64 * m, NotifKind.MAIL),
        n("5", "com.Slack", "Slack", "#design", "Dana: new tour copy options are up", 2 * h, NotifKind.CHAT),
        n("6", "com.google.android.gm", "Gmail", "The Daily Brief", "Your brief: chips, chargers and a quieter inbox", 3 * h, NotifKind.MAIL),
        n("7", "com.ubercab.eats", "Uber Eats", "Order on its way", "Arriving in 12 minutes", 4 * h, NotifKind.OTHER),
    )
    notifAccess.value = true
    perms.value = linkedMapOf("calendar" to true, "contacts" to true, "location" to true, "activity" to true, "photos" to true)
    weather.value = Weather(
        temp = 22.0, code = 2, high = 28.0, low = 18.0, rainInHours = null, fahrenheit = false, place = "Glenfield",
        fetchedAt = now, feelsLike = 21.0, windSpeed = 14.0, windDir = 225, uvMax = 7.0, sunrise = "05:52", sunset = "19:52",
        hourly = (0 until 24).map { i -> HourPoint("%02d".format((java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) + i) % 24), 22.0 + (if (i < 6) i * 0.8 else 6 - (i - 6) * 0.5), listOf(5, 5, 10, 20, 45, 70, 55, 30, 15, 10, 5, 5)[i % 12]) },
        daily = listOf(
            DayPoint("2026-11-20", 2, 18.0, 28.0, 10), DayPoint("2026-11-21", 0, 18.0, 28.0, 0), DayPoint("2026-11-22", 2, 17.0, 25.0, 20),
            DayPoint("2026-11-23", 61, 15.0, 21.0, 80), DayPoint("2026-11-24", 63, 14.0, 19.0, 90), DayPoint("2026-11-25", 2, 16.0, 23.0, 20),
            DayPoint("2026-11-26", 0, 18.0, 27.0, 0),
        ),
    )
    weatherStatus.value = ""
    news.value = listOf(
        NewsItem("Foldables go mainstream as shipments jump 38% year over year", "https://example.com/1", "The Daily Brief", now - 40 * m),
        NewsItem("A new USB-C standard promises 480 W charging", "https://example.com/2", "Hacker News", now - 2 * h),
        NewsItem("Why people are quitting notification badges", "https://example.com/3", "BBC News", now - 3 * h),
        NewsItem("Central bank holds rates steady, signals patience", "https://example.com/4", "BBC News", now - 5 * h),
        NewsItem("Show HN: a terminal-flavoured Android launcher", "https://example.com/5", "Hacker News", now - 6 * h),
    )
    newsStatus.value = ""
    tasks.value = listOf(
        TaskItem(1, "Send Q4 budget draft to Priya", false, now, day, true),
        TaskItem(2, "Book the dentist follow-up", false, now, day + 24 * h),
        TaskItem(3, "Renew the gym pass", false, now, day - 24 * h),
        TaskItem(4, "Back up the photos", true, now),
        TaskItem(5, "Pick up dry cleaning", false, now),
    )
    prefs.notes.value = listOf(
        Note(1, "# Fold-X review notes\n- [x] hinge split on home\n- [x] cover screen cards\n- [ ] widget stacking\n- [ ] icon pack previews", now - 30 * m),
        Note(2, "Gift ideas\nMaya: ceramic planter\nDad: jazz night tickets", now - 2 * 24 * h),
    )
    NowPlayingStore.state.value = NowPlaying(
        "Midnight City", "M83", "com.spotify.music", true, null, album = "Hurry Up, We're Dreaming",
        durationMs = 243_000, positionMs = 107_000, positionAt = android.os.SystemClock.elapsedRealtime(), canSeek = true,
    )
    NowPlayingStore.sessions.value = listOf(
        MediaSessionInfo("com.spotify.music", "Midnight City", true),
        MediaSessionInfo("com.google.android.youtube", "Lo-fi beats to code to", false),
        MediaSessionInfo("au.com.shiftyjelly.pocketcasts", "Hard Fork", false),
    )
    usage.value = UsageReport(
        totalMs = 2 * h + 13 * m,
        apps = listOf(
            AppUsage("com.android.chrome", 48 * m, 14), AppUsage("com.whatsapp", 31 * m, 22), AppUsage("com.spotify.music", 26 * m, 3),
            AppUsage("com.google.android.gm", 17 * m, 9), AppUsage("com.google.android.youtube", 12 * m, 2), AppUsage("com.Slack", 9 * m, 11),
        ),
        unlocks = 37,
        week = (6 downTo 0).map { d -> UsageDay(day - d * 24 * h, listOf(3, 4, 2, 5, 3, 2, 2)[d] * h + 20 * m * d) },
    )
    steps.value = StepState(available = true, permitted = true, steps = 6_481)
    val today = java.time.LocalDate.now()
    fun daysBack(vararg n: Int) = n.map { today.minusDays(it.toLong()).toString() }.toSet()
    habits.value = listOf(
        dev.foldterm.launcher.data.Habit(1, "Read 20 pages", daysBack(0, 1, 2, 3, 4, 6, 7, 8)),
        dev.foldterm.launcher.data.Habit(2, "Walk 8k steps", daysBack(1, 2, 3, 5, 6)),
        dev.foldterm.launcher.data.Habit(3, "No phone after 10pm", daysBack(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
        dev.foldterm.launcher.data.Habit(4, "Practice piano", daysBack(2, 4)),
    )
    fun ex(id: Long, amt: Double, note: String, daysAgo: Int) =
        dev.foldterm.launcher.data.Expense(id, amt, note.substringBefore(' '), note, now - daysAgo * 24 * h - id * 37 * m)
    expenses.value = listOf(
        ex(1, 14.5, "lunch with maya", 0), ex(2, 4.8, "coffee", 0), ex(3, 86.2, "groceries weekly shop", 1), ex(4, 12.0, "transport tap card", 1),
        ex(5, 32.0, "dinner pizza", 2), ex(6, 4.8, "coffee", 3), ex(7, 59.0, "fuel", 4), ex(8, 18.9, "books paperback", 5),
        ex(9, 64.3, "groceries", 8), ex(10, 9.99, "subscriptions music", 10), ex(11, 45.0, "gym pass", 12),
    )
    prefs.setBudget(1200.0)
    prefs.saveBookmarks(listOf(
        dev.foldterm.launcher.data.Bookmark(1, "https://example.com/hinge", "How the Fold's hinge got so thin", now - 2 * h, false),
        dev.foldterm.launcher.data.Bookmark(2, "https://example.com/terminal", "In praise of the command line, 50 years on", now - 20 * h, false),
        dev.foldterm.launcher.data.Bookmark(3, "https://example.com/pasta", "The only lasagne recipe you need", now - 3 * 24 * h, false),
        dev.foldterm.launcher.data.Bookmark(4, "https://example.com/budget", "A no-spreadsheet budget that works", now - 5 * 24 * h, true),
    ))
    prefs.setCountdowns(listOf("Japan trip|${java.time.LocalDate.now().plusDays(30)}", "Maya's birthday|${java.time.LocalDate.now().plusDays(12)}"))
    prefs.setUserName("Alex")
}
