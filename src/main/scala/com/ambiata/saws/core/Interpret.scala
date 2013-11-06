package com.ambiata.saws
package core

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient

object Interpret {
  type Result[A] = (Vector[AwsLog], AwsAttempt[A])

  def s3[A](action: S3Action[A]): Result[A] =
    action.run(new AmazonS3Client)

  def ec2[A](action: EC2Action[A]): Result[A] =
    action.run(new AmazonEC2Client)

  def iam[A](action: IAMAction[A]): Result[A] =
    action.run(new AmazonIdentityManagementClient)

  def s3ec2[A](action: S3EC2Action[A]): Result[A] =
    action.run(new AmazonS3Client -> new AmazonEC2Client)

  def ec2iam[A](action: EC2IAMAction[A]): Result[A] =
    action.run(new AmazonEC2Client -> new AmazonIdentityManagementClient)
}
