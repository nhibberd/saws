package com.ambiata.saws.s3

import com.ambiata.saws.core._
import com.ambiata.saws.testing._
import com.ambiata.saws.testing.Arbitraries._
import com.ambiata.mundane.testing.RIOMatcher._
import com.ambiata.mundane.io._
import org.specs2._
import org.specs2.matcher.Parameters

import scalaz.{Name =>_,_}, Scalaz._, effect._, effect.Effect._

class S3PatternSpec extends Specification with ScalaCheck { def is = section("aws") ^ s2"""

  S3Pattern should perform as expected
  ====================================

    determine a S3Address              $determineAddress
    determine a S3Prefix               $determinePrefix
    determine an invalid S3Pattern     $determineNone
    determine an failure               $determineFailure
    determine an failure               $determineFailurex

    listSummary from S3Address         $listAddress
    listSummary from S3Prefix          $listPrefix
    listSummary from invalid S3Pattern $listNone
    listSummary from failure           $listFailure

    exists from S3Address              $existsAddress
    exists from S3Prefix               $existsPrefix
    exists from invalid S3Pattern      $existsNone
    exists form failure                $existsFailure


  Support functions
  =================

    can retrieve S3Pattern from uri    $fromUri

"""

  override implicit def defaultParameters: Parameters =
    new Parameters(minTestsOk = 3, workers = 3)

  val conf = Clients.s3

  def determineAddress = prop((address: S3Address, data: String) =>
    TemporaryS3.runWithS3Address(address)(s3 => for {
    _ <- s3.put(data).execute(conf)
    s <- S3Pattern(s3.bucket, s3.key).determine.execute(conf)
  } yield s) must beOkLike(_ must beSome((z: S3Address \/ S3Prefix) => z.isLeft must beTrue )))

  def determinePrefix = prop((prefix: S3Prefix, data: String) =>
    TemporaryS3.runWithS3Prefix(prefix)(s3 => for {
    _ <- (s3 | "foo").put(data).execute(conf)
    s <- S3Pattern(s3.bucket, s3.prefix).determine.execute(conf)
  } yield s) must beOkLike(_ must beSome((z: S3Address \/ S3Prefix) => z.isRight must beTrue )))

  def determineNone = prop((r: S3Pattern) =>
    r.determine.execute(conf) must beOkLike(_ must beNone ))

  def determineFailure = prop((s3: S3Prefix, unknown: String) =>
    (s3 | unknown).toS3Pattern.determine.execute(conf) must beOkLike(_ must beNone ))

  def determineFailurex =
    S3Pattern("", "").determine.execute(conf) must beOkLike(_ must beNone )

  def listAddress = prop((address: S3Address, data: String) =>
    TemporaryS3.withS3Address(s3 => for {
      _ <- s3.put(data).execute(conf)
      l <- S3Pattern(s3.bucket, s3.key).listKeys.execute(conf)
    } yield s3 -> l) must beOkLike({
      case (a: S3Address, b: List[String]) => a.key must_== b.head
    })
  )

  def listPrefix = prop((prefix: S3Prefix, data: String, key1: S3Address, key2: S3Address) => (key1.key != key2.key) ==> {
    TemporaryS3.runWithS3Prefix(prefix)(s3 => for {
      _ <- (s3 | key1.key).put(data).execute(conf)
      _ <- (s3 | key2.key).put(data).execute(conf)
      l <- s3.toS3Pattern.listKeys.execute(conf)
    } yield s3 -> l) must beOkLike({
      case (a: S3Prefix, b: List[String]) =>
        b.toSet must_== List(key1.key, key2.key).map(s => S3Operations.concat(a.prefix, s)).toSet
    })
  })

  def listNone = prop((pattern: S3Pattern) =>
    pattern.listS3.execute(conf) must beOkLike(l => l.isEmpty))

  def listFailure = prop((s3: S3Prefix, unknown: String) =>
    (s3 | unknown).toS3Pattern.listS3.execute(conf) must beOkLike(l => l.isEmpty))

  def existsAddress = prop((address: S3Address, data: String) =>
    TemporaryS3.runWithS3Address(address)(s3 => for {
      _ <- s3.put(data).execute(conf)
      e <- S3Pattern(s3.bucket, s3.key).exists.execute(conf)
    } yield e) must beOkValue(true)
  )

  def existsPrefix = prop((prefix: S3Prefix, data: String) =>
    TemporaryS3.runWithS3Prefix(prefix)(s3 => for {
      _ <- (s3 | "foo").put(data).execute(conf)
      e <- S3Pattern(s3.bucket, s3.prefix).exists.execute(conf)
    } yield e) must beOkValue(true)
  )

  def existsNone = prop((pattern: S3Pattern) =>
    pattern.exists.execute(conf) must beOkValue(false))

  def existsFailure = prop((bucket: Clean, unknown: String) =>
    S3Pattern(bucket.s, unknown).exists.execute(conf) must beOkValue(false))

  def fromUri = prop((pattern: S3Pattern) =>
    S3Pattern.fromURI(s"s3://${pattern.render}") must beSome((a: S3Pattern) => a must_== pattern))

}
