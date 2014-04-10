import sbt._
import Keys._
import com.ambiata.promulgate.project.ProjectPlugin._

object build extends Build {
  type Settings = Def.Setting[_]

  lazy val saws = Project(
    id = "saws",
    base = file("."),
    settings = Defaults.defaultSettings ++
               projectSettings          ++
               compilationSettings      ++
               testingSettings          ++
               packageSettings
    )

  lazy val projectSettings: Seq[Settings] = Seq(
    name := "saws",
    version in ThisBuild := "1.2.1",
    organization := "com.ambiata",
    scalaVersion := "2.10.3")

  lazy val compilationSettings: Seq[Settings] = Seq(
    javacOptions ++= Seq("-Xmx3G", "-Xms512m", "-Xss4m"),
    maxErrors := 20,
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:_"),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

  lazy val packageSettings: Seq[Settings] =
    promulgate.library("com.ambiata.saws", "ambiata-oss")

  lazy val testingSettings: Seq[Settings] = Seq(
    initialCommands in console := "import org.specs2._",
    logBuffered := false,
    cancelable := true,
    javaOptions += "-Xmx3G"
  )
}
