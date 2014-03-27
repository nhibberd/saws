package com.ambiata.saws
package s3

import com.amazonaws.services.s3.AmazonS3Client
import com.ambiata.mundane.testing.ResultMatcher._
import com.ambiata.saws.testing._
import com.ambiata.scrutiny.files._
import com.ambiata.scrutiny.files.Dirs._

import java.io._
import java.util.UUID
import java.security.MessageDigest

import org.specs2._, specification._, matcher._

import scala.io.Source
import scalaz._, Scalaz._


class S3Spec extends UnitSpec with AfterExample with ThrownExpectations with LocalFiles { def is = isolated ^ s2"""

 S3 file interactions
 ========================================

 It is possible to
   upload a single file to S3          $e1
   upload multiple files to S3         $e2
   get a stream  from S3               $e3
   download a file from S3             $e4
   delete a file from S3               $e5
   delete multiple files from S3       $e6
   check existance of file on S3       $e7
   get md5 of file from S3             $e8
   copy an object in s3                $e9
   mirror a local directory to a path  $e10
   delete all files from a base path   $e11

 """

  val bucket = "ambiata-dist-test"

  // when using "isolated" above, this is a new value per example
  lazy val basePath = mkRandomDir("S3Spec.", new File(System.getProperty("java.io.tmpdir")))

  def e1 = {
    val tmpFile = createFile("test", "testing")
    val key = s3Key(tmpFile)
    (for {
      _ <- S3.putFile(bucket, key, tmpFile)
      f <- S3.readLines(bucket, key)
    } yield f).eval.unsafePerformIO.toEither must beRight(===(Seq("testing")))
  }

  def e2 = {
    val tmpDir = mkdir("e2", new File(System.getProperty("java.io.tmpdir")))
    val tmpFile1 = createFile("test1", "testing1", tmpDir)
    val tmpFile2 = createFile("test2", "testing2", tmpDir)
    val key = s3Key(tmpDir)
    (for {
      _ <- S3.putFiles(bucket, key, tmpDir)
      f <- S3.readLines(bucket, s3Key(tmpFile1, key))
      s <- S3.readLines(bucket, s3Key(tmpFile2, key))
    } yield (f, s)).eval.unsafePerformIO.toEither must beRight(===((Seq("testing1"), Seq("testing2"))))
  }

  def e3 = {
    val tmpFile = createFile("test3", "testing")
    val key = s3Key(tmpFile)
    (for {
      _ <- S3.putFile(bucket, key, tmpFile)
      f <- S3.withStreamUnsafe(bucket, key, is => Source.fromInputStream(is).getLines.toList)
    } yield f).eval.unsafePerformIO.toEither must beRight(===(List("testing")))
  }


  def e4 = {
    val tmpFile = createFile("test3", "testing")
    val key = s3Key(tmpFile)
    (for {
      _ <- S3.putFile(bucket, key, tmpFile)
      f <- S3.downloadFile(bucket, key, "target")
    } yield f).eval.unsafePerformIO.toEither must beRight(endWith("test3") ^^ ((_:File).getName))
  }

  def e5 = {
    val tmpFile = createFile("test3", "testing3")
    val key = s3Key(tmpFile)
    (for {
      _ <- S3.putFile(bucket, key, tmpFile)
      k2 <- S3.listKeys(bucket, key)
      f <- S3.readLines(bucket, key)
      _ <- S3.deleteObject(bucket, key)
    } yield f).eval.unsafePerformIO.toEither must beRight(===(Seq("testing3")))
    S3.getObject(bucket, key).eval.unsafePerformIO.toEither must beLeft
  }

  def e6 = {
    val tmpDir = mkdir("e6", new File(System.getProperty("java.io.tmpdir")))
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
    } yield (f, s)).eval.unsafePerformIO.toEither must beRight(===((Seq("testing1")), Seq("testing2")))

    S3.readLines(bucket, key1).eval.unsafePerformIO.toEither must beLeft
    S3.readLines(bucket, key2).eval.unsafePerformIO.toEither must beRight(===(Seq("testing2")))
  }

  def e7 = {
    val tmpFile = createFile("test6", "testing6")
    val key = s3Key(tmpFile)
    (for {
      _  <- S3.putFile(bucket, key, tmpFile)
      e1 <- S3.exists(bucket, key)
      e2 <- S3.exists(bucket, key + "does_not_exist")
    } yield (e1, e2)).eval.unsafePerformIO.toEither must beRight(===((true, false)))
  }

  def e8 = {
    val tmpFile = createFile("test7", "testing7")
    val key = s3Key(tmpFile)
    (for {
      _ <- S3.putFile(bucket, key, tmpFile)
      m <- S3.md5(bucket, key)
    } yield m).eval.unsafePerformIO.toEither must beRight(===(md5Hex("testing7".getBytes)))
  }

  def e9 = {
    val tmpFileFrom = createFile("test8", "testing8")
    val keyFrom = s3Key(tmpFileFrom)
    val keyTo = "test8copy"
    (for {
      _ <- S3.putFile(bucket, keyFrom, tmpFileFrom)
      _ <- S3.copyFile(bucket, keyFrom, bucket, keyTo)
      f <- S3.withStreamUnsafe(bucket, keyTo, is => Source.fromInputStream(is).getLines.toList)
    } yield f).eval.unsafePerformIO.toEither must beRight(===(List("testing8")))
  }

  def mirror(test: String, remote: String, paths: List[String]) = {
    paths.foreach(path => createFile(s"${test}/${path}", s"content:${test}/${path}"))
    S3.mirror(new File(basePath, s"${test}"), bucket, remote)
  }

  def e10 = {
    val remote = s"${basePath.getName}/mirror"
    val paths = List("1", "2", "nested/3")
    val action = mirror("test10", remote, paths) >> S3.listKeys(bucket, remote)
    action.eval.unsafePerformIO must beOkValue(paths.map(path => s"${remote}/${path}"))
  }

  def e11 = {
    val remote = s"${basePath.getName}/mirror"
    val paths = List("1", "2", "3", "4/5/6")
    val action = mirror("test11", remote, paths) >> S3.deleteAll(bucket, remote) >> S3.listKeys(bucket, remote)
    action.eval.unsafePerformIO must beOkValue(Nil)
  }

  def md5Hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("MD5").digest(bytes).map("%02X".format(_)).mkString.toLowerCase

  def s3Key(f: File, base: String = basePath.getName): String =
    base + "/" + f.getName

  def after {
    S3.deleteObjects(bucket, (_:String).startsWith(basePath.getName)).eval.unsafePerformIO
    rmdir(basePath)
  }
}
