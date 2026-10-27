# Branching for the move to Kotlin Multiplatform

> **Status: proposal, pending sign-off from @gtjeffw, @david-peeler and @jeremy303 (#99).** It starts from the
> plan in #99 and changes one step: bring `main` into the multiplatform branch by merging, not rebasing. The
> reasons are under "Rebase or merge".

## Where things stand

From the local refs on 2026-10-21. They weren't fetched, so check GitHub for newer commits.

| Branch | Owner | State |
|---|---|---|
| `main` | David | Android app, at 9a7f0e5. Released builds come from here; Play is on versionCode 13. |
| `origin/feature-ios-support` | Jeff | 204 commits on top of 2566a21 (Aug 11, 2025); last commit Sep 20, 2026. It is missing the 8 commits `main` has gained since 2566a21 (#109, #111, #112, #113, #114, c51980e, and two README/CI commits). |
| `origin/feature/support_indoor_measurements` | David | One commit (710ef45, "button for indoor measurements", Nov 2025). 6 commits behind `main`. |

`feature-ios-support` is already the branch #99 calls `multiplatform`. It:
- adds `shared/` (KMP: commonMain, androidMain, iosMain, jvmMain, with SQLDelight in place of Room),
  `androidTestApp/`, `iosTestApp/` and `iosSharedIntegrationHost/`;
- converts the Gradle scripts to `.kts`;
- moves the current Android app to `frozenApp/` and excludes it from the build (87e1979, Feb 2026).

So there's no need to branch off `main` again. Keep using that branch, or rename it.

## Plan

1. **Name the branch.** Either keep `feature-ios-support` or push it as `multiplatform`:
   ```bash
   git fetch origin
   git push origin origin/feature-ios-support:refs/heads/multiplatform
   # once nobody has feature-ios-support checked out:
   git push origin --delete feature-ios-support
   ```
2. **Android releases keep coming from `main`** until cutover (#122 and the FCC fixes). Small feature branches
   off `main` and PRs into `main`, as now. Decide whether `feature/support_indoor_measurements` goes into the
   next Android release. If it doesn't, port it to `multiplatform` later rather than merging it into both.
3. **Bring `main` into `multiplatform` regularly**, at least after every Android release and every Supabase
   migration:
   ```bash
   git switch multiplatform
   git fetch origin
   git merge origin/main          # resolve once, commit, push (no force-push)
   ```
   Then port the behaviour. On `multiplatform`, `app/` lives in `frozenApp/` and isn't built, so a fix merged
   into `frozenApp/` does nothing on its own. Every Android fix in that merge needs a matching change in
   `shared/` or `androidTestApp/`, or a note that it doesn't apply. Today that list is:
   - #111 upload loop: `MeasurementUploader`, `UploadWorker`;
   - c51980e carrier fallback: `TelephonyInfoManager`;
   - #112 Crashlytics redaction: `Log.kt`.

   Track them in the merge PR description.
4. **Releases and hotfixes before cutover.** `main` is the release line, so no release branch is needed yet.
   Tag the release commit (format in CONTRIBUTING.md, "Release & tagging"), fix on `main`, then merge `main`
   into `multiplatform` (step 3).
5. **At cutover**, keep a branch for the legacy Android app so it can still get fixes:
   ```bash
   git switch main && git pull
   git tag -a v1.0.0+<N> -m "last Android-only release"        # if not already tagged
   git push origin main:refs/heads/release/android-1.x
   ```
   Make legacy fixes on `release/android-1.x`, tag from there, and port them to `main` by cherry-pick, or by
   hand if the code has moved into `shared/`.
6. **Final merge.** Merge `main` into `multiplatform` one last time, get CI and the device checks green, then
   update `main`:
   ```bash
   git switch main
   git merge --ff-only multiplatform     # main is an ancestor after the merge in step 3
   git push origin main
   git push origin --delete multiplatform
   ```
   A fast-forward push to a protected `main` needs an admin or a bypass rule. Through a PR, use "Create a merge
   commit". Don't use "Rebase and merge": it rewrites all of the branch's commits, which breaks anyone who has
   them.
7. Development continues on `main`.

## Rebase or merge

#99 proposes that Jeff rebases `main` changes into `multiplatform`. That works while one person owns the
branch and nobody else has pulled it. It gets expensive here:

- **Every rebase replays all 200+ commits.** A `main` change to a file those commits also touched conflicts
  once per touching commit, not once in total. The 38 commits before the move to `frozenApp/` change files
  under `app/` 22 times. The move commit then conflicts with every `main` change under `app/`.
- **Every rebase needs a force-push.** Anyone else with the branch (reviewers, a second iOS developer, CI caches)
  has to reset to it. Commits made on the old copy are easy to lose or duplicate.
- **Rebasing rewrites commit hashes**, which breaks links from issues and PR reviews to specific commits.

Merging `main` in resolves each conflict once and keeps hashes stable, and step 6 can still fast-forward `main`.
The cost is a few merge commits in history. If a linear history matters, squash-merge small feature branches
into `multiplatform`. Don't rebase `multiplatform` itself.

If Jeff prefers to rebase while he is still the only one on the branch, that's fine. Switch to merging as
soon as anyone else commits to it or bases work on it.

## Risks to plan for

- **Fixes that aren't built.** `frozenApp/` is excluded from the build, so merged fixes to it compile nowhere.
  Use the port checklist in step 3. The FCC-relevant ones (#111, c51980e) matter most.
- **One backend, two clients.** Both app lines use the same Supabase project. Migrations live on `main`
  (`supabase/migrations`); `multiplatform` only has `supabase/config.toml` and `seed.sql`. Every schema change
  has to work for both clients, and for builds already on phones. Apply migrations before releasing a build that
  depends on them (see supabase/README.md once #121 lands, and doc/release-checklist.md).
- **FCC approval.** The multiplatform Android app is a new implementation of the measurement and submission
  path. Under the versioning policy in README.md it is probably a MAJOR change that the FCC/OET should review
  before it replaces the approved app. The iOS app may need its own approval. Ask the FCC early.
- **Tracked config files.** `frozenApp/cellwatch.properties` and `frozenApp/google-services.json` are tracked on
  `feature-ios-support` (seen in `git ls-tree`; contents not inspected), and so are `app/cellwatch.properties`
  and `app/google-services.json` on `main`. Check them before the final merge and before the repo goes public
  (#107). A key that's in history stays readable after the file is deleted.
- **Stale local refs.** `git fetch` before comparing branches. Local `origin/*` refs have been out of date
  before.
