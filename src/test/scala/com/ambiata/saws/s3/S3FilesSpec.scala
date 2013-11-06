package com.ambiata
package saws
package s3

import com.amazonaws.services.s3.AmazonS3Client
import org.specs2._
import specification._
import matcher._
import java.io._
import java.util.UUID
import scala.io.Source

class S3FilesSpec extends Specification with AfterExample with ThrownExpectations with S3Files { def is = isolated ^ s2"""

 S3 file interactions
 ========================================

 It is possible to
   upload a single file to S3     $e1
   upload multiple files to S3    $e2
   download a file from S3        $e3
   delete a file from S3          $e4
   delete multiple files from S3  $e5
   check existance of file on S3  $e6
                                  """

  val bucket = "ambiata-dist-test"

  // when using "isolated" above, this is a new value per example
  lazy val tmpPath = createTmpPath
  lazy val client = new AmazonS3Client

  def e1 = {
    val tmpFile = createFile("test", "testing")
    val key = s3Key(tmpFile)
    uploadFile(bucket, key, tmpFile).toOption must beSome
    readLines(bucket, key).toEither must beRight(===(Seq("testing")))
  }

  def e2 = {
    val tmpDir = createDir("e2")
    val tmpFile1 = createFile("test1", "testing1", tmpDir)
    val tmpFile2 = createFile("test2", "testing2", tmpDir)
    val key = s3Key(tmpDir)
    uploadFiles(bucket, key, tmpDir).toOption must beSome
    readLines(bucket, s3Key(tmpFile2, key)).toEither must beRight(===(Seq("testing2")))
    readLines(bucket, s3Key(tmpFile1, key)).toEither must beRight(===(Seq("testing1")))
  }

  def e3 = {
    val tmpFile = createFile("test3", "testing")
    val key = s3Key(tmpFile)
    uploadFile(bucket, key, tmpFile).toOption must beSome
    downloadFile(bucket, key).map(Source.fromInputStream(_).getLines.toSeq).toEither must beRight(===(Seq("testing")))
  }

  def e4 = {
    val tmpFile = createFile("test3", "testing3")
    val key = s3Key(tmpFile)
    uploadFile(bucket, key, tmpFile).toOption must beSome
    readLines(bucket, key).toEither must beRight(===(Seq("testing3")))
    deleteFile(bucket, key).toOption must beSome
    downloadFile(bucket, key).toOption must beNone
  }

  def e5 = {
    val tmpDir = createDir("e5")
    val tmpFile1 = createFile("test1", "testing1", tmpDir)
    val tmpFile2 = createFile("test2", "testing2", tmpDir)
    val key = s3Key(tmpDir)
    val key1 = s3Key(tmpFile1, key)
    val key2 = s3Key(tmpFile2, key)
    uploadFiles(bucket, key, tmpDir).toOption must beSome
    readLines(bucket, key1).toEither must beRight(===(Seq("testing1")))
    readLines(bucket, key2).toEither must beRight(===(Seq("testing2")))
    deleteFiles(bucket, s => s == key1).toOption must beSome
    readLines(bucket, key1).toOption must beNone
    readLines(bucket, key2).toEither must beRight(===(Seq("testing2")))
  }

  def e6 = {
    val tmpFile = createFile("test6", "testing6")
    val key = s3Key(tmpFile)
    uploadFile(bucket, key, tmpFile).toOption must beSome
    exists(bucket, key).toEither must beRight(===(true))
    exists(bucket, key + "does_not_exist").toEither must beRight(===(false))
  }

  def s3Key(f: File, base: String = tmpPath.getName): String =
    base + "/" + f.getName

  def createTmpPath(): File = {
    val d = new File("target/CopyToS3Spec." + UUID.randomUUID())
    d.mkdir()
    d
  }

  def createDir(name: String, base: File = tmpPath): File = {
    val d = new File(base, name)
    d.mkdir()
    d
  }

  def createFile(name: String, content: String, base: File = tmpPath): File = {
    val t = new File(base, name)
    val pw = new PrintWriter(t)
    pw.print(content)
    pw.close()
    t
  }

  def after {
    deleteFiles(bucket, (_:String).startsWith(tmpPath.getName))
    deleteDir(tmpPath)
  }

  def deleteDir(d: File) {
    if(d.isDirectory) d.listFiles.foreach(deleteDir) else d.delete
    d.delete
  }

}
