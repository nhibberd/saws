package com.ambiata.saws
package ec2

import com.amazonaws.services.ec2.AmazonEC2Client


/** Sydney-region IAM client. */
object EC2 {
  val Ec2Endpoint = "ec2.ap-southeast-2.amazonaws.com"

  def apply(): AmazonEC2Client = {
    val c = new AmazonEC2Client()
    c.setEndpoint(Ec2Endpoint)
    c
  }
}