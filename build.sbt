import sbt._

val catsV = "2.6.1"
val catsEffectV = "3.3.0"
val fs2V = "3.2.3"

val munitCatsEffectV = "1.0.5"

Global / onChangedBuildSource := IgnoreSourceChanges
// ThisBuild / crossScalaVersions := Seq("2.12.14","2.13.6", "3.0.1")
// ThisBuild / scalaVersion := "2.13.6"
ThisBuild / scalaVersion := "3.0.1"
ThisBuild / githubOwner := "FinFlow-Labs"
ThisBuild / githubRepository := "github-packages"

enablePlugins(GitHubPackagesPlugin)

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
  .settings(yPartial)
  .settings(
    name := "rediculous",
    testFrameworks += new TestFramework("munit.Framework"),

    libraryDependencies ++= Seq(
      "org.typelevel"               %% "cats-core"                  % catsV,
      "org.typelevel"               %% "cats-effect"                % catsEffectV,
      "co.fs2"                      %% "fs2-core"                   % fs2V,
      "co.fs2"                      %% "fs2-io"                     % fs2V,
      "org.typelevel"               %% "keypool"                    % "0.4.6",
      "org.typelevel"               %% "munit-cats-effect-3"        % munitCatsEffectV         % Test,
      "org.scalameta"               %% "munit-scalacheck"            % "0.7.27" % Test
    )
  )
  

lazy val examples =  project.in(file("examples"))
  .settings(GlobalSettingsGroup)
  .dependsOn(core)
  .settings(yPartial)
  .settings(
    name := "rediculous-examples",
    run / fork := true,
  )

lazy val yPartial = 
  Seq(
    scalacOptions ++= {
      if (scalaVersion.value.startsWith("2.12")) Seq("-Ypartial-unification")
      else Seq()
    }
  )