package com.ambiata.saws
package core

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.s3.AmazonS3Client
import scalaz._, Scalaz._, \&/._

case class AwsAction[A, +B](run: A => (Vector[AwsLog], AwsAttempt[B])) {
  def map[C](f: B => C): AwsAction[A, C] =
    flatMap[C](f andThen AwsAction.ok)

  def mapError[C](f: These[String, Throwable] => These[String, Throwable]): AwsAction[A, B] =
    AwsAction[A, B](a => run(a) match { case (log, att) => (log, att.mapError(f)) })

  def contramap[C](f: C => A): AwsAction[C, B] =
    AwsAction(c => run(f(c)))

  def flatMap[C](f: B => AwsAction[A, C]): AwsAction[A, C] =
    AwsAction[A, C](a => run(a) match {
      case (log, AwsAttempt(\/-(b))) =>
        f(b).run(a) match {
          case (log2, result) => (log ++ log2, result)
        }
      case (log, AwsAttempt(-\/(e))) =>
        (log, AwsAttempt(-\/(e)))
    }).safe

  def attempt[C](f: B => AwsAttempt[C]): AwsAction[A, C] =
    flatMap(f andThen AwsAction.attempt)

  def safe: AwsAction[A, B] =
    AwsAction(a => AwsAttempt.safe(run(a)) match {
      case AwsAttempt(-\/(err)) => (Vector(), AwsAttempt(-\/(err)))
      case AwsAttempt(\/-(ok))   => ok
    })

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
    run(a)._2

  def executeS3(implicit ev: AmazonS3Client =:= A) =
    runS3._2

  def executeEC2(implicit ev: AmazonEC2Client =:= A) =
    runEC2._2

  def executeIAM(implicit ev: AmazonIdentityManagementClient =:= A) =
    runIAM._2

  def executeS3EC2(implicit ev: (AmazonS3Client, AmazonEC2Client) =:= A) =
    runS3EC2._2

  def executeEC2IAM(implicit ev: (AmazonEC2Client, AmazonIdentityManagementClient) =:= A) =
    runEC2IAM._2

  def executeS3EC2IAM(implicit ev: (AmazonS3Client, AmazonEC2Client, AmazonIdentityManagementClient) =:= A) =
    runS3EC2IAM._2
}

object AwsAction {
  def config[A]: AwsAction[A, A] =
    AwsAction(a => (Vector(), AwsAttempt.ok(a)))

  def ok[A, B](strict: B): AwsAction[A, B] =
    value(strict)

  def attempt[A, B](value: AwsAttempt[B]): AwsAction[A, B] =
    AwsAction(_ => (Vector(), value))

  def value[A, B](value: => B): AwsAction[A, B] =
    AwsAction(_ => (Vector(), AwsAttempt.ok(value)))

  def exception[A, B](t: Throwable): AwsAction[A, B] =
    AwsAction(_ => (Vector(), AwsAttempt.exception(t)))

  def fail[A, B](message: String): AwsAction[A, B] =
    AwsAction(_ => (Vector(), AwsAttempt.fail(message)))

  def error[A, B](message: String, t: Throwable): AwsAction[A, B] =
    AwsAction(_ => (Vector(), AwsAttempt.error(message, t)))

  def withClient[A, B](f: A => B): AwsAction[A, B] =
    config[A].map(f)

  def attemptWithClient[A, B](f: A => AwsAttempt[B]): AwsAction[A, B] =
    config[A].attempt(f)

  def log[A](message: AwsLog): AwsAction[A, Unit] =
    AwsAction(_ => (Vector(message), AwsAttempt.ok(())))

  implicit def AwsActionMonad[A]: Monad[({ type l[a] = AwsAction[A, a] })#l] =
    new Monad[({ type L[a] = AwsAction[A, a] })#L] {
      def point[B](v: => B) = AwsAction.ok(v)
      def bind[B, C](m: AwsAction[A, B])(f: B => AwsAction[A, C]) = m.flatMap(f)
    }
}

object S3Action {
  def apply[A](f: AmazonS3Client => A) =
    AwsAction.withClient(f)
}

object EC2Action {
  def apply[A](f: AmazonEC2Client => A) =
    AwsAction.withClient(f)
}

object IAMAction {
  def apply[A](f: AmazonIdentityManagementClient => A) =
    AwsAction.withClient(f)
}
