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

- **A command line for your phone.** One prompt (typed or spoken) launches apps and app shortcuts, calls people, sets timers and alarms, converts units and currency, adds events and tasks, splits the screen, and searches apps, contacts, events, notes, messages and settings.
- **Hinge-aware layouts.** Panes split exactly at the Fold's crease, tabletop mode moves a live console to the bottom half, and the cover screen gets its own big-target card layout.
- **Your day on one screen.** Calendar, inbox, weather, steps, screen time, tasks, birthdays, alarms and news are summarised on Home, and every card is yours to arrange.
- **Real split screen and app pairs.** Long-press → *split with…*, or `split chrome | slack`; save pairs and launch them by name.
- **Make it yours.** Five themes including a custom editor and automatic day/night switching, icon packs or tinted mono icons, wallpaper, text size, widgets, folders, renames, gestures and a terminal screen saver.
- **Private by design.** No account, no analytics, no backend. Everything is read on the device.

## Panes

| Pane | What it does |
|---|---|
| **⌂ Home** | Cards you choose and arrange in two columns: happening now (join / directions), agenda, on-device summary, widgets, quick dial, inbox, now playing, weather, tasks, latest note, big clock, world clocks, most used apps, quick actions |
| **» Chat / @ Mail** | Live feed from your messaging and mail apps' notifications, with inline reply, the app's own buttons (archive, mark as read…), dismiss and snooze |
| **! Alerts** | Notification centre: grouped by app, clear all or per app, reply, notification buttons, snooze (15m / 1h / 3h), copy, history, per-app **pin** and **mute** rules, and today's activity by hour |
| **▦ Calendar** | **Agenda**, **day** timeline (overlaps side by side, *now* line, free gaps to tap), **week** grid and **month** views; quick-add field; show/hide individual calendars; event details; tasks with due dates and stars, which also appear in the agenda |
| **☺ People** | Contacts with photos, *in case of emergency* and favourites, companies and **groups** tabs, call / text / email / WhatsApp / share, vCard view, birthdays |
| **▣ Photos** | Recent photos by day with **album** chips, and a swipeable in-launcher viewer with share and open |
| **♫ Media** | Full player for whatever's playing: artwork, seek bar, skip and ±15/30 s jumps, switch between playing apps, audio output, volume |
| **◐ Controls** | Quick settings: ring / vibrate / silent, Do Not Disturb, volume per stream, brightness and auto-brightness, auto-rotate, screen timeout, torch, connectivity panels |
| **⏱ Screen time** | Today's total and per-app time, open counts, unlocks, a 7-day chart and how today compares with your average |
| **◎ Focus** | Focus sessions that hide distracting apps, turn on Do Not Disturb, set a clock-app timer and show a countdown banner |
| **♥ Health** | Step counter with a goal and 7-day history, plus an `htop`-style view of battery, storage, memory, uptime and hinge posture |
| **☁ Weather** | Current conditions, 12-hour rain chart, 7-day range bars, wind, UV, sunrise/sunset, severe-weather warnings, and **saved places** |
| **≡ News** | RSS/Atom headlines by source, unread dots, hide read, mark all read, and a **saved** list |
| **✎ Notes** | Autosaving scratchpad with search, copy, and a preview that renders headings, bullets and tappable `- [ ]` checklists |
| **◫ Widgets** | Host real Android widgets in one or two columns with reorder and resize; they can also go on Home and the cover screen |
| **⚙ Settings** | Themes, icons, text size, wallpaper, sidebar, home and cover layouts, gestures, permissions, search engine, morning brief, backup & restore |

## Home, dock & drawer

- **Dock:** pinned apps (sized to fit the screen), the app drawer button and a now-playing widget. Tap the widget for the Media pane, or its artwork to play/pause.
- **App drawer:** a centred grid with unread **badges**; sort by a–z, most used or newest; filter by **folders** and Android categories; an A–Z jump strip; saved **app pairs** as chips. Long-press an app for its own **shortcuts**, *split with…*, *pop-up window*, pin to dock, rename, folders, app info, hide and uninstall.
- **Gestures:** swipe up, swipe down and double-tap on empty space each run an action you choose: drawer, notification shade, prompt, alerts, media, **lock screen**, torch or nothing. On a keyboard: <kbd>/</kbd> opens the prompt, <kbd>Esc</kbd> closes it, <kbd>Alt</kbd>+<kbd>1</kbd>…<kbd>9</kbd> jumps between sections.
- **Split screen & pop-ups:** `split <left> | <right>`, saved pairs (`pair save work chrome | slack`, then `pair work` or just `work`), and `float <app>` for a floating window where the device supports it. Split screen needs the unfolded inner screen.
- **Screen saver:** *FoldTerm clock*, a large clock over falling terminal glyphs in your theme, while charging or docked.
- **Crash log:** if FoldTerm ever crashes, the next launch says so and `crashlog` shows (and copies) the details. After each update the console lists what's new.

## Built for foldables

The layout follows the hinge reported by Jetpack WindowManager:

| Posture | Layout |
|---|---|
| **Folded / cover screen** | Cover cards you choose (big clock, next event, quick dial, inbox, now playing, weather, tasks, widget, world clocks, apps, quick actions) in one column, with section tabs above the dock |
| **Open, flat** | Nav rail and two panes, split exactly at the hinge so nothing sits on the crease |
| **Half-open, book** | Same as open, with panes on either side of the fold |
| **Half-open, tabletop** | Content on the top half, a live console and the dock on the bottom half |

## Make it yours

- **Themes:** `phosphor` (green on black), `amber`, `pastel`, `paper`, and **custom** (pick a base, two accents and a background); **auto theme** switches between a day and a night theme at sunrise and sunset.
- **Icons:** colour, **mono** (tinted to the theme or any colour), or any standard Android **icon pack**, which shows how many of your apps it covers. Rename any app.
- **Background & text:** the theme's backdrop or your wallpaper with four dim levels; four text sizes; **reduced motion** (no glows or animations); an option to **hide the status bar**.
- **Layouts:** choose and order sidebar sections, Home cards (and their column), the rows of the Home summary, and cover-screen cards.
- **Prompt:** aliases (`alias work=dnd on; split slack | chrome`), command chaining with `;`, history expansion (`!!`, `!n`), `ans` for the last result, your choice of web search engine, optional key clicks, and an optional **morning brief**.
- **Backup & restore:** save every setting (layouts, notes, tasks, pairs, aliases, folders…) to a file and restore it on another phone.

## The prompt

Tap the search bar, or press <kbd>/</kbd> on a keyboard. Results update as you type: apps, contacts, sections and commands.
<kbd>Enter</kbd> runs the top result, <kbd>Tab</kbd> completes, <kbd>↑</kbd>/<kbd>↓</kbd> walk through history, and <kbd>Esc</kbd> closes the prompt.

```text
❯ help
  open <app>                launch an app (or just type its name)
  cd <section>              home · chat · mail · notifs · cal · people · photos · media · controls · usage
                            · focus · health · weather · news · notes · widgets · settings
  call | sms | email | wa <name> [msg]   act on a contact
  cal [days] · event [day] [time] <title>   e.g. event fri 3pm dentist
  todo [add|done n|rm n]    tasks; "todo add pay rent fri !" sets a due day and a star
  = <expr> · conv 5 km in mi · 100 usd in aud · time tokyo   (or just type them)
  timer 5m [label] · alarm 7:30 [label] · focus 25m [label] | stop
  weather [city] · news · notifs [clear] · usage · brief · neofetch · ip
  play | pause | next | prev · vol [0-100] · mute
  bright [0-100|auto] · dnd [on|off] · ring <normal|vibrate|silent> · rotate · torch · lock · shade
  split <left> | <right> · pair save <name> <a> | <b> · pair <name> · float <app>
  note <text> · notes · clocks add <city> · folder <name> · rename <app> = <name>
  alias name=cmd; cmd · unalias name · history [clear] · !! · !n · ans
  ice · tip
  theme <phosphor|amber|pastel|paper|custom> · dock add|rm <app> · hide | unhide <app>
  settings [wifi|bt|display|battery|apps|sound] · screensaver · grant · access · default
  maps <place> · yt <query> · g <query> · copy <text> · crashlog · reload · version
```

The prompt searches everything: apps, app shortcuts, contacts, upcoming events, notes, messages, saved pairs and settings (FoldTerm's and Android's). Maths, unit conversions, currency and world times are answered as you type. Typing a web address (`github.com`) opens it. With the prompt empty it shows recent apps and commands; <kbd>↑</kbd>/<kbd>↓</kbd> pick a suggestion while typing. Tap 🎤 to speak a command. Anything else becomes a web search.

## Install

Each release has two APKs, signed with the same key so you can switch between them without uninstalling:

| APK | Use it when |
|---|---|
| `foldterm-x.y.z.apk` (full) | You want the Mail, Chat, Alerts and Media panes, which are built from your notifications |
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
- Screen time, steps and notifications are read from Android on the device and never leave it.
- Network requests go only to [Open-Meteo](https://open-meteo.com) for weather and place search (approximate coordinates, no key), [open.er-api.com](https://www.exchangerate-api.com/docs/free) for currency rates (only when you convert currency), and the news feeds you configure.
- Optional permissions are explained where they're used: usage access (screen time), modify system settings (brightness/rotation/timeout), Do Not Disturb access, and a device-admin permission limited to locking the screen.

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
├── shell/                  command interpreter, fuzzy suggestions, calculator, units, date parsing
├── SplitActivity.kt        invisible trampoline that enters split screen
├── TerminalDream.kt        "FoldTerm clock" screen saver
├── data/                   calendar, contacts, apps, notifications/media, widgets, icon packs, weather/news,
│                           controls, screen time, crash log, lock admin, device
└── ui/                     theme, components, root layout, screens/
```

Stack: Kotlin · Jetpack Compose · Material 3 · AndroidX WindowManager · coroutines. No third-party SDKs.
