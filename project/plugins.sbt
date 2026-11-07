logLevel := Level.Warn

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")

// BryzekPins, BryzekLibSettings and the test-health plugins (CiTestListsPlugin,
// CiTestRerunPlugin), shared by every Scala build: https://github.com/mbryzek/lib-build
resolvers += "Bryzek Maven repository" at "https://maven.bryzek.com"
libraryDependencies += "com.bryzek" %% "lib-build-sbt" % "0.0.1"
