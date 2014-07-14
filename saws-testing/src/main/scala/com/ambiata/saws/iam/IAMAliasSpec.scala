package com.ambiata.saws
package iam

import com.ambiata.saws.core._
import com.ambiata.mundane.testing.ResultMatcher._
import org.specs2._, specification._
import scalaz._, Scalaz._
import testing._

class IAMAliasSpec extends UnitSpec with ScalaCheck { def is = s2"""

 IAMAlias
 ========

   Can list account aliases                       $list
   Only test against ambiata account              $isAmbiata
   Don't test against ambiata-prod account        $isNotAmbiataProd

"""

  def list =
    IAMAlias.list.eval.unsafePerformIO must beOkLike(_ contains ("ambiata"))

  def isAmbiata =
    IAMAlias.isAmbiata.eval.unsafePerformIO must beOkValue(true)

  def isNotAmbiataProd =
    IAMAlias.isAmbiataProd.eval.unsafePerformIO must beOkValue(false)
}
