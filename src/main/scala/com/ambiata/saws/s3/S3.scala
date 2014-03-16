package com.ambiata.saws
package s3

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.amazonaws.AmazonServiceException
import com.ambiata.saws.core._
import com.ambiata.mundane.io.{Directories, Files, Streams}

import java.io._

import scala.io.Source
import scala.collection.JavaConverters._
import scalaz._, Scalaz._
import scalaz.effect._
import scala.annotation.tailrec

object S3 {
  def getObject(bucket: String, key: String): S3Action[S3Object] =
    S3Action(_.getObject(bucket, key)).onResult(_.prependErrorMessage(s"Could not get S3://${bucket}/${key}"))

  def getBytes(bucket: String, key: String): S3Action[Array[Byte]] =
    withStream(bucket, key, is => Streams.bytes(is))

  def getString(bucket: String, key: String): S3Action[String] =
    withStream(bucket, key, is => Streams.read(is))

  def withStream[A](bucket: String, key: String, f: InputStream => A): S3Action[A] =
    getObject(bucket, key).map(o => f(o.getObjectContent))

  def storeObject(bucket: String, key: String, file: File, mkdirs: Boolean = false): S3Action[File] = for {
    is <- withStream(bucket, key, identity)
    s  <- Aws.fromDisjunctionString(
      Files.writeInputStreamToFile(is, file, mkdirs)
    )
  } yield s

  def readLines(bucket: String, key: String): S3Action[Seq[String]] =
    withStream(bucket, key, x => {
      val source = Source.fromInputStream(x)
      try {
        val lines = source.getLines.toSeq
        lines.length
        lines
      } finally source.close()
    })

  def downloadFile(bucket: String, key: String, to: String = "./"): S3Action[File] =
    S3.withStream(bucket, key, stream => {
      val file = new File(to+key)
      file.getParentFile.mkdirs
      Streams.pipeToFile(stream, new File(file.getCanonicalPath))
      file
    })

  def putString(bucket: String, key: String,  data: String, metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    putBytes(bucket, key, data.getBytes("UTF-8"), metadata)

  def putBytes(bucket: String, key: String,  data: Array[Byte], metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    putStream(bucket, key, new ByteArrayInputStream(data), metadata <| (_.setContentLength(data.length)))

  def putStream(bucket: String, key: String,  stream: InputStream, metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    S3Action(_.putObject(bucket, key, stream, metadata))
             .onResult(_.prependErrorMessage(s"Could not put stream to S3://${bucket}/${key}"))

  def putFile(bucket: String, key: String, file: File, metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    S3Action(_.putObject(new PutObjectRequest(bucket, key, file).withMetadata(metadata)))
             .onResult(_.prependErrorMessage(s"Could not put file to S3://${bucket}/${key}"))

  /** If file is a directory, recursivly put all files and dirs under it on S3. If file is a file, put that file on S3. */
  def putFiles(bucket: String, prefix: String, file: File, metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[List[PutObjectResult]] =
    if(file.isDirectory)
      file.listFiles.toList.traverse(f => putFiles(bucket, prefix + "/" + f.getName, f, metadata)).map(_.flatten)
    else
      putFile(bucket, prefix, file, metadata).map(List(_))

  /** copy an object from s3 to s3, without downloading the object */
  // metadata disabled, since it copies the old objects metadata
  def copyFile(fromBucket: String, fromKey: String, toBucket: String, toKey: String /*, metadata: ObjectMetadata = S3.ServerSideEncryption*/): S3Action[CopyObjectResult] =
    S3Action(_.copyObject(new CopyObjectRequest(fromBucket, fromKey, toBucket, toKey)))/*.withNewObjectMetadata(metadata) */
      .onResult(_.prependErrorMessage(s"Could not copy object from S3://${fromBucket}/${fromKey} to S3://${toBucket}/${toKey}"))

  def writeLines(bucket: String, key: String, lines: Seq[String], metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    putStream(bucket, key, new ByteArrayInputStream(lines.mkString("\n").getBytes), metadata) // TODO: Fix ram use

  def listSummary(bucket: String, prefix: String = ""): S3Action[List[S3ObjectSummary]] =
    S3Action(client => {
      @tailrec
      def allObjects(prevObjectsListing : => ObjectListing, objects : List[S3ObjectSummary]): S3Action[List[S3ObjectSummary]] = {
        val previousListing = prevObjectsListing
        val previousObjects = previousListing.getObjectSummaries.asScala.toList
        if (previousListing.isTruncated())
          allObjects(client.listNextBatchOfObjects(previousListing), objects ++ previousObjects)
        else
          S3Action.ok(objects ++ previousObjects)
      }
      allObjects(client.listObjects(bucket, prefix), List())
    }).flatMap(x => x)

  def listKeys(bucket: String, prefix: String = ""): S3Action[List[String]] =
    listSummary(bucket, prefix).map(_.map(_.getKey))

  /** use this method to make sure that a prefix ends with a slash */
  def directory(prefix: String) = prefix + (if (prefix.endsWith("/")) "" else "/")

  def exists(bucket: String, key: String): S3Action[Boolean] =
    S3Action((client: AmazonS3Client) => try {
        client.getObject(bucket, key)
        S3Action.ok(true)
      } catch {
        case ase: AmazonServiceException =>
          if (ase.getErrorCode == "NoSuchKey") S3Action.ok(false) else S3Action.exception(ase)
        case t: Throwable =>
          S3Action.exception(t)
      }).join

  def existsInBucket(bucket: String, filter: String => Boolean): S3Action[Boolean] =
    listSummary(bucket).map(_.exists(o => filter(o.getKey)))

  def deleteObject(bucket: String, key: String): S3Action[Unit] =
    S3Action(_.deleteObject(bucket, key))
             .onResult(_.prependErrorMessage(s"Could not delete S3://${bucket}/${key}"))

  def deleteObjects(bucket: String, f: String => Boolean = (s: String) => true): S3Action[Unit] =
    listSummary(bucket, "").flatMap(_.collect { case o if(f(o.getKey)) => deleteObject(bucket, o.getKey) }.sequence.map(_ => ()))

  def md5(bucket: String, key: String): S3Action[String] =
    S3Action(_.getObjectMetadata(bucket, key).getETag)
             .onResult(_.prependErrorMessage(s"Could not get md5 of S3://${bucket}/${key}"))

  def extractTarball(bucket: String, key: String, local: File, stripLevels: Int): S3Action[File] =
    withStream(bucket, key, is => Files.extractTarballStream(is, local, stripLevels)).flatMap(Aws.fromDisjunctionString)

  def extractTarballFlat(bucket: String, key: String, local: File): S3Action[File] =
    withStream(bucket, key, is => Files.extractTarballStreamFlat(is, local)).flatMap(Aws.fromDisjunctionString)

  def extractGzip(bucket: String, key: String, local: File): S3Action[File] =
    withStream(bucket, key, is => Files.extractGzipStream(is, local)).flatMap(Aws.fromDisjunctionString)

  def mirror(base: File, bucket: String, keybase: String): S3Action[Unit] = for {
    local <- S3Action.io(_ => Directories.list(base))
    _     <- local.traverse({ source =>
      val destination = source.getAbsolutePath.replace(base.getAbsolutePath + "/", "")
      S3.putFile(bucket, s"${keybase}/${destination}", source)
    })
  } yield ()

  def deleteAll(bucket: String, keybase: String): S3Action[Unit] = for {
    all <- listSummary(bucket, keybase)
    _   <- all.traverse(obj => deleteObject(bucket, obj.getKey))
  } yield ()

  /** Object metadata that enables AES256 server-side encryption. */
  def ServerSideEncryption: ObjectMetadata = {
    val m = new ObjectMetadata
    m.setServerSideEncryption(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
    m
  }
}
