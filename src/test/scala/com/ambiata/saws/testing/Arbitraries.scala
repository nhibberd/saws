package com.ambiata.saws
package testing

import com.ambiata.saws.core._
import org.scalacheck._, Arbitrary.arbitrary
import scalaz._, Scalaz._, \&/._

object Arbitraries {
  implicit def TheseArbitrary[A: Arbitrary, B: Arbitrary]: Arbitrary[A \&/ B] =
    Arbitrary(Gen.oneOf(
      arbitrary[(A, B)].map({ case (a, b) => \&/.Both(a, b) }),
      arbitrary[A].map(\&/.This(_): A \&/ B),
      arbitrary[B].map(\&/.That(_): A \&/ B)
    ))


  /** WARNING: can't use scalaz-scalacheck-binding because of specs/scalacheck/scalaz compatibility at the moment */

  implicit def DisjunctionArbitrary[A: Arbitrary, B: Arbitrary]: Arbitrary[A \/ B] =
    Arbitrary(Gen.oneOf(
      arbitrary[A].map(-\/(_)),
      arbitrary[B].map(\/-(_))
    ))

  implicit def AwsAttemptArbitrary[A: Arbitrary]: Arbitrary[AwsAttempt[A]] =
    Arbitrary(arbitrary[(String \&/ Throwable) \/ A].map(AwsAttempt.apply))
}
