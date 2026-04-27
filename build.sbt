lazy val V = _root_.scalafix.sbt.BuildInfo
val Scala213 = "2.13.18"

inThisBuild(
  List(
    tlBaseVersion := "0.1",
    organization := "org.polyvariant",
    organizationName := "Polyvariant",
    startYear := Some(2026),
    scalaVersion := Scala213,
    licenses := Seq(License.Apache2),
    developers := List(tlGitHubDev("kubukoz", "Jakub Kozłowski")),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    tlFatalWarnings := false,
  )
)

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / mergifyStewardConfig ~= (_.map(_.withMergeMinors(true)))

lazy val root = (project in file("."))
  .aggregate(rules, tests)
  .enablePlugins(NoPublishPlugin)

lazy val rules = project
  .settings(
    name := "SlowInferenceChain",
    moduleName := "SlowInferenceChain",
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafixVersion,
  )

lazy val input = project
  .enablePlugins(NoPublishPlugin)

lazy val output = project
  .enablePlugins(NoPublishPlugin)

lazy val tests = project
  .settings(
    scalafixTestkitOutputSourceDirectories := (output / Compile / unmanagedSourceDirectories).value,
    scalafixTestkitInputSourceDirectories := (input / Compile / unmanagedSourceDirectories).value,
    scalafixTestkitInputClasspath := (input / Compile / fullClasspath).value,
    scalafixTestkitInputScalacOptions := (input / Compile / scalacOptions).value,
    scalafixTestkitInputScalaVersion := (input / Compile / scalaVersion).value,
  )
  .dependsOn(rules)
  .enablePlugins(ScalafixTestkitPlugin)
  .enablePlugins(NoPublishPlugin)
