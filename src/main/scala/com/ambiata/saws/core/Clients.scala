package com.ambiata.saws
package core

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient

object Clients {
  def s3  = configured(new AmazonS3Client(), "s3-ap-southeast-2.amazonaws.com")
  def ec2 = configured(new AmazonEC2Client(), "ec2.ap-southeast-2.amazonaws.com")
  def iam = configured(new AmazonIdentityManagementClient(), "https://iam.amazonaws.com")

  def configured[A <: AmazonWebServiceClient](a: A, endpoint: String): A = {
    a.setEndpoint(endpoint)
    a
  }
}
