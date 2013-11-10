package com.ambiata.saws
package s3

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.ambiata.saws.core._

import scala.collection.JavaConverters._
import scalaz._, Scalaz._

object S3Buckets {
  def list: S3Action[List[Bucket]] =
    AwsAction.withClient(client => client.listBuckets.asScala.toList)

  def findByName(name: String): S3Action[Option[Bucket]] =
    list.map(_.find(_.getName == name))

  def ensure(name: String): S3Action[Bucket] = for {
    current <- findByName(name)
    bucket  <- current match {
      case None         => create(name)
      case Some(bucket) => bucket.pure[S3Action]
    }
  } yield bucket

  def create(name: String): S3Action[Bucket] =
    AwsAction.withClient((client: AmazonS3Client) => client.createBucket(name, Region.AP_Sydney)) <*
      AwsAction.log(AwsLog.CreateBucket(name))
}
