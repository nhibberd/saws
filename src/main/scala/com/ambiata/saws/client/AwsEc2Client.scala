package com.ambiata
package saws
package client

import scalaz._
import scala.util.{Try, Success, Failure}
import com.amazonaws.services.ec2.AmazonEC2Client


/** Sydney-region EC2 client. */
object AwsEc2Client {
  import com.ambiata.saws.ec2.EC2
  def create: AmazonEC2Client = EC2.create()
}


/** Return type used for calls made against AWS APIs. */
object AwsAttempt {
  type AwsAttempt[A] = String \/ A

  def apply[A](thunk: => A): AwsAttempt[A] = Try(thunk) match {
    case Success(a) => \/-(a)
    case Failure(e) => -\/(e.getMessage)
  }
}