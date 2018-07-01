package com.ambiata.saws

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient

import scalaz._, Scalaz._
import scalaz.effect._
import com.ambiata.mundane.control._

package object core {
  type Log = Vector[AwsLog]
  type AwsActionResult[A] = (Vector[AwsLog], Result[A])

  type S3Action[A] = Aws[AmazonS3Client, A]
  type EC2Action[A] = Aws[AmazonEC2Client, A]
  type IAMAction[A] = Aws[AmazonIdentityManagementClient, A]
  type EMRAction[A] = Aws[AmazonElasticMapReduceClient, A]
  type CloudWatchAction[A] = Aws[AmazonCloudWatchClient, A]
  type S3EC2Action[A] = Aws[(AmazonS3Client, AmazonEC2Client), A]
  type EC2IAMAction[A] = Aws[(AmazonEC2Client, AmazonIdentityManagementClient), A]
  type S3EC2IAMAction[A] = Aws[(AmazonS3Client, AmazonEC2Client, AmazonIdentityManagementClient), A]
  type IAMEMRAction[A] = Aws[(AmazonIdentityManagementClient, AmazonElasticMapReduceClient), A]

  type S3EC2 = (AmazonS3Client, AmazonEC2Client)
  type EC2IAM = (AmazonEC2Client, AmazonIdentityManagementClient)
  type S3EC2IAM = (AmazonS3Client, AmazonEC2Client, AmazonIdentityManagementClient)
  type IAMEMR = (AmazonIdentityManagementClient, AmazonElasticMapReduceClient)

}
