import sbt._
import Keys._

object depend {
  val scalaz = Seq(  "org.scalaz" %% "scalaz-core"   % "7.0.6"
                   , "org.scalaz" %% "scalaz-effect" % "7.0.6")

  val scalazStream = Seq("org.scalaz.stream" %% "scalaz-stream" % "0.4.1")

  val mundaneVersion = "1.2.1-20140926073331-855a5ca"

  val mundane = Seq(  "com.ambiata" %% "mundane-io"
                    , "com.ambiata" %% "mundane-store"
                    , "com.ambiata" %% "mundane-control").map(_ % mundaneVersion)

  val mundaneTesting = Seq("com.ambiata" %% "mundane-testing" % mundaneVersion)
  
  val aws = Seq(
      "com.amazonaws"       %  "aws-java-sdk" % "1.6.12" exclude("joda-time", "joda-time") // This is declared with a wildcard
    , "com.owtelse.codec"   %  "base64"       % "1.0.6"
    , "javax.mail"          %  "mail"         % "1.4.7")

  val specs2 = Seq(
      "org.specs2" %% "specs2-core"    % "2.3.12")

  val testing = Seq(
      "org.specs2" %% "specs2-core"        % "2.3.12" % "test"
    , "org.specs2" %% "specs2-junit"       % "2.3.12" % "test"
    , "org.specs2" %% "specs2-scalacheck"  % "2.3.12" % "test")

  val ssh = Seq("com.decodified" %% "scala-ssh" % "0.6.4")

  val resolvers = Seq(
      Resolver.sonatypeRepo("releases")
    , Resolver.typesafeRepo("releases")
    , "cloudera"              at "https://repository.cloudera.com/content/repositories/releases"
    , Resolver.url("ambiata-oss", new URL("https://ambiata-oss.s3.amazonaws.com"))(Resolver.ivyStylePatterns)
    , "Scalaz Bintray Repo"   at "http://dl.bintray.com/scalaz/releases"
    // For 2.11 version of scala-ssh only
    , "spray.io"              at "http://repo.spray.io")

}
