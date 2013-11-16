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

"""

}
