package com.ambiata.saws
package tooling

import com.ambiata.saws.core._
import com.ambiata.mundane.testing._, Arbitraries._, Laws._, ResultMatcher._
import org.specs2._, specification._, matcher._
import scalaz._, Scalaz._, \&/._
import testing._


class WaitSpec extends UnitSpec with ScalaCheck { def is = s2"""

 Wait Usage
 ==========

   wait for an optional value                               $waitForValue
   wait for a condition                                     $waitFor

"""

  def waitForValue = {
    var values = List(None, None, None, Some(3))
    var polls = 0
    Wait.waitForValue(Aws.withClient[Unit, Option[Int]](_ => {
      polls += 1
      val entry = values.head
      values = values.tail
      entry
    }), sleep = 10).execute(()).unsafePerformIO must beOkValue(3)
    polls must_== 4
  }

  def waitFor = {
    var values = List(false, false, false, true)
    var polls = 0
    Wait.waitFor(Aws.withClient[Unit, Boolean](_ => {
      polls += 1
      val entry = values.head
      values = values.tail
      entry
    }), sleep = 10).execute(()).unsafePerformIO must beOk
    polls must_== 4
  }
}
