package com.ambiata
package saws
package s3

import java.io._
import java.net.URLClassLoader
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import java.util.jar.{JarInputStream, JarEntry, JarOutputStream}
import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.io.Source
import scalaz._, Scalaz._

/**
 * Useful methods to read and write files on S3, including jar files
 */
trait S3Files {
  type EitherStr[A] = String \/ A

  /**
   * upload one file or all the files of a directory to a given bucket/key
   */
  def uploadFiles(bucket: String, key: String, file: File, client: AmazonS3Client = new AmazonS3Client, metadata: ObjectMetadata = S3.ServerSideEncryption): EitherStr[List[PutObjectResult]] =
    if(file.isDirectory) file.listFiles.toList.traverse(f => uploadFiles(bucket, key + "/" + f.getName, f, client, metadata)).map(_.flatten)
    else                 uploadFile(bucket, key, file, client, metadata).map(List(_))

  /**
   * upload one file to a given bucket/key
   */
  def uploadFile(bucket: String, key: String, file: File, client: AmazonS3Client = new AmazonS3Client, metadata: ObjectMetadata = S3.ServerSideEncryption): EitherStr[PutObjectResult] =
    file match {
      case f if(!f.exists)     => s"'${file.getPath}' doesn't exist!".left
      case f if(f.isDirectory) => s"'${file.getPath}' is not a file!".left
      case _                   =>
        try client.putObject((new PutObjectRequest(bucket, key, file).withMetadata(metadata))).right catch { case t: Throwable => t.getMessage.left }
    }

  /**
   * upload one inputStream to a given bucket/key
   */
  def uploadStream(bucket: String, key: String, input: InputStream, client: AmazonS3Client = new AmazonS3Client, metadata: ObjectMetadata = S3.ServerSideEncryption): EitherStr[PutObjectResult] =
    try client.putObject(bucket, key, input, metadata).right catch { case t: Throwable => s"can't upload the stream on $bucket/$key because ${t.getMessage}".left }

  /**
   * @return a file from S3
   */
  def downloadFile(bucket: String, key: String, client: AmazonS3Client = new AmazonS3Client): EitherStr[InputStream] =
    try client.getObject(bucket, key).getObjectContent.right catch { case t: Throwable => s"can't read from $bucket/$key because ${t.getMessage}".left }

  /**
   * @return the lines of a text file on S3
   */
  def readLines(bucket: String, key: String, client: AmazonS3Client = new AmazonS3Client): EitherStr[Seq[String]] =
    downloadFile(bucket, key, client).map(Source.fromInputStream(_).getLines.toSeq)

  /**
   * @return remove a file from S3
   */
  def deleteFile(bucket: String, key: String, client: AmazonS3Client = new AmazonS3Client): EitherStr[String] =
    try   { client.deleteObject(bucket, key); s"deleted $bucket/$key".right }
    catch { case t: Throwable => s"could not delete $bucket/$key: ${t.getMessage}".left }

  /**
   * @return remove all files from a bucket in S3
   */
  def deleteFiles(bucket: String, filter: String => Boolean = (s: String) => true, client: AmazonS3Client = new AmazonS3Client): EitherStr[Seq[String]] =
    client.listObjects(bucket).getObjectSummaries.toList.collect {
      case s if filter(s.getKey) => deleteFile(bucket, s.getKey, client)
    }.sequenceU
}

trait S3Jar extends S3Files {
  /**
   * create and push a jar file to S3
   */
  def pushJar(jarName: String, bucket: String, client: AmazonS3Client = new AmazonS3Client) = {
    val jarFile = new File(s"target/scala-2.10/$jarName")
    try {
      makeJar(jarFile)
      client.putObject(bucket, jarName, jarFile)
      s"pushed the jar file at ${jarFile.getPath} to the S3 bucket/key $bucket/$jarName"
    } catch { case t: Throwable => s"couldn't push the jar file at ${jarFile.getPath} to the S3 bucket/key $bucket/$jarName" }
  }

  /** @return add classes to the jar file */
  def makeJar(jarFile: File) = {
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
  }

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
