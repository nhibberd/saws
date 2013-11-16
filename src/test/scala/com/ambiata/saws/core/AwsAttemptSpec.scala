package com.ambiata.saws
package core

import testing.Arbitraries._
import testing.Laws._
import org.specs2._, specification._, matcher._
import scalaz._, Scalaz._

class AwsAttemptSpec extends Specification with ScalaCheck { def is = s2"""

 AwsAttempt Laws
 ===============

   equals laws                    ${equal.laws[AwsAttempt[Int]]}
   monad laws                     ${monad.laws[AwsAttempt]}


 AwsAttempt Combinators
 ======================

   isOk                           ${isOk}
   isError                        ${isError}
   isError != isOk                ${isOkExclusive}

"""

   def isOk = prop((a: Int) =>
     AwsAttempt.ok(a).isOk)

   def isError = prop((a: These[String, Throwable]) =>
     AwsAttempt.these(a).isError)

   def isOkExclusive = prop((a: AwsAttempt[Int]) =>
     a.isOk != a.isError)
}
