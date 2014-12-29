package com.ambiata.saws.s3

import java.security.MessageDigest

import com.ambiata.mundane.control._
import com.ambiata.saws.core._
import com.ambiata.saws.testing._
import com.ambiata.saws.testing.Arbitraries._
import com.ambiata.mundane.testing.RIOMatcher._
import com.ambiata.mundane.io._
import com.ambiata.mundane.io.MemoryConversions._
import org.specs2._
import org.specs2.matcher.Parameters

import scala.io.{Source, Codec}
import scalaz.{Name =>_,_}, Scalaz._, effect._, effect.Effect._

class S3AddressSpec extends Specification with ScalaCheck { def is = section("aws") ^ s2"""

 S3 file interactions
 ========================================

 It is possible to
   write string to S3                  $putString
   upload a single file to S3          $upload
   get a stream  from S3               $downloadStream
   download a file from S3             $download
   download a file from S3 to file     $downloadTo
   delete a file from S3               $delete
   check existance of file on S3       $exists
   get md5 of file from S3             $md5
   copy an object in s3                $copy
   download a file from S3 in parts    $downloadParts
   upload a small file to S3           $uploadPartsSmall
   upload a file to S3 in parts        $uploadParts
   get file size from S3               $fileSizes
   read and write string with codec    $codecString

 Test that we can remove common prefix's from S3Address
 ======================================================

  Can successfully remove common prefix              $succesfullCommonPrefix
  Will return None when there is no common prefix    $noCommonPrefix
  Will return None when there is no common bucket    $differentBuckets

 Support functions
 ================

  Can retrieve S3Address from uri                                                                            $fromUri
  A range of longs from 0 to size can be partitioned in n ranges of size m (the last range might be shorter) $partition

"""

  override implicit def defaultParameters: Parameters =
    new Parameters(minTestsOk = 5, workers = 3)

  val conf = Clients.s3

  def putString = prop((data: String, address: S3Address) => {
    TemporaryS3.runWithS3Address(address)(s3 => for {
      _ <- s3.put(data).execute(conf)
      e <- s3.get.execute(conf)
    } yield e) must beOkValue (data)
  })

  def upload = prop((data: String, address: S3Address) => {
    TemporaryFilePath.withFilePath(f => for {
      _ <- Files.write(f, data)
      e <- TemporaryS3.runWithS3Address(address)(s3 => for {
        _ <- s3.putFile(f).execute(conf)
        e <- s3.get.execute(conf)
      } yield e)
    } yield e) must beOkValue (data)
  })

  def downloadStream = prop((data: String, address: S3Address) => {
    TemporaryFilePath.withFilePath(f => for {
      _ <- Files.write(f, data)
      e <- TemporaryS3.runWithS3Address(address)(s3 => for {
        _ <- s3.putFile(f).execute(conf)
        e <- s3.withStream(is => Streams.read(is, "UTF-8")).execute(conf)
      } yield e)
    } yield e) must beOkValue (data)
  })

  def download = prop((data: String, address: S3Address) =>
    TemporaryFilePath.withFilePath(f => for {
      _ <- TemporaryS3.runWithS3Address(address)(s3 => for {
        _ <- s3.put(data).execute(conf)
        _ <- s3.getFile(f).execute(conf)
      } yield ())
      e <- Files.exists(f)
      d <- Files.read(f)
    } yield e -> d) must beOkValue (true -> data))

  def downloadTo = prop((data: String, address: S3Address) =>
    TemporaryDirPath.withDirPath(dir => for {
      r <- TemporaryS3.runWithS3Address(address)(s3 => for {
        _ <- s3.put(data).execute(conf)
        _ <- s3.getFileTo(dir).execute(conf)
        e <- Files.exists(dir </> FilePath.unsafe(s3.key))
        d <- Files.read(dir </> FilePath.unsafe(s3.key))
      } yield e -> d)
    } yield r) must beOkValue (true -> data))

  def delete = prop((data: String, address: S3Address) =>
    TemporaryS3.runWithS3Address(address)(s3 => for {
      _ <- s3.put(data).execute(conf)
      e <- s3.exists.execute(conf)
      _ <- s3.delete.execute(conf)
      d <- s3.exists.execute(conf)
    } yield e -> d) must beOkValue (true -> false))

  def exists = prop((data: String, address: S3Address) =>
    TemporaryS3.runWithS3Address(address)(s3 => for {
      _ <- s3.put(data).execute(conf)
      e <- s3.exists.execute(conf)
    } yield e) must beOkValue (true))

  def md5 = prop((data: String, address: S3Address) =>
    TemporaryS3.runWithS3Address(address)(s3 => for {
      _ <- s3.put(data).execute(conf)
      d <- s3.get.execute(conf)
      m <- s3.md5.execute(conf)
    } yield d -> m) must beOkValue (data -> md5Hex(data.getBytes("UTF8"))))

  def md5Hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("MD5").digest(bytes).map("%02X".format(_)).mkString.toLowerCase

  def copy = prop((data: String, address: S3Address, to: S3Address) =>
    TemporaryS3.runWithS3Address(address)(origin => for {
      _ <- origin.put(data).execute(conf)
      r <- TemporaryS3.runWithS3Address(to)(s3 => for {
        _ <- origin.copy(s3).execute(conf)
        o <- origin.exists.execute(conf)
        e <- s3.exists.execute(conf)
        d <- s3.get.execute(conf)
      } yield (o, e, d))
    } yield r) must beOkValue (true, true, data))

  val smallData: List[String] = List.fill(120)("testing")

  val bigData: String = new String({
    val x = new Array[Char](1024*1024*15)
    java.util.Arrays.fill(x, 'x')
    x
  })

  def downloadParts = prop((address: S3Address) =>
    TemporaryFilePath.withFilePath(f => for {
      _ <- TemporaryS3.runWithS3Address(address)(s3 => for {
        _ <- s3.putLines(smallData).execute(conf)
        _ <- RIO.using(f.toOutputStream)( out => s3.withStreamMultipart(50.bytes, in => Streams.pipe(in, out), S3.NoTick).execute(conf))
      } yield ())
      e <- Files.readLines(f)
    } yield e.toList) must beOkValue(smallData))

  def uploadPartsSmall = prop((address: S3Address) =>
    TemporaryFilePath.withFilePath(f => for {
      _ <- Files.writeLines(f, smallData)
      s <- TemporaryS3.runWithS3Address(address)(s3 => for {
        _  <- s3.putFileMultiPart(5.mb, f, S3.NoTickX).execute(conf)
        z <- s3.getLines.execute(conf)
      } yield z)
    } yield s) must beOkValue(smallData))

  def uploadParts = prop((address: S3Address) =>
    TemporaryFilePath.withFilePath(f => for {
      _ <- Files.write(f, bigData)
      s <- TemporaryS3.runWithS3Address(address)(s3 => for {
        _  <- s3.putFileMultiPart(5.mb, f, S3.NoTickX).execute(conf)
        z <- s3.get.execute(conf)
      } yield z)
    } yield s) must beOkValue(bigData))

  def fileSizes = prop((data: String, address: S3Address) =>
    TemporaryFilePath.withFilePath(f => for {
      _ <- Files.write(f, data)
      s = f.toFile.length()
      e <- TemporaryS3.runWithS3Address(address)(s3 => for {
        _ <- s3.putFile(f).execute(conf)
        e <- s3.size.execute(conf)
      } yield e)
    } yield s -> e) must beOkLike({ case (a, b) => a must_== b }))

  def codecString = prop((c: Codec, s: String) => validForCodec(s, c) ==> {
    TemporaryS3.withS3Address(s3 => for {
      _ <- s3.putWithEncoding(s, c).execute(conf)
      d <- s3.getWithEncoding(c).execute(conf)
    } yield d) must beOkValue(s) }
  )

  def validForCodec(s: String, c: Codec): Boolean =
    new String(s.getBytes(c.name), c.name) == s

  def succesfullCommonPrefix = prop((key: String, prefix: S3Prefix) => {
    val s3 = prefix / key
    s3.removeCommonPrefix(prefix) must_== Some(key)
  })

  def noCommonPrefix = {
    val s3 = S3Address("bucket","a/b/c/d/e")
    val foo = S3Prefix("bucket","a/b/e")
    s3.removeCommonPrefix(foo) must_== None
  }
  def differentBuckets = {
    val s3 = S3Address("bucket","a")
    val foo = S3Prefix("buckset","")
    s3.removeCommonPrefix(foo) must_== None
  }

  def partition = {
    S3Address.partition(10, 5) must_== Seq((0, 4), (5, 9))
    S3Address.partition(10, 4) must_== Seq((0, 3), (4, 7), (8, 9))
  }

  def fromUri = prop((address: S3Address) =>
    TemporaryS3.runWithS3Address(address)(s3 => for {
      _ <- s3.put("testing").execute(conf)
      e <- S3Address.fromUri(s"s3://${s3.bucket}/${s3.key}").execute(conf)
    } yield s3 -> e) must beOkLike ({ case (s: S3Address, z: Option[S3Address]) => z must beSome(s) }))

}
