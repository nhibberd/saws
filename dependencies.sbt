libraryDependencies ++= Seq("com.ambiata" %% "mundane-io"      % "1.2.1-20140706102726-48c45a7",
                            "com.ambiata" %% "mundane-store"   % "1.2.1-20140706102726-48c45a7",
                            "com.ambiata" %% "mundane-control" % "1.2.1-20140706102726-48c45a7",
                            "com.ambiata" %% "mundane-testing" % "1.2.1-20140706102726-48c45a7" % "test")

libraryDependencies ++= Seq(
    "org.scalaz.stream"   %% "scalaz-stream"              % "0.4.1",
    "com.amazonaws"       %  "aws-java-sdk"               % "1.6.12",
    "com.owtelse.codec"   %  "base64"                     % "1.0.6",
    "javax.mail"          %  "mail"                       % "1.4.7")

  val specs2    = Seq("specs2-core", "specs2-junit", "specs2-html", "specs2-matcher-extra", "specs2-scalacheck").map(c =>
                      "org.specs2"           %% c                 % "2.3.10" % "test")

libraryDependencies ++= Seq(
    "org.specs2"          %% "specs2-core"                % "2.3.10",
    "org.specs2"          %% "specs2-junit"               % "2.3.10"               % "test",
    "org.specs2"          %% "specs2-scalacheck"          % "2.3.10"               % "test",
    "com.decodified"      %% "scala-ssh"                  % "0.6.4"                % "test")

resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.typesafeRepo("releases"),
    "cloudera"              at "https://repository.cloudera.com/content/repositories/releases",
    "spray"                 at "http://repo.spray.io",
    Resolver.url("ambiata-oss", new URL("https://ambiata-oss.s3.amazonaws.com"))(Resolver.ivyStylePatterns),
    "Scalaz Bintray Repo"   at "http://dl.bintray.com/scalaz/releases")
