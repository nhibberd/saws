import sbt._
import Keys._
import com.ambiata.promulgate.project.ProjectPlugin._
import com.ambiata.promulgate.version.VersionPlugin._
import sbtassembly.Plugin._
import com.typesafe.sbt.SbtProguard._

object build extends Build {
  type Settings = Def.Setting[_]

  lazy val saws = Project(
    id = "saws",
    base = file("."),
    settings = standardSettings ++ promulgate.library("com.ambiata.saws", "ambiata-oss"),
    aggregate = Seq(core, ec2, s3, iam, emr, ses, testing, shim)
    ).dependsOn(core, ec2, s3, iam, emr, ses, shim)

  lazy val standardSettings = Defaults.coreDefaultSettings ++
                   projectSettings          ++
                   compilationSettings      ++
                   testingSettings          ++
                   proguard                 ++
                   Seq(resolvers ++= depend.resolvers)

  lazy val projectSettings: Seq[Settings] = Seq(
      name := "saws"
    , version in ThisBuild := "1.2.1"
    , organization := "com.ambiata"
    , scalaVersion := "2.11.2"
    , crossScalaVersions := Seq(scalaVersion.value)
  ) ++ Seq(prompt)

  /*
   Hadoop has an imcompatible dependency on `aws-java-sdk` with the version in use here.
   Therefore we are using Proguard to map the package names in `aws-java-sdk` in use
   here to avoid runtime errros.
   ProguardPre will create a mapping file of the `aws-java-sdk` and `Mappings.mapping`
   will then filter and rename the mappings file before the final Proguard task runs the
   mappings over the library.
   */
  val ProguardPre = config("proguard-pre")

  def dependenciesPre: Seq[Setting[_]] = Seq(
    ivyConfigurations += ProguardPre,
    libraryDependencies <+= (ProguardKeys.proguardVersion in ProguardPre) { version =>
      "net.sf.proguard" % "proguard-base" % version % ProguardPre.name
    }
  )

  val proguard =
    proguardSettings         ++
    inConfig(ProguardPre)(ProguardSettings.default ++ dependenciesPre ++ Seq(managedClasspath <<= (managedClasspath, managedClasspath in Compile).map({ case (y, x) => y ++ x })) )       ++
    dependenciesPre          ++
    Seq[Settings](
        ProguardKeys.options in ProguardPre <<= (update, packageBin in Compile).map({ case (u, b) => Mappings.premapping(u, b) })
      , ProguardKeys.options in Proguard <<= (ProguardKeys.proguard in ProguardPre, name, version, update, packageBin in Compile).map({
          case(_, n, v, u, b) => Mappings.mapping(n, v, u, b)
        })
      , javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G")
    )                        ++
    addArtifact(name.apply(n => Artifact(s"$n", "jar", "jar", "shade")), (ProguardKeys.proguard in Proguard, packageBin in Compile, name, version).map({ case (_, s, n, v) =>
      s.getParentFile / "proguard" / s"$n-proguard-$v.jar"}))

  lazy val core = Project(
    id = "core"
  , base = file("saws-core")
  , settings = standardSettings ++ lib("core") ++ Seq[Settings](
      name := "saws-core"
    ) ++ Seq[Settings](libraryDependencies ++= depend.scalaz ++ depend.aws ++ depend.scalazStream ++ depend.mundane ++ depend.testing)
  ).dependsOn(shim)

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
    ) ++ Seq[Settings](libraryDependencies ++= depend.specs2 ++ depend.ssh ++ depend.mundane ++ depend.mundaneTesting)
  ).dependsOn(iam, emr, ec2, ses, s3)

  lazy val shim = Project(
    id = "shim"
  , base = file("saws-shim")
  , settings = projectSettings ++ compilationSettings ++ Seq(resolvers ++= depend.resolvers) ++ lib("shim") ++ proguardSettings ++
      Seq[Settings](libraryDependencies ++= depend.aws) ++
      inConfig(ProguardPre)(ProguardSettings.default ++ dependenciesPre ++ Seq(managedClasspath <<= (managedClasspath, managedClasspath in Compile).map({ case (y, x) => y ++ x}))) ++
      dependenciesPre ++
      Seq[Settings](name := "saws-shim"
        , ProguardKeys.options in ProguardPre <<= (update, packageBin in Compile).map({ case (u, _) => Mappings.preshim(u) })
        , ProguardKeys.options in Proguard <<= (ProguardKeys.proguard in ProguardPre, name, version, update, packageBin in Compile).map({
            case(_, n, v, u, b) => Mappings.shim(n, v, u, b)
          })
      , javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G")
      ) ++
      addArtifact(name.apply(n => Artifact(s"$n", "jar", "jar", "shade")), (ProguardKeys.proguard in Proguard, packageBin in Compile, name, version).map({ case (_, s, n, v) => s.getParentFile / "proguard" / s"$n-proguard-$v.jar"}))
  )

  lazy val compilationSettings: Seq[Settings] = Seq(
    javacOptions ++= Seq("-Xmx3G", "-Xms512m", "-Xss4m"),
    maxErrors := 20,
    scalacOptions ++= Seq("-feature", "-language:_"),
    scalacOptions in Compile ++= Seq("-deprecation", "-unchecked", "-Xfatal-warnings"),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

  lazy val packageSettings: Seq[Settings] =
    promulgate.library("com.ambiata.saws", "ambiata-oss")

  lazy val testingSettings: Seq[Settings] = Seq(
    initialCommands in console := "import org.specs2._"
    , logBuffered := false
    , cancelable := true
    , javaOptions += "-Xmx3G"
    , testOptions in Test ++= (if (Option(System.getenv("FORCE_AWS")).isDefined || Option(System.getenv("AWS_ACCESS_KEY")).isDefined)
                                 Seq()
                               else
                                 Seq(Tests.Argument("--", "exclude", "aws")))
  )

  def lib(name: String) =
    promulgate.library(s"com.ambiata.saws.$name", "ambiata-oss")

  lazy val prompt = shellPrompt in ThisBuild := { state =>
    val name = Project.extract(state).currentRef.project
    (if (name == "saws") "" else name) + "> "
  }

}
