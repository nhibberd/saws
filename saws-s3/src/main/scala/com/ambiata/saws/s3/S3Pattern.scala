package com.ambiata.saws.s3

import com.amazonaws.services.s3.model.{PutObjectResult, ObjectMetadata}
import com.ambiata.mundane.io._
import com.ambiata.saws.core.S3Action
import com.ambiata.saws.s3.{S3Operations => Op}
import com.ambiata.saws.s3.S3Pattern._

import scalaz._, Scalaz._

/**
 * Representation of a unknown location S3 designated by a `bucket` and a `unknown`, which
 * may represent a `S3Prefix` or `S3Address`
 */
case class S3Pattern(bucket: String, unknown: String) {
  def removeCommonPrefix(data: S3Prefix): Option[String] =
    S3Operations.removeCommonPrefix(bucket, unknown, data.bucket, data.prefix)

  def removeCommonPattern(data: S3Pattern): Option[String] =
    S3Operations.removeCommonPrefix(bucket, unknown, data.bucket, data.unknown)

  def render: String =
    Op.render(bucket, unknown)

  def resolve: S3Action[List[S3Address]] =
    listS3.map(_.map(obj => obj.s3))

  def determine: S3Action[Option[S3Address \/ S3Prefix]] =
    S3Address(bucket, unknown).exists.flatMap({
            case true  => S3Address(bucket, unknown).left[S3Prefix].some.pure[S3Action]
            case false =>
              S3Prefix(bucket, unknown).exists.map({
                case true => S3Prefix(bucket, unknown).right[S3Address].some
                case false => None
              })
          })

  def size: S3Action[Option[Long]] =
    determine.flatMap({
      case Some(\/-(v)) => v.size.map(Some(_))
      case Some(-\/(v)) => v.size.map(Some(_))
      case None         => none.pure[S3Action]
    })

  def listS3: S3Action[List[SizedS3Address]] =
    determine.flatMap({
      case Some(\/-(v)) => v.listS3
      case Some(-\/(v)) => v.getS3.map(List(_))
      case None         => nil.pure[S3Action]
    })

  def listKeys: S3Action[List[String]] =
    listS3.map(_.map(_.s3.key))

  def delete: S3Action[Unit] =
    resolve.map(_.map(_.delete))

  def exists: S3Action[Boolean] =
    determine.flatMap({
      case Some(\/-(v)) => v.exists
      case Some(-\/(v)) => v.exists
      case None         => false.pure[S3Action]
    })
}


/**
 *  Note: drop(1) is used on the s3 uri to ensure that the `prefix` / `key` does not start with
 *  a delimiter (`/`). The amazon aws-java-sdk does not support key's starting with a delimiter
 */
object S3Pattern {
  def fromURI(s: String): Option[S3Pattern] = {
    val uri = new java.net.URI(s)
    uri.getScheme match {
      case "s3" =>
        Some(S3Pattern(uri.getHost, uri.getPath.drop(1)))
      case _ =>
        None
    }
  }
}
