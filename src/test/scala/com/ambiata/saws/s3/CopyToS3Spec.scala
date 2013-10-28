package com.ambiata
package saws
package s3

import com.amazonaws.services.s3.AmazonS3Client
import org.specs2._
import specification._
import matcher._
import java.io._
import java.util.UUID

class CopyToS3Spec extends Specification with AfterExample with ThrownExpectations with CopyToS3 { def is = isolated ^ s2"""

 Copying files/dirs from local disk to S3
 ========================================

 It is possible to
   copy a single file to S3  $e1
   copy multiple files to S3 $e2
                             """

  val bucket = "ambiata-dist-test"

  // when using "isolated" above, this is a new value per example
  lazy val tmpPath = createTmpPath
  lazy val client = new AmazonS3Client

  def e1 = {
    val tmpFile = createFile("test", "testing")
    val key = s3Key(tmpFile)
    val res = copyFiles(Array("--local", tmpFile.getPath, "--bucket", bucket, "--key", key))
    res.toOption must beSome
    readLines(bucket, key).toEither must beRight(===(Seq("testing")))
  }

  def e2 = {
    val tmpDir = createDir("e2")
    val tmpFile1 = createFile("test1", "testing1", tmpDir)
    val tmpFile2 = createFile("test2", "testing2", tmpDir)
    val key = s3Key(tmpDir)
    val res = copyFiles(Array("--local", tmpDir.getPath, "--bucket", bucket, "--key", key))
    res.toOption must beSome
    readLines(bucket, s3Key(tmpFile2, key)).toEither must beRight(===(Seq("testing2")))
    readLines(bucket, s3Key(tmpFile1, key)).toEither must beRight(===(Seq("testing1")))
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
