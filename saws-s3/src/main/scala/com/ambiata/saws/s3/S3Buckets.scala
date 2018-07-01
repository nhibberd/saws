package com.ambiata.saws
package s3

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.ambiata.saws.core._

import scala.collection.JavaConverters._
import scalaz._, Scalaz._

object S3Buckets {
  val VersioningEnabled = new BucketVersioningConfiguration(BucketVersioningConfiguration.ENABLED)
  val VersioningSuspended = new BucketVersioningConfiguration(BucketVersioningConfiguration.SUSPENDED)

  def list: S3Action[List[Bucket]] =
    S3Action(client => client.listBuckets.asScala.toList)

  def findByName(name: String): S3Action[Option[Bucket]] =
    list.map(_.find(_.getName == name))

  def exists(name: String): S3Action[Boolean] =
    S3Action(client => client.doesBucketExist(name))

  def ensure(name: String): S3Action[Bucket] = for {
    e <- exists(name)
    b <- if(e) new Bucket(name).pure[S3Action] else create(name)
  } yield b

  def create(name: String): S3Action[Bucket] =
    S3Action(client => client.createBucket(new CreateBucketRequest(name, Region.AP_Sydney))) <*
      AwsLog.CreateBucket(name).log

  def isVersioned(bucket: String): S3Action[Boolean] =
    S3Action(client =>
      client
        .getBucketVersioningConfiguration(bucket)
        .getStatus == BucketVersioningConfiguration.ENABLED)

  def enableVersioning(bucket: String): S3Action[Unit] =
    setBucketVersioningConfiguration(bucket, VersioningEnabled)

  def disableVersioning(bucket: String): S3Action[Unit] = for {
    v <- isVersioned(bucket)
    _ <- S3Action.when(v, setBucketVersioningConfiguration(bucket, VersioningSuspended))
  } yield ()

  def setBucketVersioningConfiguration(bucket: String, configuration: BucketVersioningConfiguration): S3Action[Unit] =
    S3Action(client =>
      client.setBucketVersioningConfiguration(
        new SetBucketVersioningConfigurationRequest(bucket, configuration)))
}
