# Vendor SMS app adaptation backlog

Status of the two mark-as-read mechanisms per SMS app family. Evidence lives in the
`device_dumps` archive (see its AGENTS.md for retention rules); paths below are relative to
that archive. Keep this document aligned with `TelephonySmsNotificationPackages` and the
`NO_READ_ACTION` telemetry (`sms.notify_read`) — that event is the live evidence feed for
which vendor needs work next.

## Mechanisms

1. **DB write (primary)** — hook-side `OperateSmsActionHelper` updates `Telephony.Sms`
   `read=1` + `seen=1` (incl. thread-wide related rows) from the phone process. Works when the
   SMS app derives unread state from the telephony provider.
2. **Notification semantic action (backup)** — app-side `CodeNotificationReadCoordinator`
   clicks a mark-as-read action the SMS app attached to its own notification. Works only when
   the app posts `SEMANTIC_ACTION_MARK_AS_READ` (or an English "mark as read" title).

## Per-family status

| Family | DB write | Semantic action | Notes |
|---|---|---|---|
| AOSP Messaging (`com.android.messaging`) | expected OK (telephony-backed) | yes | devices/google_husky, devices/google_kodiak cover the AOSP family |
| Google Messages (`com.google.android.apps.messaging`) | OK (user-confirmed, issue #273) | yes | the reference case |
| MIUI/HyperOS (`com.android.mms`) | telephony columns only | **no** (zero `SEMANTIC_ACTION_MARK_AS_READ` hits in jadx) | bugle-derived: own Room `BugleDatabase` with `unread_count`/`read` (storage/bugle) keeps unread state independent of telephony; unread predicate `(read=0 OR seen=0)` is covered by the DB write's `seen=1`, the app-own badge/DB and its notification are the remaining gap — adaptation hooks inside the already-scoped `com.android.mms` process. Evidence: devices/xiaomi_pudding sms-focus capture (`jadx/com.android.mms`), apps/teleservice for the phone process |
| Samsung One UI (`com.samsung.android.messaging`) | unknown | unknown | dump slot devices/samsung_pa3q exists; wait for telemetry/feedback |
| OnePlus/Oppo (`com.android.messaging` / ColorOS app) | unknown | unknown | dump slot devices/oneplus_plk110 exists; wait for telemetry/feedback |

## Rules for extending this table

- Only move a family to a "supported" cell with either a device-dump-derived proof or repeated
  `sms.notify_read` / `sms.operate` telemetry showing success on that vendor.
- Vendor in-process adaptations hook only packages already present in the Xposed scope
  (`com.android.phone`, `com.xiaomi.phone`, `com.android.mms`,
  `com.google.android.apps.messaging`, `com.android.providers.telephony`) — no new scope
  entries for notification cosmetics.
- Record every new vendor fact in a dated capture under `device_dumps/devices/<codename>/`
  (one slot per retention key), never in this document alone.
