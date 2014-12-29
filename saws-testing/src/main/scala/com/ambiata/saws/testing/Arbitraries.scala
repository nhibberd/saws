package com.ambiata.saws.testing

import com.ambiata.mundane.control._
import com.ambiata.mundane.testing.Arbitraries._
import com.ambiata.saws.core._
import com.ambiata.saws.s3._

import org.scalacheck._, Arbitrary._

import scalaz._, Scalaz._, effect.IO

import scala.io.Codec

object Arbitraries {
  implicit def AwsLogArbitrary: Arbitrary[AwsLog] = Arbitrary(Gen.oneOf(
    arbitrary[String].map(AwsLog.CreateVPC),
    arbitrary[String].map(AwsLog.CreateInternetGateway),
    arbitrary[String].map(AwsLog.CreateRouteTable),
    arbitrary[String].map(AwsLog.CreateSubnet),
    arbitrary[String].map(AwsLog.CreateRole),
    arbitrary[String].map(AwsLog.CreateBucket),
    arbitrary[String].map(AwsLog.CreateInstanceProfile),
    arbitrary[String].map(AwsLog.CreateSecurityGroup),
    arbitrary[String].map(AwsLog.UpdateRole),
    arbitrary[String].map(AwsLog.UpdateBucket),
    arbitrary[String].map(AwsLog.UpdateInstanceProfile),
    arbitrary[String].map(AwsLog.UpdateSecurityGroup),
    arbitrary[String].map(AwsLog.StartInstance),
    arbitrary[String].map(AwsLog.StopInstance),
    arbitrary[String].map(AwsLog.Info),
    arbitrary[String].map(AwsLog.Warn),
    arbitrary[String].map(AwsLog.Debug)
  ))

  implicit def AwsIntArbitrary: Arbitrary[Aws[Int, Int]] =
    Arbitrary(for {
      base  <- arbitrary[Aws[Int, Int => Int]]
      m     <- arbitrary[Int]
    } yield base.map(_(m)))

  implicit def AwsFuncArbitrary: Arbitrary[Aws[Int, Int => Int]] =
    Arbitrary(for {
      logs  <- Gen.choose(0, 4).flatMap(n => Gen.listOfN(n, arbitrary[AwsLog]))
      f     <- func
      base  <- arbitrary[Result[Int]]
    } yield  Aws(c => RIO.result(base.map(n => f(n)).map(logs.toVector -> _))))

  def func: Gen[Int => Int => Int] = arbitrary[Int].flatMap(x => Gen.oneOf(
    (m: Int) => (n: Int) => n,
    (m: Int) => (n: Int) => m,
    (m: Int) => (n: Int) => x,
    (m: Int) => (n: Int) => n * m * x,
    (m: Int) => (n: Int) => n * m,
    (m: Int) => (n: Int) => n * x,
    (m: Int) => (n: Int) => m * x
  ))

  implicit def CodecArbitrary: Arbitrary[Codec] = Arbitrary(Gen.oneOf(
      Codec.UTF8
    , Codec.ISO8859
  ))

  def testBucket: String = Option(System.getenv("AWS_TEST_BUCKET")).getOrElse("ambiata-dev-view")

  case class Clean(s: String)
  implicit def CleanArbitrary: Arbitrary[Clean] =
    Arbitrary(Gen.identifier.map(Clean.apply))

  implicit def S3AddressArbitrary: Arbitrary[S3Address] = Arbitrary(for {
    p <- arbitrary[S3Prefix]
    k <- Gen.identifier
  } yield p | k)

  implicit def S3PrefixArbitrary: Arbitrary[S3Prefix] = Arbitrary(for {
    i <- Gen.choose(1, 5)
    a <- Gen.listOfN(i, Gen.identifier)
    z = a.mkString("/")
    f <- Gen.oneOf("", "/")
  } yield S3Prefix(testBucket, s"tests-${java.util.UUID.randomUUID().toString}/" + z + f))

  implicit def S3PatternArbitrary: Arbitrary[S3Pattern] =
    Arbitrary(Gen.oneOf(arbitrary[S3Prefix].map(_.toS3Pattern), arbitrary[S3Address].map(_.toS3Pattern)))
}
