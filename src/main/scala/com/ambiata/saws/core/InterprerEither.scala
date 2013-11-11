package com.ambiata.saws
package core

import com.ambiata.saws.s3._
import com.ambiata.saws.ec2._
import com.ambiata.saws.iam._
import scalaz._, Scalaz._, \&/._

object InterpretEither {
  type Result[A] = These[String, Throwable] \/ A

  def s3[A](action: S3Action[A]): Result[A] =
    Interpret.s3(action)._2.run

  def ec2[A](action: EC2Action[A]): Result[A] =
    Interpret.ec2(action)._2.run

  def iam[A](action: IAMAction[A]): Result[A] =
    Interpret.iam(action)._2.run

  def s3ec2[A](action: S3EC2Action[A]): Result[A] =
    Interpret.s3ec2(action)._2.run

  def ec2iam[A](action: EC2IAMAction[A]): Result[A] =
    Interpret.ec2iam(action)._2.run

  def s3ec2iam[A](action: S3EC2IAMAction[A]): Result[A] =
    Interpret.s3ec2iam(action)._2.run
}
