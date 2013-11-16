package com.ambiata.saws
package testing

import com.ambiata.saws.core._
import org.scalacheck._, Arbitrary.arbitrary
import scalaz._, Scalaz._, \&/._

object Arbitraries {
  implicit def AwsAttemptArbitrary[A: Arbitrary]: Arbitrary[AwsAttempt[A]] =
    Arbitrary(arbitrary[(String \&/ Throwable) \/ A].map(AwsAttempt.apply))

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
      base  <- arbitrary[AwsAttempt[Int]]
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

  /** WARNING: can't use scalaz-scalacheck-binding because of specs/scalacheck/scalaz compatibility at the moment */
  implicit def TheseArbitrary[A: Arbitrary, B: Arbitrary]: Arbitrary[A \&/ B] =
    Arbitrary(Gen.oneOf(
      arbitrary[(A, B)].map({ case (a, b) => \&/.Both(a, b) }),
      arbitrary[A].map(\&/.This(_): A \&/ B),
      arbitrary[B].map(\&/.That(_): A \&/ B)
    ))

  implicit def DisjunctionArbitrary[A: Arbitrary, B: Arbitrary]: Arbitrary[A \/ B] =
    Arbitrary(Gen.oneOf(
      arbitrary[A].map(-\/(_)),
      arbitrary[B].map(\/-(_))
    ))
}
