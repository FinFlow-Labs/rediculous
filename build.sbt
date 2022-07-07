// ThisBuild / tlBaseVersion := "0.4" // your current series x.y

ThisBuild / organization := "syther.labs"
ThisBuild / githubOwner := "syther-labs"
ThisBuild / githubRepository := "rediculous"
Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / githubWorkflowPublishTargetBranches += RefPredicate.Equals(Ref.Branch("sj-fast-lane-fork"))
ThisBuild / githubWorkflowScalaVersions := Seq("3.1.0")
// ThisBuild / tlCiReleaseBranches := Seq("sj-fast-lane-fork")

// true by default, set to false to publish to s01.oss.sonatype.org
// ThisBuild / tlSonatypeUseLegacyHost := false


val catsV = "2.7.0"
val catsEffectV = "3.3.3"
val fs2V = "3.2.3"

val munitCatsEffectV = "1.0.7"

ThisBuild / crossScalaVersions := Seq("2.13.8", "3.1.0")
ThisBuild / scalaVersion := "2.13.6"
ThisBuild / versionScheme := Some("early-semver")

enablePlugins(GitHubPackagesPlugin)
enablePlugins(DynVerPlugin)

// Projects
lazy val `rediculous` = project.in(file("."))
  .settings(
    publish / skip := true,
  )
  .aggregate(core, examples)

lazy val core = project
  .settings(
    name := "rediculous",
    testFrameworks += new TestFramework("munit.Framework"),

    libraryDependencies ++= Seq(
      "org.typelevel"               %%% "cats-core"                  % catsV,

      "org.typelevel"               %%% "cats-effect"                % catsEffectV,

      "co.fs2"                      %%% "fs2-core"                   % fs2V,
      "co.fs2"                      %%% "fs2-io"                     % fs2V,
      "co.fs2"                      %%% "fs2-scodec"                 % fs2V,

      "org.typelevel"               %%% "keypool"                    % "0.4.7",

      "io.chrisdavenport"           %%% "cats-scalacheck"            % "0.3.1" % Test,
      "org.typelevel"               %%% "munit-cats-effect-3"        % munitCatsEffectV         % Test,
      "io.chrisdavenport"           %%% "whale-tail-manager"         % "0.0.8" % Test,
      "org.scalameta"               %%% "munit-scalacheck"           % "0.7.29" % Test,
      "com.github.jnr" % "jnr-unixsocket" % "0.38.15" % Test,
    )
  )
  

lazy val examples =  project
  .dependsOn(core)
  .settings(
    publish / skip := true,
    name := "rediculous-examples",
    run / fork := true,
  )
