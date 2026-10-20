# Version Maintenance

This document is for contributors. README files describe player-visible
features and changes. Keep Git procedures, compatibility implementation,
and release administration out of README files.

## Three-stage implementation plan

### Stage 1: Record the policy

Status: The policy is agreed and documented; review and integration of this
documentation change are pending.

- Record development priorities, older-version support, branch triggers,
  porting, validation, and release boundaries in this document.
- Link it from `BUILDING.md`. Keep maintenance procedures out of README files.
- Check local links, whitespace, and the diff. Do not change runtime code,
  build configuration, version numbers, tags, or publishing tools in this stage.

Completion: The documentation is reviewed and merged, with no unrelated changes.
Rollback: Revert the documentation change; no runtime rollback is needed.

### Stage 2: Apply the policy to the next real feature

Status: Pending the next real feature. Do not create an artificial old branch
or replay an already integrated commit just to demonstrate cherry-pick.

1. Identify the expected behavior and affected sources, resources, game targets,
   and loaders. Implement and validate the feature on 26.3 first.
2. Assess older targets and choose shared code, a local adapter, or a documented
   feature omission. Follow the shared-code and validation sections below.
3. If an old target needs an independent implementation, preserve a known-good
   maintenance baseline before integrating the incompatible change. Do not leave
   a retained target broken or treat exclusion from a release as a code freeze.
4. Validate every affected target. Record supported behavior, intentional
   differences, skipped ports, and pending checks in the feature PR.
5. If releasing, select only the intended targets through the existing release
   manifests. Do not require Snapshot adaptation unless it is in scope.

Completion: The feature PR contains the compatibility decisions and validation
results; retained targets work, and any independent line has a preserved baseline.
If lines remain compatible, no maintenance branch or cherry-pick is required.
Rollback: Close an unmerged PR, or add a revert or fix after merge. Leave the
known-good old-line baseline and published history intact.

### Stage 3: Prepare maintenance-line publishing when needed

Status: Conditional; not started. Trigger this stage only when an independent
maintenance line needs to publish. Complete it before that line's first release.

1. Review `scripts/release.sh`, tag and version conventions, release notes,
   target manifests, and the relevant GitHub Actions workflow on the target line.
2. Adapt the `main`-only release assumptions for the intended maintenance line.
   Preserve clean-tree, remote-commit, version, and artifact checks. Do not add
   automatic porting, automatic merging, or a new build framework as part of this.
3. Check the actual candidate commit with publishing disabled. If remote
   validation is needed, verify the workflow checks out that commit and disables
   every publishing option. An existing-tag run against old code is not proof.
4. Review the validation results and obtain explicit release authorization before
   creating a release tag, pushing it, or uploading artifacts.

Completion: The intended maintenance commit and selected artifacts pass the
no-publish checks, with no uploads or changes to existing release tags.
Rollback: Revert unshipped tooling changes and keep maintenance-line publishing
blocked until corrected. For a published defect, issue a new corrective release.

## Development priorities

| Minecraft line | Role | Feature policy |
| --- | --- | --- |
| 26.3 | Primary development and behavior reference | Develop and validate new features here first. |
| 26.4 Snapshot | Optional preview adaptation | Follow selected snapshots when useful; adaptation may pause while upstream changes. |
| 26.2 and earlier supported targets | Best-effort compatibility | Port a feature only when its behavior and maintenance cost are acceptable. |

The build configuration defines the available loader and game targets.
See [BUILDING.md](../BUILDING.md) for commands and artifacts. A newer
Minecraft version does not automatically become the primary development
line. Reassess that choice explicitly when 26.4 becomes stable.

Skipping one feature does not end feature development for an entire old
line. Keep accepting suitable features unless the maintainer explicitly
moves that line to fixes only. Ending maintenance is a separate decision.
Record these decisions in the relevant issue or PR, with the affected
game and loader targets and the reason.

## Keep shared code while it remains useful

`main` remains the integrated development branch, with 26.3 as its primary
target. Keep the existing Gradle modules, source sets, and platform adapters.
Do not create a permanent branch for every game version or loader.

Before implementing a feature, identify all targets that compile its
sources or package its resources. After validating the feature on 26.3,
assess the older targets:

- Reuse shared code when the APIs and behavior remain compatible.
- Use a local platform adapter when it provides reliable behavior at an
  acceptable maintenance cost.
- Skip the feature when it requires large duplicated implementations,
  produces poor results, or cannot preserve the intended behavior.

Not publishing an old target does not freeze its behavior: shared source
changes still affect its next build. If a target must retain its old
behavior, preserve that implementation through an adapter or a maintenance
branch. Do not leave a retained target broken or accumulate broad version
checks just to keep every target on one branch.

Keep Snapshot-specific APIs, resources, and tests inside their target
module. Snapshots remain opt-in and use the separate preview release path.
If an experiment needs disruptive shared changes, validate it on a short-lived
preview branch before considering integration. Stable targets must not
acquire Snapshot-specific behavior or dependencies.

## Create maintenance branches only when needed

Create a maintenance branch when ongoing compatibility work becomes too
costly, functionality must diverge beyond a reasonable adapter, or a line
moves to fixes only. Preserve a known-good baseline before integrating the
incompatible change into `main`.

Use `mc/<line>` for a long-lived maintenance branch. Name it for the actual
compatibility range it maintains, not automatically for each Gradle module.
Keep Fabric and NeoForge modules together where practical. Retain source,
resource, and test directories that other modules still reference; do not
remove them just because their directory name refers to another version.

Use short-lived `feature/<name>` and `fix/<name>` branches from the relevant
line. Fix an old-only bug on the affected line, then assess whether `main`
also needs the fix. Preserve published history when a line is retired.

## Port changes explicitly

Keep feature work, game API upgrades, unrelated refactoring, and release
preparation in separate logical commits. For a multi-commit change, identify
the complete dependency sequence, including registration, resources, and tests.

Once lines diverge, manual ports are sufficient:

1. Decide whether the target needs the feature or fix.
2. Check the source commits, prerequisites, and existing target behavior.
3. Create a short-lived branch from the target maintenance line.
4. Apply the required commits in dependency order with
   `git cherry-pick -x <commit>`.
5. Adapt to the target APIs while preserving the agreed player-visible behavior.
6. Validate the affected game and loader combinations, then review through a PR.

Do not merge all of `main` to transfer one feature. Resolve conflicts using
the target's APIs, not by accepting the source side wholesale. If a commit
is empty or partially present, compare behavior and tests before skipping
or completing it. If an equivalent port is not feasible, record that decision
instead of merging a broken or misleading implementation. Abort an unsuccessful
cherry-pick with `git cherry-pick --abort` while it is still in progress.

In the PR, record the source SHAs and dependency order, target line and loaders,
adaptation differences or skipped behavior, and validation results. The PR
and the `-x` trailers provide traceability; no separate port ledger or automatic
merge system is required. Later source fixes need a new port assessment.

## Validate behavior, not only patches

- Check the diff for unintended game API, dependency, Mixin, and resource changes.
- Build every affected target and run the relevant automated tests. Shared
  changes require checking all targets that use the changed implementation.
- For runtime changes, verify the actual mod build in-world on the affected
  targets. For rendering changes, check relevant toggles and resource reloads.
- Record compilation, automated tests, in-world checks, and performance
  measurements separately. Mark anything not checked as pending. A matching
  patch, passing compilation, or main-menu startup does not prove equivalent
  behavior. Documentation-only changes do not require a game build.

## Preserve the release boundary

Keep the existing repository-wide unique release version numbers and tag
formats. Do not reuse or move a published tag, and do not bump versions for
maintenance documentation. Select only affected targets through the existing
release target manifests; a global version bump is not a reason to republish
unaffected targets. Keep Snapshot previews separate from stable releases.

`scripts/release.sh` currently requires `main` and alignment with `origin/main`.
A new maintenance branch is not release-ready until that guard and the related
tag, notes, and workflow assumptions are reviewed and safely adapted. Preserve
the clean-tree and remote-commit checks. Verify the actual checked-out commit
with all publishing options disabled before using the adapted release path.
Do not bypass the checks or publish an old line through `main` as a workaround.

Do not rewrite existing release history to undo a port. Close an unmerged PR,
or add a revert or fix after merge. Correct a published defect with a new
release rather than replacing its tag or artifacts.
