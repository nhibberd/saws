package com.ambiata.saws
package core

import testing.Arbitraries._
import testing.Laws._
import org.specs2._, specification._, matcher._
import scalaz._, Scalaz._, \&/._

class AwsActionSpec extends Specification with ScalaCheck { def is = s2"""

 AwsAction Laws
 ===============

   monad laws                     ${monad.laws[({ type l[a] = AwsAction[Int, a] })#l]}


 AwsAction Combinators
 ======================

   safe catches exceptions        ${safe}

"""

  type Error = String \&/ Throwable

  def safe = prop((t: Throwable) =>
    AwsAction.value[Int, Int](throw t).safe.runNoLog(0) == AwsAttempt.exception(t))

  implicit def AwsActionEqual[A: Equal]: Equal[AwsAction[Int, A]] = new Equal[AwsAction[Int, A]] {
    def equal(a1: AwsAction[Int, A], a2: AwsAction[Int, A]): Boolean =
      a1.run(0) === a2.run(0)
  }
}
