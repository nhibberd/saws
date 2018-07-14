import sbt._
import Keys._
import com.ambiata.promulgate.project.ProjectPlugin._

object build extends Build {
  type Settings = Def.Setting[_]

  lazy val ossBucket: String =
    sys.env.getOrElse("AMBIATA_IVY_OSS", "ambiata-oss")

  lazy val saws = Project(
    id = "saws",
    base = file("."),
    settings = standardSettings ++ promulgate.library("com.ambiata.saws", ossBucket),
    aggregate = Seq(core, ec2, s3, iam, emr, ses, cw, testing)
    ).dependsOn(core, ec2, s3, iam, emr, ses, cw)

  lazy val standardSettings = Defaults.coreDefaultSettings ++
                   projectSettings          ++
                   compilationSettings      ++
                   testingSettings          ++
                   Seq(resolvers ++= depend.resolvers)

  lazy val projectSettings: Seq[Settings] = Seq(
      name := "saws"
    , version in ThisBuild := "1.2.2"
    , organization := "com.ambiata"
    , scalaVersion := "2.11.6"
    , crossScalaVersions := Seq(scalaVersion.value)
    , publishArtifact in (Test, packageBin) := true
  ) ++ Seq(prompt)

  lazy val core = Project(
    id = "core"
  , base = file("saws-core")
  , settings = standardSettings ++ lib("core") ++ Seq[Settings](
      name := "saws-core"
    ) ++ Seq[Settings](libraryDependencies ++= depend.scalaz ++ depend.aws ++ depend.mundane ++ depend.testing)
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

  lazy val cw = Project(
      id = "cw"
    , base = file("saws-cw")
    , settings = standardSettings ++ lib("cw") ++ Seq[Settings](name := "saws-cw")  ++
        Seq[Settings](libraryDependencies ++= depend.scalaz ++ depend.testing ++ depend.disorder ++ depend.mundaneTesting)
  ).dependsOn(core)

  lazy val testing = Project(
    id = "testing"
  , base = file("saws-testing")
  , settings = standardSettings ++ lib("testing") ++ Seq[Settings](name := "saws-testing"
    ) ++ Seq[Settings](libraryDependencies ++= depend.specs2 ++ depend.ssh ++ depend.mundane ++ depend.mundaneTesting ++ depend.disorder)
  ).dependsOn(iam, emr, ec2, ses, s3, cw, cw % "test->test")

  lazy val compilationSettings: Seq[Settings] = Seq(
    javacOptions ++= Seq("-Xmx3G", "-Xms512m", "-Xss4m"),
    maxErrors := 10,
    scalacOptions ++= Seq("-feature", "-language:_"),
    scalacOptions in Compile ++= Seq(
      "-target:jvm-1.6"
    , "-deprecation"
    , "-unchecked"
    , "-feature"
    , "-language:_"
    , "-Ywarn-value-discard"
    , "-Yno-adapted-args"
    , "-Xlint"
    , "-Xfatal-warnings"
    , "-Yinline-warnings"),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

  lazy val packageSettings: Seq[Settings] =
    promulgate.library("com.ambiata.saws", ossBucket)

  lazy val testingSettings: Seq[Settings] = Seq(
      initialCommands in console := "import org.specs2._"
    , logBuffered := false
    , cancelable := true
    , javaOptions += "-Xmx3G"
    , fork in Test := Option(System.getenv("NO_FORK")).map(_ != "true").getOrElse(true)
    , testOptions in Test ++= (if (Option(System.getenv("FORCE_AWS")).isDefined || Option(System.getenv("AWS_ACCESS_KEY")).isDefined)
                                 Seq()
                               else
                                 Seq(Tests.Argument("--", "exclude", "aws")))
  )

  def lib(name: String) =
    promulgate.library(s"com.ambiata.saws.$name", ossBucket)

  lazy val prompt = shellPrompt in ThisBuild := { state =>
    val name = Project.extract(state).currentRef.project
    (if (name == "saws") "" else name) + "> "
  }

}
