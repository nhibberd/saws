package com.ambiata.saws.core

import com.ambiata.mundane.testing.Arbitraries._
import com.ambiata.mundane.testing.Laws._
import com.ambiata.saws.testing.Arbitraries._
import org.specs2._
import com.ambiata.saws.testing._

class AwsLogSpec extends UnitSpec with ScalaCheck { def is = s2"""

 AwsLog Laws
 ===========

   equals laws                    ${equal.laws[AwsLog]}

"""

}
