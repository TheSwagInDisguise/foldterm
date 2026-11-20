package dev.foldterm.launcher.shell

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import dev.foldterm.launcher.BuildConfig
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.Section
import dev.foldterm.launcher.data.AppEntry
import dev.foldterm.launcher.data.ContactSummary
import dev.foldterm.launcher.data.NowPlayingStore
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.Themes

enum class LineKind { CMD, OUT, DIM, OK, ERR, ACCENT }

data class Line(val text: String, val kind: LineKind)

enum class SuggestionKind { COMMAND, APP, CONTACT, SECTION, WEB, EVENT, NOTE, MESSAGE, ALIAS, PAIR }

data class Suggestion(
    val kind: SuggestionKind,
    val label: String,
    val detail: String,
    val app: AppEntry? = null,
    val run: () -> Unit,
)

/**
 * A tiny command interpreter behind the global prompt. Anything that is not a command
 * falls through to fuzzy app/contact/section matching, then to a web search.
 */
class Shell(private val vm: LauncherViewModel) {

    private class Cmd(
        val name: String,
        val usage: String,
        val help: String,
        val aliases: Set<String> = emptySet(),
        val run: (args: List<String>, rest: String) -> Unit,
    )

    private val commands: List<Cmd> by lazy { buildCommands() }

    private var invokedAs = ""

    fun commandNames(): List<String> = commands.map { it.name }

    fun execute(raw: String) {
        val line = raw.trim()
        if (line.isEmpty()) return
        vm.out("❯ $line", LineKind.CMD)
        if (Calc.isExpression(line)) {
            runCatching { vm.out("= ${Calc.format(Calc.eval(line))}", LineKind.ACCENT) }
                .onFailure { vm.out("calc: ${it.message}", LineKind.ERR) }
            return
        }
        // aliases expand once: `alias wk=open slack` makes `wk` run `open slack`
        val head = line.substringBefore(' ')
        vm.prefs.aliases.value[head.lowercase()]?.let { expansion ->
            val expanded = (expansion + " " + line.substringAfter(' ', "")).trim()
            vm.out("↳ $expanded", LineKind.DIM)
            runCommand(expanded)
            return
        }
        runCommand(line)
    }

    private val urlLike = Regex("^(https?://)?([\\w-]+\\.)+[a-z]{2,}(/\\S*)?$", RegexOption.IGNORE_CASE)

    private fun runCommand(line: String) {
        val parts = line.split(Regex("\\s+"))
        val name = parts[0].lowercase()
        val rest = line.substring(parts[0].length).trim()
        val cmd = commands.firstOrNull { it.name == name || name in it.aliases }
        if (cmd != null) {
            invokedAs = name
            runCatching { cmd.run(parts.drop(1), rest) }
                .onFailure { vm.out("${cmd.name}: ${it.message ?: it.javaClass.simpleName}", LineKind.ERR) }
            return
        }
        val best = suggest(line).firstOrNull { it.kind != SuggestionKind.COMMAND }
        if (urlLike.matches(line) && vm.apps.value.none { it.label.equals(line, true) }) {
            vm.openUrl(if (line.startsWith("http", true)) line else "https://$line")
            vm.shellOpen.value = false
            return
        }
        if (best != null && best.kind != SuggestionKind.WEB) {
            vm.out("→ ${best.label}", LineKind.DIM)
            best.run()
        } else {
            vm.out("$name: not a command — searching the web", LineKind.DIM)
            vm.webSearch(line)
        }
    }

    // ───────────────────────── suggestions ─────────────────────────

    fun suggest(input: String, limit: Int = 8): List<Suggestion> {
        val q = input.trim()
        if (q.isEmpty()) return emptyList()
        if (Calc.isExpression(q)) {
            val res = runCatching { Calc.format(Calc.eval(q)) }.getOrNull()
            if (res != null) return listOf(
                Suggestion(SuggestionKind.COMMAND, "= $res", "result · ⏎ to keep") { execute("= ${q.removePrefix("=").trim()}") },
            )
        }
        val first = q.substringBefore(' ').lowercase()
        val arg = q.substringAfter(' ', "").trim()
        val out = mutableListOf<Suggestion>()

        // argument completion for a few commands
        if (arg.isNotEmpty() || q.endsWith(" ")) {
            when (first) {
                "unhide" -> return rankApps(arg, vm.allApps.value.filter { it.key in vm.prefs.hidden.value }).take(limit).map { app ->
                    Suggestion(SuggestionKind.APP, "unhide ${app.label}", app.packageName, app) { execute("unhide ${app.label}") }
                }
                "float", "popup", "pop" -> return rankApps(arg).take(limit).map { app ->
                    Suggestion(SuggestionKind.APP, "float ${app.label}", "pop-up window", app) { execute("float ${app.label}") }
                }
                "split", "sp" -> {
                    // complete whichever side is being typed
                    val left = arg.substringBefore('|').trim()
                    val typingRight = '|' in arg
                    val part = if (typingRight) arg.substringAfter('|').trim() else left
                    return rankApps(part).filter { !it.isWork }.take(limit).map { app ->
                        val label = if (typingRight) "split $left | ${app.label}" else "split ${app.label} | "
                        Suggestion(SuggestionKind.APP, label, if (typingRight) "open side by side" else "then pick the right-hand app", app) {
                            if (typingRight) {
                                execute(label)
                            } else {
                                vm.input.value = label
                            }
                            Unit
                        }
                    }
                }
                "open", "launch", "run", "info", "uninstall", "hide" -> return rankApps(arg).take(limit).map { app ->
                    Suggestion(SuggestionKind.APP, "$first ${app.label}", app.packageName, app) { execute("$first ${app.label}") }
                }
                "call", "sms", "text", "email", "mail" -> if (first != "mail" || arg.isNotEmpty()) {
                    return rankContacts(arg).take(limit).map { c ->
                        Suggestion(SuggestionKind.CONTACT, "$first ${c.name}", c.phone ?: c.email ?: "") { execute("$first ${c.name}") }
                    }
                }
                "cd", "go" -> return Section.entries.filter { it.id.startsWith(arg.lowercase()) || it.title.lowercase().startsWith(arg.lowercase()) }
                    .map { s -> Suggestion(SuggestionKind.SECTION, "cd ${s.id}", s.title) { vm.go(s); vm.shellOpen.value = false } }
                "theme" -> return Themes.names.filter { it.startsWith(arg.lowercase()) }
                    .map { t -> Suggestion(SuggestionKind.COMMAND, "theme $t", "switch theme") { execute("theme $t") } }
            }
        }

        commands.filter { it.name.startsWith(first) && arg.isEmpty() }.take(3).forEach { c ->
            out += Suggestion(SuggestionKind.COMMAND, c.usage, c.help) { vm.input.value = "${c.name} " }
        }
        Section.entries.filter { score(it.title, q) > 0 }.forEach { s ->
            out += Suggestion(SuggestionKind.SECTION, "~/${s.id}", "go to ${s.title}") { vm.go(s); vm.shellOpen.value = false; vm.input.value = "" }
        }
        rankApps(q).take(5).forEach { app ->
            out += Suggestion(SuggestionKind.APP, app.label, if (app.isWork) "work profile" else app.packageName, app) { vm.launch(app) }
        }
        rankContacts(q).take(3).forEach { c ->
            out += Suggestion(SuggestionKind.CONTACT, c.name, c.phone ?: c.email ?: c.company ?: "contact") {
                vm.go(Section.PEOPLE); vm.selectContact(c); vm.shellOpen.value = false; vm.input.value = ""
            }
        }
        vm.prefs.pairs.value.filterKeys { score(it, q.removePrefix("pair ").trim()) >= 50 }.forEach { (k, v) ->
            out += Suggestion(SuggestionKind.PAIR, "pair $k", "${vm.labelOf(v.first)} | ${vm.labelOf(v.second)}") { vm.launchPair(k) }
        }
        vm.prefs.aliases.value.filterKeys { it.startsWith(first) && arg.isEmpty() }.forEach { (k, v) ->
            out += Suggestion(SuggestionKind.ALIAS, k, "→ $v") { execute(k) }
        }
        if (q.length >= 2) {
            val now = System.currentTimeMillis()
            vm.events.value.filter { it.end > now && score(it.title, q) >= 50 }.take(2).forEach { e ->
                out += Suggestion(SuggestionKind.EVENT, e.title, "${Fmt.dayLabel(e.begin)} ${Fmt.time(e.begin)}") {
                    vm.selectedEvent.value = e.instanceKey; vm.go(Section.CALENDAR); vm.shellOpen.value = false; vm.input.value = ""
                }
            }
            vm.prefs.notes.value.filter { it.text.contains(q, true) }.take(2).forEach { n ->
                out += Suggestion(SuggestionKind.NOTE, n.title, "note · ${Fmt.ago(n.updated)}") { vm.openNote(n.id) }
            }
            vm.notifications.value.filter { !it.removed && (it.title.contains(q, true) || it.text.contains(q, true)) }.take(2).forEach { m ->
                out += Suggestion(SuggestionKind.MESSAGE, m.title.ifBlank { m.app }, "${m.app} · ${m.text.take(40)}") {
                    vm.selectedNotif.value = m.key
                    vm.go(if (m.kind == dev.foldterm.launcher.data.NotifKind.MAIL) Section.MAIL else Section.CHAT)
                    vm.shellOpen.value = false; vm.input.value = ""
                }
            }
            if (urlLike.matches(q)) out += Suggestion(SuggestionKind.WEB, "open $q", "website") {
                vm.openUrl(if (q.startsWith("http", true)) q else "https://$q"); vm.shellOpen.value = false; vm.input.value = ""
            }
        }
        out += Suggestion(SuggestionKind.WEB, "search \"$q\"", "web") { vm.webSearch(q); vm.shellOpen.value = false; vm.input.value = "" }
        // apps first when the query clearly names one
        return out.sortedBy {
            when (it.kind) {
                SuggestionKind.APP -> if (score(it.label, q) >= 80) 0 else 2
                SuggestionKind.COMMAND -> 1
                SuggestionKind.SECTION -> 1
                SuggestionKind.ALIAS, SuggestionKind.PAIR -> 0
                SuggestionKind.CONTACT -> 3
                SuggestionKind.EVENT, SuggestionKind.NOTE, SuggestionKind.MESSAGE -> 4
                SuggestionKind.WEB -> if (it.label.startsWith("open ")) 1 else 9
            }
        }.take(limit)
    }

    private fun rankApps(q: String, pool: List<AppEntry> = vm.apps.value): List<AppEntry> =
        if (q.isBlank()) pool.sortedByDescending { vm.usageOf(it) }.take(8)
        else pool.map { it to score(it.label, q) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<AppEntry, Int>> { it.second }.thenByDescending { vm.usageOf(it.first) })
            .map { it.first }

    private fun rankContacts(q: String): List<ContactSummary> =
        if (q.isBlank()) vm.contacts.value.filter { it.starred }.take(8)
        else vm.contacts.value.map { it to score(it.name, q) }.filter { it.second > 0 }
            .sortedByDescending { it.second }.map { it.first }

    /** 100 exact, 90 prefix, 70 word-prefix, 50 substring, 20 subsequence, 0 none. */
    private fun score(text: String, query: String): Int {
        val t = text.lowercase()
        val q = query.lowercase().trim()
        if (q.isEmpty()) return 0
        if (t == q) return 100
        if (t.startsWith(q)) return 90
        if (t.split(' ', '-', '.', '_').any { it.startsWith(q) }) return 70
        if (t.contains(q)) return 50
        var i = 0
        for (ch in t) if (i < q.length && ch == q[i]) i++
        return if (i == q.length && q.length >= 2) 20 else 0
    }

    private fun findApp(name: String): AppEntry? = rankApps(name).firstOrNull()

    /** Parses "a | b" (or "a b" when both are one word) into two apps. */
    private fun twoApps(rest: String, example: String): Pair<AppEntry, AppEntry> {
        val pieces = if ('|' in rest) rest.split('|', limit = 2).map { it.trim() } else rest.trim().split(Regex("\\s+"), limit = 2)
        val a = pieces.getOrElse(0) { "" }
        val b = pieces.getOrElse(1) { "" }
        require(a.isNotBlank() && b.isNotBlank()) { "usage: $example" }
        val l = rankApps(a).firstOrNull { !it.isWork } ?: error("no app matching '$a'")
        val r = rankApps(b).firstOrNull { !it.isWork } ?: error("no app matching '$b'")
        return l to r
    }

    private fun listPairs() {
        val p = vm.prefs.pairs.value
        if (p.isEmpty()) vm.out("no pairs — `pair save work chrome | slack`", LineKind.DIM)
        p.toSortedMap().forEach { (k, v) -> vm.out("  ${k.padEnd(10)} ${vm.labelOf(v.first)} | ${vm.labelOf(v.second)}") }
    }

    private fun findContact(name: String): ContactSummary? = rankContacts(name).firstOrNull()

    // ───────────────────────── commands ─────────────────────────

    private fun buildCommands(): List<Cmd> = listOf(
        Cmd("help", "help", "list commands", setOf("?", "man")) { _, _ ->
            vm.out("commands:", LineKind.ACCENT)
            commands.forEach { vm.out("  ${it.usage.padEnd(24)} ${it.help}") }
            vm.out("anything else: fuzzy-matches apps, contacts and sections, then searches the web.", LineKind.DIM)
        },
        Cmd("open", "open <app>", "launch an app", setOf("launch", "run")) { _, rest ->
            val app = findApp(rest) ?: error("no app matching '$rest'")
            vm.out("launching ${app.label}", LineKind.OK); vm.launch(app)
        },
        Cmd("apps", "apps [filter]", "list installed apps", setOf("ls")) { _, rest ->
            val list = if (rest.isBlank()) vm.apps.value else rankApps(rest)
            list.forEach { vm.out("  ${it.label.padEnd(28)} ${it.packageName}${if (it.isWork) "  [work]" else ""}") }
            vm.out("${list.size} apps", LineKind.DIM)
        },
        Cmd("cd", "cd <section>", "switch pane (home, mail, cal…)", setOf("go")) { _, rest ->
            val s = Section.find(rest) ?: error("no such section: $rest")
            vm.go(s); vm.shellOpen.value = false
        },
        Cmd("call", "call <name|number>", "dial a contact") { _, rest ->
            val number = if (rest.any { it.isDigit() } && rest.none { it.isLetter() }) rest
            else findContact(rest)?.phone ?: error("no phone number for '$rest'")
            vm.dial(number)
        },
        Cmd("sms", "sms <name|number>", "text a contact", setOf("text")) { _, rest ->
            val number = if (rest.any { it.isDigit() } && rest.none { it.isLetter() }) rest
            else findContact(rest)?.phone ?: error("no phone number for '$rest'")
            vm.sms(number)
        },
        Cmd("email", "email <name|address>", "compose an email") { _, rest ->
            val addr = if ('@' in rest || rest.isBlank()) rest else findContact(rest)?.email ?: error("no email for '$rest'")
            vm.email(addr)
        },
        Cmd("cal", "cal [days]", "print upcoming events", setOf("agenda")) { args, _ ->
            val days = args.firstOrNull()?.toIntOrNull() ?: 1
            val end = Fmt.addDays(Fmt.startOfDay(System.currentTimeMillis()), days)
            val list = vm.events.value.filter { it.end >= System.currentTimeMillis() && it.begin < end }
            if (list.isEmpty()) vm.out(if (vm.perms.value["calendar"] == true) "nothing scheduled" else "calendar permission needed — run `grant`", LineKind.DIM)
            var lastDay = ""
            list.forEach { e ->
                val d = Fmt.dayLabel(e.begin)
                if (d != lastDay) { vm.out(d, LineKind.ACCENT); lastDay = d }
                val time = if (e.allDay) "all-day" else Fmt.time(e.begin)
                vm.out("  ${time.padEnd(9)} ${e.title}${if (e.location.isNotBlank()) "  @ ${e.location}" else ""}")
            }
        },
        Cmd("new", "new <event|contact|email>", "create something") { args, _ ->
            when (args.firstOrNull()) {
                "event" -> vm.newEvent()
                "contact" -> vm.start(vm.contactsRepo.newIntent())
                "email", "mail" -> vm.email("")
                else -> error("usage: new <event|contact|email>")
            }
        },
        Cmd("todo", "todo [add|done|rm] …", "manage tasks", setOf("task", "tasks")) { args, rest ->
            when (args.firstOrNull()) {
                null, "ls", "list" -> {
                    val list = vm.tasks.value
                    if (list.isEmpty()) vm.out("no tasks — `todo add buy milk`", LineKind.DIM)
                    list.forEachIndexed { i, t -> vm.out("  ${i + 1}. [${if (t.done) "x" else " "}] ${t.text}", if (t.done) LineKind.DIM else LineKind.OUT) }
                }
                "add" -> { vm.addTask(rest.removePrefix("add").trim()); vm.out("added", LineKind.OK) }
                "done", "x" -> {
                    val i = args.getOrNull(1)?.toIntOrNull()?.minus(1) ?: error("usage: todo done <n>")
                    vm.toggleTask(vm.tasks.value.getOrNull(i)?.id ?: error("no task #${i + 1}")); vm.out("toggled #${i + 1}", LineKind.OK)
                }
                "rm", "del" -> {
                    val i = args.getOrNull(1)?.toIntOrNull()?.minus(1) ?: error("usage: todo rm <n>")
                    vm.removeTask(vm.tasks.value.getOrNull(i)?.id ?: error("no task #${i + 1}")); vm.out("removed #${i + 1}", LineKind.OK)
                }
                else -> { vm.addTask(rest); vm.out("added", LineKind.OK) }
            }
        },
        Cmd("weather", "weather", "current conditions", setOf("wttr")) { _, _ ->
            val w = vm.weather.value
            if (w == null) { vm.out(vm.weatherStatus.value.ifBlank { "no data yet" }, LineKind.DIM); vm.reloadWeather(); return@Cmd }
            vm.out("${w.glyph}  ${w.temp.toInt()}°${w.unit} ${w.description}${w.place?.let { "  ($it)" } ?: ""}", LineKind.ACCENT)
            vm.out("   high ${w.high.toInt()}° / low ${w.low.toInt()}°")
            vm.out(if (w.rainInHours == null) "   no rain in the next 12h" else "   rain likely in ~${w.rainInHours}h")
        },
        Cmd("news", "news [n]", "latest headlines") { args, _ ->
            val n = args.firstOrNull()?.toIntOrNull() ?: 8
            val items = vm.news.value.take(n)
            if (items.isEmpty()) { vm.out(vm.newsStatus.value.ifBlank { "no stories yet" }, LineKind.DIM); vm.reloadNews() }
            items.forEachIndexed { i, it -> vm.out("  ${(i + 1).toString().padStart(2)}. ${it.title}  — ${it.source}") }
        },
        Cmd("notifs", "notifs [clear]", "recent notifications", setOf("n", "inbox")) { args, _ ->
            if (!vm.notifAccess.value) { vm.out("notification access is off — run `access`", LineKind.ERR); return@Cmd }
            if (args.firstOrNull() == "clear") { vm.clearAllNotifications(); vm.out("cleared all notifications", LineKind.OK); return@Cmd }
            vm.notifications.value.filter { !it.removed }.take(15).forEach {
                vm.out("  ${Fmt.ago(it.time).padStart(4)}  ${it.app}: ${it.title} — ${it.text.take(60)}")
            }
        },
        Cmd("play", "play | pause | next | prev", "media controls", setOf("pause", "next", "prev", "skip")) { _, _ ->
            // the alias that was typed decides the action
            when (invokedAs) {
                "pause" -> NowPlayingStore.pause()
                "next", "skip" -> NowPlayingStore.next()
                "prev" -> NowPlayingStore.previous()
                else -> NowPlayingStore.play()
            }
            if (vm.nowPlaying.value == null) vm.out("no active media session${if (!vm.notifAccess.value) " (needs `access`)" else ""}", LineKind.DIM)
        },
        Cmd("theme", "theme <name>", "phosphor | amber | pastel | paper") { args, _ ->
            val t = args.firstOrNull()?.lowercase()
            if (t == null || t !in Themes.names) { vm.out("themes: ${Themes.names.joinToString(", ")} (current: ${vm.prefs.theme.value})"); return@Cmd }
            vm.prefs.setTheme(t); vm.out("theme → $t", LineKind.OK)
        },
        Cmd("grant", "grant", "request runtime permissions", setOf("perms")) { _, _ ->
            vm.perms.value.forEach { (k, v) -> vm.out("  ${k.padEnd(10)} ${if (v) "granted" else "denied"}", if (v) LineKind.OK else LineKind.ERR) }
            vm.requestAllPermissions()
        },
        Cmd("access", "access", "enable notification access") { _, _ -> vm.openNotificationAccess() },
        Cmd("default", "default", "make foldterm the home app") { _, _ -> vm.requestDefaultHome() },
        Cmd("settings", "settings [wifi|bt|display|battery|apps|sound]", "system settings", setOf("set")) { args, _ ->
            val action = when (args.firstOrNull()) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bt", "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "sound" -> Settings.ACTION_SOUND_SETTINGS
                "home" -> Settings.ACTION_HOME_SETTINGS
                null -> Settings.ACTION_SETTINGS
                else -> error("unknown settings page")
            }
            vm.start(Intent(action))
        },
        Cmd("info", "info <app>", "app info page") { _, rest ->
            val app = findApp(rest) ?: error("no app matching '$rest'")
            vm.appsRepo.appInfo(app)
        },
        Cmd("uninstall", "uninstall <app>", "remove an app", setOf("rm")) { _, rest ->
            val app = findApp(rest) ?: error("no app matching '$rest'")
            vm.start(vm.appsRepo.uninstallIntent(app))
        },
        Cmd("dock", "dock [add|rm <app>|reset]", "edit the dock") { args, rest ->
            val name = rest.substringAfter(' ', "").trim()
            when (args.firstOrNull()) {
                "add" -> { val a = findApp(name) ?: error("no app '$name'"); vm.pinToDock(a); vm.out("pinned ${a.label}", LineKind.OK) }
                "rm" -> { val a = findApp(name) ?: error("no app '$name'"); vm.unpinFromDock(a); vm.out("unpinned ${a.label}", LineKind.OK) }
                "reset" -> { vm.prefs.setDock(null); vm.out("dock reset", LineKind.OK) }
                else -> vm.dockApps().forEach { vm.out("  ${it.label}") }
            }
        },
        Cmd("=", "= <expression>", "calculator, e.g. = 2*(3+4)", setOf("calc")) { _, rest ->
            val v = Calc.eval(rest)
            vm.out("= ${Calc.format(v)}", LineKind.ACCENT)
        },
        Cmd("timer", "timer <5m|1h30m|90s> [label]", "start a countdown") { args, _ ->
            val secs = When.duration(args.firstOrNull() ?: error("usage: timer 5m [label]")) ?: error("can't read '${args.first()}' — try 5m, 1h30m or 90s")
            val label = args.drop(1).joinToString(" ")
            val i = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
            if (vm.start(i, "no clock app can set timers")) vm.out("timer set: ${When.format(secs)}${if (label.isNotBlank()) " · $label" else ""}", LineKind.OK)
        },
        Cmd("alarm", "alarm <7:30|7pm> [label]", "set an alarm") { args, _ ->
            val raw = args.take(2).joinToString("").takeIf { args.getOrNull(1)?.lowercase() in setOf("am", "pm") } ?: args.firstOrNull() ?: error("usage: alarm 7:30 [label]")
            val (h, m) = When.clock(raw) ?: error("can't read '$raw' — try 7:30, 7pm or 19:30")
            val label = args.drop(if (raw.length > (args.firstOrNull()?.length ?: 0)) 2 else 1).joinToString(" ")
            val i = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
            if (vm.start(i, "no clock app can set alarms")) vm.out("alarm set for %02d:%02d%s".format(h, m, if (label.isNotBlank()) " · $label" else ""), LineKind.OK)
        },
        Cmd("event", "event [day] [time] <title>", "quick-add, e.g. event fri 3pm dentist") { args, _ ->
            if (args.isEmpty()) { vm.newEvent(); return@Cmd }
            val p = When.event(args)
            val title = p.title.ifBlank { "New event" }
            vm.out("→ ${Fmt.dayLabel(p.begin)} ${if (p.hasTime) Fmt.time(p.begin) else "${Fmt.time(p.begin)} (guessed)"} · $title", LineKind.DIM)
            vm.start(
                Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, title)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, p.begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, p.begin + 3_600_000),
                "no calendar app",
            )
        },
        Cmd("torch", "torch [on|off]", "flashlight", setOf("flashlight", "light")) { args, _ ->
            vm.setTorch(when (args.firstOrNull()?.lowercase()) { "on" -> true; "off" -> false; else -> null })
        },
        Cmd("hide", "hide <app>", "hide an app from the drawer") { _, rest ->
            val app = findApp(rest) ?: error("no app matching '$rest'")
            vm.setHidden(app, true); vm.out("hidden ${app.label} — `unhide ${app.label}` to undo", LineKind.OK)
        },
        Cmd("unhide", "unhide <app>", "show a hidden app again") { _, rest ->
            val app = rankApps(rest, vm.allApps.value.filter { it.key in vm.prefs.hidden.value }).firstOrNull()
                ?: error("no hidden app matching '$rest'")
            vm.setHidden(app, false); vm.out("unhid ${app.label}", LineKind.OK)
        },
        Cmd("hidden", "hidden", "list hidden apps") { _, _ ->
            val list = vm.allApps.value.filter { it.key in vm.prefs.hidden.value }
            if (list.isEmpty()) vm.out("no hidden apps", LineKind.DIM)
            list.forEach { vm.out("  ${it.label}") }
        },
        Cmd("note", "note <text>", "save a quick note", setOf("jot")) { _, rest ->
            if (rest.isBlank()) { vm.newNote(); vm.go(Section.NOTES); vm.shellOpen.value = false; return@Cmd }
            vm.newNote(rest); vm.out("noted", LineKind.OK)
        },
        Cmd("notes", "notes [filter]", "list notes") { _, rest ->
            val list = vm.prefs.notes.value.filter { rest.isBlank() || it.text.contains(rest, true) }
            if (list.isEmpty()) vm.out("no notes", LineKind.DIM)
            list.take(20).forEach { vm.out("  ${Fmt.ago(it.updated).padStart(4)}  ${it.title}") }
        },
        Cmd("alias", "alias [name=command]", "create or list shortcuts") { _, rest ->
            if (rest.isBlank()) {
                val a = vm.prefs.aliases.value
                if (a.isEmpty()) vm.out("no aliases — try `alias wk=open slack`", LineKind.DIM)
                a.toSortedMap().forEach { (k, v) -> vm.out("  ${k.padEnd(10)} → $v") }
                return@Cmd
            }
            val name = rest.substringBefore('=').substringBefore(' ').trim().lowercase()
            val expansion = (if ('=' in rest) rest.substringAfter('=') else rest.substringAfter(' ', "")).trim().removeSurrounding("\"").removeSurrounding("'")
            require(name.isNotEmpty() && expansion.isNotEmpty()) { "usage: alias name=command" }
            require(commands.none { it.name == name || name in it.aliases }) { "'$name' is a built-in command" }
            vm.prefs.setAlias(name, expansion); vm.out("alias $name → $expansion", LineKind.OK)
        },
        Cmd("unalias", "unalias <name>", "remove a shortcut") { args, _ ->
            val name = args.firstOrNull()?.lowercase() ?: error("usage: unalias name")
            require(name in vm.prefs.aliases.value) { "no alias '$name'" }
            vm.prefs.setAlias(name, null); vm.out("removed $name", LineKind.OK)
        },
        Cmd("split", "split <left> | <right>", "two apps side by side", setOf("sp")) { _, rest ->
            val (l, r) = twoApps(rest, "split chrome | notes")
            vm.split(l, r)
        },
        Cmd("pair", "pair <name> | save <name> <a> | <b> | rm <name>", "saved app pairs") { args, rest ->
            when (args.firstOrNull()?.lowercase()) {
                null, "ls", "list" -> listPairs()
                "save", "add" -> {
                    val name = args.getOrNull(1)?.lowercase() ?: error("usage: pair save work chrome | slack")
                    val (l, r) = twoApps(rest.substringAfter(args[0]).trim().substringAfter(args[1]).trim(), "pair save work chrome | slack")
                    vm.prefs.setPair(name, l.key to r.key)
                    vm.out("saved pair '$name': ${l.label} | ${r.label} — run `pair $name`", LineKind.OK)
                }
                "rm", "del", "delete" -> {
                    val name = args.getOrNull(1)?.lowercase() ?: error("usage: pair rm <name>")
                    require(name in vm.prefs.pairs.value) { "no pair '$name'" }
                    vm.prefs.setPair(name, null); vm.out("removed pair '$name'", LineKind.OK)
                }
                else -> vm.launchPair(args[0].lowercase())
            }
        },
        Cmd("pairs", "pairs", "list saved app pairs") { _, _ -> listPairs() },
        Cmd("float", "float <app>", "open an app in a pop-up window", setOf("popup", "pop")) { _, rest ->
            val app = findApp(rest) ?: error("no app matching '$rest'")
            vm.out("pop-up: ${app.label}", LineKind.OK); vm.popup(app)
        },
        Cmd("vol", "vol [0-100]", "media volume", setOf("volume")) { args, _ ->
            vm.volume(args.firstOrNull()?.removeSuffix("%")?.toIntOrNull())
        },
        Cmd("mute", "mute", "media volume to 0") { _, _ -> vm.volume(0) },
        Cmd("maps", "maps <place>", "search the map", setOf("map", "nav")) { _, rest -> vm.directions(rest) },
        Cmd("yt", "yt <query>", "search YouTube", setOf("youtube")) { _, rest ->
            vm.openUrl("https://www.youtube.com/results?search_query=${android.net.Uri.encode(rest)}")
        },
        Cmd("copy", "copy <text>", "copy text to the clipboard", setOf("cp")) { _, rest -> vm.copy(rest) },
        Cmd("crashlog", "crashlog [clear]", "show (and copy) the last crash") { args, _ ->
            val trace = dev.foldterm.launcher.data.CrashLog.read(vm.getApplication())
            when {
                trace == null -> vm.out("no crash recorded", LineKind.OK)
                args.firstOrNull() == "clear" -> { dev.foldterm.launcher.data.CrashLog.clear(vm.getApplication()); vm.out("cleared", LineKind.OK) }
                else -> {
                    trace.lines().take(40).forEach { vm.out(it, if (it.contains("Exception") || it.contains("Error")) LineKind.ERR else LineKind.DIM) }
                    vm.copy(trace)
                    vm.out("full trace copied — paste it to whoever's fixing this. `crashlog clear` to dismiss", LineKind.ACCENT)
                }
            }
        },
        Cmd("bright", "bright [0-100|auto]", "screen brightness", setOf("brightness")) { args, _ ->
            val a = args.firstOrNull()?.lowercase()?.removeSuffix("%")
            when {
                a == null -> vm.out("brightness ${(vm.controls.brightness() * 100).toInt()}%${if (vm.controls.autoBrightness()) " (auto)" else ""}")
                a == "auto" -> vm.control { vm.controls.setAutoBrightness(true) }
                else -> { val v = a.toIntOrNull() ?: error("usage: bright 0-100 | auto"); vm.control { vm.controls.setBrightness(v / 100f) }; vm.out("brightness → $v%", LineKind.OK) }
            }
        },
        Cmd("dnd", "dnd [on|off]", "do not disturb") { args, _ ->
            val on = when (args.firstOrNull()?.lowercase()) { "on" -> true; "off" -> false; else -> !vm.controls.dndOn() }
            vm.control { vm.controls.setDnd(on) }
            vm.out("do not disturb ${if (on) "on" else "off"}", LineKind.OK)
        },
        Cmd("ring", "ring <normal|vibrate|silent>", "ringer mode", setOf("ringer")) { args, _ ->
            val mode = when (args.firstOrNull()?.lowercase()) {
                "normal", "on", "loud" -> android.media.AudioManager.RINGER_MODE_NORMAL
                "vibrate", "vib" -> android.media.AudioManager.RINGER_MODE_VIBRATE
                "silent", "off" -> android.media.AudioManager.RINGER_MODE_SILENT
                else -> error("usage: ring normal | vibrate | silent")
            }
            vm.control { vm.controls.setRingerMode(mode) }
        },
        Cmd("rotate", "rotate [on|off]", "auto-rotate") { args, _ ->
            val on = when (args.firstOrNull()?.lowercase()) { "on" -> true; "off" -> false; else -> !vm.controls.autoRotate() }
            vm.control { vm.controls.setAutoRotate(on) }
            vm.out("auto-rotate ${if (on) "on" else "off"}", LineKind.OK)
        },
        Cmd("usage", "usage", "today's screen time", setOf("screentime")) { _, _ ->
            val r = vm.screenTime.report() ?: run { vm.out("usage access needed — opening settings", LineKind.ERR); vm.start(vm.screenTime.settingsIntent()); return@Cmd }
            vm.out("today ${Fmt.duration(r.totalMs)} · ${r.unlocks} unlocks", LineKind.ACCENT)
            r.apps.take(8).forEach { vm.out("  ${Fmt.duration(it.ms).padStart(6)}  ${vm.appLabelForPackage(it.pkg)}") }
        },
        Cmd("focus", "focus [25m] [label] | stop", "focus session: hides distracting apps", setOf("pomodoro")) { args, _ ->
            val first = args.firstOrNull()?.lowercase()
            when {
                first == "stop" || first == "end" -> vm.stopFocus()
                first == null && vm.focusActive() -> vm.out("focusing until ${Fmt.time(vm.prefs.focusUntil.value)}${vm.prefs.focusLabel.value.let { if (it.isNotBlank()) " · $it" else "" }}")
                else -> {
                    val secs = first?.let { When.duration(it) }
                    val minutes = (secs ?: 25 * 60) / 60
                    val label = (if (secs != null) args.drop(1) else args).joinToString(" ")
                    require(minutes in 1..600) { "focus length must be 1m–10h" }
                    vm.startFocus(minutes, label)
                }
            }
        },
        Cmd("lock", "lock", "lock the screen") { _, _ -> vm.lockScreen() },
        Cmd("shade", "shade", "pull down notifications", setOf("notifications")) { _, _ -> vm.expandNotifications() },
        Cmd("g", "g <query>", "web search", setOf("web", "search", "ddg")) { _, rest -> vm.webSearch(rest) },
        Cmd("date", "date", "date and time") { _, _ ->
            vm.out("${Fmt.longDate(System.currentTimeMillis())}  ${Fmt.time(System.currentTimeMillis())}")
        },
        Cmd("uptime", "uptime", "device vitals", setOf("top", "htop", "battery")) { _, _ ->
            val v = vm.deviceRepo.vitals()
            vm.out("  battery  ${v.battery}%${if (v.charging) " (charging)" else ""}  ${v.tempC}°C")
            vm.out("  storage  ${Fmt.gb(v.storageFreeGb)} free of ${Fmt.gb(v.storageTotalGb)}")
            vm.out("  memory   ${Fmt.gb(v.ramAvailGb)} free of ${Fmt.gb(v.ramTotalGb)}")
            vm.out("  uptime   ${Fmt.duration(v.uptimeMs)}")
        },
        Cmd("fold", "fold", "hinge / posture info") { _, _ ->
            val f = vm.fold.value
            vm.out("posture: ${f.label}${f.hinge?.let { "  hinge=${it.toShortString()} ${if (f.vertical) "vertical" else "horizontal"}" } ?: ""}")
        },
        Cmd("whoami", "whoami", "who you are") { _, _ ->
            vm.out(vm.prefs.userName.value.ifBlank { "user (set a name in settings)" })
        },
        Cmd("echo", "echo <text>", "print text") { _, rest -> vm.out(rest) },
        Cmd("reload", "reload", "refresh all data", setOf("sync", "r")) { _, _ ->
            vm.loadApps(); vm.refresh(force = true); vm.out("reloading…", LineKind.DIM)
        },
        Cmd("clear", "clear", "clear the scrollback", setOf("cls")) { _, _ -> vm.clearLog() },
        Cmd("version", "version", "build info", setOf("about")) { _, _ ->
            vm.out("foldterm ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        },
    )
}
