package com.ambiata.saws
package core

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient
import scalaz._, Scalaz._, \&/._
import scalaz.effect._
import com.ambiata.mundane.control.Attempt

// FIX Remove specialization of IO. This is the first step in making it AwsActionT,
//     letting us add IO without breaking all the other code for now.
case class AwsAction[A, +B](action: A => IO[(Vector[AwsLog], Attempt[B])]) {
  def map[C](f: B => C): AwsAction[A, C] =
    flatMap[C](f andThen AwsAction.ok)

  def mapError(f: These[String, Throwable] => These[String, Throwable]): AwsAction[A, B] =
    AwsAction[A, B](a => action(a).map(aa => aa match { case (log, att) => (log, att.mapError(f)) }))

  def contramap[C](f: C => A): AwsAction[C, B] =
    AwsAction(c => action(f(c)))

  def flatMap[C](f: B => AwsAction[A, C]): AwsAction[A, C] =
    AwsAction[A, C](a => action(a).flatMap(aa => aa match {
      case (log, Attempt(\/-(b))) =>
        f(b).action(a).map(bb => bb match {
          case (log2, result) => (log ++ log2, result)
        })
      case (log, Attempt(-\/(e))) =>
        (log, Attempt(-\/(e))).pure[IO]
    })).safe

  def attempt[C](f: B => Attempt[C]): AwsAction[A, C] =
    flatMap(f andThen AwsAction.attempt)

  def orElse[BB >: B](alt: => BB): AwsAction[A, BB] =
    AwsAction[A, BB](a => action(a).map(aa => aa match {
      case (log, Attempt.Ok(b))    => (log, Attempt.ok(b))
      case (log, Attempt.Error(_)) => (log, Attempt.ok(alt))
    }))

  def onError(f: These[String, Throwable] => AwsAction[A, Unit]): AwsAction[A, Unit] =
    AwsAction(a => action(a).map(aa => aa match {
      case (log, Attempt.Ok(_))    => (log, Attempt.ok(()))
      case (log, Attempt.Error(e)) => f(e).run(a) match {
        case (log2, attp) => (log ++ log2, attp)
      }
    }))

  def safe: AwsAction[A, B] =
    AwsAction(a => action(a).map(aa => Attempt.safe(aa) match {
      case Attempt(-\/(err)) => (Vector(), Attempt(-\/(err)))
      case Attempt(\/-(ok))  => ok
    }))

  def retry(i: Int, lf: (Int, These[String, Throwable]) => Vector[AwsLog] = (_,_) => Vector()): AwsAction[A, B] =
    AwsAction[A, B](a => action(a).flatMap(aa => aa match {
      case (log, Attempt.Ok(b))    => (log, Attempt.ok(b)).pure[IO]
      case (log, Attempt.Error(e)) => if(i > 0) retry(i - 1, lf).action(a).map(rr => rr match {
        case (nlog, nattp) => (log ++ lf(i, e) ++ nlog, nattp)
      }) else (log ++ lf(i, e), Attempt.these(e)).pure[IO]
    }))

  /** after running the action, print the last log message to the console */
  def flush: AwsAction[A, B] =
    AwsAction[A, B](a => action(a).map(aa => aa match {
      case (log, r) => { log.lastOption.foreach(println); (log, r) }
    }))

  def run(a: A): (Vector[AwsLog], Attempt[B]) =
    runIO(a).unsafePerformIO

  def runIO(a: A): IO[(Vector[AwsLog], Attempt[B])] =
    safe.action(a)

  def runS3(implicit ev: AmazonS3Client =:= A) =
    run(Clients.s3)

  def runEC2(implicit ev: AmazonEC2Client =:= A) =
    run(Clients.ec2)

  def runIAM(implicit ev: AmazonIdentityManagementClient =:= A) =
    run(Clients.iam)

  def runEMR(implicit ev: AmazonElasticMapReduceClient =:= A) =
    run(Clients.emr)

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
  // FIX Remove other operations and just use this directly.
  def apply[A] = new AwsActions[A] {}

  def config[A]: AwsAction[A, A]                                       = apply[A].config
  def ok[A, B](strict: B): AwsAction[A, B]                             = apply[A].ok(strict)
  def attempt[A, B](value: Attempt[B]): AwsAction[A, B]                = apply[A].attempt(value)
  def value[A, B](value: => B): AwsAction[A, B]                        = apply[A].value(value)
  def exception[A, B](t: Throwable): AwsAction[A, B]                   = apply[A].exception(t)
  def fail[A, B](message: String): AwsAction[A, B]                     = apply[A].fail(message)
  def error[A, B](message: String, t: Throwable): AwsAction[A, B]      = apply[A].error(message, t)
  def withClient[A, B](f: A => B): AwsAction[A, B]                     = apply[A].withClient(f)
  def attemptWithClient[A, B](f: A => Attempt[B]): AwsAction[A, B]     = apply[A].attemptWithClient(f)
  def log[A](message: AwsLog): AwsAction[A, Unit]                      = apply[A].log(message)
  def unlog[A](result: (Vector[AwsLog], Attempt[A])): Attempt[A]       = apply[A].unlog(result)
  def aggregateActions[A, B](as: List[AwsAction[A, B]], ef: These[String, Throwable] => Unit): AwsAction[A, List[B]] =
    apply[A].aggregateActions(as, ef)

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
    AwsAction(a => (Vector(), Attempt.ok(a)).pure[IO])

  def ok[B](strict: B): AwsAction[A, B] =
    value(strict)

  def attempt[B](value: Attempt[B]): AwsAction[A, B] =
    AwsAction(_ => (Vector(), value).pure[IO])

  def value[B](value: => B): AwsAction[A, B] =
    AwsAction(_ => (Vector(), Attempt.ok(value)).pure[IO])

  def exception[B](t: Throwable): AwsAction[A, B] =
    AwsAction(_ => (Vector(), Attempt.exception(t)).pure[IO])

  def fail[B](message: String): AwsAction[A, B] =
    AwsAction(_ => (Vector(), Attempt.fail(message)).pure[IO])

  def error[B](message: String, t: Throwable): AwsAction[A, B] =
    AwsAction(_ => (Vector(), Attempt.error(message, t)).pure[IO])

  def these[B](t: These[String, Throwable]): AwsAction[A, B] =
    AwsAction(_ => (Vector(), Attempt.these(t)).pure[IO])

  def withClient[B](f: A => B): AwsAction[A, B] =
    config.map(f)

  def attemptWithClient[B](f: A => Attempt[B]): AwsAction[A, B] =
    config.attempt(f)

  def log(message: AwsLog): AwsAction[A, Unit] =
    AwsAction(_ => (Vector(message), Attempt.ok(())).pure[IO])

  def unlog(result: (Vector[AwsLog], Attempt[A])): Attempt[A] = result match {
    case (log, attempt) => attempt
  }

  /** Turn a List of AwsActions into an AwsAction of List, ignoring errors */
  // FIX ef should be IO[Unit]
  def aggregateActions[B](as: List[AwsAction[A, B]], ef: These[String, Throwable] => Unit): AwsAction[A, List[B]] =
    AwsAction(a =>
      as.map(_.run(a)).foldLeft(IO { (Vector[AwsLog](), Attempt.ok(List[B]())) }) {
        case (acc, (log, Attempt.Ok(b)))    => acc.map({ case (alog, results) => (alog ++ log, results.map(_ :+ b))})
        case (acc, (log, Attempt.Error(e))) => acc.map({ case (alog, results) => { ef(e); (alog ++ log, results) }})
      })
}

object S3Action extends AwsActions[AmazonS3Client] {
  def apply[A](f: AmazonS3Client => A) =
    AwsAction.withClient(f)
}

object EC2Action extends AwsActions[AmazonEC2Client] {
  def apply[A](f: AmazonEC2Client => A) =
    AwsAction.withClient(f)
}

object IAMAction extends AwsActions[AmazonIdentityManagementClient] {
  def apply[A](f: AmazonIdentityManagementClient => A) =
    AwsAction.withClient(f)
}

object EMRAction extends AwsActions[AmazonElasticMapReduceClient] {
  def apply[A](f: AmazonElasticMapReduceClient => A) =
    AwsAction.withClient(f)
}
