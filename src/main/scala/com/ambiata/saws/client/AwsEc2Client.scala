package com.ambiata
package saws
package client

import scalaz._
import scala.util.{Try, Success, Failure}
import com.amazonaws.services.ec2.AmazonEC2Client


/** Sydney-region EC2 client. */
object AwsEc2Client {
  val Ec2Endpoint = "ec2.ap-southeast-2.amazonaws.com"

  def create: AmazonEC2Client = {
    val c = new AmazonEC2Client()
    c.setEndpoint(Ec2Endpoint)
    c
  }
}


/** Return type used for calls made against AWS APIs. */
object AwsAttempt {
  type AwsAttempt[A] = String \/ A

  def apply[A](thunk: => A): AwsAttempt[A] = Try(thunk) match {
    case Success(a) => \/-(a)
    case Failure(e) => -\/(e.getMessage)
  }
}