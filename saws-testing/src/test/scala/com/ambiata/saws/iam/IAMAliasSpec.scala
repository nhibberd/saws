package com.ambiata.saws.iam

import com.ambiata.mundane.testing.ResultMatcher._
import com.ambiata.saws.core._
import com.ambiata.saws.testing._
import org.specs2._, specification._
import scalaz._, Scalaz._

class IAMAliasSpec extends IntegrationSpec with ScalaCheck { def is = s2"""

 IAMAlias
 ========

   Can list account aliases                       $list

"""

  def list =
    IAMAlias.list.eval.unsafePerformIO must beOkLike(l => !l.isEmpty)
}
