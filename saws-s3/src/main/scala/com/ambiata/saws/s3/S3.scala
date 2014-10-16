package com.ambiata.saws.s3

import com.amazonaws.services.s3.model.{ObjectMetadata, Bucket}
import com.ambiata.saws.core._

import scalaz._, Scalaz._
import scala.collection.JavaConversions._

object S3 {

  def listBuckets: S3Action[List[Bucket]] =
    S3Action(_.listBuckets.toList).onResult(_.prependErrorMessage(s"Could access the buckets list"))

  def isS3Accessible: S3Action[Unit] =
    listBuckets.map(_ => ()).orElse(S3Action.fail("S3 is not accessible"))

  /** Object metadata that enables AES256 server-side encryption. */
  def ServerSideEncryption: ObjectMetadata = {
    val m = new ObjectMetadata
    m.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
    m
  }

  val NoTick: Function0[Unit] = () => ()
}
