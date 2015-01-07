package com.ambiata.saws.s3

import java.security.MessageDigest

import com.ambiata.disorder._
import com.ambiata.mundane.control._
import com.ambiata.mundane.io._
import com.ambiata.mundane.io.Arbitraries._
import com.ambiata.mundane.io.Temporary._
import com.ambiata.mundane.io.MemoryConversions._
import com.ambiata.saws.core.{AwsSpec => _, _}
import com.ambiata.saws.testing._
import com.ambiata.saws.testing.AwsMatcher._
import com.ambiata.saws.testing.Arbitraries._
import com.ambiata.saws.s3.S3Temporary._
import org.specs2._

import scala.io.{Source, Codec}
import scalaz.{Name =>_,_}, Scalaz._, effect._, effect.Effect._

class S3AddressSpec extends AwsSpec(5) { def is = s2"""

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
  def putString = prop((data: String, s3: S3Temporary) => for {
    a <- s3.address
    _ <- a.put(data)
    e <- a.get
  } yield e ==== data)

  def upload = prop((data: String, s3: S3Temporary, local: LocalTemporary) => for {
    f <- S3Action.fromRIO(local.fileWithContent(data))
    a <- s3.address
    _ <- a.putFile(f)
    e <- a.get
  } yield e ==== data)

  def downloadStream = prop((data: String, s3: S3Temporary, local: LocalTemporary) => for {
    f <- S3Action.fromRIO(local.fileWithContent(data))
    a <- s3.address
    _ <- a.putFile(f)
    e <- a.withStream(is => Streams.read(is, "UTF-8"))
  } yield e ==== data)

  def download = prop((data: String, s3: S3Temporary, local: LocalTemporary) => for {
    a <- s3.address
    f <- S3Action.fromRIO(local.file)
    _ <- a.put(data)
    _ <- a.getFile(f)
    e <- S3Action.fromRIO(Files.exists(f))
    d <- S3Action.fromRIO(Files.read(f))
  } yield e -> d ==== true -> data)

  def downloadTo = prop((data: String, s3: S3Temporary, local: LocalTemporary) => for {
    d <- S3Action.fromRIO(local.directory)
    a <- s3.address
    _ <- a.put(data)
    _ <- a.getFileTo(d)
    e <- S3Action.fromRIO(Files.exists(d </> FilePath.unsafe(a.key)))
    d <- S3Action.fromRIO(Files.read(d </> FilePath.unsafe(a.key)))
  } yield e -> d ==== true -> data)

  def delete = prop((data: String, s3: S3Temporary) => for {
    a <- s3.address
    _ <- a.put(data)
    b <- a.exists
    _ <- a.delete
    e <- a.exists
  } yield b -> e ==== true -> false)


  def exists = prop((data: String, s3: S3Temporary) => for {
    a <- s3.address
    _ <- a.put(data)
    e <- a.exists
  } yield e ==== true)

  def md5 = prop((data: String, s3: S3Temporary) => for {
    a <- s3.address
    _ <- a.put(data)
    d <- a.get
    m <- a.md5
  } yield d -> m ==== data -> md5Hex(data.getBytes("UTF8")))

  def md5Hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("MD5").digest(bytes).map("%02X".format(_)).mkString.toLowerCase

  def copy = prop((data: String, s3: S3Temporary) => for {
    o <- s3.address
    _ <- o.put(data)
    d <- s3.address
    _ <- o.copy(d)
    e <- o.exists
    z <- d.exists
    r <- d.get
  } yield (e, z, r) ==== ((true, true, data)))

  val smallData: List[String] = List.fill(120)("testing")

  val bigData: String = new String({
    val x = new Array[Char](1024*1024*15)
    java.util.Arrays.fill(x, 'x')
    x
  })

  def downloadParts = prop((s3: S3Temporary, local: LocalTemporary) => for {
    a <- s3.address
    f <- S3Action.fromRIO(local.fileWithParent)
    _ <- a.putLines(smallData)
    _ <- S3Action.using(S3Action.fromRIO(f.toOutputStream))(out => a.withStreamMultipart(50.bytes, in => Streams.pipe(in, out), S3.NoTick))
    r <- S3Action.fromRIO(Files.readLines(f))
  } yield r.toList ==== smallData)

  def uploadPartsSmall = prop((s3: S3Temporary, local: LocalTemporary) => for {
    f <- S3Action.fromRIO(local.file)
    _ <- S3Action.fromRIO(Files.writeLines(f, smallData))
    a <- s3.address
    _ <- a.putFileMultiPart(5.mb, f, S3.NoTickX)
    r <- a.getLines
  } yield r ==== smallData)

  def uploadParts = prop((s3: S3Temporary, local: LocalTemporary) => for {
    f <- S3Action.fromRIO(local.file)
    _ <- S3Action.fromRIO(Files.write(f, bigData))
    a <- s3.address
    _ <- a.putFileMultiPart(5.mb, f, S3.NoTickX)
    r <- a.get
  } yield r ==== bigData)

  def fileSizes = prop((data: String, s3: S3Temporary, local: LocalTemporary) => for {
    f <- S3Action.fromRIO(local.file)
    _ <- S3Action.fromRIO(Files.write(f, data))
    s <- S3Action.safe(f.toFile.length)
    a <- s3.address
    _ <- a.putFile(f)
    r <- a.size
  } yield s ==== r)

  def validForCodec(s: String, c: Codec): Boolean =
    new String(s.getBytes(c.name), c.name) == s

  def codecString = prop((c: Codec, s: String, s3: S3Temporary) => validForCodec(s, c) ==> (for {
    a <- s3.address
    _ <- a.putWithEncoding(s, c)
    d <- a.getWithEncoding(c)
  } yield d ==== s))

  def succesfullCommonPrefix = prop((id: Ident, prefix: S3Prefix) => {
    val s3 = prefix / id.value
    s3.removeCommonPrefix(prefix) must_== Some(id.value)
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

  def fromUri = prop((data: String, s3: S3Temporary) => for {
    a <- s3.address
    _ <- a.put(data)
    e <- S3Address.fromUri(s"s3://${a.bucket}/${a.key}")
  } yield e ==== a.some)

}
