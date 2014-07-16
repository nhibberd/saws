import sbt._
import Keys._
import com.ambiata.promulgate.project.ProjectPlugin._

object build extends Build {
  type Settings = Def.Setting[_]

  lazy val saws = Project(
    id = "saws",
    base = file("."),
    settings = standardSettings ++ promulgate.library("com.ambiata.saws", "ambiata-oss"),
    aggregate = Seq(core, ec2, s3, emr, ses, testing)
    ).dependsOn(core, ec2, s3, emr, ses)

  lazy val standardSettings = Defaults.defaultSettings ++
                   projectSettings          ++
                   compilationSettings      ++
                   testingSettings          ++
                   Seq(resolvers ++= depend.resolvers)

  lazy val projectSettings: Seq[Settings] = Seq(
      name := "saws"
    , version in ThisBuild := "1.2.1"
    , organization := "com.ambiata"
    , scalaVersion := "2.10.4"
  ) ++ Seq(prompt)

  lazy val core = Project(
    id = "core"
  , base = file("saws-core")
  , settings = standardSettings ++ lib("core") ++ Seq[Settings](
      name := "saws-core"
    ) ++ Seq[Settings](libraryDependencies ++= depend.scalaz ++ depend.aws ++ depend.scalazStream ++ depend.mundane ++ depend.mundaneTesting ++ depend.testing)
  )

  lazy val iam = Project(
    id = "iam"
  , base = file("saws-iam")
  , settings = standardSettings ++ lib("iam") ++ Seq[Settings](name := "saws-iam")
  ).dependsOn(core)

  lazy val ec2 = Project(
    id = "ec2"
  , base = file("saws-ec2")
  , settings = standardSettings ++ lib("ec2") ++ Seq[Settings](name := "saws-ec2")
  ).dependsOn(core, iam)

  lazy val s3 = Project(
    id = "s3"
  , base = file("saws-s3")
  , settings = standardSettings ++ lib("s3") ++ Seq[Settings](name := "saws-s3") 
  ).dependsOn(core)

  lazy val emr = Project(
    id = "emr"
  , base = file("saws-emr")
  , settings = standardSettings ++ lib("emr") ++ Seq[Settings](name := "saws-emr")
  ).dependsOn(core)

  lazy val ses = Project(
    id = "ses"
  , base = file("saws-ses")
  , settings = standardSettings ++ lib("ses") ++ Seq[Settings](name := "saws-ses")
  ).dependsOn(core)

  lazy val testing = Project(
    id = "testing"
  , base = file("saws-testing")
  , settings = standardSettings ++ lib("testing") ++ Seq[Settings](name := "saws-testing"
    ) ++ Seq[Settings](libraryDependencies ++= depend.specs2 ++ depend.ssh)
  ).dependsOn(iam, emr, ec2, ses, s3)

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

  def lib(name: String) =
    promulgate.library(s"com.ambiata.saws.$name", "ambiata-oss")

  lazy val prompt = shellPrompt in ThisBuild := { state =>
    val name = Project.extract(state).currentRef.project
    (if (name == "saws") "" else name) + "> "
  }

}
