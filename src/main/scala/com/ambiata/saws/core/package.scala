package com.ambiata.saws

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient

package object core {
  type S3Action[A] = AwsAction[AmazonS3Client, A]
  type EC2Action[A] = AwsAction[AmazonEC2Client, A]
  type IAMAction[A] = AwsAction[AmazonIdentityManagementClient, A]
  type S3EC2Action[A] = AwsAction[(AmazonS3Client, AmazonEC2Client), A]

  object S3Action extends AwsActionTemplate[AmazonS3Client]
  object EC2Action extends AwsActionTemplate[AmazonEC2Client]
  object IAMAction extends AwsActionTemplate[AmazonIdentityManagementClient]
  object S3EC2Action extends AwsActionTemplate[(AmazonS3Client, AmazonEC2Client)]
}
