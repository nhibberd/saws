package com.ambiata
package saws
package testing

import com.ambiata.saws.core._
import org.scalacheck._, Arbitrary._
import scalaz._, Scalaz._
import mundane.control.Attempt
import mundane.testing.Arbitraries._

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

  implicit def AwsActionIntArbitrary: Arbitrary[AwsAction[Int, Int]] =
    Arbitrary(for {
      base  <- arbitrary[AwsAction[Int, Int => Int]]
      m     <- arbitrary[Int]
    } yield base.map(_(m)))

  implicit def AwsActionFuncArbitrary: Arbitrary[AwsAction[Int, Int => Int]] =
    Arbitrary(for {
      logs  <- Gen.choose(0, 4).flatMap(n => Gen.listOfN(n, arbitrary[AwsLog]))
      f     <- func
      base  <- arbitrary[Attempt[Int]]
    } yield AwsAction[Int, Int => Int](a => (logs.toVector, base.map(n => f(n)))))

  def func: Gen[Int => Int => Int] = arbitrary[Int].flatMap(x => Gen.oneOf(
    (m: Int) => (n: Int) => n,
    (m: Int) => (n: Int) => m,
    (m: Int) => (n: Int) => x,
    (m: Int) => (n: Int) => n * m * x,
    (m: Int) => (n: Int) => n * m,
    (m: Int) => (n: Int) => n * x,
    (m: Int) => (n: Int) => m * x
  ))

}
