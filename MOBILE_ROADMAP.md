# Roshdeandishe — Mobile & Parent-Communication Roadmap

**Status: proposed-change roadmap.** This document captures mobile,
offline/video, and parent-communication strategy. It is not the current system
specification. Use [`openspec/specs/current-system.md`](openspec/specs/current-system.md),
[`android/README.md`](android/README.md), source code, and tests to determine
what is actually implemented. Do not start substantial backend or Android work
from this roadmap without a focused OpenSpec change proposal.

**Reconciliation note (2026-07-08, parent bot):** the parent-bot half of this
plan was already built, in a separate session, before this doc's first draft
landed — see CLAUDE.md's "Bale bot integration, Phase 1" (§7 Done) and §11 Q8.
The actual implementation differs from what §4/§5 originally proposed here in
two important ways: it's **Bale-only, deliberately not a generic multi-channel
abstraction** (CLAUDE.md §10 now has an explicit rule against building one),
and identity linking is **phone-number-based**, not `InviteToken`-based. §4/§5
below have been corrected to match reality; treat the corrected text as current,
not the original proposal.

**Reconciliation note (2026-07-08, student app technical choices):** the
STUDENT channel's stack and mobile-auth mechanism, both discussed as open in
the original draft below, have now been explicitly decided in a planning
conversation and the backend half is built — see CLAUDE.md's "Mobile auth
backend + read-only enrollment endpoint" (§7 Done). **Native Android (Kotlin +
Jetpack Compose), not Capacitor** — Android-only for v1 (iOS deferred), chosen
over Capacitor for long-term UX ceiling despite Capacitor being faster to ship
given this codebase's existing web/Alpine skillset; also not Kotlin
Multiplatform/Compose Multiplatform, since Android-only removes any need for
cross-platform tooling at all. **Mobile auth is JWT bearer tokens**, additive
to session auth, not phone+OTP (no SMS gateway integration exists or is
planned) — see §4/§5/§7 below, corrected to match. The Android app itself
(Kotlin/Compose UI, networking, local auth state, and read-only student
dashboard shell) now exists under `android/`. Richer offline write workflows,
push notifications, homework, messaging, native exam-taking, and video remain
future work.

## 1. Purpose & scope

Captures the mobile/channel strategy discussed for reaching students and
parents, informed by a market scan of leading school-software products in the
US, EU, and East Asia. Two distinct deliverables are in scope: a native-feeling
student app, and a parent communication channel. They are **not the same
product** — see §2.

## 2. Channel strategy by persona (core decision)

- **STUDENT — native Android app (Kotlin + Jetpack Compose), not Capacitor,
  PLUS a JTE+Alpine web channel (added 2026-07-11 — see §4).** Students need
  real interactivity: submitting homework, joining a video class, taking
  exams, browsing a class feed. None of that works well inside a chat bot, so
  students get an actual app — decided over Capacitor for long-term UX
  ceiling, and Android-only for v1 (iOS deferred — see §4). The web channel is
  **additive, not a replacement**: it exists for students on shared school
  computers or non-Android devices, reusing the exact same `/api/**` REST
  surface (and, once built, the `exam` module's scoped exam-take token — see
  `MOSHAVERE_PORT_SPEC.md`) rather than introducing a second rich-client
  technology alongside native.
- **PARENT — chat-bot channel, not an app.** Parents mostly want passive
  updates (grades, attendance, announcements, invoices) plus the occasional
  reply — exactly what ClassDojo, Remind, and SchoolFox already optimize for
  worldwide (§3). Building and shipping a whole parent app for that is
  disproportionate. Instead:
  - **Bale bot — built.** Shipped 2026-07-07 as the `bale` module (CLAUDE.md
    §7 Done): outbound invoice notifications plus inbound self-service Persian
    text commands ("مدرسه‌های من" / "فرزندان من"). Bale is sanctions-safe and
    reliably available inside Iran without a VPN, which is why it went first.
  - **Telegram bot — not built, and not a drop-in second endpoint.**
    `CLAUDE.md` §10 now has an explicit rule against a generic
    `Notification`/multi-channel abstraction — the `bale` module is
    intentionally Bale-specific. A Telegram channel, if ever wanted, means a
    **second, independent module** (its own webhook, its own linking flow),
    not reusing `BaleFacade` against a second token. Given Telegram's own
    unreliability inside Iran, whether that's worth the maintenance cost at
    all is an open question — see §7.
  - **Scope, as built**: broadcast notifications + minimal self-service replies
    (fixed Persian-text commands, not free-form chat). Rich back-and-forth
    messaging is out of scope — see §6.
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

- **Student app stack — decided 2026-07-08: native Android, Kotlin + Jetpack
  Compose. Not Capacitor, not KMP/Compose Multiplatform.** Android-only for
  v1 — iOS deferred, common for Iranian consumer apps given App Store
  distribution friction under sanctions; removes any need for cross-platform
  tooling at all for now. Capacitor (a static Alpine.js SPA bundle, matching
  this project's zero-npm-build web bias) was the faster-to-ship option and
  was seriously considered, but native was chosen for long-term UX ceiling —
  no WebView tax, first-party offline storage (Room, not a JS↔native SQLite
  bridge), and better native performance for later phases (video class,
  homework). This is a real second stack for this repository and a deliberate
  trade of short-term speed for long-term quality. Talks to the existing
  `/api/**` REST layer over HTTP; the server-rendered JTE pages stay as-is for
  admin/teacher desktop use. The Android project itself is a separate Gradle
  project under `android/` (not part of the one-Maven-artifact backend).
- **Offline storage.** Room (Android's first-party SQLite ORM) for durable
  data — homework drafts, an outbox for offline-submitted work — scoped to
  exactly what each phase needs to cache; no general sync engine, since v1 is
  read-only with nothing to reconcile.
- **Mobile auth — RESOLVED 2026-07-08: JWT bearer tokens, additive to session
  auth.** Built: `POST /api/auth/token` (`iam.shared.JwtTokenProvider` +
  `JwtAuthenticationFilter`, one `SecurityFilterChain`, not two — the filter
  no-ops without an `Authorization: Bearer` header, so session-cookie web
  login is completely unaffected). This is exactly the "revisit if a mobile
  client makes session cookies impractical" condition `CLAUDE.md` §11 Q1
  flagged — a native app has no cookie jar to share with the backend at all,
  which made the call concrete. Not phone+OTP: `iam.UserEntity.phoneNumber`
  (added for Bale linking) was considered, but would need a new SMS gateway
  integration that doesn't exist and isn't planned; JWT needed no new
  external dependency. Token lifetime is 12h, no refresh-token flow (logout
  is client-side token deletion only) — deferred until a concrete need for
  silent long-lived sessions shows up. See CLAUDE.md §7 Done for the full
  backend description, including the new `GET /api/me/enrollment` endpoint
  this required (STUDENT otherwise has nothing of its own to show — see §6).
- **Student web channel — RESOLVED 2026-07-11: JTE+Alpine, additive to the
  native app, not a rival technology.** Raised while scoping the moshavere
  port (`MOSHAVERE_PORT_SPEC.md`): does porting an interactive feature like
  exam-taking mean students need a SPA or the native app specifically? No —
  an exam-taking screen (timer, question list, section navigation,
  fetch-based submit) is well within what this project's existing
  Alpine.js pattern already does elsewhere (`finance.js`, `students.js`), so
  it gets the same treatment as the admin/teacher management pages: new
  student-facing JTE pages (e.g. a `take-exam.jte`) plus an `Alpine.data`
  component, calling the same REST + scoped exam-token API the native app
  will also call. This keeps the project at two frontend paradigms
  (JTE+Alpine for every web-facing surface, native Android for mobile), not
  three — no new build tooling, no separate SPA project. Explicitly **not**
  a reason to reconsider the native-app decision above: video class,
  offline homework drafts, and push notifications still need a real native
  app; only the fully-REST-already features (exam-taking today, anything
  else built REST-first later) get a web page for free this way. Student
  web pages live in the `web` module (or, if a feature's controller needs
  student-only data no other page composes, alongside that module's own
  admin controller) — same placement rule `CLAUDE.md` §3 already uses for
  `web`.
- **Video class.** Integrate self-hosted **BigBlueButton**; do not build a
  custom WebRTC stack. It matches the classroom-shaped feature set (whiteboard,
  breakout rooms, polls) every regional leader converges on, and — critically
  for this project — it's self-hostable on Iranian infrastructure, unlike
  Zoom/Google Meet which are sanctions-blocked.
- **Parent bot channel — `bale` module (built, corrected from this doc's
  original proposal).** *Originally proposed here as a generic `notifications`
  module reusing the `InviteToken` pattern; the actual build, done in a
  separate session, took a different and now-authoritative shape:*
  - The module is named `bale` and is deliberately Bale-specific, not a
    generic gateway — `CLAUDE.md` §10 explicitly forbids building a
    `Notification`/multi-channel abstraction (same YAGNI reasoning as keeping
    `InviteEmailSender` inside `iam/internal`). A second channel gets its own
    module when actually requested, not a shared abstraction built ahead of need.
  - Identity linking is **phone-number-based**, not `InviteToken`-based: a
    parent claims their phone via authenticated `PUT /api/me/phone`
    (`iam.UserEntity.phoneNumber`), then Bale's native "share contact" flow
    resolves an incoming chat to that phone number, linking `chat_id` → user
    (`bale.internal.BaleLinkEntity`, upserted on re-link).
  - This forced a real prerequisite that didn't exist before:
    **`academic.StudentGuardianEntity`** + `AcademicFacade.linkGuardian` /
    `listGuardians` / `listWards`, gated by a new `GUARDIAN_MANAGE` permission
    (`SCHOOL_ADMIN`-only) — the first modeled Parent↔Student relationship in
    the codebase (previously `PARENT` was just a role with no link to *which*
    child). This is reusable by any future parent-facing feature, not just the
    bot — worth keeping in mind for §5 item 4 (`homework`) and beyond.
  - Outbound flow: `finance.InvoiceIssuedEvent` → `bale.internal.
    InvoiceIssuedEventListener` notifies the student and every linked guardian
    — same audit-listener shape as `FinanceAuditEventListener` (`CLAUDE.md`
    §3), just pushing via the bot instead of writing an audit row.
  - The webhook (`POST /api/bale/webhook/{secret}`) is secured by a random
    secret path segment, not `@RequiresModule`/`@RequiresPermission` — the
    caller is Bale's server, not a browser session; authorization instead
    comes from every query being scoped to the linked user's own data.
  - **Known gaps at the time this was written — all since closed:**
    `/finance`'s `GET invoices` scoping to a guardian's own children shipped
    2026-07-13 (`CLAUDE.md` §7 Done, "Finance deepening, phase 3"); a
    `BaleFlowIntegrationTest` (webhook link flow + outbound
    `InvoiceIssuedEvent` notification, using a local JDK `HttpServer` in
    place of a real Bale API — Docker/Testcontainers is available again)
    and an admin UI for guardian linking (`GET /students`'s new
    "مدیریت والدین" panel per student row — no backend change, composes the
    existing `academic.StudentController` guardian endpoints with
    `iam.MembershipController` the same way the class-roster panel already
    does) both shipped 2026-07-17.

## 5. Build order (roadmap-level, not committed dates)

1. ~~`notifications` module~~ — **done, 2026-07-07**, shipped as the `bale`
   module (see §4). The follow-up bug/gap fixes flagged at the time
   (guardian-link admin UI, guardian invoice scoping, `BaleFlowIntegrationTest`)
   are now all done too — see §4 above.
2. ~~**Mobile auth mechanism**~~ — **done, 2026-07-08**: `POST /api/auth/token`,
   additive JWT bearer auth (see §4). Prerequisite for the Android app itself,
   which is next.
3. **Native Android student app shell** — **thin v1 built** under `android/`:
   login, persisted auth state, profile, memberships, and current enrollment.
   Timetable and announcements do not exist as backend features yet. Backend
   and web grades/exams now exist, but richer native presentation and native
   exam-taking are still future work. Push is explicitly deferred to a later
   phase, not part of this shell.
4. **`homework`** (or extend `academic`) — class feed + assignment
   hand-out/hand-in. Homework-specific attachment ownership, retention, and
   access checks are not designed yet. Finance expense attachment upload
   exists, but it is not automatically the homework model. Can likely reuse
   `StudentGuardianEntity`/`listWards` (§4) if a parent-visible homework status
   is ever wanted.
5. **Video class integration** (BigBlueButton, tied to class/timetable).
6. **`messaging` module** — real-time student-teacher chat (WebSocket/STOMP),
   in-app only; parents stay on the bot channel, not this module.
7. **Native exam integration** — the backend/web exam engine exists. Native
   exam-taking, offline behavior, and anti-cheat expectations remain a large
   separate scope and need their own change proposal.

## 6. Explicitly deferred / out of scope for now

- **Richer native grades UX** — backend/web grades now exist. Native display,
  offline behavior, notifications, and any grade-scale refinements should be
  specified before expanding the Android shell.
- **Push notification provider for the native app** — FCM reliability inside
  Iran is questionable; local alternatives (e.g. Pushe) need evaluation, not
  decided.
- **Rich parent messaging** — deferred to a possible future extension of the
  `messaging` module; the bot channel is notification + minimal-reply only in v1.
- **Any AI features** — forbidden outright per `CLAUDE.md` §10. Restated here
  because "AI-adaptive homework" showed up in the East Asia research (§3) and
  must not be read as license to build it.

## 7. Open questions (do not resolve without explicit discussion)

1. ~~Mobile auth mechanism~~ — **Resolved 2026-07-08: JWT bearer tokens**, not
   phone+OTP (see §4). Coexistence with session auth is built and verified
   (`MobileAuthFlowIntegrationTest`, `LoginDashboardFlowIntegrationTest` still
   green).
2. Push notification provider for the native app — still open, deliberately
   deferred past the read-only shell (§5 item 3).
3. Is a **second, fully-independent Telegram module** worth building at all,
   given Telegram's own unreliability in Iran and the now-explicit rule
   (`CLAUDE.md` §10) against sharing code with `bale`? Leaning Bale-only
   unless a concrete need surfaces.
4. BigBlueButton hosting/ops — self-hosted infra for it doesn't exist yet; who
   runs it and where is unresolved.
5. Should the Bale-established `phoneNumber` + `StudentGuardianEntity` model
   become the identity backbone for a future parent-facing mobile surface? Not
   decided. (The student app's own mobile auth is now settled — item 1 — and
   deliberately does *not* use `phoneNumber`/OTP; this question is now scoped
   only to a possible future parent-facing mobile surface, not the student app.)
