package com.ambiata.saws.s3

import java.security.MessageDigest

import com.ambiata.mundane.control._
import com.ambiata.saws.core._
import com.ambiata.saws.testing._
import com.ambiata.saws.testing.Arbitraries._
import com.ambiata.mundane.testing.ResultTIOMatcher._
import com.ambiata.mundane.io._
import com.ambiata.mundane.io.MemoryConversions._
import org.specs2._

import scala.io.{Source, Codec}
import scalaz.{Name =>_,_}, Scalaz._, effect._, effect.Effect._

class S3AddressSpec extends Specification with ScalaCheck { def is = s2"""

 S3 file interactions
 ========================================

 It is possible to
   write string to S3                  $putString              ${tag("aws")}
   upload a single file to S3          $upload                 ${tag("aws")}
   get a stream  from S3               $downloadStream         ${tag("aws")}
   download a file from S3             $download               ${tag("aws")}
   download a file from S3 to file     $downloadTo             ${tag("aws")}
   delete a file from S3               $delete                 ${tag("aws")}
   check existance of file on S3       $exists                 ${tag("aws")}
   get md5 of file from S3             $md5                    ${tag("aws")}
   copy an object in s3                $copy                   ${tag("aws")}
   download a file from S3 in parts    $downloadParts          ${tag("aws")}
   upload a small file to S3           $uploadPartsSmall       ${tag("aws")}
   upload a file to S3 in parts        $uploadParts            ${tag("aws")}
   get file size from S3               $fileSizes              ${tag("aws")}
   read and write string with codec    $codecString            ${tag("aws")}

 Test that we can remove common prefix's from S3Address
 ======================================================

  Can successfully remove common prefix              $succesfullCommonPrefix         ${tag("aws")}
  Will return None when there is no common prefix    $noCommonPrefix                 ${tag("aws")}
  Can handle a bucket as the common prefix           $bucketPrefix                   ${tag("aws")}
  Will return None when there is no common bucket    $differentBuckets               ${tag("aws")}

 Support functions
 ================

  Can retrieve S3Address from uri                                                                            $fromUri     ${tag("aws")}
  A range of longs from 0 to size can be partitioned in n ranges of size m (the last range might be shorter) $partition

"""

  val conf = Clients.s3

  def putString = {
    TemporaryS3.withS3Address(s3 => for {
      _ <- s3.put("test string").executeT(conf)
      e <- s3.get.executeT(conf)
    } yield e) must beOkValue ("test string")
  }

  def upload = {
    TemporaryFilePath.withFilePath(f => for {
      _ <- Files.write(f, "testing")
      e <- TemporaryS3.withS3Address(s3 => for {
        _ <- s3.putFile(f).executeT(conf)
        e <- s3.get.executeT(conf)
      } yield e)
    } yield e) must beOkValue ("testing")
  }

  def downloadStream = {
    TemporaryFilePath.withFilePath(f => for {
      _ <- Files.write(f, "testing")
      e <- TemporaryS3.withS3Address(s3 => for {
        _ <- s3.putFile(f).executeT(conf)
        e <- s3.withStreamUnsafe(is =>
          Source.fromInputStream(is).getLines.toList).executeT(conf)
      } yield e)
    } yield e) must beOkValue (List("testing"))
  }

  def download =
    TemporaryFilePath.withFilePath(f => for {
      _ <- TemporaryS3.withS3Address(s3 => for {
        _ <- s3.put("testing").executeT(conf)
        _ <- s3.getFile(f).executeT(conf)
      } yield ())
      e <- Files.exists(f)
      d <- Files.read(f)
    } yield e -> d) must beOkValue (true -> "testing")

  def downloadTo =
    TemporaryDirPath.withDirPath(dir => for {
      r <- TemporaryS3.withS3Address(s3 => for {
        _ <- s3.put("testing").executeT(conf)
        _ <- s3.getFileTo(dir).executeT(conf)
        e <- Files.exists(dir </> FilePath.unsafe(s3.key))
        d <- Files.read(dir </> FilePath.unsafe(s3.key))
      } yield e -> d)
    } yield r) must beOkValue (true -> "testing")

  def delete =
    TemporaryS3.withS3Address(s3 => for {
      _ <- s3.put("testing").executeT(conf)
      e <- s3.exists.executeT(conf)
      _ <- s3.delete.executeT(conf)
      d <- s3.exists.executeT(conf)
    } yield e -> d) must beOkValue (true -> false)

  def exists =
    TemporaryS3.withS3Address(s3 => for {
      _ <- s3.put("testing").executeT(conf)
      e <- s3.exists.executeT(conf)
    } yield e) must beOkValue (true)


  def md5 =
    TemporaryS3.withS3Address(s3 => for {
      _ <- s3.put("testing").executeT(conf)
      m <- s3.md5.executeT(conf)
    } yield m) must beOkValue (md5Hex("testing".getBytes))

  def md5Hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("MD5").digest(bytes).map("%02X".format(_)).mkString.toLowerCase

  def copy =
    TemporaryS3.withS3Address(origin => for {
      _ <- origin.put("testing").executeT(conf)
      r <- TemporaryS3.withS3Address(s3 => for {
        _ <- origin.copy(s3).executeT(conf)
        o <- origin.exists.executeT(conf)
        e <- s3.exists.executeT(conf)
        d <- s3.get.executeT(conf)
      } yield (o, e, d))
    } yield r) must beOkValue (true, true, "testing")

  val smallData: List[String] = List.fill(120)("testing")
  val bigData: String = new String({
    val x = new Array[Char](1024*1024*15)
    java.util.Arrays.fill(x, 'x')
    x
  })

  def downloadParts =
    TemporaryFilePath.withFilePath(f => for {
      _ <- TemporaryS3.withS3Address(s3 => for {
        _ <- s3.putLines(smallData).executeT(conf)
        _ <- ResultT.using(f.toOutputStream)( out => s3.withStreamMultipart(50.bytes, in => Streams.pipe(in, out), S3.NoTick).executeT(conf))
      } yield ())
      e <- Files.readLines(f)
    } yield e.toList) must beOkValue(smallData)

  def uploadPartsSmall =
    TemporaryFilePath.withFilePath(f => for {
      _ <- Files.writeLines(f, smallData)
      s <- TemporaryS3.withS3Address(s3 => for {
        _  <- s3.putFileMultiPart(5.mb, f, S3.NoTick).executeT(conf)
        z <- s3.getLines.executeT(conf)
      } yield z)
    } yield s) must beOkValue(smallData)

  def uploadParts =
    TemporaryFilePath.withFilePath(f => for {
      _ <- Files.write(f, bigData)
      s <- TemporaryS3.withS3Address(s3 => for {
        _  <- s3.putFileMultiPart(5.mb, f, S3.NoTick).executeT(conf)
        z <- s3.get.executeT(conf)
      } yield z)
    } yield s) must beOkValue(bigData)

  def fileSizes =
    TemporaryFilePath.withFilePath(f => for {
      _ <- Files.write(f, "testing")
      s = f.toFile.length()
      e <- TemporaryS3.withS3Address(s3 => for {
        _ <- s3.putFile(f).executeT(conf)
        e <- s3.size.executeT(conf)
      } yield e)
    } yield s -> e) must beOkLike({ case (a, b) => a must_== b })

  def codecString = prop((c: Codec, s: String) => validForCodec(s, c) ==> {
    TemporaryS3.withS3Address(s3 => for {
      _ <- s3.putWithEncoding(s, c).executeT(conf)
      d <- s3.getWithEncoding(c).executeT(conf)
    } yield d) must beOkValue(s) }
  )

  def validForCodec(s: String, c: Codec): Boolean =
    new String(s.getBytes(c.name), c.name) == s

  def succesfullCommonPrefix = {
    val s3 = S3Address("bucket","a/b/c/d/e")
    val foo = S3Address("bucket","a/b/c")
    s3.removeCommonPrefix(foo) must_== Some("d/e")
  }

  def noCommonPrefix = {
    val s3 = S3Address("bucket","a/b/c/d/e")
    val foo = S3Address("bucket","a/b/e")
    s3.removeCommonPrefix(foo) must_== None
  }

  def bucketPrefix = {
    val s3 = S3Address("bucket","a")
    val foo = S3Address("bucket","")
    s3.removeCommonPrefix(foo) must_== Some("a")
  }

  def differentBuckets = {
    val s3 = S3Address("bucket","a")
    val foo = S3Address("buckset","")
    s3.removeCommonPrefix(foo) must_== None
  }

  def partition = {
    S3Address.partition(10, 5) must_== Seq((0, 4), (5, 9))
    S3Address.partition(10, 4) must_== Seq((0, 3), (4, 7), (8, 9))
  }

  def fromUri =
    TemporaryS3.withS3Address(s3 => for {
      _ <- s3.put("testing").executeT(conf)
      e <- S3Address.fromUri(s"s3://${s3.bucket}/${s3.key}").executeT(conf)
    } yield s3 -> e) must beOkLike ({ case (s: S3Address, z: Option[S3Address]) => z must beSome(s) })

}
