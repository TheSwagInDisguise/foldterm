# FoldTerm

FoldTerm is a terminal-styled Android home screen built for foldables. It follows the "User Intelligence" mock-ups: a nav rail, a global prompt, a home view with "happening now", agenda and summary, plus Calendar, Mail, Chat, People, Photos, Health and News panes and a dock with now-playing. All text is monospace and the search bar works as a command shell.

## Foldable behaviour
| Posture | Layout |
|---|---|
| Cover screen / narrow (< 600dp) | Single column, section tabs above the dock |
| Unfolded flat (book hinge) | Nav rail + two panes. The split lines up with the hinge so nothing sits on the crease |
| Half-open, vertical hinge (book) | Same as above |
| Half-open, horizontal hinge (tabletop) | Content above the hinge, live console and dock below it |

## The prompt
Tap the prompt, or press `/` on a hardware keyboard. As you type, apps, contacts, sections and commands are matched. Enter runs the top match, Tab completes, and ↑/↓ step through history.

```
help                      list commands
open <app>                launch an app (or just type its name)
cd <section>              home, chat, mail, cal, people, photos, health, news, settings
call|sms|email <name>     act on a contact
cal [days]                print upcoming events
todo [add|done n|rm n]    tasks
weather · news · notifs   quick readouts
play|pause|next|prev      media controls
theme <phosphor|amber|pastel|paper>
dock add|rm <app>|reset   edit the dock
grant · access · default  permissions, notification access, set as home app
settings [wifi|bt|display|battery|apps|sound]
uptime · fold · date · clear · reload · version
```
Anything else is searched on the web.

## Where the data comes from
Everything is read on the device, and FoldTerm never signs in to your accounts.

- **Calendar / agenda**: Android's CalendarContract (any synced calendar).
- **People**: the system contacts provider. You set "In case of emergency" contacts inside FoldTerm.
- **Mail / Chat**: notifications from mail and messaging apps, via notification access. You can reply inline when the app supports it.
- **Now playing**: active media sessions. This also needs notification access.
- **Weather**: [Open-Meteo](https://open-meteo.com) (no API key), using coarse location or a lat,lon override.
- **News**: any RSS/Atom feeds, set in Settings.
- **Health**: the hardware step counter plus device vitals (battery, storage, memory).
- **Photos**: MediaStore.
- **Tasks**: stored locally.

## Builds and releases (GitHub Actions only)
`.github/workflows/build.yml` runs on every push and PR. It builds `assembleRelease` and uploads the APK as a workflow artifact. Pushing a tag such as `v0.2.0` also publishes a GitHub Release with the signed APK attached. A tag build fails if no signing key is configured.

### Signing secrets
Add these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | your `.jks` keystore, base64-encoded |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

If you don't have a keystore yet, create one once and keep it safe. Every future update must be signed with the same key.
```bash
keytool -genkeypair -v -keystore foldterm.jks -keyalg RSA -keysize 4096 -validity 10000 -alias foldterm
base64 -w0 foldterm.jks   # paste the output into KEYSTORE_BASE64 (macOS: base64 -i foldterm.jks)
```
Keystores are git-ignored, and nothing secret is stored in the repo.

## First run
1. Install the APK from the latest Release.
2. Open FoldTerm and accept the permission prompts (`grant` re-requests them).
3. Settings → **set default** to make it your home app.
4. Settings → notification access **grant** to enable the Mail, Chat and now-playing panes.

Requires Android 10 (API 29) or newer.
