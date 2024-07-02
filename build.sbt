name := "lib-util"

organization := "com.mbryzek"

ThisBuild / scalaVersion := "3.4.2"

ThisBuild / javacOptions ++= Seq("-source", "17", "-target", "17")

crossScalaVersions := Seq("2.13.14", "3.4.2")

lazy val allScalacOptions = Seq(
  "-feature",
  "-Xfatal-warnings",
)

lazy val root = project
  .in(file("."))
  .settings(
    resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
    scalafmtOnCompile := true,
    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false,
    testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= allScalacOptions,
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.17.0",
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
    ),
  )
version := "0.0.6"
