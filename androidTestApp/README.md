# androidTestApp

This directory is reserved for Android-side KMP integration/testing work so the legacy `app/` module stays untouched.

Current status:
- Scaffold only (not wired into Gradle settings yet)
- Intended to host future Android test harness/app wiring for shared KMP sync

Planned contents:
- dedicated Android module build files
- adapter wiring to shared sync service
- local-Supabase smoke/integration entrypoints
