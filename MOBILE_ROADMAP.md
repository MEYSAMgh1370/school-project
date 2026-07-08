# Roshdeandishe — Mobile & Parent-Communication Roadmap

**Status: DRAFT.** This document is still being refined and is explicitly *not*
resolved architecture the way `CLAUDE.md` is. It governs the future
mobile/offline/video/bot strategy the same way `UX_ROADMAP.md` governs the
management-UI buildout — subordinate to `CLAUDE.md`, which remains the source
of truth and wins on any conflict. Nothing here should be treated as committed
until the user says the strategy is settled; do not start backend work off this
doc without checking in first.

## 1. Purpose & scope

Captures the mobile/channel strategy discussed for reaching students and
parents, informed by a market scan of leading school-software products in the
US, EU, and East Asia. Two distinct deliverables are in scope: a native-feeling
student app, and a parent communication channel. They are **not the same
product** — see §2.

## 2. Channel strategy by persona (core decision)

- **STUDENT — native/hybrid app via Capacitor.** Students need real
  interactivity: submitting homework, joining a video class, taking exams,
  browsing a class feed. None of that works well inside a chat bot, so
  students get an actual app.
- **PARENT — chat-bot channel, not an app.** Parents mostly want passive
  updates (grades, attendance, announcements, invoices) plus the occasional
  reply — exactly what ClassDojo, Remind, and SchoolFox already optimize for
  worldwide (§3). Building and shipping a whole parent app for that is
  disproportionate. Instead:
  - **Bale bot — primary.** Bale is sanctions-safe and reliably available
    inside Iran without a VPN.
  - **Telegram bot — secondary / best-effort.** Bale's Bot API mirrors
    Telegram's, so this is the *same* integration against a second
    endpoint/token, not a second build. Telegram itself is unreliable inside
    Iran (historically blocked, needs a VPN), so treat it as a bonus channel
    for diaspora/VPN-using parents, not the dependable path.
  - **Scope for both bots**: broadcast notifications + minimal replies (e.g.
    confirming an absence excuse). Rich back-and-forth messaging is out of
    scope for v1 — see §6.
- **TEACHER / SCHOOL_ADMIN — unchanged.** They stay on the existing JTE
  server-rendered management UI (`UX_ROADMAP.md`). No separate mobile app is
  planned for them unless explicitly requested later.

## 3. Market research summary

- **US** (PowerSchool, ClassDojo, Remind): the SIS/admin layer (grades,
  attendance, schedules, fees) and the communication layer (messaging,
  translated announcements) are usually separate products. The student/parent
  *mobile app* is mostly a read-only window plus notifications; heavy
  interaction (submitting homework, exams) happens in a separate LMS.
  Auto-translation of messages (35–90+ languages) is table stakes because of
  immigrant parent populations.
- **EU** (Sdui/SchoolFox — now one group, Pronote, Magister): messaging,
  timetable, and absence-reporting are the daily-use hooks. Video is a feature
  *inside* a course-group chat (activatable from within the chat), not a
  separate standalone product. Per-course folders double as the homework
  hand-out/hand-in mechanism.
- **East Asia**: China (DingTalk, ClassIn) puts live video class at the *core*,
  with classroom mechanics (raise-hand, rewards, shared whiteboard) layered on
  top. Korea (Classting) and Japan (Classi) center on a class-feed/SNS-style
  timeline plus AI-adaptive homework — the feed drives daily engagement more
  than gradebook access does.
- **Common core across all three regions** (the real table-stakes list):
  timetable, announcements/feed, messaging, homework hand-out/hand-in, absence
  reporting, grades view, push notifications. Video and exams are universal
  but always second-tier — every leader integrates an existing engine rather
  than building one from scratch.

## 4. Architecture decisions

- **Student app stack.** Capacitor wrapping a separate lightweight SPA
  (Alpine.js, matching the project's existing zero-npm-build bias), *not* JTE
  — JTE is server-rendered and Capacitor needs a static bundle shipped on the
  device. Talks to the existing `/api/**` REST layer; the server-rendered JTE
  pages stay as-is for admin/teacher desktop use.
- **Offline storage.** `@capacitor-community/sqlite` for durable data —
  homework drafts, an outbox for offline-submitted work — because SQLite lives
  on the device filesystem outside the JS runtime and isn't subject to OS
  eviction. IndexedDB/simple cache is acceptable *only* for re-fetchable read
  data (timetable, announcements, cached grades): WKWebView's IndexedDB is
  best-effort and the OS can evict it under storage pressure or disuse — never
  put unrecoverable data there.
- **Mobile auth — explicitly an OPEN QUESTION, not decided.** Session cookies
  are awkward from the `capacitor://localhost` origin (effectively
  cross-origin). Likely resolves to an additive token-based auth endpoint for
  the mobile app only, alongside (not replacing) the existing session-based
  web auth — this is exactly the "revisit if a mobile client makes session
  cookies impractical" condition `CLAUDE.md` §11 Q1 already flagged. Treat as a
  blocking decision before any Capacitor *backend* work starts.
- **Video class.** Integrate self-hosted **BigBlueButton**; do not build a
  custom WebRTC stack. It matches the classroom-shaped feature set (whiteboard,
  breakout rooms, polls) every regional leader converges on, and — critically
  for this project — it's self-hostable on Iranian infrastructure, unlike
  Zoom/Google Meet which are sanctions-blocked.
- **Parent bot channel — new `notifications` module.** Bot Gateway integration
  (Bale Bot API primary, Telegram Bot API secondary, same integration code
  against two endpoints/tokens). Identity linking reuses the existing
  `InviteToken` pattern (`CLAUDE.md` §4): a parent sends `/start <code>` where
  the code comes from a linking token bound to their `SchoolMembership` — the
  bot-channel analogue of redeeming an invite token, except it binds a
  `chat_id` instead of setting a password. Other modules publish
  notification-worthy events (finance → invoice issued, academic → absence
  recorded, iam → invite issued) the same way `FinanceAuditEventListener`
  already reacts to `InvoiceIssuedEvent` (`CLAUDE.md` §3's audit-listener
  pattern); the `notifications` module's listener pushes via the bot instead of
  writing an audit row.

## 5. Build order (roadmap-level, not committed dates)

1. **`notifications` module** (bot gateway) — lowest complexity, standalone
   value, doesn't block on any mobile app work.
2. **Mobile auth mechanism** (token-based, additive) — prerequisite for any
   Capacitor backend work.
3. **Capacitor student app shell** — read-only (timetable, announcements;
   grades view blocked on the grades feature not existing yet, per
   `UX_ROADMAP.md` §3's PARENT/STUDENT section) with offline cache. Proves the
   full pipeline (auth from webview, offline cache, push) before adding
   interactive features.
4. **`homework`** (or extend `academic`) — class feed + assignment
   hand-out/hand-in. Needs file upload, which doesn't exist anywhere in the
   codebase yet — a real backend prerequisite, not just a UI task.
5. **Video class integration** (BigBlueButton, tied to class/timetable).
6. **`messaging` module** — real-time student-teacher chat (WebSocket/STOMP),
   in-app only; parents stay on the bot channel, not this module.
7. **Exams** — last, and the largest individual scope (question banks, timing,
   anti-cheat expectations).

## 6. Explicitly deferred / out of scope for now

- **Grades feature/data model** — doesn't exist at all yet; several mobile
  features (grades view) block on it (already noted in `UX_ROADMAP.md` §3).
- **Push notification provider for the native app** — FCM reliability inside
  Iran is questionable; local alternatives (e.g. Pushe) need evaluation, not
  decided.
- **Rich parent messaging** — deferred to a possible future extension of the
  `messaging` module; the bot channel is notification + minimal-reply only in v1.
- **Any AI features** — forbidden outright per `CLAUDE.md` §10. Restated here
  because "AI-adaptive homework" showed up in the East Asia research (§3) and
  must not be read as license to build it.

## 7. Open questions (do not resolve without explicit discussion)

1. Mobile auth mechanism — token scheme, coexistence with session auth.
2. Push notification provider for the native app.
3. Is a Telegram bot worth building at all given its unreliability in Iran, or
   should v1 be Bale-only?
4. BigBlueButton hosting/ops — self-hosted infra for it doesn't exist yet; who
   runs it and where is unresolved.
