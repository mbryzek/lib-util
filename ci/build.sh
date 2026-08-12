#!/usr/bin/env bash
#
# lib-util's build (ISS-2173). READ devops/templates/ci/build.sh AND
# devops/docs/ci.md FIRST: the four load-bearing properties — landing this file
# IS the enrolment, `set -euo pipefail` is not style, the fleet posts the `ci`
# context, and `# ci-needs:` drives the preflight — are explained there and are
# unchanged here.
#
# WHAT A GREEN HERE VOUCHES FOR: this library's own sources compile under
# `-Werror` with the full `-Wunused` set, its sources AND its `build.sbt` are
# scalafmt-clean, and its own ScalaTest suite passes, on the exact commit about
# to be squashed.
#
# WHAT IT DOES NOT VOUCH FOR, and this is the important half: THAT ANY CONSUMER
# STILL COMPILES. Nothing in the fleet builds `platform` or `acumen` against a
# lib PR's tree. A consumer resolves a RELEASED version out of Maven Central
# (`"com.bryzek" %% "lib-util" % "0.0.40"`), and no released version exists until
# a human runs `dev release lib` — which prompts and shells out to GPG, so a
# merge here publishes nothing. So a PR that removes a method is green here and
# breaks platform at the moment somebody bumps the pin. That break surfaces in
# the CONSUMER's own version-bump PR, verified by the consumer's own `ci`, which
# is where `dev release lib` and `Agent::LibraryPin` already put it. Enrolling
# this repo narrows nothing about that and is not meant to.
#
# NO DATABASE BLOCK, and that is the whole difference from
# devops/templates/ci/build-scala.sh. That template exists for platform and
# acumen, which reach a Postgres through `CONF_DB_DEV_URL`; this suite is pure
# in-process ScalaTest over string, date and CSV helpers and opens no connection
# to anything. Copying the block would start a container, hold a host port from
# the range `dev db session` allocates out of, and depend on the DigitalOcean
# registry credential — three ways to go red that have nothing to do with the
# code under test, and the session-database port range is the single biggest
# producer of false red in the fleet (ISS-2178).
#
# `scalafmtSbtCheck` IS SEPARATE FROM `scalafmtCheckAll` AND BOTH ARE NEEDED.
# `scalafmtOnCompile := true` formats `src/`, so the sources are clean by
# construction and `scalafmtCheckAll` is the guard against somebody turning that
# off. NOTHING formats `build.sbt` — which is exactly why all four lib build.sbt
# files had drifted out of format by the time this file was written, unnoticed,
# with a formatter configured in each repo. `scalafmtSbtCheck` is the half that
# was actually missing.
#
# `# ci-needs: heap:4G` — MEASURED, not copied (ISS-1171's method, run
# 2026-08-11). Cold checkout, `SBT_OPTS="-Xmx4G -Xss4M"`, process-tree RSS
# sampled with `/bin/ps -axo pid=,ppid=,rss=`: peak 1.2 GB, exit 0, 89 tests
# passed. 4G is `Agent::Heap::MIN_GB` — the least any runner hands a build, so
# nothing below it is worth measuring and this excludes no machine. The token is
# here rather than omitted because an omission is indistinguishable from nobody
# having looked. `Test / fork` is false in this build, so `SBT_OPTS` is the whole
# ceiling and there is no forked test JVM outside it to pin.
#
# ci-needs: heap:4G
set -euo pipefail

echo "building ${CI_REPO:-?} @ ${CI_SHA:-?} (${CI_EVENT:-?}, clean=${CI_CLEAN_BUILD:-?})"

# One sbt invocation, in this order deliberately: sbt aborts the remaining tasks
# on the first failure, so a badly formatted PR is told so in seconds rather than
# after a cold compile.
sbt scalafmtCheckAll scalafmtSbtCheck test
