package com.ambiata.saws

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient

import scalaz._, Scalaz._
import com.ambiata.mundane.control.Attempt

package object core {
  type S3Action[A] = AwsAction[AmazonS3Client, A]
  type EC2Action[A] = AwsAction[AmazonEC2Client, A]
  type IAMAction[A] = AwsAction[AmazonIdentityManagementClient, A]
  type EMRAction[A] = AwsAction[AmazonElasticMapReduceClient, A]
  type S3EC2Action[A] = AwsAction[(AmazonS3Client, AmazonEC2Client), A]
  type EC2IAMAction[A] = AwsAction[(AmazonEC2Client, AmazonIdentityManagementClient), A]
  type S3EC2IAMAction[A] = AwsAction[(AmazonS3Client, AmazonEC2Client, AmazonIdentityManagementClient), A]
  type AwsActionResult[A] = (Vector[AwsLog], Attempt[A])

  implicit def S3EC2ActionInstances: MonadS3[S3EC2Action] with MonadEC2[S3EC2Action] =
    new MonadS3[S3EC2Action] with MonadEC2[S3EC2Action] {
      def liftS3[A](f: S3Action[A]) = AwsAction({ case (s3, ec2) => f.run(s3) })
      def liftEC2[A](f: EC2Action[A]) =  AwsAction({ case (s3, ec2) => f.run(ec2) })
    }

  implicit def EC2IAMActionInstances: MonadIAM[EC2IAMAction] with MonadEC2[EC2IAMAction] =
    new MonadIAM[EC2IAMAction] with MonadEC2[EC2IAMAction] {
      def liftIAM[A](f: IAMAction[A]) = AwsAction({ case (ec2, iam) => f.run(iam) })
      def liftEC2[A](f: EC2Action[A]) =  AwsAction({ case (ec2, iam) => f.run(ec2) })
    }

  implicit def S3EC2IAMActionInstances: MonadS3[S3EC2IAMAction] with MonadIAM[S3EC2IAMAction] with MonadEC2[S3EC2IAMAction] =
    new MonadS3[S3EC2IAMAction] with MonadIAM[S3EC2IAMAction] with MonadEC2[S3EC2IAMAction] {
      def liftS3[A](f: S3Action[A]) = AwsAction({ case (s3, ec2, iam) => f.run(s3) })
      def liftIAM[A](f: IAMAction[A]) = AwsAction({ case (s3, ec2, iam) => f.run(iam) })
      def liftEC2[A](f: EC2Action[A]) =  AwsAction({ case (s3, ec2, iam) => f.run(ec2) })
    }

  implicit class S3ActionSyntax[A](action: S3Action[A]) {
    def liftS3[F[_]: MonadS3] = implicitly[MonadS3[F]].liftS3(action)
  }
  implicit class EC2ActionSyntax[A](action: EC2Action[A]) {
    def liftEC2[F[_]: MonadEC2] = implicitly[MonadEC2[F]].liftEC2(action)
  }
  implicit class IAMActionSyntax[A](action: IAMAction[A]) {
    def liftIAM[F[_]: MonadIAM] = implicitly[MonadIAM[F]].liftIAM(action)
  }

  implicit def AwsActionResultMonad: Monad[AwsActionResult] =
    new Monad[AwsActionResult] {
      def point[A](a: =>A) = (Vector[AwsLog](), Attempt.ok(a))
      def bind[A, B](m: AwsActionResult[A])(f: A => AwsActionResult[B]) =
        m match {
          case (logs, Attempt.Ok(a))    => val (l, r) = f(a); (logs ++ l, r)
          case (logs, Attempt.Error(e)) => (logs, Attempt.these[B](e))
        }
      }

  implicit class AwsActionDisjunctionSyntax[B](d: String \/ B) {
    def toAwsAction[A] = d match {
      case -\/(err) => AwsAction.fail[A, B](err)
      case \/-(b)   => AwsAction.value[A, B](b)
    }
  }
}
