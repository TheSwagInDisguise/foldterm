package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.Section
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T

private val GROUPS = listOf(
    "your day" to listOf(Section.HOME, Section.CALENDAR, Section.NOTIFS, Section.MAIL, Section.CHAT, Section.PEOPLE, Section.WEATHER),
    "get things done" to listOf(Section.NOTES, Section.LINKS, Section.MEMOS, Section.HABITS, Section.MONEY, Section.FOCUS),
    "play & listen" to listOf(Section.MEDIA, Section.RADIO, Section.PODCASTS, Section.NEWS, Section.PHOTOS, Section.GAMES),
    "phone" to listOf(Section.CONTROLS, Section.USAGE, Section.HEALTH, Section.TOOLS, Section.WIDGETS, Section.SETTINGS),
)

private val BLURBS = mapOf(
    Section.HOME to "your cards", Section.CALENDAR to "agenda, day, week, month", Section.NOTIFS to "every notification",
    Section.MAIL to "mail from notifications", Section.CHAT to "messages & replies", Section.PEOPLE to "contacts & groups",
    Section.WEATHER to "forecast & places", Section.NOTES to "notes & checklists", Section.LINKS to "read later",
    Section.MEMOS to "voice recordings", Section.HABITS to "streaks", Section.MONEY to "spending & budget",
    Section.FOCUS to "distraction-free timer", Section.MEDIA to "now playing", Section.RADIO to "internet radio",
    Section.PODCASTS to "episodes", Section.NEWS to "rss headlines", Section.PHOTOS to "recent & albums",
    Section.GAMES to "serpent, mines, typer", Section.CONTROLS to "quick settings", Section.USAGE to "screen time",
    Section.HEALTH to "steps & vitals", Section.TOOLS to "compass, level, dice", Section.WIDGETS to "android widgets",
    Section.SETTINGS to "make it yours",
)

/** Every section as a tile, grouped, so the sidebar can stay short. */
@Composable
fun HubScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    Panel(Modifier.fillMaxSize(), title = "hub") {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GROUPS.forEach { (title, sections) ->
                item(key = "h_$title", span = { GridItemSpan(maxLineSpan) }) {
                    T(title, color = t.dim, size = 12.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                }
                sections.forEach { s ->
                    item(key = s.id) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.panelAlt)
                                .border(1.dp, t.border, RoundedCornerShape(16.dp)).clickable { vm.go(s) }.padding(14.dp),
                        ) {
                            T(s.glyph, color = t.accent, size = 24.sp)
                            Spacer(Modifier.height(6.dp))
                            T(s.title.lowercase(), weight = FontWeight.Bold)
                            T(BLURBS[s] ?: "", color = t.dim, size = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
