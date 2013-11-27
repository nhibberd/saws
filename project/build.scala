import sbt._
import Keys._
import sbtrelease.ReleasePlugin._
import com.ambiata.promulgate.Plugin._

object build extends Build {
  type Settings = Def.Setting[_]

  lazy val saws = Project(
    id = "saws",
    base = file("."),
    settings = Defaults.defaultSettings ++
               projectSettings          ++
               compilationSettings      ++
               testingSettings          ++
               publishingSettings       ++
               promulgate.sources
    )

  lazy val projectSettings: Seq[Settings] = Seq(
    name := "saws",
    organization := "com.ambiata",
    scalaVersion := "2.10.3")

  lazy val compilationSettings: Seq[Settings] = Seq(
    javacOptions ++= Seq("-Xmx3G", "-Xms512m", "-Xss4m"),
    maxErrors := 20,
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:_"),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

  lazy val testingSettings: Seq[Settings] = Seq(
    initialCommands in console := "import org.specs2._",
    logBuffered := false,
    cancelable := true,
    javaOptions += "-Xmx3G"
  )

  lazy val publishingSettings: Seq[Settings] = releaseSettings ++ Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishTo <<= version { v =>
      val artifactory = "http://etd-packaging.research.nicta.com.au/artifactory/"
      val flavour = if (v.trim.endsWith("SNAPSHOT")) "libs-snapshot-local" else "libs-release-local"
      val url = artifactory + flavour
      val name = "etd-packaging.research.nicta.com.au"
      Some(Resolver.url(name, new URL(url)))
    },
    credentials += Credentials(Path.userHome / ".credentials")
  )
}
