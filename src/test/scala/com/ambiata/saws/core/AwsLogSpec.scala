package com.ambiata.saws
package core

import testing.Arbitraries._
import testing.Laws._
import org.specs2._
import testing._

class AwsLogSpec extends UnitSpec with ScalaCheck { def is = s2"""

 AwsLog Laws
 ===========

   equals laws                    ${equal.laws[AwsLog]}

"""

}
