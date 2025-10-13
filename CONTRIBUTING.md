# Contributing to CellWatch

Thanks for considering a contribution! This guide explains how to set up your environment, follow our conventions, and submit changes that are easy to review and ship.

## Table of Contents
- [Project layout](#project-layout)
- [Prerequisites](#prerequisites)
- [Local setup](#local-setup)
- [Configuration (`cellwatch.properties`)](#configuration-cellwatchproperties)
- [Running the stack](#running-the-stack)
- [Branching model](#branching-model)
- [Commit style](#commit-style)
- [Linting & tests](#linting--tests)
- [Secrets & GitGuardian](#secrets--gitguardian)
- [Pull requests](#pull-requests)
- [Release & tagging](#release--tagging)
- [Issue labels](#issue-labels)
- [Security](#security)
- [No separate Code of Conduct (for now)](#no-separate-code-of-conduct-for-now)

## Project layout
```
/app        Android app (Kotlin, Gradle, minSdk 29, targetSdk 34)
/supabase   Local Supabase project (config.toml, full_schema.sql, post_fcc_submission_func.sql, seed.sql)
```

## Prerequisites
- Android Studio Giraffe+ with JDK 17
- Docker + Supabase CLI (`supabase >= 1.180`)

## Local setup
```bash
git clone https://github.com/CellWatch/cellwatch-app.git
cd cellwatch-app
```

## Configuration (`cellwatch.properties`)
Create `cellwatch.properties` at the repository root:

```properties
# your local development machine's IP address - used for debug build
SUPABASE_LOCAL_URL="http://127.0.0.1:54321"
SUPABASE_LOCAL_API_KEY=

# Supabase cloud URL - used for release build
SUPABASE_URL= <your supabase url>
SUPABASE_API_KEY= <your supabase api key>

# Changing MSAK_SERVER_ENV from "local" to "prod" or "staging" will use M-Lab's
# production or staging servers during local development. The production
# servers are always used in release builds.
MSAK_SERVER_ENV="local"
MSAK_LOCAL_SERVER_HOST="10.0.2.2:8080"
MSAK_LOCAL_SERVER_SECURE=false
MSAK_LATENCY_PORT=1053
MSAK_LOCAL_LATENCY_PORT=1053

MAPBOX_DOWNLOADS_TOKEN= <your mapbox api key>

TCP_TUPLE_URL="http://34.201.124.67/"
```

**Crashlytics is required.** Include a valid `google-services.json` in `/app` and ensure uploads are enabled for debug and release builds according to org policy.

## Running the stack
Start Supabase (local):
```bash
cd supabase
supabase start
supabase db reset
```

Build & install the Android app (debug):
```bash
cd ../app
./gradlew assembleDebug
./gradlew :app:installDebug
```

## Branching model
- `main` is protected.
- Use feature branches: `feat/<topic>`, `fix/<topic>`, `chore/<topic>`, `docs/<topic>`.
- Keep PRs focused and small where possible.

## Commit style
Use **Conventional Commits**:
```
feat(app): add M-Lab staging toggle
fix(export): correct latency p50 aggregation
docs: update README with cellwatch.properties example
chore: bump supabase cli in CI
```

## Secrets & GitGuardian
We enforce secret scanning on commits and PRs.

Local setup:
```bash
pip install ggshield
ggshield auth login
ggshield secret scan pre-commit   # scan staged changes before committing
ggshield secret scan repo .       # scan entire repo
```

CI runs `ggshield secret scan ci`. PRs failing secret scans will be blocked.

## Pull requests
Checklist:
- [ ] Feature branch from `main`
- [ ] Code compiles; no new warnings
- [ ] Tests pass; add tests for new logic
- [ ] **ggshield** passes (no secrets leaked)
- [ ] Screenshots/GIFs for UI changes
- [ ] For export changes: attach before/after JSON samples
- [ ] Request review from a maintainer

## Release & tagging
- Create annotated tags and GitHub Releases with highlights and migration notes
- Attach sample FCC export JSON if applicable

## Issue labels
- `good first issue`, `help wanted`
- `android`, `backend`, `schema`, `export`, `infra`
- `bug`, `docs`, `chore`

## Security
- Report vulnerabilities privately to `cellwatch@groups.gatech.edu` or via GitHub Security Advisory
- Never commit real keys; store them in `cellwatch.properties` locally and CI secret stores
- Use pre-commit secret scans and avoid posting keys in issue/PR text
