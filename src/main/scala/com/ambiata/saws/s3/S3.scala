package com.ambiata.saws
package s3

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{ObjectMetadata, S3Object, S3ObjectSummary, ObjectListing, PutObjectResult, PutObjectRequest}
import com.amazonaws.AmazonServiceException
import com.ambiata.saws.core._
import com.ambiata.mundane.io.{Files, Streams}

import java.io.{InputStream, FileInputStream, File}
import java.io.ByteArrayInputStream

import scala.io.Source
import scala.collection.JavaConverters._
import scalaz._, Scalaz._
import scala.annotation.tailrec

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
    AwsAction.withClient[AmazonS3Client, S3Object](_.getObject(bucket, key))
             .mapError(AwsAttempt.prependThis(_, s"Could not get S3://${bucket}/${key}"))

  def getBytes(bucket: String, key: String): S3Action[Array[Byte]] =
    getStream(bucket, key).map(Streams.bytes(_))

  def getStream(bucket: String, key: String): S3Action[InputStream] =
    getObject(bucket, key).map(_.getObjectContent)

  def storeObject(bucket: String, key: String, file: File): S3Action[File] = for {
    is <- getStream(bucket, key)
    s  <- Files.writeInputStreamToFile(is, file) match {
      case -\/(e) => AwsAction.fail[AmazonS3Client, File](e)
      case \/-(f) => AwsAction.ok[AmazonS3Client, File](f)
    }
  } yield s

  def readLines(bucket: String, key: String): S3Action[Seq[String]] =
    getStream(bucket, key).map {x =>
      val source = Source.fromInputStream(x)
      val lines = source.getLines.toSeq
      lines.length
      source.close()
      lines
    }

  def putStream(bucket: String, key: String,  stream: InputStream, metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    AwsAction.withClient[AmazonS3Client, PutObjectResult](_.putObject(bucket, key, stream, metadata))
             .mapError(AwsAttempt.prependThis(_, s"Could not put stream to S3://${bucket}/${key}"))

  def putFile(bucket: String, key: String, file: File, metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    AwsAction.withClient[AmazonS3Client, PutObjectResult](_.putObject(new PutObjectRequest(bucket, key, file).withMetadata(metadata)))
             .mapError(AwsAttempt.prependThis(_, s"Could not put file to S3://${bucket}/${key}"))

  /** If file is a directory, recursivly put all files and dirs under it on S3. If file is a file, put that file on S3. */
  def putFiles(bucket: String, prefix: String, file: File, metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[List[PutObjectResult]] =
    if(file.isDirectory)
      file.listFiles.toList.traverse(f => putFiles(bucket, prefix + "/" + f.getName, f, metadata)).map(_.flatten)
    else
      putFile(bucket, prefix, file, metadata).map(List(_))

  def writeLines(bucket: String, key: String, lines: Seq[String], metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    putStream(bucket, key, new ByteArrayInputStream(lines.mkString("\n").getBytes), metadata) // TODO: Fix ram use

  def listSummary(bucket: String, prefix: String = ""): S3Action[List[S3ObjectSummary]] =
    AwsAction.config[AmazonS3Client].flatMap(client => {
      @tailrec
      def allObjects(prevObjectsListing : => ObjectListing, objects : List[S3ObjectSummary]): S3Action[List[S3ObjectSummary]] = {
        val previousListing = prevObjectsListing
        val previousObjects = previousListing.getObjectSummaries.asScala.toList
        if (previousListing.isTruncated())
          allObjects(client.listNextBatchOfObjects(previousListing), objects ++ previousObjects)
        else
          AwsAction.ok(objects ++ previousObjects)
      }
      allObjects(client.listObjects(bucket, prefix), List())
    })

  def listKeys(bucket: String, prefix: String = ""): S3Action[List[String]] =
    listSummary(bucket, prefix).map(_.map(_.getKey))

  def exists(bucket: String, key: String): S3Action[Boolean] =
    AwsAction { client: AmazonS3Client =>
      try {
        client.getObject(bucket, key)
        (Vector(), AwsAttempt.ok(true))
      } catch {
        case ase: AmazonServiceException => (Vector(), if (ase.getErrorCode == "NoSuchKey") AwsAttempt.ok(false) else AwsAttempt.exception(ase))
        case t: Throwable                => (Vector(), AwsAttempt.exception(t))
      }
    }

  def existsInBucket(bucket: String, filter: String => Boolean): S3Action[Boolean] =
    listSummary(bucket).map(_.exists(o => filter(o.getKey)))

  def deleteObject(bucket: String, key: String): S3Action[Unit] =
    AwsAction.withClient[AmazonS3Client, Unit](_.deleteObject(bucket, key))
             .mapError(AwsAttempt.prependThis(_, s"Could not delete S3://${bucket}/${key}"))

  def deleteObjects(bucket: String, f: String => Boolean = (s: String) => true): S3Action[Unit] =
    listSummary(bucket, "").flatMap(_.collect { case o if(f(o.getKey)) => deleteObject(bucket, o.getKey) }.sequence.map(_ => ()))

  def md5(bucket: String, key: String): S3Action[String] =
    AwsAction.withClient[AmazonS3Client, String](_.getObjectMetadata(bucket, key).getETag)
             .mapError(AwsAttempt.prependThis(_, s"Could not get md5 of S3://${bucket}/${key}"))

  /** Object metadata that enables AES256 server-side encryption. */
  def ServerSideEncryption: ObjectMetadata = {
    val m = new ObjectMetadata
    m.setServerSideEncryption(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
    m
  }
}
