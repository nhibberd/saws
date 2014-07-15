package com.ambiata.saws
package core

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient

case class Client[A](get: () => A)

object Client {
  def apply[A: Client] = implicitly[Client[A]]

  implicit def AmazonEC2ClientInstance: Client[AmazonEC2Client] = Client(() => Clients.ec2)
  implicit def AmazonS3ClientInstance: Client[AmazonS3Client] = Client(() => Clients.s3)
  implicit def AmazonIdentityManagementClientInstance: Client[AmazonIdentityManagementClient] = Client(() => Clients.iam)
  implicit def AmazonElasticMapReduceClientInstance: Client[AmazonElasticMapReduceClient] = Client(() => Clients.emr)
  implicit def Tuple2Client[A: Client, B: Client]: Client[(A, B)] = Client(() => (Client[A].get(), Client[B].get()))
  implicit def Tuple3Client[A: Client, B: Client, C: Client]: Client[(A, B, C)] = Client(() => (Client[A].get(), Client[B].get(), Client[C].get()))
  implicit def Tuple4Client[A: Client, B: Client, C: Client, D: Client]: Client[(A, B, C, D)] = Client(() => (Client[A].get(), Client[B].get(), Client[C].get(), Client[D].get()))
  implicit def Tuple5Client[A: Client, B: Client, C: Client, D: Client, E: Client]: Client[(A, B, C, D, E)] = Client(() => (Client[A].get(), Client[B].get(), Client[C].get(), Client[D].get(), Client[E].get()))
}
