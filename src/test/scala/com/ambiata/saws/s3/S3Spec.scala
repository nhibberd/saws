package com.ambiata
package saws
package s3

import com.amazonaws.services.s3.AmazonS3Client
import org.specs2._
import specification._
import matcher._
import java.io._
import java.util.UUID
import java.security.MessageDigest
import scala.io.Source
import com.ambiata.scrutiny.files.LocalFiles
import testing._

class S3Spec extends UnitSpec with AfterExample with ThrownExpectations with LocalFiles { def is = isolated ^ s2"""

 S3 file interactions
 ========================================

 It is possible to
   upload a single file to S3     $e1
   upload multiple files to S3    $e2
   download a file from S3        $e3
   delete a file from S3          $e4
   delete multiple files from S3  $e5
   check existance of file on S3  $e6
   get md5 of file from S3        $e7
   copy an object in s3           $e8
                                  """

  val bucket = "ambiata-dist-test"

  // when using "isolated" above, this is a new value per example
  lazy val basePath = mkRandomDir("CopyToS3Spec.")
  lazy val client = new AmazonS3Client

  def e1 = {
    val tmpFile = createFile("test", "testing")
    val key = s3Key(tmpFile)
    (for {
      _ <- S3.putFile(bucket, key, tmpFile)
      f <- S3.readLines(bucket, key)
    } yield f).run(client)._2.toEither must beRight(===(Seq("testing")))
  }

  def e2 = {
    val tmpDir = mkdir("e2")
    val tmpFile1 = createFile("test1", "testing1", tmpDir)
    val tmpFile2 = createFile("test2", "testing2", tmpDir)
    val key = s3Key(tmpDir)
    (for {
      _ <- S3.putFiles(bucket, key, tmpDir)
      f <- S3.readLines(bucket, s3Key(tmpFile1, key))
      s <- S3.readLines(bucket, s3Key(tmpFile2, key))
    } yield (f, s)).executeS3.toEither must beRight(===((Seq("testing1"), Seq("testing2"))))
  }

  def e3 = {
    val tmpFile = createFile("test3", "testing")
    val key = s3Key(tmpFile)
    (for {
      _ <- S3.putFile(bucket, key, tmpFile)
      f <- S3.withStream(bucket, key, is => Source.fromInputStream(is).getLines.toList)
    } yield f).run(client)._2.toEither must beRight(===(List("testing")))
  }

  def e4 = {
    val tmpFile = createFile("test3", "testing3")
    val key = s3Key(tmpFile)
    (for {
      _ <- S3.putFile(bucket, key, tmpFile)
      f <- S3.readLines(bucket, key)
      _ <- S3.deleteObject(bucket, key)
    } yield f).executeS3.toEither must beRight(===(Seq("testing3")))
    S3.getObject(bucket, key).executeS3.toEither must beLeft
  }

  def e5 = {
    val tmpDir = mkdir("e5")
    val tmpFile1 = createFile("test1", "testing1", tmpDir)
    val tmpFile2 = createFile("test2", "testing2", tmpDir)
    val key = s3Key(tmpDir)
    val key1 = s3Key(tmpFile1, key)
    val key2 = s3Key(tmpFile2, key)
    (for {
      _ <- S3.putFiles(bucket, key, tmpDir)
      f <- S3.readLines(bucket, key1)
      s <- S3.readLines(bucket, key2)
      _ <- S3.deleteObjects(bucket, s => s == key1)
    } yield (f, s)).executeS3.toEither must beRight(===((Seq("testing1")), Seq("testing2")))

    S3.readLines(bucket, key1).executeS3.toEither must beLeft
    S3.readLines(bucket, key2).executeS3.toEither must beRight(===(Seq("testing2")))
  }

  def e6 = {
    val tmpFile = createFile("test6", "testing6")
    val key = s3Key(tmpFile)
    (for {
      _  <- S3.putFile(bucket, key, tmpFile)
      e1 <- S3.exists(bucket, key)
      e2 <- S3.exists(bucket, key + "does_not_exist")
    } yield (e1, e2)).executeS3.toEither must beRight(===((true, false)))
  }

  def e7 = {
    val tmpFile = createFile("test7", "testing7")
    val key = s3Key(tmpFile)
    (for {
      _ <- S3.putFile(bucket, key, tmpFile)
      m <- S3.md5(bucket, key)
    } yield m).executeS3.toEither must beRight(===(md5Hex("testing7".getBytes)))
  }

  def e8 = {
    val tmpFileFrom = createFile("test8", "testing8")
    val keyFrom = s3Key(tmpFileFrom)
    val keyTo = "test8copy"
    (for {
      _ <- S3.putFile(bucket, keyFrom, tmpFileFrom)
      _ <- S3.copyFile(bucket, keyFrom, bucket, keyTo)
      f <- S3.withStream(bucket, keyTo, is => Source.fromInputStream(is).getLines.toList)
    } yield f).run(client)._2.toEither must beRight(===(List("testing8")))
  }

  def md5Hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("MD5").digest(bytes).map("%02X".format(_)).mkString.toLowerCase

  def s3Key(f: File, base: String = basePath.getName): String =
    base + "/" + f.getName

  def after {
    S3.deleteObjects(bucket, (_:String).startsWith(basePath.getName)).executeS3
    rmdir(basePath)
  }
}
