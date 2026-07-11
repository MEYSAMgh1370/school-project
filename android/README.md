# Roshdeandishe Student App (Android)

Native Android client (Kotlin + Jetpack Compose), v1 thin read-only shell:
login, profile, school memberships, and (if the logged-in user is a STUDENT)
their current class enrollment. Talks to the existing `/api/**` REST layer in
the main Spring Boot backend via JWT bearer tokens (`POST /api/auth/token`) -
see the root project's `CLAUDE.md` and `MOBILE_ROADMAP.md` for the full
architecture context and why this exists.

This is a standalone Gradle project, not part of the backend's Maven build -
there is no build-time coupling between the two.

## First-time setup

This scaffold was written without Gradle/Android Studio available in the
environment it was created in, so **the Gradle wrapper jar is not included**.
Before building, open this directory in Android Studio (which generates the
wrapper automatically), or run once with a local Gradle install:

```
gradle wrapper --gradle-version 8.10.2
```

Then `./gradlew assembleDebug` as usual.

## Configuration

The backend base URL is not yet externalized to a build config field - point
`NetworkModule`'s base URL at your local backend (e.g. `http://10.0.2.2:8080/`
from the Android emulator, which maps to the host machine's `localhost`).
