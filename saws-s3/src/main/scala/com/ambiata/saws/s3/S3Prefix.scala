package com.ambiata.saws.s3

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.ambiata.mundane.io.{FilePath, DirPath, Directories}
import com.ambiata.saws.core._
import com.ambiata.saws.s3.{S3Operations => Op}

import scalaz._, Scalaz._, effect._, stream._, concurrent.Task

import scala.collection.JavaConverters._
import scala.annotation.tailrec

/**
 * Representation of a prefix on S3 designated by a `bucket` and a `prefix`.
 *
 */
case class S3Prefix(bucket: String, prefix: String) {
  def removeCommonPrefix(data: S3Prefix): Option[String] =
    Op.removeCommonPrefix(bucket, prefix, data.bucket, data.prefix)

  def render: String =
    Op.render(bucket, prefix)

  def awsPrefix: String = {
    if (prefix.endsWith(Op.DELIMITER) || prefix.isEmpty)
      prefix
    else
      prefix + "/"
  }

  def /(suffix: String): S3Prefix =
    S3Prefix(bucket, Op.concat(prefix, suffix))

  def |(suffix: String): S3Address =
    S3Address(bucket, Op.concat(prefix, suffix))

  def size: S3Action[Long] =
    listS3.map(_.map(_.size)).map(_.sum)

// ------ Write

  def putFiles(dir: DirPath): S3Action[Unit] =
    pufFilesWithMetadata(dir, S3.ServerSideEncryption)

  def pufFilesWithMetadata(dir: DirPath, metadata: ObjectMetadata): S3Action[Unit] = for {
    local <- S3Action.fromResultT(Directories.list(dir))
    _     <- local.traverse({ source =>
      val destination = source.toFile.getAbsolutePath.replace(dir.toFile.getAbsolutePath + "/", "")
      (this | destination).putFile(source)
    })
  } yield ()

// ------ Read

  def getFiles(dir: DirPath): S3Action[List[FilePath]] =
    getFilesWithMetadata(dir, S3.ServerSideEncryption)

  def getFilesWithMetadata(dir: DirPath, metadata: ObjectMetadata): S3Action[List[FilePath]] = for {
    l <- listSummary
    z <- l.traverse({ obj =>
      val destination = dir </> FilePath.unsafe(obj.getKey.replace(awsPrefix, ""))
      S3Address(obj.getBucketName, obj.getKey).getFile(destination)
    })
  } yield z

  def listS3: S3Action[List[SizedS3Address]] =
    listSummary.map(_.map(z => SizedS3Address(S3Address(bucket, z.getKey), z.getSize)))

  def listSummary: S3Action[List[S3ObjectSummary]] =
    S3Action(client => {
      @tailrec
      def allObjects(prevObjectsListing : => ObjectListing, objects : List[S3ObjectSummary]): S3Action[List[S3ObjectSummary]] = {
        val previousListing = prevObjectsListing
        val previousObjects = previousListing.getObjectSummaries.asScala.toList
        if (previousListing.isTruncated)
          allObjects(client.listNextBatchOfObjects(previousListing), objects ++ previousObjects)
        else
          S3Action.ok(objects ++ previousObjects)
      }
      allObjects(client.listObjects(bucket, awsPrefix), List())
    }).flatMap(x => x)

  def listKeys: S3Action[List[String]] =
    listSummary.map(_.map(_.getKey))

  def listKeysHead: S3Action[List[String]] =
    S3Action(client => {
      val request = new ListObjectsRequest(bucket, awsPrefix, null, "/", null)
      val common = client.listObjects(request).getCommonPrefixes.asScala.toList
      val prefixes = common.flatMap(_.split("/").lastOption)
      prefixes
    })

  def exists: S3Action[Boolean] =
    S3Action((client: AmazonS3Client) => try {
      val request = new ListObjectsRequest(bucket, awsPrefix, null, "/", null)
      request.setMaxKeys(1)
      val list = client.listObjects(request)
      val summaries = list.getObjectSummaries
      S3Action.ok(summaries.asScala.nonEmpty)
    } catch {
      case ase: AmazonServiceException =>
        if (ase.getErrorCode == "NoSuchBucket") S3Action.ok(false) else S3Action.exception[Boolean](ase)
      case t: Throwable =>
        S3Action.exception[Boolean](t)
    }).join

  def delete: S3Action[Unit] =
    listSummary >>=
      (_.traverse(obj => S3Address(bucket, obj.getKey).delete).void)
}

object S3Prefix {
  def fromUri(uri: String): S3Action[Option[S3Prefix]] = {
    S3Pattern.fromURI(uri).traverseU(_.determine.map(_.flatMap(_.toOption))).map(_.flatten)
  }
}