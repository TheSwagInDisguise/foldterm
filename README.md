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

- [Features](#features)
- [Built for foldables](#built-for-foldables)
- [The prompt](#the-prompt)
- [Install](#install)
- [Privacy](#privacy)
- [Building & releasing](#building--releasing)

---

## Features

| Pane | What it shows |
|---|---|
| **⌂ Home** | Greeting, a *happening now / up next* card with join and directions buttons, today's agenda, and an on-device summary of steps, weather, inbox, news, tasks and battery |
| **» Chat / @ Mail** | Live feed from your messaging and mail apps' notifications, with inline reply where the app supports it |
| **▦ Calendar** | Agenda and month views across all synced calendars, event details, and a task list |
| **☺ People** | Contacts with *in case of emergency* and favourite groups, quick call/text/email, vCard view |
| **▣ Photos** | Recent photos grouped by day |
| **♥ Health** | Step counter with a daily goal, plus an `htop`-style view of battery, storage, memory and hinge posture |
| **≡ News** | Headlines from any RSS or Atom feeds you choose |
| **⚙ Settings** | Themes, permissions, default-launcher setup, units, location override, feeds |

The dock at the bottom holds your pinned apps, the app drawer and a **now playing** media control. Long-press any app to pin, unpin, open app info or uninstall.

**Themes:** `phosphor` (green on black), `amber`, `pastel` (light and soft, like the original mock-ups) and `paper` (planner-style). Switch from Settings or with `theme <name>`.

## Built for foldables

The layout follows the hinge reported by Jetpack WindowManager:

| Posture | Layout |
|---|---|
| **Folded / cover screen** | Single column, section tabs above the dock |
| **Open, flat** | Nav rail and two panes, split exactly at the hinge so nothing sits on the crease |
| **Half-open, book** | Same as open, with panes on either side of the fold |
| **Half-open, tabletop** | Content on the top half, a live console and the dock on the bottom half |

## The prompt

Tap the search bar, or press <kbd>/</kbd> on a keyboard. Results update as you type: apps, contacts, sections and commands.
<kbd>Enter</kbd> runs the top result, <kbd>Tab</kbd> completes, <kbd>↑</kbd>/<kbd>↓</kbd> walk through history, and <kbd>Esc</kbd> closes the prompt.

```text
❯ help
  open <app>                launch an app (or just type its name)
  cd <section>              home · chat · mail · cal · people · photos · health · news · settings
  call | sms | email <name> act on a contact
  cal [days]                print upcoming events
  todo [add|done n|rm n]    manage tasks
  weather · news · notifs   quick readouts
  play | pause | next | prev
  theme <phosphor|amber|pastel|paper>
  dock add|rm <app> | reset
  settings [wifi|bt|display|battery|apps|sound]
  grant · access · default  permissions · notification access · set as home app
  uptime · fold · date · clear · reload · version
```

Anything that isn't a command or a match becomes a web search.

## Install

1. Download `foldterm-x.y.z.apk` from **[Releases](https://github.com/TheSwagInDisguise/foldterm/releases/latest)** and open it on your phone. You may need to allow installs from your browser.
2. Launch **FoldTerm** and accept the permission prompts. You can run `grant` later to ask again.
3. **Settings → home app → set default** makes FoldTerm your home screen.
4. **Settings → notifs → grant** turns on the Mail, Chat and now-playing panes.

Requires Android 10 (API 29) or newer. Every permission is optional; a pane without its permission shows a one-tap fix.

## Privacy

FoldTerm has no account, no analytics and no backend.

- Calendar, contacts, photos and steps are read from Android's own providers and stay on the device.
- Mail and chat come from notifications that are already on your phone. FoldTerm never signs in to your accounts.
- Network requests go only to [Open-Meteo](https://open-meteo.com) for weather (approximate coordinates, no key) and to the news feeds you configure.

## Building & releasing

Everything is built by GitHub Actions ([`build.yml`](.github/workflows/build.yml)):

- **Every push and pull request** builds a release APK and attaches it to the workflow run as an artifact.
- **New version on `main`**: when [`version.txt`](version.txt) holds a version with no release yet, CI builds, signs, tags `vX.Y.Z` and publishes a GitHub Release with the APK. Pushing a `v*` tag does the same. Without the signing secrets the build still passes, but the release is skipped (with a warning), because Android won't install an unsigned APK.

### Signing key

Add these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | The release keystore (`.jks`), base64-encoded |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

To create a keystore once (keep it backed up, because every future update must be signed with the same key):

```bash
keytool -genkeypair -v -keystore foldterm.jks -keyalg RSA -keysize 4096 -validity 10000 -alias foldterm
```

```bash
base64 -w0 foldterm.jks
```

On macOS use `base64 -i foldterm.jks`. Paste the output into `KEYSTORE_BASE64`.

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
├── data/                   calendar, contacts, apps, notifications, weather/news, device
└── ui/                     theme, components, root layout, screens/
```

Stack: Kotlin · Jetpack Compose · Material 3 · AndroidX WindowManager · coroutines. No third-party SDKs.
