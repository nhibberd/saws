package com.ambiata.saws.s3

import com.ambiata.saws.s3.{S3Operations => Op}
import com.amazonaws.event.{ProgressEvent, ProgressListener}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.transfer.{TransferManagerConfiguration, TransferManager}
import com.amazonaws.services.s3.transfer.model.UploadResult
import com.ambiata.saws.core._
import com.ambiata.mundane.io._
import com.ambiata.mundane.io.MemoryConversions._
import com.ambiata.mundane.path._
import com.ambiata.mundane.control._
import com.ambiata.mundane.data._

import java.io._

import scala.io.Codec

import scalaz._, Scalaz._, concurrent.Task
import scalaz.\&/._

case class SizedS3Address(s3: S3Address, size: Long)

case class S3UploadResult(etag: String, versionId: String)

/**
 * Representation of an object on S3 designated by a `bucket` and a `key`
 *
 */
case class S3Address(bucket: String, key: String) {
  def toS3Pattern: S3Pattern =
    S3Pattern(bucket, key)

  def removeCommonPrefix(data: S3Prefix): Option[String] =
    Op.removeCommonPrefix(bucket, key, data.bucket, data.prefix)

  def render: String =
    Op.render("S3Address", bucket, key)

  def /(suffix: String): S3Address =
    S3Address(bucket, Op.concat(key, suffix))

  def size: S3Action[Long] =
    getS3.map(_.size)

  def withStream[A](f: InputStream => RIO[A]): S3Action[A] =
    getObject.flatMap(o => S3Action.fromRIO(f(o.getObjectContent)))

// ------------- Read

  def getS3: S3Action[SizedS3Address] =
    getObjectMetadata.map(o => SizedS3Address(this, o.getContentLength))

  def getObjectMetadata: S3Action[ObjectMetadata] =
    S3Action(_.getObjectMetadata(new GetObjectMetadataRequest(bucket, key)))

  /* Ensure callers of this function call close on the S3Object  */
  def getObject: S3Action[S3Object] =
    S3Action(_.getObject(bucket, key)).onResult(_.prependErrorMessage(s"Could not get S3://$render"))

  def getBytes: S3Action[Array[Byte]] =
    withStream(is => Streams.readBytes(is))

  def get: S3Action[String] =
    getWithEncoding(Codec.UTF8)

  def getWithEncoding(encoding: Codec): S3Action[String] =
    withStream(is => Streams.readWithEncoding(is, encoding))

  def getLines: S3Action[List[String]] =
    withStream(Streams.read(_)).map(_.lines.toList)

  def getFile(destination: LocalPath): S3Action[LocalFile] =
    withStream(destination.writeStream)

  def getFileTo(dir: LocalPath): S3Action[LocalFile] =
    getFile(dir / Path(key))

  def exists: S3Action[Boolean] =
    if (bucket.equals("")) S3Action.ok(false)
    else {
      getObjectMetadata.onResult({
        case Ok(_) =>
          Ok(true)
        case Error(That(t)) =>
          t match {
            case ase: AmazonServiceException =>
              if (ase.getStatusCode == 404) Result.ok(false) else Result.exception[Boolean](ase)
            case t: Throwable =>
              Result.exception[Boolean](t)
          }
        case Error(s) =>
          Error(s)

      })
    }

  /** copy an object from s3 to s3, without downloading the object */
  // metadata disabled, since it copies the old objects metadata
  def copy(toS3: S3Address): S3Action[CopyObjectResult] =
    S3Action.client.map { client =>
      val metadata =  client.getObjectMetadata(new GetObjectMetadataRequest(bucket, key))
      metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
      client.copyObject(new CopyObjectRequest(bucket, key, toS3.bucket, toS3.key).withNewObjectMetadata(metadata))
    }.onResult(_.prependErrorMessage(s"Could not copy object from S3://$render to S3://${toS3.render}"))

  def md5: S3Action[String] =
    S3Action(_.getObjectMetadata(bucket, key).getETag)
      .onResult(_.prependErrorMessage(s"Could not get md5 of S3://$render"))

// ------------- Write

  def put(data: String): S3Action[PutObjectResult] =
    putWithEncoding(data, Codec.UTF8)

  def putWithEncoding(data: String, encoding: Codec): S3Action[PutObjectResult] =
    putWithEncodingAndMetadata(data, encoding, S3.ServerSideEncryption)

  def putWithEncodingAndMetadata(data: String, encoding: Codec, metadata: ObjectMetadata): S3Action[PutObjectResult] =
    putBytesWithMetadata(data.getBytes(encoding.name), metadata)

  def putLines(lines: List[String]): S3Action[PutObjectResult] =
    putLinesWithEncoding(lines, Codec.UTF8)

  def putLinesWithEncoding(lines: List[String], encoding: Codec) =
    putLinesWithEncodingAndMetadata(lines, encoding, S3.ServerSideEncryption)

  def putLinesWithEncodingAndMetadata(lines: List[String], encoding: Codec, metadata: ObjectMetadata): S3Action[PutObjectResult] =
    putWithEncodingAndMetadata(Lists.prepareForFile(lines), encoding, metadata)

  def putBytes(data: Array[Byte]): S3Action[PutObjectResult] =
    putBytesWithMetadata(data, S3.ServerSideEncryption)

  def putBytesWithMetadata(data: Array[Byte], metadata: ObjectMetadata): S3Action[PutObjectResult] =
    putStreamWithMetadata(new ByteArrayInputStream(data), S3Address.ReadLimitDefault, metadata <| (_.setContentLength(data.length)))

  def putFile(file: LocalFile): S3Action[S3UploadResult] =
    putFileWithMetaData(file, S3.ServerSideEncryption)

  def putFileWithMetaData(file: LocalFile, metadata: ObjectMetadata): S3Action[S3UploadResult] =
    Op.putFileWithMetaData(bucket, key, file, metadata)

  def putStream(stream: InputStream): S3Action[PutObjectResult] =
    putStreamWithMetadata(stream, S3Address.ReadLimitDefault, S3.ServerSideEncryption)

  def putStreamWithReadLimit(stream: InputStream, readLimit: Int): S3Action[PutObjectResult] =
    putStreamWithMetadata(stream, readLimit, S3.ServerSideEncryption)

  def putStreamWithMetadata(stream: InputStream, readLimit: Int, metadata: ObjectMetadata): S3Action[PutObjectResult] =
    Op.putStreamWithMetadata(bucket, key, stream, readLimit, metadata)

  /**
   * Note: when you use this method with a Stream you need to set the contentLength on the metadata object
   * to avoid having the stream materialised fully in memory
   *
   * The minimum maxPartSize is 5mb. If passed less than 5mb, it will be increased to the minimum limit of 5mb
   */
  def putFileMultiPart(maxPartSize: BytesQuantity, filePath: LocalFile, tick: Long => Unit): S3Action[S3UploadResult] =
    putFileMultiPartWithMetadata(maxPartSize, filePath, tick, S3.ServerSideEncryption)

  /**
   * Note: when you use this method with a Stream you need to set the contentLength on the metadata object
   * to avoid having the stream materialised fully in memory
   *
   * The minimum maxPartSize is 5mb. If passed less than 5mb, it will be increased to the minimum limit of 5mb
   */
  def putFileMultiPartWithMetadata(maxPartSize: BytesQuantity, filePath: LocalFile, tick: Long => Unit, metadata: ObjectMetadata): S3Action[S3UploadResult] =
    Op.putFileMultiPartWithMetadata(bucket, key, maxPartSize, filePath, tick, metadata)

  /**
   * Note: when you use this method with a Stream you need to set the contentLength on the metadata object
   * to avoid having the stream materialised fully in memory
   *
   * The minimum maxPartSize is 5mb. If passed less than 5mb, it will be increased to the minimum limit of 5mb
   */
  def putStreamMultiPart(maxPartSize: BytesQuantity, stream: InputStream, readLimit: Int, tick: Long => Unit): S3Action[S3UploadResult] =
    putStreamMultiPartWithMetaData(maxPartSize, stream, readLimit, tick, S3.ServerSideEncryption)

  /**
   * Note: when you use this method with a Stream you need to set the contentLength on the metadata object
   * to avoid having the stream materialised fully in memory
   *
   * The minimum maxPartSize is 5mb. If passed less than 5mb, it will be increased to the minimum limit of 5mb
   */
  def putStreamMultiPartWithMetaData(maxPartSize: BytesQuantity, stream: InputStream, readLimit: Int, tick: Long => Unit, metadata: ObjectMetadata): S3Action[S3UploadResult] =
    Op.putStreamMultiPartWithMetaData(bucket, key, maxPartSize, stream, readLimit, tick, metadata)

  /** cache and pass your own transfer manager if you need to run lots of uploads */
  def putStreamMultiPartWithTransferManager(transferManager: TransferManager, stream: InputStream, readLimit: Int, tick: Long => Unit, metadata: ObjectMetadata): S3Action[() => UploadResult] =
    Op.putStreamMultiPartWithTransferManager(bucket, key, transferManager, stream, readLimit, tick, metadata)

// -------------- Other

  def delete: S3Action[Unit] =
    S3Action(_.deleteObject(bucket, key))
      .onResult(_.prependErrorMessage(s"Could not delete S3://$render"))

  /**
   * Download a file in multiparts
   *
   * The tick method can be used inside hadoop to notify progress
   */
  def withStreamMultipart(maxPartSize: BytesQuantity, f: InputStream => RIO[Unit], tick: () => Unit): S3Action[Unit] = for {
    client   <- S3Action.client
    requests <- createRequests(maxPartSize)
    _ <- Aws.fromRIO(requests.traverseU(z => {
      RIO.safe[Unit](tick()) >>
      RIO.using(RIO.safe(client.getObject(z)))(zz => f(zz.getObjectContent))
    }))
  } yield ()

  /** create a list of multipart requests */
  def createRequests(maxPartSize: BytesQuantity): S3Action[List[GetObjectRequest]] = for {
    client <- S3Action.client
    metadata = client.getObjectMetadata(bucket, key)
    parts = S3Address.partition(metadata.getContentLength, maxPartSize.toBytes.value)
  } yield parts.map { case (start, end) => new GetObjectRequest(bucket, key).withRange(start, end) }
}

object S3Address {
  val ReadLimitDefault: Int = 8 * 1024 // 8k

  def fromUri(uri: String): S3Action[Option[S3Address]] = {
    S3Pattern.fromURI(uri).traverseU(_.determine.map(_.flatMap(_.swap.toOption))).map(_.flatten)
  }

  /** partition a number of bytes, going from 0 to totalSize - 1 into parts of size partSize. The last part might be smaller */
  def partition(totalSize: Long, partSize: Long): List[(Long, Long)] = {
    val numberOfParts = totalSize / partSize
    val lastPartSize = totalSize % partSize
    (0 until numberOfParts.toInt).toList.map(part => (part * partSize, (part+1) * partSize - 1)) ++
      (if (lastPartSize == 0) List() else List((totalSize - lastPartSize, totalSize - 1)))
  }
}
