#!/usr/bin/env bash
#
# lib-util's build (ISS-2173). READ devops/templates/ci/build.sh AND
# devops/docs/ci.md FIRST: the three load-bearing properties — landing this file
# IS the enrolment, `set -euo pipefail` is not style, and the fleet posts the
# `ci` context — are explained there and are unchanged here.
#
# WHAT A GREEN HERE VOUCHES FOR: this library's own sources compile under
# `-Werror` with the full `-Wunused` set and its own ScalaTest suite passes, on
# the exact commit about to be squashed.
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
# FORMATTING IS REPORTED, NEVER ENFORCED (ISS-19427). A build fails only for a
# functional problem, and an unformatted file behaves exactly as its formatted
# copy does. `scalafmtOnCompile := true` formats the sources, and
# `scripts/check-format` (a copy of devops templates/ci/check-format, which runs
# `dev fmt report`: the native scalafmt CLI over the sources AND `build.sbt`,
# which nothing else formats) prints every unformatted file and exits 0. The
# hygiene sweep runs it over `main` and fixes what it finds.
#
# `-Xmx4G` in `.jvmopts` — MEASURED, not copied (ISS-1171's method, run
# 2026-08-11). Cold checkout, `SBT_OPTS="-Xmx4G -Xss4M"`, process-tree RSS
# sampled with `/bin/ps -axo pid=,ppid=,rss=`: peak 1.2 GB, exit 0, 89 tests
# passed. 4G is `Agent::Heap::MIN_GB` — the least any runner hands a build, so
# nothing below it is worth measuring. The file is here rather than omitted
# because an omission is indistinguishable from nobody having looked.
# `Test / fork` is false in this build, so `SBT_OPTS` is the whole ceiling and
# there is no forked test JVM outside it to pin.
#
# TEST HEALTH READS THE NEXT TWO LINES (ISS-19887, design DES-18577 "Every repository
# with CI"). The first is where sbt leaves a JUnit report per suite, which is what the
# fleet writes the test ledger from: `TEST-<suite>.xml` is the build's run of a suite,
# `FIRST-<suite>.xml` the first run of one the build reran. The second says this build
# runs with the lists platform holds: whatever starts it fetches them first and exports
# the file as `CI_TEST_LISTS`. ci/test-health.sh and the three project/Ci*.scala files
# are the runner half, copies of devops templates/sbt-test-health held to it below.
#
# ci-test-reports: target/test-reports/*.xml
# ci-test-lists: required
set -euo pipefail

echo "building ${CI_REPO:-?} @ ${CI_SHA:-?} (${CI_EVENT:-?}, clean=${CI_CLEAN_BUILD:-?})"

# THE SECURITY PINS ARE OWNED BY devops, and this is what holds the copies here to them.
# project/BryzekPins.scala declares the Jackson 2, Jackson 3 and logback versions every lib-*
# build resolves, and the pin specs assert them as behaviour of the resolved jars. All of them are
# byte-identical copies of devops's templates/scala-libs, read off devops's main with
# `dev repo cat` (no fetch of our own, ISS-2232), so an advisory bump is one edit there and a copy
# that is not taken fails here rather than drifting into a per-repo floor. ISS-15715
#
# A READ THAT FAILS IS NOT A DRIFT (ISS-21067). Each template is read into a file and the read is
# checked before anything is compared: diffed straight off a process substitution, a read that
# answered nothing is an empty template and the whole copy reads as drift. A read is retried once,
# and one that fails twice exits 75 -- this box could not measure the copy, which says nothing
# about the branch.
#
# THE TEST HEALTH RUNNER IS OWNED THE SAME WAY (ISS-19887): templates/sbt-test-health holds the
# lists filter, the rerun and the shell half every sbt repository runs its suite through.
synced=(
  "scala-libs/project/BryzekPins.scala project/BryzekPins.scala"
  "scala-libs/test/JacksonPinSpec.scala src/test/scala/com/bryzek/pins/JacksonPinSpec.scala"
  "scala-libs/test/Jackson3PinSpec.scala src/test/scala/com/bryzek/pins/Jackson3PinSpec.scala"
  "scala-libs/test/LogbackPinSpec.scala src/test/scala/com/bryzek/pins/LogbackPinSpec.scala"
  "sbt-test-health/project/CiTestLists.scala project/CiTestLists.scala"
  "sbt-test-health/project/CiTestListsPlugin.scala project/CiTestListsPlugin.scala"
  "sbt-test-health/project/CiTestRerunPlugin.scala project/CiTestRerunPlugin.scala"
  "sbt-test-health/ci/test-health.sh ci/test-health.sh"
)
template_bytes=$(mktemp "${TMPDIR:-/tmp}/ci-template.XXXXXX")
for pair in "${synced[@]}"; do
  read -r template copy <<<"$pair"
  read_rc=0
  dev repo cat devops "templates/$template" >"$template_bytes" ||
    dev repo cat devops "templates/$template" >"$template_bytes" || read_rc=$?
  if [ "$read_rc" -ne 0 ]; then
    echo "ci/build.sh: could not read devops templates/$template (dev repo cat exit $read_rc, twice) -- a read failure, not a drift in $copy; nothing was measured" >&2
    rm -f "$template_bytes"
    exit 75
  fi
  if ! diff -u "$template_bytes" "$copy"; then
    echo "ci/build.sh: sync it -- dev repo cat devops templates/$template > $copy" >&2
    rm -f "$template_bytes"
    exit 1
  fi
done
rm -f "$template_bytes"

# ci-advisory: scripts/check-format
scripts/check-format || echo "check-format: could not measure this tree — advisory, nothing fails on it"

# THE SUITE, WITH TEST HEALTH (ISS-19887). `ci_test_health_sbt` prints the `ci-lists:` line for
# the lists `CI_TEST_LISTS` names (exiting 75 on a file that is not one, or on `CI_SLOW_SPECS=1`
# with none), and `ciTest` runs every spec the lists leave in -- the slow ones alone under
# `CI_SLOW_SPECS=1` -- then reruns each spec that failed once, printing `ci-flake:` per spec that
# passed the second time and `ci-rerun: done`. A spec that fails twice fails the build.
source ci/test-health.sh
ci_test_health_sbt
sbt -Dsbt.log.noformat=true "${ci_sbt_test_commands[@]}"
