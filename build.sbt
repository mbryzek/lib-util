name := "lib-util"

version := "0.0.50"

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

// `-feature` is what makes a `-Werror` failure name the construct, the file and the line.
// Without it the compiler says only "there was 1 feature warning; re-run with -feature",
// and the `ci` log is the only artifact that run leaves -- nobody can re-run it
// interactively. Order below is the two general flags, then the `-W` set alphabetically,
// so a new option has one obvious place to go.
lazy val allScalacOptions = Seq(
  "-feature",
  "-Werror",
  "-Wimplausible-patterns",
  "-Wunused:imports",
  "-Wunused:linted",
  "-Wunused:locals",
  "-Wunused:params",
  "-Wunused:privates"
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
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
    )
  )
