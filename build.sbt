name := "lib-util"

version := "0.0.81"

// Publishing metadata, the guava pin, the browser-stack exclusions, the compiler gate and the
// root project's test reporting are shared by every lib-* repository and DECLARED in
// project/BryzekLibSettings.scala, a copy of devops's
// templates/scala-libs/project/BryzekLibSettings.scala that ci/build.fast.sh holds byte-identical to
// it. That file carries why each setting is what it is; a change is edited there and copied here.
BryzekLibSettings.buildSettings("lib-util")

ThisBuild / scalaVersion := "3.10.0"

// The Jackson 2, Jackson 3 and logback security pins are DECLARED in project/BryzekPins.scala, a
// copy of devops's templates/scala-libs/project/BryzekPins.scala that ci/build.fast.sh holds
// byte-identical to it, together with the pin specs under src/test/scala/com/bryzek/pins. That
// file carries why each floor is what it is; an advisory bump is edited there and copied here.
//
// Applied as `dependencyOverrides`, which governs THIS build's resolution only -- sbt writes none
// of it into the published POM -- so it decides what this repo compiles and tests against and
// imposes no floor on a consumer. Jackson 3 is on this classpath through
// logstash-logback-encoder 9; logback is test-scope only.
ThisBuild / dependencyOverrides ++= BryzekPins.jackson2 ++ BryzekPins.jackson3 ++ BryzekPins.logback

lazy val root = project
  .in(file("."))
  .settings(BryzekLibSettings.rootSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.tototoshi" %% "scala-csv" % "2.0.0",
      "commons-codec" % "commons-codec" % "1.23.0",
      "joda-time" % "joda-time" % "2.15.1",
      "org.typelevel" %% "cats-core" % "2.13.0",
      // API only — every consumer already brings its own binding (logback, through Play). Declared
      // at compile scope rather than Provided so this library's own suite can assert the line a
      // logger actually receives.
      "org.slf4j" % "slf4j-api" % "2.0.20",
      // The Marker `KeyValueLoggerBuilder` attaches its accumulated pairs to, so a JSON encoder
      // emits each pair as a TOP-LEVEL FIELD alongside the rendered `message` rather than only
      // inside it. Compile scope, not Provided: the reference is in the bytecode of a class every
      // consumer loads, so a consumer without this jar throws NoClassDefFoundError on its first
      // log line. It costs a consumer that renders with a pattern encoder nothing at runtime --
      // logback hands the marker to the encoder, which ignores it -- so acumen and every test JVM
      // see the same lines they see today.
      //
      // The pom declares logback-classic `provided`, so this brings no logging backend of its own;
      // the version each consumer already pins is what binds.
      "net.logstash.logback" % "logstash-logback-encoder" % "9.0",
      "org.playframework" %% "play-json" % "3.0.6",
      "ch.qos.logback" % "logback-classic" % BryzekPins.logbackVersion % Test,
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
