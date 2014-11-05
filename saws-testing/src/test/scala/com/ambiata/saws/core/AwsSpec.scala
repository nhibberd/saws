package com.ambiata.saws
package core

import com.ambiata.com.amazonaws.services.s3.AmazonS3Client
import com.ambiata.com.amazonaws.services.ec2.AmazonEC2Client
import com.ambiata.com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient

import com.ambiata.mundane.control._
import com.ambiata.mundane.testing.Arbitraries._
import com.ambiata.mundane.testing.Laws._
import com.ambiata.saws.testing.AwsMatcher._

import testing._, Arbitraries._
import org.specs2._, specification._, matcher._
import scalaz._, Scalaz._, \&/._
import scalaz.effect.IO

class AwsSpec extends UnitSpec with ScalaCheck { def is = s2"""

 AwsAction Laws
 ==============

   monad laws                                               ${monad.laws[({ type l[a] = Aws[Int, a] })#l]}


 AwsAction Combinators
 =====================

   safe catches exceptions                                  $safe
   retry                                                    $retry


 AwsAction Usage
 ===============

   creating an IAM Action                                   $iam
   creating an S3 Action                                    $s3
   creating an EC2 Action                                   $ec2
   composing actions of same type                           $composition
   composing actions of same type w/scalaz monad            $compositionMonad
   composing actions of same type w/scalaz monad anonymoys  $compositionMonadAnon
   composing actions of same type w/scalaz appplicative <*  $compositionApplicativeLeft
   composing actions of same type w/scalaz appplicative *>  $compositionApplicativeRight
   composing actions of different types                     $compositionLift
   composing actions of moar different types                $compositionLiftAgain
   logging                                                  $logging
   handling errors                                          $errors

"""

  type Error = String \&/ Throwable

  /*  Aws Combinators */

  def safe = prop((t: Throwable) =>
    Aws.safe[Int, Int](throw t).run(0).unsafePerformIO == (Vector(), Result.exception(t)))

  def retry = {
    var c = 5
    val action = Aws.result((n: Int) => {
      val ret = if (c < 3) Result.ok(1) else Result.fail[Int]("fail")
      c = c - 1
      ret
    })

    def logf(n: Int)(i: Int, e: These[String, Throwable]) =
      Vector(AwsLog.Warn(s"Result ${(n + 1) - i}/${n + 1} failed with err - ${Result.asString(e)}"))

    action.retry(5, logf(5)) must runLogOkValue[Int, Int](_.run(1))(1, Vector(
      AwsLog.Warn("Result 1/6 failed with err - fail"),
      AwsLog.Warn("Result 2/6 failed with err - fail"),
      AwsLog.Warn("Result 3/6 failed with err - fail")))
  }

  /*  Aws Usage */

  def iam =
    IAMAction(client => client) must beOkLike(_ must beAnInstanceOf[AmazonIdentityManagementClient])

  def s3 =
    S3Action(client => client) must beOkLike(_ must beAnInstanceOf[AmazonS3Client])

  def ec2 =
    EC2Action(client => client) must beOkLike(_ must beAnInstanceOf[AmazonEC2Client])

  def composition = {
    val ten = EC2Action(_ => 10)
    val twenty = EC2Action(_ => 20)

    val answer = for {
      a <- ten
      b <- twenty
    } yield a + b

    answer must beOkValue(30)
  }

  def compositionMonad = {
    val ten = EC2Action(_ => 10)
    val plusTwenty = (n: Int) => EC2Action(_ => n + 20)

    /* Run first effect, and use its result to run second effect. */
    val answer = ten >>= plusTwenty

    answer must beOkValue(30)
  }

  def compositionMonadAnon = {
    val sideEffects = scala.collection.mutable.ListBuffer[String]()

    val red = EC2Action(_ => { sideEffects += "red"; () })
    val blue = EC2Action(_ => { sideEffects += "blue"; () })

    /* Run both effects and ignore intermediate computed value. */
    val answer = (red >> blue) must beOk

    sideEffects.toList must_== List("red", "blue")
  }


  def compositionApplicativeLeft = {
    val sideEffects = scala.collection.mutable.ListBuffer[String]()

    val red = EC2Action(_ => { sideEffects += "red"; "red" })
    val blue = EC2Action(_ => { sideEffects += "blue"; "blue" })

    /* Run both effects, but take value computed on left. */
    val answer = red <* blue

    (answer must beOkValue("red")) and
      (sideEffects.toList must_== List("red", "blue"))
  }

  def compositionApplicativeRight = {
    val sideEffects = scala.collection.mutable.ListBuffer[String]()

    val red = EC2Action(_ => { sideEffects += "red"; "red" })
    val blue = EC2Action(_ => { sideEffects += "blue"; "blue" })

    /* Run both effects, but take value computed on right. */
    val answer = red *> blue

    (answer must beOkValue("blue")) and
      (sideEffects.toList must_== List("red", "blue"))
  }

  def compositionLift = {
    val ten = EC2Action(_ => 10)
    val twenty = S3Action(_ => 20)

    val answer: S3EC2Action[Int] = for {
      a <- ten       .liftAws[S3EC2]
      b <- twenty    .liftAws[S3EC2]
    } yield a + b

    answer must beOkValue(30)
  }

  def compositionLiftAgain = {
    val ten = EC2Action(_ => 10)
    val twenty = S3Action(_ => 20)
    val thirty = IAMAction(_ => 30)

    val answer: S3EC2IAMAction[Int] = for {
      a <- ten       .liftAws[S3EC2IAM]
      b <- twenty    .liftAws[S3EC2IAM]
      c <- thirty    .liftAws[S3EC2IAM]
    } yield a + b + c

    answer must beOkValue(60)
  }

  def logging = {
    val ten = EC2Action(_ => 10)
    val twenty = EC2Action(_ => 20)

    val answer = for {
      a <- ten       <* AwsLog.Info("adding 10").log
      b <- twenty    <* AwsLog.Debug("adding 20").log
    } yield a + b

    answer must beLogOkValue(30, Vector(
      AwsLog.Info("adding 10"),
      AwsLog.Debug("adding 20")))
  }

  def errors = prop((message: String, throwable: Throwable) =>
    Aws.error[Int, Int](message, throwable).execute(0).unsafePerformIO match {
      case Ok(result) =>
        failure
      case Error(error) =>
        error must_== Both(message, throwable): execute.Result
    })

  /*  Aws Support (Instances specialized for law checking) */

  implicit def AwsEqual[A: Equal]: Equal[Aws[Int, A]] = new Equal[Aws[Int, A]] {
    def equal(a1: Aws[Int, A], a2: Aws[Int, A]): Boolean =
      a1.run(0).unsafePerformIO === a2.run(0).unsafePerformIO
  }
}
