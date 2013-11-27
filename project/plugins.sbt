resolvers += Resolver.url("artifactory-plugins", new URL("http://etd-packaging.research.nicta.com.au/artifactory/libs-release-local"))(Patterns("[organization]/[module]_[scalaVersion]_[sbtVersion]/[revision]/[artifact](-[classifier])-[revision].[ext]"))

addSbtPlugin("com.ambiata" % "promulgate" % "0.7.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.7.1")

addSbtPlugin("com.orrsella" % "sbt-stats" % "1.0.5")
