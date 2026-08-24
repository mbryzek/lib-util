name := "lib-util"

version := "0.0.53"

ThisBuild / javacOptions ++= Seq("-source", "17", "-target", "17")

// The published groupId. Do not remove: nothing else sets it, and without it the
// artifact publishes under a default groupId that no consumer resolves (lib-util 0.0.34).
ThisBuild / organization := "com.bryzek"
ThisBuild / homepage := Some(url("https://github.com/mbryzek/lib-util"))
ThisBuild / licenses := Seq("MIT" -> url("https://github.com/mbryzek/lib-util/blob/main/LICENSE"))
ThisBuild / developers := List(
  Developer("mbryzek", "Michael Bryzek", "mbryzek@alum.mit.edu", url("https://github.com/mbryzek"))
)
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/mbryzek/lib-util"), "scm:git@github.com:mbryzek/lib-util.git")
)

ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / sonatypeCredentialHost := "central.sonatype.com"
ThisBuild / sonatypeRepository := "https://central.sonatype.com/api/v1/publisher"

ThisBuild / scalaVersion := "3.8.4"
// Keep the unused browser-automation stack off the test classpath.
//
// It arrives by two transitive routes -- play-test -> io.fluentlenium:fluentlenium-core, and
// scalatestplus-play -> org.seleniumhq.selenium:htmlunit-driver -> net.sourceforge.htmlunit --
// and each of the four packages underneath carries an open high or critical advisory:
// net.sourceforge.htmlunit:htmlunit, org.eclipse.jetty 9.4 (htmlunit's websocket client),
// org.codehaus.plexus:plexus-utils (fluentlenium's maven-model) and io.appium:java-client.
// The worst of them cannot be bumped: net.sourceforge.htmlunit:htmlunit has no release above
// 2.70.0, because the fix shipped under a renamed coordinate (org.htmlunit:htmlunit 3.0.0) that
// nothing on this classpath resolves. Excluding is the only remediation in our hands until
// play-test and htmlunit-driver move to it.
//
// org.seleniumhq.selenium IS DELIBERATELY LEFT ALONE, and htmlunit-driver with it. `play.api.test
// .PlayRunners` -- which `play.api.test.Helpers` extends, and which every GuiceOneServerPerSuite
// spec initializes on its way to starting a test server -- holds `val HTMLUNIT =
// classOf[HtmlUnitDriver]` and `val FIREFOX = classOf[FirefoxDriver]` as class literals, so both
// driver classes must RESOLVE or Helpers' static initializer throws NoClassDefFoundError and
// every server-per-suite spec dies before its first assertion. Resolving them needs only their
// own supertypes, which are all Selenium; neither loads anything from net.sourceforge.htmlunit.
// None of the Selenium artifacts carries an open advisory.
//
// What does go is genuinely unreferenced: the only play-test classes naming fluentlenium are
// TestBrowser and WebDriverFactory, and nothing here constructs a WebDriver, extends a
// scalatestplus-play browser trait, or uses anything from it beyond PlaySpec,
// GuiceOneAppPerSuite and GuiceOneServerPerSuite. A browser test written after this fails to
// link, loudly, at the moment it is written -- which is the intended trade: add a maintained
// stack (org.htmlunit, or Playwright) rather than inherit a stale one through a transitive.
ThisBuild / excludeDependencies ++= Seq(
  ExclusionRule("net.sourceforge.htmlunit"),
  ExclusionRule("io.fluentlenium"),
  ExclusionRule("io.appium")
)

// `-feature` is what makes a `-Werror` failure name the construct, the file and the line.
// Without it the compiler says only "there was 1 feature warning; re-run with -feature",
// and the `ci` log is the only artifact that run leaves -- nobody can re-run it
// interactively. Order below is the two general flags, then the `-W` set alphabetically,
// so a new option has one obvious place to go.
//
// `-Wvalue-discard` (E175) and `-Wnonunit-statement` (E176) are here for ONE class of silent
// bug: a computation that produced a value carrying its own outcome -- a `Future` nobody
// awaited, an `Either`/`Try`/`ValidatedNec` nobody inspected -- thrown away, so the code
// compiles, runs and reports success while the work it stands for never happened. Fixing one
// means HANDLING the value: await it, fold it, bind it, return it. Adapting it to `Unit`
// re-hides the exact thing the flag found, so it is not a fix.
//
// RAISED ON THE VALUE'S TYPE, NOT ON EVERY STATEMENT, and the three `-Wconf` lines below are
// how. Bare, the pair fires on every statement whose value goes unused: a `mutable.Map#put`
// returning the entry it displaced, a Java builder returning itself, a generated DAO `insert`
// returning the row it just wrote, a ScalaTest matcher returning the `Assertion` it has already
// thrown on. None of those is a dropped result, and none of them has any remedy other than the
// `: Unit` ascription this rule forbids -- so the two ids are silenced, then re-raised for the
// types where a discard IS the defect. Re-raised as an ERROR rather than a warning, so the gate
// does not depend on `-Werror` staying on above it.
//
// ORDER IS LOAD-BEARING: a later `-Wconf` rule beats an earlier one, so the sequence is
// silence-then-raise and never the reverse. Growing the gate means adding a type to the last
// line; deleting the two silencing lines is not the same thing and never was -- it surfaces
// tens of thousands of statements whose only legal fix this rule has ruled out.
//
// `-Xlint` IS NOT IN THIS SET, and its Scala 3 replacement is why. The option is Scala 2's;
// Scala 3 deprecates it and schedules it for removal, pointing at `-Wshadow` instead -- and
// `-Wshadow` fires on a constructor parameter forwarded to a base class that re-exposes it as a
// `val` of the same name, which is the only spelling that relationship has. Its remedy is a
// worse parameter name at every named-argument call site, so the lint costs more than the
// shadowing it reports.
lazy val allScalacOptions = Seq(
  "-feature",
  "-Werror",
  "-Wimplausible-patterns",
  "-Wnonunit-statement",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Wconf:id=E175:s",
  "-Wconf:id=E176:s",
  "-Wconf:msg=value of type ((scala\\.concurrent\\.)?Future|(scala\\.util\\.)?Try|(scala\\.util\\.)?Either|cats\\.data\\.Validated):e"
)

lazy val root = project
  .in(file("."))
  .settings(
    scalafmtOnCompile := true,
    Compile / packageDoc / mappings := Seq(),
    Compile / packageDoc / publishArtifact := true,
    // ISS-356: `-oDF` prints a per-test duration to stdout; `-u` writes one JUnit XML file per
    // suite, which is the only correct per-suite attribution this build produces. stdout carries
    // no per-line suite marker, so once suites run in parallel a reader (devops/bin/test-timings)
    // can only attribute a duration to whichever "[info] ClassName:" header was printed most
    // recently by ANY thread -- which on a real run named the wrong suite for every test it
    // reported as slow.
    testOptions ++= Seq(
      Tests.Argument("-oDF"),
      Tests.Argument(TestFrameworks.ScalaTest, "-u", (target.value / "test-reports").getAbsolutePath)
    ),
    scalacOptions ++= allScalacOptions,
    libraryDependencies ++= Seq(
      "com.github.tototoshi" %% "scala-csv" % "2.0.0",
      "commons-codec" % "commons-codec" % "1.22.1",
      "joda-time" % "joda-time" % "2.14.3",
      "org.typelevel" %% "cats-core" % "2.13.0",
      // API only — every consumer already brings its own binding (logback, through Play). Declared
      // at compile scope rather than Provided so this library's own suite can assert the line a
      // logger actually receives.
      "org.slf4j" % "slf4j-api" % "2.0.18",
      "org.playframework" %% "play-json" % "3.0.6",
      "ch.qos.logback" % "logback-classic" % "1.6.3" % Test,
      // org.lz4:lz4-java reaches the test classpath only here, transitively:
      // scalatestplus-play -> play-ws -> play -> pekko-serialization-jackson -> lz4-java.
      // Nothing on that classpath can call it. Pekko loads an LZ4 codec reflectively only when
      // pekko.serialization.jackson.compression.algorithm is set to lz4 (it ships `off`, and this
      // repo has no application.conf to override it), and the JacksonSerializer that would read
      // the setting is reached only through pekko remoting/clustering/persistence, none of which
      // resolves here. org.lz4 is dead upstream -- its newest version is a relocation POM, so
      // there is no version to bump to -- which makes dropping the jar the only way to keep
      // advisories against it off this build. Excluded on this dependency rather than through a
      // build-wide `excludeDependencies`, which writes an <exclusions> block onto every
      // compile-scope dependency in the published POM; this one is test scope, so it reaches no
      // consumer. ISS-4734
      ("org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test)
        .exclude("org.lz4", "lz4-java")
    )
  )
