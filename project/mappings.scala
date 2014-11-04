import sbt._
import Keys._

object Mappings {
  def premapping(update: UpdateReport, file: File) =
    injar(file) ++
    keepAmbiata ++
    Seq("-printmapping mappings.map"
      , "-keep class com.amazonaws.** { *; }"
      , "-keepclassmembers class com.amazonaws.** { *; }") ++
    setup(file, update) ++
    Seq("-outjars empty.jar") ++
    dont


  def mapping(name: String, version: String, update: UpdateReport, file: File) = {
    val r = IO.readLines(file.getParentFile / "proguard" / "mappings.map")
    val s = r.filter(!_.startsWith(" "))
    val t = s.map(_.replace("-> com.amazonaws", "-> com.ambiata.com.amazonaws"))
    IO.writeLines(file.getParentFile / "proguard" / "aws.map", t)
    injar(file) ++
    keepAmbiata ++
    Seq("-applymapping aws.map") ++
    Seq(s"-outjars ${name}-proguard-${version}.jar") ++
    setup(file, update) ++
    dont
  }

  def injar(file: File) = Seq(
      "-ignorewarnings"
    , s"-injars $file"
    )

  def setup(file: File, update: UpdateReport) = Seq(
      "-libraryjars <java.home>/lib/rt.jar"
    , s"-libraryjars $file"
    , update.select(module = moduleFilter(organization = "com.amazonaws")).map(f => s"-injars $f(!META-INF/MANIFEST.MF)").mkString("\n")
    )

  val keepAmbiata =
    Seq("-keep class com.ambiata.** { *; }", "-keepclassmembers class com.ambiata.** { *; }")

  val dont =
    Seq("-dontoptimize", "-dontshrink", "-dontpreverify")

  def preshim(update: UpdateReport) = {
    Seq(update.select(module = moduleFilter(organization = "com.amazonaws")).map(f => s"-injars $f(!META-INF/MANIFEST.MF)").mkString("\n")) ++
    Seq("-ignorewarnings"
      , "-printmapping mappings.map"
      , "-keep class com.amazonaws.** { *; }"
      , "-keepclassmembers class com.amazonaws.** { *; }"
      , "-libraryjars <java.home>/lib/rt.jar") ++
    Seq(update.select(module = moduleFilter(organization = "com.amazonaws")).map(f => s"-libraryjars $f(!META-INF/MANIFEST.MF)").mkString("\n")) ++
    Seq("-outjars empty.jar") ++
    dont
  }

  def shim(name: String, version: String, update: UpdateReport, file: File) = {
    val r = IO.readLines(file.getParentFile / "proguard" / "mappings.map")
    val s = r.filter(!_.startsWith(" "))
    val t = s.map(_.replace("-> com.amazonaws", "-> com.ambiata.com.amazonaws"))
    IO.writeLines(file.getParentFile / "proguard" / "aws.map", t)
    Seq(update.select(module = moduleFilter(organization = "com.amazonaws")).map(f => s"-injars $f(!META-INF/MANIFEST.MF)").mkString("\n")) ++
    Seq("-ignorewarnings"
      , "-keep class com.amazonaws.** { *; }"
      , "-keepclassmembers class com.amazonaws.** { *; }"
      , "-applymapping aws.map"
      , "-libraryjars <java.home>/lib/rt.jar") ++
    Seq(update.select(module = moduleFilter(organization = "com.amazonaws")).map(f => s"-libraryjars $f(!META-INF/MANIFEST.MF)").mkString("\n")) ++
    Seq(s"-outjars ${name}-proguard-${version}.jar") ++
    dont
  }

}
