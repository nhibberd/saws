package com.ambiata.saws
package core

import testing.Arbitraries._
import testing.Laws._
import org.specs2._, specification._, matcher._

class AwsLogSpec extends Specification with ScalaCheck { def is = s2"""

 AwsLog Laws
 ===========

   equals laws                    ${equal.laws[AwsLog]}

"""

}
