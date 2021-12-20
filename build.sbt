import sbt._

val catsV = "2.7.0"
val catsEffectV = "3.3.0"
val fs2V = "3.2.3"

val munitCatsEffectV = "1.0.5"

Global / onChangedBuildSource := IgnoreSourceChanges
// ThisBuild / crossScalaVersions := Seq("2.12.14","2.13.6", "3.0.1")
// ThisBuild / scalaVersion := "2.13.6"
ThisBuild / scalaVersion := "3.1.0"
ThisBuild / githubOwner := "FinFlow-Labs"
ThisBuild / githubRepository := "github-packages"

enablePlugins(GitHubPackagesPlugin)
enablePlugins(DynVerPlugin)

val GlobalSettingsGroup: Seq[Setting[_]] = Seq(
  githubOwner := "FinFlow-Labs",
  githubRepository := "github-packages",
)

// Projects
lazy val `rediculous` = project.in(file("."))
  .aggregate(core, core, examples)

lazy val core = project
  .in(file("core"))
  .settings(GlobalSettingsGroup)
  .settings(
    name := "rediculous",
    testFrameworks += new TestFramework("munit.Framework"),

    libraryDependencies ++= Seq(
      "org.typelevel"               %% "cats-core"                  % catsV,
      "org.typelevel"               %% "cats-effect"                % catsEffectV,
      "co.fs2"                      %% "fs2-core"                   % fs2V,
      "co.fs2"                      %% "fs2-io"                     % fs2V,
      "org.typelevel"               %% "keypool"                    % "0.4.7",
      "org.typelevel"               %% "munit-cats-effect-3"        % munitCatsEffectV         % Test,
      "org.scalameta"               %% "munit-scalacheck"            % "0.7.29" % Test
    )
  )
  

lazy val examples =  project.in(file("examples"))
  .settings(GlobalSettingsGroup)
  .dependsOn(core)
  .settings(
    name := "rediculous-examples",
    run / fork := true,
  )