package com.ambiata.saws
package core

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.s3.AmazonS3Client
import scalaz._, Scalaz._, \&/._
import com.ambiata.mundane.control.Attempt

case class AwsAction[A, +B](unsafeRun: A => (Vector[AwsLog], Attempt[B])) {
  def map[C](f: B => C): AwsAction[A, C] =
    flatMap[C](f andThen AwsAction.ok)

  def mapError(f: These[String, Throwable] => These[String, Throwable]): AwsAction[A, B] =
    AwsAction[A, B](a => run(a) match { case (log, att) => (log, att.mapError(f)) })

  def contramap[C](f: C => A): AwsAction[C, B] =
    AwsAction(c => run(f(c)))

  def flatMap[C](f: B => AwsAction[A, C]): AwsAction[A, C] =
    AwsAction[A, C](a => run(a) match {
      case (log, Attempt(\/-(b))) =>
        f(b).run(a) match {
          case (log2, result) => (log ++ log2, result)
        }
      case (log, Attempt(-\/(e))) =>
        (log, Attempt(-\/(e)))
    }).safe

  def attempt[C](f: B => Attempt[C]): AwsAction[A, C] =
    flatMap(f andThen AwsAction.attempt)

  def orElse[BB >: B](alt: => BB): AwsAction[A, BB] =
    AwsAction[A, BB](a => run(a) match {
      case (log, Attempt.Ok(b))    => (log, Attempt.ok(b))
      case (log, Attempt.Error(_)) => (log, Attempt.ok(alt))
    })

  def onError(f: These[String, Throwable] => AwsAction[A, Unit]): AwsAction[A, Unit] =
    AwsAction(a => run(a) match {
      case (log, Attempt.Ok(_))    => (log, Attempt.ok(()))
      case (log, Attempt.Error(e)) => f(e).run(a) match {
        case (log2, attp) => (log ++ log2, attp)
      }
    })

  def safe: AwsAction[A, B] =
    AwsAction(a => Attempt.safe(unsafeRun(a)) match {
      case Attempt(-\/(err)) => (Vector(), Attempt(-\/(err)))
      case Attempt(\/-(ok))  => ok
    })

  def retry(i: Int, lf: (Int, These[String, Throwable]) => Vector[AwsLog] = (_,_) => Vector()): AwsAction[A, B] =
    AwsAction[A, B](a => run(a) match {
      case (log, Attempt.Ok(b))    => (log, Attempt.ok(b))
      case (log, Attempt.Error(e)) => if(i > 0) retry(i - 1, lf).unsafeRun(a) match {
        case (nlog, nattp) => (log ++ lf(i, e) ++ nlog, nattp)
      } else (log ++ lf(i, e), Attempt.these(e))
    })

  /** after running the action, print the last log message to the console */
  def flush: AwsAction[A, B] =
    AwsAction[A, B](a => run(a) match {
      case (log, r) => { log.lastOption.foreach(println); (log, r) }
    })

  def run(a: A): (Vector[AwsLog], Attempt[B]) =
    safe.unsafeRun(a)

  def runS3(implicit ev: AmazonS3Client =:= A) =
    run(Clients.s3)

  def runEC2(implicit ev: AmazonEC2Client =:= A) =
    run(Clients.ec2)

  def runIAM(implicit ev: AmazonIdentityManagementClient =:= A) =
    run(Clients.iam)

  def runS3EC2(implicit ev: (AmazonS3Client, AmazonEC2Client) =:= A) =
    run(Clients.s3 -> Clients.ec2)

  def runEC2IAM(implicit ev: (AmazonEC2Client, AmazonIdentityManagementClient) =:= A) =
    run(Clients.ec2 -> Clients.iam)

  def runS3EC2IAM(implicit ev: (AmazonS3Client, AmazonEC2Client, AmazonIdentityManagementClient) =:= A) =
    run((Clients.s3, Clients.ec2, Clients.iam))

  def execute(a: A) =
    AwsAction.unlog(run(a))

  def executeS3(implicit ev: AmazonS3Client =:= A) =
    AwsAction.unlog(runS3)

  def executeEC2(implicit ev: AmazonEC2Client =:= A) =
    AwsAction.unlog(runEC2)

  def executeIAM(implicit ev: AmazonIdentityManagementClient =:= A) =
    AwsAction.unlog(runIAM)

  def executeS3EC2(implicit ev: (AmazonS3Client, AmazonEC2Client) =:= A) =
    AwsAction.unlog(runS3EC2)

  def executeEC2IAM(implicit ev: (AmazonEC2Client, AmazonIdentityManagementClient) =:= A) =
    AwsAction.unlog(runEC2IAM)

  def executeS3EC2IAM(implicit ev: (AmazonS3Client, AmazonEC2Client, AmazonIdentityManagementClient) =:= A) =
    AwsAction.unlog(runS3EC2IAM)
}

object AwsAction {
  private def actions[A] = new AwsActions[A] {}

  def config[A]: AwsAction[A, A]                                       = actions[A].config
  def ok[A, B](strict: B): AwsAction[A, B]                             = actions[A].ok(strict)
  def attempt[A, B](value: Attempt[B]): AwsAction[A, B]             = actions[A].attempt(value)
  def value[A, B](value: => B): AwsAction[A, B]                        = actions[A].value(value)
  def exception[A, B](t: Throwable): AwsAction[A, B]                   = actions[A].exception(t)
  def fail[A, B](message: String): AwsAction[A, B]                     = actions[A].fail(message)
  def error[A, B](message: String, t: Throwable): AwsAction[A, B]      = actions[A].error(message, t)
  def withClient[A, B](f: A => B): AwsAction[A, B]                     = actions[A].withClient(f)
  def attemptWithClient[A, B](f: A => Attempt[B]): AwsAction[A, B]  = actions[A].attemptWithClient(f)
  def log[A](message: AwsLog): AwsAction[A, Unit]                      = actions[A].log(message)
  def unlog[A](result: (Vector[AwsLog], Attempt[A])): Attempt[A] = actions[A].unlog(result)
  def aggregateActions[A, B](as: List[AwsAction[A, B]], ef: These[String, Throwable] => Unit): AwsAction[A, List[B]] =
    actions[A].aggregateActions(as, ef)

  implicit def AwsActionMonad[A]: Monad[({ type l[a] = AwsAction[A, a] })#l] =
    new Monad[({ type L[a] = AwsAction[A, a] })#L] {
      def point[B](v: => B) = AwsAction.ok(v)
      def bind[B, C](m: AwsAction[A, B])(f: B => AwsAction[A, C]) = m.flatMap(f)
    }
}

/**
 * This trait is extended by S3, EC2, IAM to provide specialised versions of AwsActions
 */
trait AwsActions[A] {
  def config: AwsAction[A, A] =
    AwsAction(a => (Vector(), Attempt.ok(a)))

  def ok[B](strict: B): AwsAction[A, B] =
    value(strict)

  def attempt[B](value: Attempt[B]): AwsAction[A, B] =
    AwsAction(_ => (Vector(), value))

  def value[B](value: => B): AwsAction[A, B] =
    AwsAction(_ => (Vector(), Attempt.ok(value)))

  def exception[B](t: Throwable): AwsAction[A, B] =
    AwsAction(_ => (Vector(), Attempt.exception(t)))

  def fail[B](message: String): AwsAction[A, B] =
    AwsAction(_ => (Vector(), Attempt.fail(message)))

  def error[B](message: String, t: Throwable): AwsAction[A, B] =
    AwsAction(_ => (Vector(), Attempt.error(message, t)))

  def these[B](t: These[String, Throwable]): AwsAction[A, B] =
    AwsAction(_ => (Vector(), Attempt.these(t)))

  def withClient[B](f: A => B): AwsAction[A, B] =
    config.map(f)

  def attemptWithClient[B](f: A => Attempt[B]): AwsAction[A, B] =
    config.attempt(f)

  def log(message: AwsLog): AwsAction[A, Unit] =
    AwsAction(_ => (Vector(message), Attempt.ok(())))

  def unlog(result: (Vector[AwsLog], Attempt[A])): Attempt[A] = result match {
    case (log, attempt) => attempt
  }

  /** Turn a List of AwsActions into an AwsAction of List, ignoring errors */
  def aggregateActions[B](as: List[AwsAction[A, B]], ef: These[String, Throwable] => Unit): AwsAction[A, List[B]] =
    AwsAction(a =>
      as.map(_.run(a)).foldLeft((Vector[AwsLog](), Attempt.ok(List[B]()))) {
        case ((alog, aattp), (log, Attempt.Ok(b)))    => (alog ++ log, aattp.map(_ :+ b))
        case ((alog, aattp), (log, Attempt.Error(e))) => { ef(e); (alog ++ log, aattp) }
      })

}

object S3Action extends AwsActions[AmazonS3Client] {
  def apply[A](f: AmazonS3Client => A) =
    AwsAction.withClient(f)

  def S3ActionMonad: Monad[S3Action] = AwsAction.AwsActionMonad[AmazonS3Client]
}

object EC2Action extends AwsActions[AmazonEC2Client] {
  def apply[A](f: AmazonEC2Client => A) =
    AwsAction.withClient(f)

  def EC2ActionMonad: Monad[EC2Action] = AwsAction.AwsActionMonad[AmazonEC2Client]
}

object IAMAction extends AwsActions[AmazonIdentityManagementClient] {
  def apply[A](f: AmazonIdentityManagementClient => A) =
    AwsAction.withClient(f)

  def IAMActionMonad: Monad[IAMAction] = AwsAction.AwsActionMonad[AmazonIdentityManagementClient]
}
