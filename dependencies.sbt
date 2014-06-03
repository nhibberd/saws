libraryDependencies ++= Seq("com.ambiata" %% "mundane-store"   % "1.2.1-20140603064529-a563586",
                            "com.ambiata" %% "mundane-testing" % "1.2.1-20140603064529-a563586")

libraryDependencies ++= Seq(
    "org.scalaz.stream"   %% "scalaz-stream"              % "0.3",
    "com.github.scopt"    %% "scopt"                      % "3.1.0",
    "com.amazonaws"       %  "aws-java-sdk"               % "1.6.12",
    "com.owtelse.codec"   %  "base64"                     % "1.0.6",
    "javax.mail"          %  "mail"                       % "1.4.7",
    "com.chuusai"         %  "shapeless_2.10.3"           % "2.0.0-M1")

libraryDependencies ++= Seq(
    "org.specs2"          %% "specs2-core"                % "2.3.10"               % "test",
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
