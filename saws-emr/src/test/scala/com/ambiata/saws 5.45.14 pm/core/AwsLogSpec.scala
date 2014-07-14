package com.ambiata
package saws
package core

import mundane.testing.Arbitraries._
import mundane.testing.Laws._
import testing.Arbitraries._
import org.specs2._
import testing._

class AwsLogSpec extends UnitSpec with ScalaCheck { def is = s2"""

 AwsLog Laws
 ===========

   equals laws                    ${equal.laws[AwsLog]}

"""

}
