package com.ambiata.saws
package tooling

import com.ambiata.saws.core._
import com.ambiata.saws.testing._, Arbitraries._, Laws._, AwsAttemptMatcher._
import org.specs2._, specification._, matcher._
import scalaz._, Scalaz._, \&/._


class WaitSpec extends Specification with ScalaCheck { def is = s2"""

 Wait Usage
 ==========

   wait for an optional value                               ${waitForValue}
   wait for a condition                                     ${waitFor}

"""

  def waitForValue = {
    var values = List(None, None, None, Some(3))
    var polls = 0
    Wait.waitForValue(AwsAction.value[Unit, Option[Int]]({
      polls += 1
      val entry = values.head
      values = values.tail
      entry
    }), sleep = 10).execute(()) must beOkValue(3)
    polls must_== 4
  }

  def waitFor = {
    var values = List(false, false, false, true)
    var polls = 0
    Wait.waitFor(AwsAction.value[Unit, Boolean]({
      polls += 1
      val entry = values.head
      values = values.tail
      entry
    }), sleep = 10).execute(()) must beOk
    polls must_== 4
  }
}
