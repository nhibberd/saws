package com.ambiata.saws.s3

import com.ambiata.saws.core._
import com.ambiata.saws.testing._
import com.ambiata.saws.testing.Arbitraries._
import com.ambiata.mundane.testing.RIOMatcher._
import com.ambiata.mundane.io._
import org.specs2._
import org.specs2.matcher.Parameters

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

  override implicit def defaultParameters: Parameters =
    new Parameters(minTestsOk = 3, workers = 3)

  val conf = Clients.s3

  def upload = prop((prefix: S3Prefix, data: String, one: S3Address, two: S3Address) =>
    TemporaryDirPath.withDirPath(dir => for {
      _ <- Files.write(dir </> FilePath.unsafe(one.key), data)
      _ <- Files.write(dir </> FilePath.unsafe(two.key), data)
      e <- TemporaryS3.runWithS3Prefix(prefix)(s3 => for {
        _ <- s3.putFiles(dir).execute(conf)
        f <- (s3 | one.key).exists.execute(conf)
        b <- (s3 | two.key).exists.execute(conf)
      } yield f -> b)
    } yield e) must beOkValue (true -> true))

  def download = prop((dataOne: String, dataTwo: String, prefix: S3Prefix, one: S3Prefix, two: S3Prefix) =>
    TemporaryDirPath.withDirPath(dir => for {
      e <- TemporaryS3.withS3Prefix(s3 => for {
        _ <- (s3 | one.prefix).put(dataOne).execute(conf)
        _ <- (s3 | two.prefix).put(dataTwo).execute(conf)
        _ <- s3.getFiles(dir).execute(conf)
        o <- Files.read(dir </> FilePath.unsafe(one.prefix))
        t <- Files.read(dir </> FilePath.unsafe(two.prefix))
      } yield o -> t)
    } yield e) must beOkLike ({ case (a, b) => { a must_== dataOne; b must_== dataTwo }  }))

  def delete = prop((prefix: S3Prefix, one: S3Prefix, two: S3Prefix) =>
    TemporaryS3.withS3Prefix(s3 => for {
      _  <- (s3 | one.prefix).put("").execute(conf)
      _  <- (s3 | two.prefix).put("").execute(conf)
      f  <- (s3 | one.prefix).exists.execute(conf)
      b  <- (s3 | two.prefix).exists.execute(conf)
      _  <- s3.delete.execute(conf)
      df <- (s3 | one.prefix).exists.execute(conf)
      db <- (s3 | two.prefix).exists.execute(conf)
    } yield (f, b, df, db)) must beOkValue (true, true, false, false))

  def exists = prop((prefix: S3Prefix, key: String, data: String) =>
    TemporaryS3.runWithS3Prefix(prefix)(s3 => for {
      _  <- (s3 | key).put(data).execute(conf)
      e  <- s3.exists.execute(conf)
    } yield e) must beOkValue (true))

  def existsFail = prop((prefix: S3Prefix) =>
    TemporaryS3.runWithS3Prefix(prefix)(s3 => for {
      e  <- s3.exists.execute(conf)
    } yield e) must beOkValue (false))

  def deletePrefix = prop((prefix: S3Prefix, key: String, data:String) =>
    TemporaryS3.runWithS3Prefix(prefix)(s3 => for {
      _ <- (s3 | key).put(data).execute(conf)
      b <- s3.exists.execute(conf)
      _ <- s3.delete.execute(conf)
      e <- s3.exists.execute(conf)
    } yield b -> e) must beOkValue (true -> false))

  def list = prop((prefix: S3Prefix, one: S3Address, two: S3Address) => (one.key != two.key) ==> {
    TemporaryS3.runWithS3Prefix(prefix)(s3 => for {
      _ <- (s3 | one.key).put("").execute(conf)
      _ <- (s3 | two.key).put("").execute(conf)
      l <- s3.listKeys.execute(conf)
    } yield s3 -> l) must beOkLike({
      case (a: S3Prefix, b: List[String]) =>
        List(one.key, two.key).map(s => S3Operations.concat(a.prefix, s)).toSet must_== b.toSet
    })
  })

  def listPrefix = prop((prefix: S3Prefix, one: S3Prefix, two: S3Prefix) => (one.prefix != two.prefix) ==> {
    TemporaryS3.withS3Prefix(s3 => for {
      _ <- (s3 | one.prefix).put("").execute(conf)
      _ <- (s3 | two.prefix).put("").execute(conf)
      l <- s3.listPrefix.execute(conf)
    } yield s3 -> l) must beOkLike({
      case (a: S3Prefix, b: List[S3Prefix]) =>
        List(one.prefix, two.prefix).map(s => a / s).toSet must_== b.toSet
    })
  })

  def fileSizes = TemporaryDirPath.withDirPath(dir => for {
    _  <- Files.write(dir </> FilePath("foo"), "1")
    _  <- Files.write(dir </> DirPath("foos") </> FilePath("bar"), "testing")
    s1 = (dir </> FilePath("foo")).toFile.length()
    s2 = (dir </> DirPath("foos") </> FilePath("bar")).toFile.length()
    s3 <- TemporaryS3.withS3Prefix(s3 => for {
      _ <- s3.putFiles(dir).execute(conf)
      s <- s3.size.execute(conf)
    } yield s)
  } yield (s1, s2, s3)) must beOkLike({ case (a: Long, b: Long, s3: Long) => a + b must_== s3 })

  def succesfullCommonPrefix = prop((prefix: S3Prefix, key: String) => {
    val s3 = prefix | key
    s3.removeCommonPrefix(prefix) must_== Some(key)
  })

  def noCommonPrefix = {
    val s3 = S3Prefix("bucket","a/b/c/d/e")
    val foo = S3Prefix("bucket","a/b/e")
    s3.removeCommonPrefix(foo) must_== None
  }

  def fromUri = prop((prefix: S3Prefix, key: String, data: String) => (key.length != 0) ==> {
    TemporaryS3.runWithS3Prefix(prefix)(s3 => for {
      _ <- (s3 | key).put(data).execute(conf)
      e <- S3Prefix.fromUri(s"s3://${prefix.bucket}/${prefix.prefix}").execute(conf)
    } yield s3 -> e) must beOkLike({ case (s: S3Prefix, z: Option[S3Prefix]) => z must beSome(s)})
  })
}
