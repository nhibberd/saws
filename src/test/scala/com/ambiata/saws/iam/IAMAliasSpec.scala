package com.ambiata.saws
package iam

import com.ambiata.saws.core._
import com.ambiata.saws.testing.AwsAttemptMatcher._
import org.specs2._, specification._
import scalaz._, Scalaz._

class IAMAliasSpec extends Specification with ScalaCheck { def is = s2"""

 IAMAlias
 ========

   Can list account aliases                       ${list}
   Only test against ambiata account              ${isAmbiata}
   Don't test against ambiata-prod account        ${isNotAmbiataProd}

"""

  def list =
    IAMAlias.list.executeIAM must beOkLike(_ contains ("ambiata"))

  def isAmbiata =
    IAMAlias.isAmbiata.executeIAM must beOkValue(true)

  def isNotAmbiataProd =
    IAMAlias.isAmbiataProd.executeIAM must beOkValue(false)
}
