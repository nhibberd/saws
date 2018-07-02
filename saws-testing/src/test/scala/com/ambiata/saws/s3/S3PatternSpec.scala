package com.ambiata.saws.s3

import com.ambiata.disorder._
import com.ambiata.saws.core._
import com.ambiata.saws.testing._
import com.ambiata.saws.testing.Arbitraries._
import com.ambiata.saws.testing.AwsMatcher._
import com.ambiata.saws.s3.S3Temporary._
import com.ambiata.mundane.control._
import com.ambiata.mundane.data._
import com.ambiata.mundane.io._
import com.ambiata.mundane.io.Arbitraries._
import com.ambiata.mundane.io.MemoryConversions._
import com.ambiata.mundane.io.Temporary._
import com.ambiata.mundane.path._
import org.specs2._
import execute.AsResult

import scalaz.{Name =>_,_}, Scalaz._, effect._, effect.Effect._

class S3PatternSpec extends AwsScalaCheckSpec(5) { def is = s2"""

  S3Pattern should perform as expected
  ====================================

    determine either a S3Address or a S3Prefix from a S3Pattern
      determine a S3Address           $determineAddress
      determine a S3Prefix            $determinePrefix
      determine a failure as None     $determineFailure
      determine a failure as None     $determineFailurex
      determine an invalid S3Pattern  $determineNone

    determine a S3Address or a S3Prefix from a S3Pattern or fail
      determine an address and succeed $determineAddressAndSucceed
      determine an address and fail    $determineAddressAndFail
      determine a prefix and succeed   $determinePrefixAndSucceed
      determine a prefix and fail      $determinePrefixAndFail

    listSummary from S3Address         $listAddress
    listSummary from S3Prefix          $listPrefix
    listSummary from invalid S3Pattern $listNone
    listSummary from failure           $listFailure

    exists from S3Address              $existsAddress
    exists from S3Prefix               $existsPrefix
    exists from invalid S3Pattern      $existsNone
    exists from failure                $existsFailure

    delete from S3Address              $deleteAddress
    delete from S3Prefix               $deletePrefix
    delete from invalid S3Pattern      $deleteNone
    delete from failure                $deleteFailure

    download
      get a stream  from S3            $downloadStream
      download a file from S3          $download
      download a file from S3 to file  $downloadTo
      download a file from S3 in parts $downloadParts

    upload
      write string to S3               $putString
      upload a single file to S3       $upload
      upload a small file to S3        $uploadPartsSmall
      upload a file to S3 in parts     $uploadParts

  Support functions
  =================

    can retrieve S3Pattern from uri    $fromUri

"""
  def determineAddress = prop((s3: S3Temporary, data: String) => for {
    a <- s3.address
    _ <- a.put(data)
    s <- a.toS3Pattern.determine
  } yield s ==== a.left[S3Prefix].some)

  def determinePrefix = prop((s3: S3Temporary, id: Ident, data: String) => for {
    p <- s3.prefix
    _ <- (p | id.value).put(data)
    s <- p.toS3Pattern.determine
  } yield s ==== p.right[S3Address].some)

  def determineNone = prop((r: S3Pattern) =>
    r.determine.map(_ must beNone))

  def determineFailure = prop((s3: S3Prefix, unknown: Ident) =>
    (s3 | unknown.value).toS3Pattern.determine.map(_ must beNone))

  def determineFailurex =
    S3Pattern("", "").determine.map(_ must beNone)

  def determineAddressAndSucceed = prop((s3: S3Temporary, data: String) => for {
    a <- s3.address
    _ <- a.put(data)
    s <- a.toS3Pattern.determineAddress
  } yield s ==== a)

  def determineAddressAndFail = prop((s3: S3Temporary) =>
    AsResult(s3.prefix.flatMap(_.toS3Pattern.determineAddress) >> S3Action.safe(ok)).not)

  def determinePrefixAndSucceed = prop((s3: S3Temporary, data: String, suffix: NonEmptyString) => for {
    a <- s3.prefix
    _ <- (a | suffix.value).put(data)
    s <- a.toS3Pattern.determinePrefix
  } yield s ==== a)

  def determinePrefixAndFail = prop((s3: S3Temporary) =>
    AsResult(s3.address.flatMap(_.toS3Pattern.determinePrefix) >> S3Action.safe(ok)).not)

  def listAddress = prop((s3: S3Temporary, data: String) => for {
    a <- s3.address
    _ <- a.put(data)
    l <- a.toS3Pattern.listKeys
  } yield l ==== List(a.key))

  def listPrefix = prop((s3: S3Temporary, key: DistinctPair[Ident], data: String) => for {
    p <- s3.prefix
    _ <- (p | key.first.value).put(data)
    _ <- (p | key.second.value).put(data)
    l <- p.toS3Pattern.listKeys
  } yield l.sorted ==== List((p | key.first.value).key, (p | key.second.value).key).sorted)

  def listNone = prop((pattern: S3Pattern) =>
    pattern.listS3.map(_ ==== nil))

  def listFailure = prop((s3: S3Prefix, unknown: Ident) =>
    (s3 | unknown.value).toS3Pattern.listS3.map(_ ==== nil))

  def existsAddress = prop((s3: S3Temporary, data: String) => for {
    a <- s3.address
    _ <- a.put(data)
    e <- a.toS3Pattern.exists
  } yield e ==== true)

  def existsPrefix = prop((s3: S3Temporary, id: Ident, data: String) => for {
    p <- s3.prefix
    _ <- (p | id.value).put(data)
    e <- p.toS3Pattern.exists
  } yield e ==== true)

  def existsNone = prop((pattern: S3Pattern) =>
    pattern.exists.map(_ ==== false))

  def existsFailure = prop((bucket: Ident, unknown: Ident) =>
    S3Pattern(bucket.value + java.util.UUID.randomUUID().toString, unknown.value).exists.map(_ ==== false))

  def deleteAddress = prop((s3: S3Temporary, data: String) => for {
    a <- s3.address
    _ <- a.put(data)
    _ <- a.toS3Pattern.delete
    e <- a.exists
  } yield e ==== false)

  def deletePrefix = prop((s3: S3Temporary, id: Ident, data: String) => for {
    p <- s3.prefix
    _ <- (p | id.value).put(data)
    _ <- p.toS3Pattern.delete
    e <- p.exists
  } yield e ==== false)

  def deleteNone = prop((pattern: S3Pattern) =>
    pattern.delete.as(ok))

  def deleteFailure = prop((bucket: Ident, unknown: Ident) =>
    S3Pattern(bucket.value + java.util.UUID.randomUUID().toString, unknown.value).delete.as(ok))

  def fromUri = prop((pattern: S3Pattern) =>
    S3Pattern.fromURI(s"s3://${pattern.bucket}/${pattern.unknown}") ==== pattern.some)

  def downloadStream = prop((data: String, s3: S3Temporary, local: LocalTemporary) => for {
    f <- S3Action.fromRIO(local.fileWithContent(data))
    a <- s3.pattern
    _ <- a.putFile(f)
    e <- a.withStream(is => Streams.readWithEncoding(is, "UTF-8"))
  } yield e ==== data)

  def download = prop((data: String, s3: S3Temporary, local: LocalTemporary) => for {
    a <- s3.pattern
    f <- S3Action.fromRIO(local.path)
    _ <- a.put(data)
    _ <- a.getFile(f)
    e <- S3Action.fromRIO(f.exists)
    d <- S3Action.fromRIO(f.read)
  } yield e -> d ==== true -> data.some)

  def downloadTo = prop((data: String, s3: S3Temporary, local: LocalTemporary) => for {
    d <- S3Action.fromRIO(local.directory)
    a <- s3.pattern
    _ <- a.put(data)
    _ <- a.getFileTo(d.toLocalPath)
    p = d.toLocalPath / Path(a.unknown)
    e <- S3Action.fromRIO(p.exists)
    d <- S3Action.fromRIO(p.read)
  } yield e -> d ==== true -> data.some)

  def downloadParts = prop((s3: S3Temporary, local: LocalTemporary) => for {
    a <- s3.pattern
    f <- S3Action.fromRIO(for {
      f <- local.path
      _ <- f.doesNotExist(s"${f} already exists", f.dirname.mkdirs)
    } yield f)
    _ <- a.putLines(smallData)
    _ <- S3Action.using(S3Action.fromRIO(f.path.toOutputStream))(out => a.withStreamMultipart(50.bytes, in => Streams.pipe(in, out), S3.NoTick))
    r <- S3Action.fromRIO(f.readLines)
  } yield r ==== smallData.some)

  def putString = prop((data: String, s3: S3Temporary) => for {
    a <- s3.pattern
    _ <- a.put(data)
    e <- a.get
  } yield e ==== data)

  def upload = prop((data: String, s3: S3Temporary, local: LocalTemporary) => for {
    f <- S3Action.fromRIO(local.fileWithContent(data))
    a <- s3.pattern
    _ <- a.putFile(f)
    e <- a.get
  } yield e ==== data)

  def uploadPartsSmall = prop((s3: S3Temporary, local: LocalTemporary) => for {
    f <- S3Action.fromRIO(local.fileWithContent(Lists.prepareForFile(smallData)))
    a <- s3.pattern
    _ <- a.putFileMultiPart(5.mb, f, S3.NoTickX)
    r <- a.getLines
  } yield r ==== smallData)

  def uploadParts = prop((s3: S3Temporary, local: LocalTemporary) => for {
    f <- S3Action.fromRIO(local.fileWithContent(bigData))
    a <- s3.pattern
    _ <- a.putFileMultiPart(5.mb, f, S3.NoTickX)
    r <- a.get
  } yield r ==== bigData)

  val smallData: List[String] = List.fill(120)("testing")

  val bigData: String = new String({
    val x = new Array[Char](1024*1024*15)
    java.util.Arrays.fill(x, 'x')
    x
  })
}
