# Permatasks

A private, offline task manager for Android that **keeps reminding you until you actually close the task**.
Built for a Pixel 8 Pro (Android 14+), installed as an APK, no Play Store, no account, no cloud.

## What it does today (Phase 1)

**Nagging that doesn't give up**
- A task with a date stays an open loop until you answer: **Done**, **Snooze** (10m … tomorrow morning),
  **Move to another date**, **Skip this time** (recurring) or **Won't do**.
- Swiping the notification away doesn't count as an answer: it comes straight back, silently.
- Escalation per priority (configurable): e.g. P1 re-nags every 5 min and, after 2 ignored reminders,
  takes over the screen like an alarm clock, with a looping alarm sound.
- Follows **Bedtime mode / Do Not Disturb**: reminders wait while it's on and all come back when it ends.
  Optional extra quiet hours; optional "P1 breaks through".
- Exact alarms (unaffected by Doze), re-armed after reboot, app update and time-zone changes.
- Each reminder shows how often you've been reminded and how many times you've snoozed.

**Tasks**
- Quick add in plain English: `Call mom tomorrow 6pm p1 #Family @phone`,
  `Stand-up every weekday 9:30am`, `Water plants every! 3 days`, `Dentist 3 nov`, `Check oven in 20 minutes`.
- Projects, sub-tasks, labels, priorities P1–P4, date-only or timed due dates, "start nagging early".
- Repeats: daily / weekdays / weekly on chosen days / every N days-weeks-months-years / from completion (`every!`).
- Today (with overdue), Upcoming (14 days + later + no date), Browse projects, Completed log, per-task history.

**Everywhere on the phone**
- Home-screen widget: overdue + today, tick to complete, `+` to add.
- Quick Settings tile, long-press app shortcuts (Add task / Voice task), and **Share → Add as task** from any app.
- Voice entry using the **on-device** recogniser only (audio never leaves the phone).

**Driving**
- Drive mode: huge buttons, current task with Done / In 1 hour, voice add with spoken confirmation,
  "Read out" today's tasks.
- Optional: reminders read aloud while Bluetooth audio (your car) is connected.

**Security & privacy**
- The APK has **no INTERNET permission** (enforced in the manifest and checked in CI). It cannot send data anywhere.
- Database encrypted with SQLCipher (AES-256); its key is sealed by the Android Keystore (StrongBox / Titan M2).
- PIN on every start + fingerprint / face unlock; lockout after repeated wrong PINs; relock timeout.
- Screenshots and recent-apps previews blocked; task names hidden on the lock screen by default.
- Android cloud backup and device-transfer disabled. No analytics, no Google services.

## Roadmap

| Phase | Scope |
|---|---|
| 2 | Saved filters (`overdue & p1 & #work`), board view, calendar view, templates, more widgets, stats |
| 3 | Location reminders (Google Play Services geofencing), calendar-event triggers, Bluetooth/Wi-Fi triggers, task dependencies, Android Auto |
| 4 | Notes: block editor, images, checklists whose items nag, `[[wiki links]]`, backlinks, graph, voice notes |
| 5 | Encrypted backup/restore (passphrase, written to Drive/OneDrive through the system file picker) |
| Later | Optional self-hosted web companion and shared family lists (ids are already UUIDs for sync) |

## Getting the APK

Every push builds `app-release.apk` in GitHub Actions (**Actions → Build APK → latest run → Artifacts**).
Pushing a tag like `v0.1.0` also attaches it to a GitHub Release.

### One-time: set up a permanent signing key (important)

Android only installs an update over an existing app if both are signed with the **same key**.
Without your own key, CI signs with a throwaway debug key, so each new build would need an uninstall
(which deletes your tasks). Create a key once and store it as repository secrets:

```bash
keytool -genkeypair -v -keystore permatasks.jks -alias permatasks -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 permatasks.jks > permatasks.jks.b64
```

Then in **GitHub → Settings → Secrets and variables → Actions** add:

| Secret | Value |
|---|---|
| `PERMATASKS_KEYSTORE_BASE64` | contents of `permatasks.jks.b64` |
| `PERMATASKS_KEYSTORE_PASSWORD` | keystore password |
| `PERMATASKS_KEY_ALIAS` | `permatasks` |
| `PERMATASKS_KEY_PASSWORD` | key password |

Keep `permatasks.jks` safe somewhere private; never commit it.

### Install on the phone

1. Download the APK on the phone, open it, allow "install unknown apps" for your browser / Files.
2. Open Permatasks, choose a PIN, enable fingerprint/face.
3. Tap **Fix** on each item of the setup card (notifications, full-screen alarms, exact alarms, battery).
4. For voice: Settings → System → Languages → Speech → make sure the English offline/on-device pack is installed.
5. Long-press the home screen → Widgets → Permatasks to add the widget; edit Quick Settings to add the tile.

## Building locally

```bash
./gradlew :app:testDebugUnitTest   # parser, recurrence and scheduling tests
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Kotlin, Jetpack Compose (Material 3), Room + SQLCipher, Glance widgets, AlarmManager. minSdk 34, targetSdk 36.
