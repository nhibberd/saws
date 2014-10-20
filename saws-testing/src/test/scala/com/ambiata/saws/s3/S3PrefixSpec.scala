package com.ambiata.saws.s3

import com.ambiata.saws.core._
import com.ambiata.saws.testing._
import com.ambiata.mundane.testing.ResultTIOMatcher._
import com.ambiata.mundane.io._
import org.specs2._

import scalaz.{Name =>_,_}, Scalaz._, effect._, effect.Effect._

class S3PrefixSpec extends Specification with ScalaCheck { def is = section("aws") ^ s2"""

 S3 file interactions
 ====================

 It is possible to
   upload multiple files to S3         $upload
   download multiple files from S3     $download
   delete multiple files from S3       $delete
   check existence of prefix on S3     $exists
   check existence can fail on S3      $existsFail
   delete all files from a base path   $deletePrefix
   list all files in prefix on S3      $list
   list all S3Prefix's in prefix on S3 $listPrefix
   get list of file sizes              $fileSizes

 Test that we can remove common prefix's from S3Address
 ======================================================

  Can successfully remove common prefix              $succesfullCommonPrefix
  Will return None when there is no common prefix    $noCommonPrefix

 Support functions
 =========================================

  Can retrieve S3Prefix from uri        $fromUri

"""

  val conf = Clients.s3

  def upload =
    TemporaryDirPath.withDirPath(dir => for {
      _ <- Files.write(dir </> FilePath("foo"), "")
      _ <- Files.write(dir </> DirPath("foos") </> FilePath("bar"), "")
      e <- TemporaryS3.withS3Prefix(s3 => for {
        _ <- s3.putFiles(dir).executeT(conf)
        f <- (s3 | "foo").exists.executeT(conf)
        b <- (s3 / "foos" | "bar").exists.executeT(conf)
      } yield f -> b)
    } yield e) must beOkValue (true -> true)

  def download =
    TemporaryDirPath.withDirPath(dir => for {
      e <- TemporaryS3.withS3Prefix(s3 => for {
        _ <- (s3 | "foo").put("").executeT(conf)
        _ <- (s3 / "foos" | "bar").put("").executeT(conf)
        _ <- s3.getFiles(dir).executeT(conf)
        f <- Files.exists(dir </> FilePath("foo"))
        b <- Files.exists(dir </>  DirPath("foos") </> FilePath("bar"))
      } yield f -> b)
    } yield e) must beOkValue (true -> true)

  def delete = TemporaryS3.withS3Prefix(s3 => for {
    _  <- (s3 | "foo").put("").executeT(conf)
    _  <- (s3 / "foos" | "bar").put("").executeT(conf)
    f  <- (s3 | "foo").exists.executeT(conf)
    b  <- (s3 / "foos" | "bar").exists.executeT(conf)
    _  <- s3.delete.executeT(conf)
    df <- (s3 | "foo").exists.executeT(conf)
    db <- (s3 / "foos" | "bar").exists.executeT(conf)
  } yield (f, b, df, db)) must beOkValue (true, true, false, false)

  def exists = TemporaryS3.withS3Prefix(s3 => for {
    _  <- (s3 | "foo").put("").executeT(conf)
    e  <- s3.exists.executeT(conf)
  } yield e) must beOkValue (true)

  def existsFail = TemporaryS3.withS3Prefix(s3 => for {
    e  <- s3.exists.executeT(conf)
  } yield e) must beOkValue (false)

  def deletePrefix = TemporaryS3.withS3Prefix(s3 => for {
    _ <- (s3 | "foo").put("").executeT(conf)
    b <- s3.exists.executeT(conf)
    _ <- s3.delete.executeT(conf)
    e <- s3.exists.executeT(conf)
  } yield b -> e) must beOkValue (true -> false)

  def list = TemporaryS3.withS3Prefix(s3 => for {
    _ <- (s3 | "foo").put("").executeT(conf)
    _ <- (s3 | "foo2").put("").executeT(conf)
    _ <- (s3 / "foos" | "bar").put("").executeT(conf)
    l <- s3.listKeys.executeT(conf)
  } yield s3 -> l) must beOkLike({
    case (a: S3Prefix, b: List[String]) =>
      List("foo", "foo2", "foos/bar").map(s => S3Operations.concat(a.prefix, s)) must_== b
  })

  def listPrefix = TemporaryS3.withS3Prefix(s3 => for {
    _ <- (s3 | "foo").put("").executeT(conf)
    _ <- (s3 | "foo2").put("").executeT(conf)
    _ <- (s3 / "foos" | "bar").put("").executeT(conf)
    l <- s3.listPrefix.executeT(conf)
  } yield s3 -> l) must beOkLike({
    case (a: S3Prefix, b: List[S3Prefix]) =>
      List("foo", "foo2", "foos/bar").map(s => a / s) must_== b
  })

  def fileSizes = TemporaryDirPath.withDirPath(dir => for {
    _  <- Files.write(dir </> FilePath("foo"), "1")
    _  <- Files.write(dir </> DirPath("foos") </> FilePath("bar"), "testing")
    s1 = (dir </> FilePath("foo")).toFile.length()
    s2 = (dir </> DirPath("foos") </> FilePath("bar")).toFile.length()
    s3 <- TemporaryS3.withS3Prefix(s3 => for {
      _ <- s3.putFiles(dir).executeT(conf)
      s <- s3.size.executeT(conf)
    } yield s)
  } yield (s1, s2, s3)) must beOkLike({ case (a: Long, b: Long, s3: Long) => a + b must_== s3 })

  def succesfullCommonPrefix = {
    val s3 = S3Prefix("bucket","a/b/c/d/e")
    val foo = S3Prefix("bucket","a/b/c")
    s3.removeCommonPrefix(foo) must_== Some("d/e")
  }

  def noCommonPrefix = {
    val s3 = S3Prefix("bucket","a/b/c/d/e")
    val foo = S3Prefix("bucket","a/b/e")
    s3.removeCommonPrefix(foo) must_== None
  }

  def fromUri =
    TemporaryS3.withS3Prefix(s3 => for {
      _ <- (s3 | "foo").put("testing").executeT(conf)
      e <- S3Prefix.fromUri(s"s3://${s3.bucket}/${s3.prefix}").executeT(conf)
    } yield s3 -> e) must beOkLike ({ case (s: S3Prefix, z: Option[S3Prefix]) => z must beSome(s) })
}
