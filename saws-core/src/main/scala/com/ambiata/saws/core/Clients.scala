package com.ambiata.saws
package core

import com.ambiata.com.amazonaws.AmazonWebServiceClient
import com.ambiata.com.amazonaws.services.s3.AmazonS3Client
import com.ambiata.com.amazonaws.services.ec2.AmazonEC2Client
import com.ambiata.com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.ambiata.com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient
import com.ambiata.com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient

object Clients {
  def s3  = configured(new AmazonS3Client(), "s3-ap-southeast-2.amazonaws.com")
  def ec2 = configured(new AmazonEC2Client(), "ec2.ap-southeast-2.amazonaws.com")
  def iam = configured(new AmazonIdentityManagementClient(), "https://iam.amazonaws.com")
  def emr = configured(new AmazonElasticMapReduceClient(), "elasticmapreduce.ap-southeast-2.amazonaws.com")
  def ses = configured(new AmazonSimpleEmailServiceClient(), "email.us-east-1.amazonaws.com")

  def configured[A <: AmazonWebServiceClient](a: A, endpoint: String): A = {
    a.setEndpoint(endpoint)
    a
  }
}
