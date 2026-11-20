<div align="center">

# `❯ foldterm_`

**A terminal-flavoured home screen for foldable Android phones.**

Agenda, inbox, people, weather and news in hinge-aware panes, with a command line where the search bar used to be.

[![Build](https://github.com/TheSwagInDisguise/foldterm/actions/workflows/build.yml/badge.svg)](https://github.com/TheSwagInDisguise/foldterm/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/TheSwagInDisguise/foldterm?include_prereleases&sort=semver)](https://github.com/TheSwagInDisguise/foldterm/releases/latest)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)

[**Download the latest APK →**](https://github.com/TheSwagInDisguise/foldterm/releases/latest)

</div>

---

## Contents

- [Highlights](#highlights)
- [Panes](#panes)
- [Home, dock & drawer](#home-dock--drawer)
- [Built for foldables](#built-for-foldables)
- [Make it yours](#make-it-yours)
- [The prompt](#the-prompt)
- [Install](#install)
- [Privacy](#privacy)
- [Building & releasing](#building--releasing)

---

## Highlights

- **A command line for your phone.** One prompt launches apps, calls people, sets timers, does maths, adds events, splits the screen and searches everything.
- **Hinge-aware layouts.** Panes split exactly at the Fold's crease, tabletop mode moves a live console to the bottom half, and the cover screen gets its own card layout.
- **Your day on one screen.** Calendar, inbox, weather, steps, tasks and news are summarised on Home, and every card is configurable.
- **Real split screen and app pairs.** Long-press → *split with…*, or `split chrome | slack`; save pairs and launch them by name.
- **Widgets, icon packs, themes.** Host any Android widget, use any standard icon pack or tinted mono icons, and choose from four themes plus your wallpaper.
- **Private by design.** No account, no analytics, no backend. Everything is read on the device.

## Panes

| Pane | What it does |
|---|---|
| **⌂ Home** | Configurable cards: happening now (join / directions), agenda, on-device summary, widgets, quick dial, inbox, now playing, weather, tasks, latest note, big clock |
| **» Chat / @ Mail** | Live feed from your messaging and mail apps' notifications, with inline reply where the app supports it |
| **! Alerts** | Notification centre: grouped by app, clear all or per app, reply, copy, history, per-app **pin** and **mute** rules, and today's activity by hour |
| **▦ Calendar** | **Agenda**, **day** timeline (overlaps side by side, a live *now* line, free gaps you tap to fill) and **month** views, event details, and a task list |
| **☺ People** | Contacts with *in case of emergency* and favourite groups, companies, quick call/text/email, share, vCard view |
| **▣ Photos** | Recent photos grouped by day |
| **♫ Media** | Full player for whatever's playing: artwork, seek bar, skip and ±15/30 s jumps, switch between apps that are playing, current audio output, volume |
| **♥ Health** | Step counter with a daily goal, plus an `htop`-style view of battery, storage, memory, uptime and hinge posture |
| **☁ Weather** | Current conditions and feels-like, a 12-hour rain chart, 7-day range bars, wind, UV and sunrise/sunset, and severe-weather warnings (also shown on Home) |
| **≡ News** | Headlines from any RSS or Atom feeds you choose, filterable by source |
| **✎ Notes** | A scratchpad with autosave, search and copy. `note <text>` jots from the prompt |
| **◫ Widgets** | Host real Android widgets (clocks, music, to-do lists…) in one or two columns, with reorder and resize. They can also go on Home |
| **⚙ Settings** | Themes, icons, wallpaper, sidebar, home and cover layouts, permissions, default launcher, units, location, feeds |

## Home, dock & drawer

- **Dock:** your pinned apps (sized to fit the screen), the app drawer button and a now-playing widget. Tap the widget for the Media pane, or its artwork to play/pause.
- **App drawer:** a centred grid with unread **badges**, filtering, saved **app pairs** as chips, and a long-press menu with *split with…*, *pop-up window*, pin to dock, app info, hide and uninstall.
- **Gestures:** swipe up for the drawer, swipe down for the notification shade (or to close the drawer), long-press for haptic menus. On a keyboard: <kbd>/</kbd> opens the prompt, <kbd>Esc</kbd> closes it, <kbd>Alt</kbd>+<kbd>1</kbd>…<kbd>9</kbd> jumps between sections.
- **Split screen & pop-ups:** `split <left> | <right>`, saved pairs (`pair save work chrome | slack`, then `pair work` or just `work`), and `float <app>` for a floating window where the device supports it. Split screen needs the unfolded inner screen.
- **Crash log:** if FoldTerm ever crashes, the next launch says so and `crashlog` shows (and copies) the details.

## Built for foldables

The layout follows the hinge reported by Jetpack WindowManager:

| Posture | Layout |
|---|---|
| **Folded / cover screen** | Cover cards (big clock, next event, quick dial, inbox, now playing, weather, tasks) in one column, with section tabs above the dock |
| **Open, flat** | Nav rail and two panes, split exactly at the hinge so nothing sits on the crease |
| **Half-open, book** | Same as open, with panes on either side of the fold |
| **Half-open, tabletop** | Content on the top half, a live console and the dock on the bottom half |

## Make it yours

- **Themes:** `phosphor` (green on black), `amber`, `pastel` (light and soft) and `paper` (planner-style), from Settings or `theme <name>`.
- **Icons:** colour, **mono** (tinted to the theme or any colour you pick), or any standard Android **icon pack**, which shows how many of your apps it covers.
- **Background:** the theme's backdrop, or your wallpaper with four dim levels.
- **Sidebar:** switch sections on or off and reorder them. Hidden sections still open with `cd <section>`.
- **Home layout:** choose the cards, their order and their column (left/right).
- **Cover screen:** choose the cards for the folded phone, or keep the classic layout.
- **Hidden apps, aliases and pairs:** keep the drawer tidy and turn long commands into one word.

## The prompt

Tap the search bar, or press <kbd>/</kbd> on a keyboard. Results update as you type: apps, contacts, sections and commands.
<kbd>Enter</kbd> runs the top result, <kbd>Tab</kbd> completes, <kbd>↑</kbd>/<kbd>↓</kbd> walk through history, and <kbd>Esc</kbd> closes the prompt.

```text
❯ help
  open <app>                launch an app (or just type its name)
  cd <section>              home · chat · mail · notifs · cal · people · photos · media · health · weather · news · notes · widgets · settings
  call | sms | email <name> act on a contact
  cal [days]                print upcoming events
  todo [add|done n|rm n]    manage tasks
  weather · news · notifs [clear]   quick readouts
  crashlog [clear]          last crash details
  play | pause | next | prev
  theme <phosphor|amber|pastel|paper>
  dock add|rm <app> | reset
  hide | unhide <app> · hidden
  = <expr>                  calculator (or just type 2*(3+4))
  timer <5m|1h30m|90s> [label]
  alarm <7:30|7pm> [label]
  event [day] [time] <title>  e.g. event fri 3pm dentist
  torch [on|off] · shade
  note <text> · notes [filter]
  alias name=command · unalias name   e.g. alias wk=open slack
  split <left> | <right>    two apps side by side (inner screen)
  pair save <name> <a> | <b> · pair <name> · pairs · pair rm <name>
  float <app>               open in a pop-up window
  vol [0-100] · mute
  maps <place> · yt <query> · copy <text>
  settings [wifi|bt|display|battery|apps|sound]
  grant · access · default  permissions · notification access · set as home app
  uptime · fold · date · clear · reload · version
```

The prompt searches everything: apps, contacts, upcoming events, notes and messages. Typing a web address (`github.com`) opens it, and maths gets answered as you type. With the prompt empty, it shows your recent apps and commands. Apps you open often rank higher in results. Anything that isn't a command or a match becomes a web search.

## Install

Each release has two APKs, signed with the same key so you can switch between them without uninstalling:

| APK | Use it when |
|---|---|
| `foldterm-x.y.z.apk` (full) | You want the Mail, Chat and now-playing panes, which are built from your notifications |
| `foldterm-x.y.z-lite.apk` | Your phone refuses the full APK with *"this app requests access to sensitive data"* |

1. Download an APK from **[Releases](https://github.com/TheSwagInDisguise/foldterm/releases/latest)** and open it on your phone. You may need to allow installs from your browser.
2. Launch **FoldTerm** and accept the permission prompts. You can run `grant` later to ask again.
3. **Settings → home app → set default** makes FoldTerm your home screen.
4. *(full only)* **Settings → notifs → grant** turns on Mail, Chat and now playing.

Requires Android 10 (API 29) or newer. Every permission is optional; a pane without its permission shows a one-tap fix.

### Troubleshooting

- **"Requests access to sensitive data" / install blocked.** Google Play Protect flags sideloaded apps that can read notifications, and in some countries it blocks them outright. Install the **lite** APK instead. If you want the full edition, open the Play Store, tap your profile → **Play Protect** → ⚙ → turn off **Scan apps with Play Protect**, install the full APK, then turn scanning back on.
- **Notification access switch is greyed out.** Android restricts this for sideloaded apps. Go to **Settings → Apps → FoldTerm → ⋮ → Allow restricted settings**, then try again.

## Privacy

FoldTerm has no account, no analytics and no backend.

- Calendar, contacts, photos and steps are read from Android's own providers and stay on the device.
- Mail and chat come from notifications that are already on your phone. FoldTerm never signs in to your accounts.
- Network requests go only to [Open-Meteo](https://open-meteo.com) for weather (approximate coordinates, no key) and to the news feeds you configure.

## Building & releasing

Everything is built by GitHub Actions ([`build.yml`](.github/workflows/build.yml)):

- **Every push and pull request** builds a release APK and attaches it to the workflow run as an artifact.
- **New version on `main`**: when [`version.txt`](version.txt) holds a version with no release yet, CI builds, signs, tags `vX.Y.Z` and publishes a GitHub Release with the APK. Pushing a `v*` tag does the same. Every release is signed (see below).

### Signing key

Releases are signed with the key in [`signing/`](signing), so CI works with no setup. Anyone with access to this repo can sign APKs that install as updates. That's fine for a personal build. For a locked-down key, add these repository secrets; they take priority over the committed key:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | The release keystore (`.jks`), base64-encoded |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Switching keys means uninstalling the old build once, because Android only accepts updates signed with the same key.

### Cutting a release

Bump the version and push to `main`:

```bash
echo "0.2.0" > version.txt
```

```bash
git commit -am "Release 0.2.0" && git push
```

### Project layout

```text
app/src/main/java/dev/foldterm/launcher/
├── MainActivity.kt         window/fold tracking, permission + home-role requests
├── LauncherViewModel.kt    state and actions
├── shell/Shell.kt          command interpreter and fuzzy suggestions
├── SplitActivity.kt        invisible trampoline that enters split screen
├── data/                   calendar, contacts, apps, notifications/media, widgets, icon packs, weather/news, device
└── ui/                     theme, components, root layout, screens/
```

Stack: Kotlin · Jetpack Compose · Material 3 · AndroidX WindowManager · coroutines. No third-party SDKs.
