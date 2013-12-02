package com.ambiata.saws
package core

import testing.Arbitraries._
import testing.Laws._
import org.specs2._, specification._, matcher._
import scalaz._, Scalaz._, \&/._
import testing._

class AwsAttemptSpec extends UnitSpec with ScalaCheck { def is = s2"""

 AwsAttempt Laws
 ===============

   equals laws                    ${equal.laws[AwsAttempt[Int]]}
   monad laws                     ${monad.laws[AwsAttempt]}


 AwsAttempt Combinators
 ======================

   isOk                           $isOk
   isError                        $isError
   isError != isOk                $isOkExclusive
   mapError                       $mapError
   toOption Some case             $okToOption
   toOption None case             $errorToOption
   ||| ok case                    $okOr
   ||| error case                 $errorOr
   getOrElse ok case              $okGetOrElse
   getOrElse error case           $errorGetOrElse
   run                            $run
   toDisjunction is alias of run  $toDisjunction

"""
   type Error = String \&/ Throwable

   def isOk = prop((a: Int) =>
     AwsAttempt.ok(a).isOk)

   def isError = prop((a: Error) =>
     AwsAttempt.these(a).isError)

   def isOkExclusive = prop((a: AwsAttempt[Int]) =>
     a.isOk != a.isError)

   def mapError = prop((a: Error, b: Error) =>
     AwsAttempt.these(a).mapError(_ => b) == AwsAttempt.these(b))

   def okToOption = prop((a: Int) =>
     AwsAttempt.ok(a).toOption == Some(a))

   def errorToOption = prop((a: Error) =>
     AwsAttempt.these(a).toOption == None)

   def okOr = prop((a: Int, b: AwsAttempt[Int]) =>
     (AwsAttempt.ok(a) ||| b) == AwsAttempt.ok(a))

   def errorOr = prop((a: Error, b: AwsAttempt[Int]) =>
     (AwsAttempt.these(a) ||| b) == b)

   def okGetOrElse = prop((a: Int, b: Int) =>
     AwsAttempt.ok(a).getOrElse(b) == a)

   def errorGetOrElse = prop((a: Error, b: Int) =>
     AwsAttempt.these(a).getOrElse(b) == b)

   def run = prop((a: Error \/ Int) =>
     AwsAttempt(a).run == a)

   def toDisjunction = prop((a: AwsAttempt[Int]) =>
     a.run == a.toDisjunction)
}
