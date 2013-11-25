package com.ambiata.saws
package s3

import scalaz._, Scalaz._, \&/._
import com.amazonaws.services.s3.AmazonS3Client
import java.io._
import java.net.URLClassLoader
import java.util.jar.{JarInputStream, JarEntry, JarOutputStream}
import scala.collection.mutable
import com.ambiata.saws.core._


object S3Jar {
  /** create and push a jar file to S3 */
  def pushJar(jarName: String, bucket: String, base: String = "target/scala-2.10"): S3Action[String] =
    AwsAction.config[AmazonS3Client].flatMap(client => {
      val jarFile = new File(base, jarName)
      (for {
        _ <- makeJar(jarFile)
        _ <- S3.putFile(bucket, jarName, jarFile)
      } yield s"pushed the jar file at ${jarFile.getPath} to the S3 bucket/key $bucket/$jarName")
        .mapError(t => This(s"couldn't push the jar file at ${jarFile.getPath} to the S3 bucket/key $bucket/$jarName - ${t}"))
    })  

  /** @return add classes to the jar file */
  def makeJar(jarFile: File): S3Action[File] = AwsAction.value {
    val jars = getClass.getClassLoader.asInstanceOf[URLClassLoader].getURLs
    val jarBuilder = JarBuilder(jarFile.getPath)
    val exclude = Seq("JavaVirtualMachines", "jre/lib", "eclipse", "hadoop-client", "hadoop-hdfs", "hadoop-common", "hadoop-auth", "idea_rt")

    val jarsToInclude = jars.filterNot(_.getPath.contains("eclipse")).collect {
      case j if j.getFile.contains("hadoop-core")   => j.getFile.replaceAll("hadoop-core-.*", "hadoop-core-0.20.2-cdh3u1.jar")
      case j if j.getFile.contains("avro-mapred")   => j.getFile.replace("-hadoop2", "")
      case j if !exclude.exists(j.getFile.contains) => j.getFile
    }

    jarsToInclude.foreach { path =>
      if (new File(path).isFile) jarBuilder.addJar(path)
      else                       jarBuilder.addClassDirectory(path)
    }
    jarBuilder.close
    jarFile
  }.safe

  case class JarBuilder(jarPath: String) {
    private val jos = new JarOutputStream(new FileOutputStream(jarPath))
    private val entries: mutable.Set[String] = mutable.Set.empty

    def addJar(jarFile: String): Unit =
      addJarEntries(jarFile, e => true)

    /** Add the class files found in a given directory */
    def addClassDirectory(path: String): Unit = {
      def addSubDirectory(p: String, baseDirectory: String) {
        Option(new File(p).listFiles: Seq[File]).getOrElse(Nil).foreach { f =>
          if (f.isDirectory) addSubDirectory(f.getPath, baseDirectory)
          else {
            val stream = new FileInputStream(f)
            try { addEntryFromStream(f.getPath.replace(baseDirectory, ""), stream) } finally { stream.close() }
          }
        }

      }
      addSubDirectory(path, path)
    }

    /** Add an entry to the JAR file from an input stream. If the entry already exists,
      * do not add it. */
    private def addEntryFromStream(entryName: String, is: InputStream) {
      if (!entries.contains(entryName)) {
        entries += entryName
        jos.putNextEntry(new JarEntry(entryName))
        val buffer: Array[Byte] = new Array(1024)
        var readCnt = 0
        while ({readCnt = is.read(buffer); readCnt > 0}) {
          jos.write(buffer, 0, readCnt)
        }
      }
    }

    /** Add entries for an existing JAR file based on some predicate. */
    private def addJarEntries(jarFile: String, p: JarEntry => Boolean) {
      val jis = new JarInputStream(new FileInputStream(jarFile))
      Stream.continually(jis.getNextJarEntry).takeWhile(_ != null) foreach { entry =>
        if (p(entry)) {
          val name: String = entry.getName
          addEntryFromStream(name, jis)
        }
      }
      jis.close()
    }

    def close = jos.close
  }
}
