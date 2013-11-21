package com.ambiata.saws
package core

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient

import com.ambiata.saws.testing._, Arbitraries._, Laws._, AwsAttemptMatcher._
import org.specs2._, specification._, matcher._
import scalaz._, Scalaz._, \&/._


class AwsActionSpec extends Specification with ScalaCheck { def is = s2"""

 AwsAction Laws
 ==============

   monad laws                                               ${monad.laws[({ type l[a] = AwsAction[Int, a] })#l]}


 AwsAction Combinators
 =====================

   safe catches exceptions                                  ${safe}
   retry                                                    ${retry}


 AwsAction Usage
 ===============

   creating an IAM Action                                   ${iam}
   creating an S3 Action                                    ${s3}
   creating an EC2 Action                                   ${ec2}
   composing actions of same type                           ${composition}
   composing actions of same type w/scalaz monad            ${compositionMonad}
   composing actions of same type w/scalaz monad anonymoys  ${compositionMonadAnon}
   composing actions of same type w/scalaz appplicative <*  ${compositionApplicativeLeft}
   composing actions of same type w/scalaz appplicative *>  ${compositionApplicativeRight}
   composing actions of different types                     ${compositionLift}
   composing actions of moar different types                ${compositionLiftAgain}
   logging                                                  ${logging}
   handling errors                                          ${errors}
   handling errors just as an error message                 ${errorMessages}


"""

  type Error = String \&/ Throwable

  /*  AwsAction Combinators */

  def safe = prop((t: Throwable) =>
    AwsAction.value[Int, Int](throw t).safe.unsafeRun(0) == (Vector(), AwsAttempt.exception(t)))

  def retry = {
    var c = 5
    def r(a: Int): (Vector[AwsLog], AwsAttempt[Int]) = {
      val ret = if(c < 3) (Vector(), AwsAttempt.ok(a)) else (Vector(), AwsAttempt.fail("fail"))
      c = c - 1
      ret
    }

    def logf(n: Int)(i: Int, e: These[String, Throwable]) =
      Vector(AwsLog.Warn(s"Attempt ${(n + 1) - i}/${n + 1} failed with err - ${AwsAttempt.asString(e)}"))

    AwsAction[Int, Int](r).retry(5, logf(5)).unsafeRun(1) must_== (Vector(
      AwsLog.Warn("Attempt 1/6 failed with err - fail"),
      AwsLog.Warn("Attempt 2/6 failed with err - fail"),
      AwsLog.Warn("Attempt 3/6 failed with err - fail")), AwsAttempt(\/-(1)))
  }

  /*  AwsAction Usage */

  def iam =
    IAMAction(client => client).executeIAM must
      beOkLike(_ must beAnInstanceOf[AmazonIdentityManagementClient])

  def s3 =
    S3Action(client => client).executeS3 must
      beOkLike(_ must beAnInstanceOf[AmazonS3Client])

  def ec2 =
    EC2Action(client => client).executeEC2 must
      beOkLike(_ must beAnInstanceOf[AmazonEC2Client])

  def composition = {
    val ten = EC2Action(_ => 10)
    val twenty = EC2Action(_ => 20)

    val answer = for {
      a <- ten
      b <- twenty
    } yield a + b

    answer.executeEC2 must beOkValue(30)
  }

  def compositionMonad = {
    val ten = EC2Action(_ => 10)
    val plusTwenty = (n: Int) => EC2Action(_ => n + 20)

    /* Run first effect, and use its result to run second effect. */
    val answer = ten >>= plusTwenty

    answer.executeEC2 must beOkValue(30)
  }

  def compositionMonadAnon = {
    val sideEffects = scala.collection.mutable.ListBuffer[String]()

    val red = EC2Action(_ => { sideEffects += "red"; () })
    val blue = EC2Action(_ => { sideEffects += "blue"; () })

    /* Run both effects and ignore intermediate computed value. */
    val answer = (red >> blue).executeEC2

    sideEffects.toList must_== List("red", "blue")
  }


  def compositionApplicativeLeft = {
    val sideEffects = scala.collection.mutable.ListBuffer[String]()

    val red = EC2Action(_ => { sideEffects += "red"; "red" })
    val blue = EC2Action(_ => { sideEffects += "blue"; "blue" })

    /* Run both effects, but take value computed on left. */
    val answer = red <* blue

    (answer.executeEC2 must beOkValue("red")) and
      (sideEffects.toList must_== List("red", "blue"))
  }

  def compositionApplicativeRight = {
    val sideEffects = scala.collection.mutable.ListBuffer[String]()

    val red = EC2Action(_ => { sideEffects += "red"; "red" })
    val blue = EC2Action(_ => { sideEffects += "blue"; "blue" })

    /* Run both effects, but take value computed on right. */
    val answer = red *> blue

    (answer.executeEC2 must beOkValue("blue")) and
      (sideEffects.toList must_== List("red", "blue"))
  }

  def compositionLift = {
    val ten = EC2Action(_ => 10)
    val twenty = S3Action(_ => 20)

    val answer: S3EC2Action[Int] = for {
      a <- ten       .liftEC2[S3EC2Action]
      b <- twenty    .liftS3[S3EC2Action]
    } yield a + b

    answer.executeS3EC2 must beOkValue(30)
  }

  def compositionLiftAgain = {
    val ten = EC2Action(_ => 10)
    val twenty = S3Action(_ => 20)
    val thirty = IAMAction(_ => 30)

    val answer: S3EC2IAMAction[Int] = for {
      a <- ten       .liftEC2[S3EC2IAMAction]
      b <- twenty    .liftS3[S3EC2IAMAction]
      c <- thirty    .liftIAM[S3EC2IAMAction]
    } yield a + b + c

    answer.executeS3EC2IAM must beOkValue(60)
  }

  def logging = {
    val ten = EC2Action(_ => 10)
    val twenty = EC2Action(_ => 20)

    val answer = for {
      a <- ten       <* AwsLog.Info("adding 10").log
      b <- twenty    <* AwsLog.Debug("adding 20").log
    } yield a + b

    answer.runEC2 match {
      case (log, attempt) =>
        (attempt must beOkValue(30)) and
          (log must_== Vector(AwsLog.Info("adding 10"), AwsLog.Debug("adding 20")))
    }
  }

  def errors = prop((message: String, throwable: Throwable) =>
    AwsAction.error[Int, Int](message, throwable).execute(0) match {
      case AwsAttempt.Ok(result) =>
        failure
      case AwsAttempt.Error(error) =>
        error must_== Both(message, throwable): execute.Result
    })

  def errorMessages = prop((message: String, throwable: Throwable) =>
    AwsAction.error[Int, Int](message, throwable).execute(0) match {
      case AwsAttempt.Ok(result) =>
        failure
      case AwsAttempt.ErrorMessage(error) =>
        /* This match unifies the message and the exception to a single error. */
        error must_== AwsAttempt.asString(Both(message, throwable)): execute.Result
    })

  /*  AwsAction Support (Instances specialized for law checking) */

  implicit def AwsActionEqual[A: Equal]: Equal[AwsAction[Int, A]] = new Equal[AwsAction[Int, A]] {
    def equal(a1: AwsAction[Int, A], a2: AwsAction[Int, A]): Boolean =
      a1.unsafeRun(0) === a2.unsafeRun(0)
  }
}
