name := "lib-util"

organization := "com.bryzek"

version := "0.0.23"

ThisBuild / javacOptions ++= Seq("-source", "17", "-target", "17")

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
ThisBuild / publishMavenStyle := true

ThisBuild / scalaVersion := "3.7.4"

lazy val allScalacOptions = Seq(
  "-feature",
  "-Xfatal-warnings",
  "-Wunused:locals",
  "-Wunused:params",
  "-Wimplausible-patterns",
  "-Wunused:linted",
  "-Wunused:unsafe-warn-patvars",
  "-Wunused:imports",
  "-Wunused:privates",
)

lazy val root = project
  .in(file("."))
  .settings(
    resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
    scalafmtOnCompile := true,
    Compile / packageDoc / mappings := Seq(),
    Compile / packageDoc / publishArtifact := true,
    testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= allScalacOptions,
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.20.0",
      "joda-time" % "joda-time" % "2.14.0",
      "org.typelevel" %% "cats-core" % "2.12.0",
      "org.playframework" %% "play-json" % "3.0.6",
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
    ),
  )
