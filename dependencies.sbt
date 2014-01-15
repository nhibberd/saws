libraryDependencies += "com.ambiata" %% "mundane" % "1.2.1-20140115000415-1264e7d"

libraryDependencies ++= Seq(
    "org.scalaz"          %% "scalaz-core"                % "7.0.4",
    "org.scalaz.stream"   %% "scalaz-stream"              % "0.3",
    "com.github.scopt"    %% "scopt"                      % "3.1.0",
    "com.amazonaws"       %  "aws-java-sdk"               % "1.6.12",
    "com.owtelse.codec"   %  "base64"                     % "1.0.6",
    "javax.mail"          %  "mail"                       % "1.4.7")

libraryDependencies ++= Seq(
    "org.specs2"          %% "specs2-core"                % "2.3.4"        % "test",
    "org.specs2"          %% "specs2-junit"               % "2.3.4"        % "test",
    "org.specs2"          %% "specs2-scalacheck"          % "2.3.4"        % "test",
    "com.decodified"      %% "scala-ssh"                  % "0.6.4"        % "test",
    "org.scalacheck"      %% "scalacheck"                 % "1.11.1"       % "test",
    "com.ambiata"         %% "scrutiny"                   % "1.1-20131224033803-9095a20"          % "test")

resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.typesafeRepo("releases"),
    "cloudera"              at "https://repository.cloudera.com/content/repositories/releases",
    "spray"                 at "http://repo.spray.io",
    "artifactory"           at "http://etd-packaging.research.nicta.com.au/artifactory/libs-release-local",
    "Scalaz Bintray Repo"   at "http://dl.bintray.com/scalaz/releases")
