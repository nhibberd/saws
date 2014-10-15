package com.ambiata.saws.s3

import com.ambiata.mundane.control.ResultTIO
import com.ambiata.saws.core._
import com.ambiata.saws.testing._
import com.ambiata.mundane.testing.ResultTIOMatcher._
import com.ambiata.mundane.io._
import org.specs2._

import scalaz.{Name =>_,_}, Scalaz._, effect._, effect.Effect._

class S3PatternSpec extends Specification { def is = s2"""

  S3Pattern should perform as expected
  ====================================

    determine a S3Address              $determineAddress         ${tag("aws")}
    determine a S3Prefix               $determinePrefix          ${tag("aws")}
    determine an invalid S3Pattern     $determineNone            ${tag("aws")}
    determine an failure               $determineFailure         ${tag("aws")}

    listSummary from S3Address         $listAddress              ${tag("aws")}
    listSummary from S3Prefix          $listPrefix               ${tag("aws")}
    listSummary from invalid S3Pattern $listNone                 ${tag("aws")}
    listSummary from failure           $listFailure              ${tag("aws")}

    exists from S3Address              $existsAddress            ${tag("aws")}
    exists from S3Prefix               $existsPrefix             ${tag("aws")}
    exists from invalid S3Pattern      $existsNone               ${tag("aws")}
    exists form failure                $existsFailure            ${tag("aws")}


  Support functions
  =================

    can retrieve S3Pattern from uri    $fromUri

"""
  val conf = Clients.s3

  val invalidS3Pattern = S3Pattern("ambiata-dev-view", "nope")
  val failureS3Pattern = S3Pattern("ambiata-dev-zzz", "nope")

  def determineAddress = TemporaryS3.withS3Address(s3 => for {
    _ <- s3.put("").executeT(conf)
    s <- S3Pattern(s3.bucket, s3.key).determine.executeT(conf)
  } yield s) must beOkLike(_ must beSome((z: S3Address \/ S3Prefix) => z.isLeft must beTrue ))

  def determinePrefix = TemporaryS3.withS3Prefix(s3 => for {
    _ <- (s3 | "foo").put("").executeT(conf)
    s <- S3Pattern(s3.bucket, s3.prefix).determine.executeT(conf)
  } yield s) must beOkLike(_ must beSome((z: S3Address \/ S3Prefix) => z.isRight must beTrue ))

  def determineNone =
    invalidS3Pattern.determine.executeT(conf) must beOkLike(_ must beNone )

  def determineFailure =
    failureS3Pattern.determine.executeT(conf) must beOkLike(_ must beNone )

  def listAddress = TemporaryS3.withS3Address(s3 => for {
    _ <- s3.put("").executeT(conf)
    l <- S3Pattern(s3.bucket, s3.key).listKeys.executeT(conf)
  } yield s3 -> l) must beOkLike({
    case (a: S3Address, b: List[String]) => a.key must_== b.head
  })

  def listPrefix = TemporaryS3.withS3Prefix(s3 => for {
    _ <- (s3 | "foo").put( "").executeT(conf)
    _ <- (s3 | "foo2").put( "").executeT(conf)
    _ <- (s3 / "foos" | "bar").put( "").executeT(conf)
    l <- S3Pattern(s3.bucket, s3.prefix).listKeys.executeT(conf)
  } yield s3 -> l) must beOkLike({
    case (a: S3Prefix, b: List[String]) =>
      List("foo", "foo2", "foos/bar").map(s => S3Operations.concat(a.prefix, s)) must_== b
  })

  def listNone =
    invalidS3Pattern.listS3.executeT(conf) must beOkLike(l => l.isEmpty)

  def listFailure =
    failureS3Pattern.listS3.executeT(conf) must beOkLike(l => l.isEmpty)

  def existsAddress =TemporaryS3.withS3Address(s3 => for {
    _ <- s3.put("").executeT(conf)
    e <- S3Pattern(s3.bucket, s3.key).exists.executeT(conf)
  } yield e) must beOkValue(true)

  def existsPrefix = TemporaryS3.withS3Prefix(s3 => for {
    _ <- (s3 | "foo").put( "").executeT(conf)
    e <- S3Pattern(s3.bucket, s3.prefix).exists.executeT(conf)
  } yield e) must beOkValue(true)

  def existsNone =
    invalidS3Pattern.exists.executeT(conf) must beOkValue(false)

  def existsFailure =
    failureS3Pattern.exists.executeT(conf) must beOkValue(false)

  def fromUri =
    S3Pattern.fromURI("s3://ambiata-dev-view/foo/bar/foos/bars") must beSome((a: S3Pattern) => a must_== S3Pattern("ambiata-dev-view", "foo/bar/foos/bars"))

}
