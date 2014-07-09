libraryDependencies ++= Seq("com.ambiata" %% "mundane-io"      % "1.2.1-20140706115053-2c11cc2",
                            "com.ambiata" %% "mundane-store"   % "1.2.1-20140706115053-2c11cc2",
                            "com.ambiata" %% "mundane-control" % "1.2.1-20140706115053-2c11cc2",
                            "com.ambiata" %% "mundane-testing" % "1.2.1-20140706115053-2c11cc2")

libraryDependencies ++= Seq(
    "org.scalaz.stream"   %% "scalaz-stream"              % "0.4.1",
    "com.amazonaws"       %  "aws-java-sdk"               % "1.6.12"
        exclude("joda-time", "joda-time"), // This is declared with a wildcard
    "com.owtelse.codec"   %  "base64"                     % "1.0.6",
    "javax.mail"          %  "mail"                       % "1.4.7")

libraryDependencies ++= Seq(
    "org.specs2"          %% "specs2-core"                % "2.3.12",
    "org.specs2"          %% "specs2-matcher"             % "2.3.12",
    "org.specs2"          %% "specs2-junit"               % "2.3.12"               % "test",
    "org.specs2"          %% "specs2-scalacheck"          % "2.3.12"               % "test",
    "com.decodified"      %% "scala-ssh"                  % "0.6.4"                % "test")

resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.typesafeRepo("releases"),
    "cloudera"              at "https://repository.cloudera.com/content/repositories/releases",
    "spray"                 at "http://repo.spray.io",
    Resolver.url("ambiata-oss", new URL("https://ambiata-oss.s3.amazonaws.com"))(Resolver.ivyStylePatterns),
    "Scalaz Bintray Repo"   at "http://dl.bintray.com/scalaz/releases")
