package com.ambiata.saws
package s3

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{ObjectMetadata, S3Object, S3ObjectSummary}
import com.ambiata.saws.core._
import com.ambiata.mundane.io.Streams

import java.io.InputStream

import scala.io.Source
import scala.collection.JavaConverters._
import scalaz._, Scalaz._

/** Wrapper for Java S3 client. */
case class S3(client: AmazonS3Client)

/** Sydney-region S3 client. */
object S3 {
  val S3Endpoint = "s3-ap-southeast-2.amazonaws.com"
  def apply(): S3 = {
    val c = new AmazonS3Client()
    c.setEndpoint(S3Endpoint)
    S3(c)
  }

  def getObject(bucket: String, key: String): S3Action[S3Object] =
    AwsAction.withClient(_.getObject(bucket, key))

  def getBytes(bucket: String, key: String): S3Action[Array[Byte]] =
    getStream(bucket, key).map(Streams.bytes(_))

  def getStream(bucket: String, key: String): S3Action[InputStream] =
    getObject(bucket, key).map(_.getObjectContent)

  def readLines(bucket: String, key: String): S3Action[Seq[String]] =
    getStream(bucket, key).map(Source.fromInputStream(_).getLines.toSeq)

  def listSummary(bucket: String, prefix: String): S3Action[List[S3ObjectSummary]] =
    AwsAction.withClient(client =>
      client.listObjects(bucket, prefix).getObjectSummaries.asScala.toList)

  def listKeys(bucket: String, prefix: String): S3Action[List[String]] =
    listSummary(bucket, prefix).map(_.map(_.getKey))

  /** Object metadata that enables AES256 server-side encryption. */
  def ServerSideEncryption: ObjectMetadata = {
    val m = new ObjectMetadata
    m.setServerSideEncryption(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
    m
  }
}

