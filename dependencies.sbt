libraryDependencies ++= Seq(
    "org.scalaz"          %% "scalaz-core"                % "7.0.4",
    "com.github.scopt"    %% "scopt"                      % "3.1.0",
    "com.amazonaws"       %  "aws-java-sdk"               % "1.6.1",
    "com.ambiata"         %% "mundane"                    % "1.2.0-SNAPSHOT",
    "com.owtelse.codec"   %  "base64"                     % "1.0.6",
    "org.specs2"          %% "specs2"                     % "2.4-SNAPSHOT" % "optional")

libraryDependencies ++= Seq(
    "com.decodified"      %% "scala-ssh"                  % "0.6.4"        % "test",
    "org.scalacheck"      %% "scalacheck"                 % "1.11.0"       % "test",
    "org.hamcrest"        %  "hamcrest-all"               % "1.1"          % "test",
    "org.mockito"         %  "mockito-all"                % "1.9.5"        % "test",
    "junit"               %  "junit"                      % "4.11"         % "test",
    "org.pegdown"         %  "pegdown"                    % "1.2.1"        % "test",
    "org.specs2"          %  "classycle"                  % "1.4.1"        % "test",
    "com.ambiata"         %% "scrutiny"                   % "0.1-SNAPSHOT" % "test")


resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.typesafeRepo("releases"),
    "cloudera"              at "https://repository.cloudera.com/content/repositories/releases",
    "spray"                 at "http://repo.spray.io",
    "artifactory"           at "http://etd-packaging.research.nicta.com.au/artifactory/libs-release-local",
    "artifactory snapshot"  at "http://etd-packaging.research.nicta.com.au/artifactory/libs-snapshot-local")
