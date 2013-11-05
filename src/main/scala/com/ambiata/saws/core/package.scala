package com.ambiata.saws

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient

import scalaz._, Scalaz._

package object core {
  type S3Action[A] = AwsAction[AmazonS3Client, A]
  type EC2Action[A] = AwsAction[AmazonEC2Client, A]
  type IAMAction[A] = AwsAction[AmazonIdentityManagementClient, A]
  type S3EC2Action[A] = AwsAction[(AmazonS3Client, AmazonEC2Client), A]

  implicit def S3EC2ActionInstances: MonadS3[S3EC2Action] with MonadEC2[S3EC2Action] =
    new MonadS3[S3EC2Action] with MonadEC2[S3EC2Action] {
      def liftS3[A](f: S3Action[A]) = AwsAction({ case (s3, ec2) => f.run(s3) })
      def liftEC2[A](f: EC2Action[A]) =  AwsAction({ case (s3, ec2) => f.run(ec2) })
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
}
