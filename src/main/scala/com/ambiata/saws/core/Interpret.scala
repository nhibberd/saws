package com.ambiata.saws
package core

import com.ambiata.saws.s3._
import com.ambiata.saws.ec2._
import com.ambiata.saws.iam._

object Interpret {
  type Result[A] = (Vector[AwsLog], AwsAttempt[A])

  def s3[A](action: S3Action[A]): Result[A] =
    action.run(S3().client)

  def ec2[A](action: EC2Action[A]): Result[A] =
    action.run(EC2().client)

  def iam[A](action: IAMAction[A]): Result[A] =
    action.run(IAM().client)

  def s3ec2[A](action: S3EC2Action[A]): Result[A] =
    action.run(S3().client -> EC2().client)

  def ec2iam[A](action: EC2IAMAction[A]): Result[A] =
    action.run(EC2().client -> IAM().client)
}
