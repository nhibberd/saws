package com.ambiata.saws.s3

import com.ambiata.saws.s3.{S3Operations => Op}
import com.ambiata.com.amazonaws.event.{ProgressEvent, ProgressListener}
import com.ambiata.com.amazonaws.services.s3.AmazonS3Client
import com.ambiata.com.amazonaws.services.s3.model._
import com.ambiata.com.amazonaws.AmazonServiceException
import com.ambiata.com.amazonaws.services.s3.transfer.{TransferManagerConfiguration, TransferManager}
import com.ambiata.com.amazonaws.services.s3.transfer.model.UploadResult
import com.ambiata.saws.core._
import com.ambiata.mundane.io._
import com.ambiata.mundane.io.MemoryConversions._
import com.ambiata.mundane.control._
import com.ambiata.mundane.data._

import java.io._

import scala.io.Codec

import scalaz._, Scalaz._, effect._, stream._, concurrent.Task


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
    Op.render(bucket, key)

  def /(suffix: String): S3Address =
    S3Address(bucket, Op.concat(key, suffix))

  def size: S3Action[Long] =
    getS3.map(_.size)

  def withStream[A](f: InputStream => ResultT[IO, A]): S3Action[A] =
    getObject.flatMap(o => S3Action.fromResultT(f(o.getObjectContent)))

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
    withStream(is => Streams.read(is, encoding.name))

  def getLines: S3Action[List[String]] =
    withStream(Streams.read(_)).map(_.lines.toList)

  def getFile(destination: FilePath): S3Action[FilePath] =
    withStream(Files.writeStream(destination, _)).as(destination)

  def getFileTo(dir: DirPath): S3Action[FilePath] = {
    val destination = dir </> FilePath.unsafe(key)
    getFile(destination)
  }

  def exists: S3Action[Boolean] =
    if (bucket.equals("")) S3Action.ok(false)
    else {
      S3Action((client: AmazonS3Client) => try {
        Aws.using(S3Action.safe(client.getObject(bucket, key)))(_ => S3Action.ok(true))
      } catch {
        case ase: AmazonServiceException =>
          if (ase.getErrorCode == "NoSuchKey" || ase.getErrorCode == "NoSuchBucket") S3Action.ok(false) else S3Action.exception[Boolean](ase)
        case t: Throwable =>
          S3Action.exception[Boolean](t)
      }).join
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

  val ReadLimitDefault: Int = 8 * 1024 // 8k

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
    putStreamWithMetadata(new ByteArrayInputStream(data), metadata <| (_.setContentLength(data.length)))

  def putFile(file: FilePath): S3Action[S3UploadResult] =
    putFileWithMetaData(file, S3.ServerSideEncryption)

  def putFileWithMetaData(file: FilePath, metadata: ObjectMetadata): S3Action[S3UploadResult] =
    S3Action(_.putObject(new PutObjectRequest(bucket, key, file.toFile).withMetadata(metadata)))
      .onResult(_.prependErrorMessage(s"Could not put file to S3://$render")).map(p => S3UploadResult(p.getETag, p.getVersionId))

  def putStream(stream: InputStream): S3Action[PutObjectResult] =
    putStreamWithMetadata(stream, S3.ServerSideEncryption)

  def putStreamWithMetadata(stream: InputStream, metadata: ObjectMetadata): S3Action[PutObjectResult] =
    S3Action(_.putObject(bucket, key, stream, metadata))
      .onResult(_.prependErrorMessage(s"Could not put stream to S3://$render"))

  /**
   * Note: when you use this method with a Stream you need to set the contentLength on the metadata object
   * to avoid having the stream materialised fully in memory
   *
   * The minimum maxPartSize is 5mb. If passed less than 5mb, it will be increased to the minimum limit of 5mb
   */
  def putFileMultiPart(maxPartSize: BytesQuantity, filePath: FilePath, tick: Long => Unit): S3Action[S3UploadResult] =
    putFileMultiPartWithMetadata(maxPartSize, filePath, tick, S3.ServerSideEncryption)

  /**
   * Note: when you use this method with a Stream you need to set the contentLength on the metadata object
   * to avoid having the stream materialised fully in memory
   *
   * The minimum maxPartSize is 5mb. If passed less than 5mb, it will be increased to the minimum limit of 5mb
   */
  def putFileMultiPartWithMetadata(maxPartSize: BytesQuantity, filePath: FilePath, tick: Long => Unit, metadata: ObjectMetadata): S3Action[S3UploadResult] = {
    S3Action.safe({
      val file = new File(filePath.path)
      val length = file.length
      file -> length
    }) >>= {
      case (file, length) =>
        // only set the content length if > 10Mb. Otherwise an error will be thrown by AWS because
        // the minimum upload size will be too small
        if (length > 10.mb.toBytes.value) {
          metadata.setContentLength(length)
          S3Action.safe (new FileInputStream(file)) >>=
            { input => putStreamMultiPartWithMetaData(maxPartSize, input, ReadLimitDefault, tick, metadata) }
        }
        else
          putFileWithMetaData(filePath, metadata)
    }
  }

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
  def putStreamMultiPartWithMetaData(maxPartSize: BytesQuantity, stream: InputStream, readLimit: Int, tick: Long => Unit, metadata: ObjectMetadata): S3Action[S3UploadResult] = {
    S3Action { client: AmazonS3Client =>
      // create a transfer manager
      val configuration = new TransferManagerConfiguration
      if (maxPartSize < 5.mb.toBytes) setupConf(5.mb.toBytes.value)
      else setupConf(maxPartSize.toBytes.value)

      def setupConf(l: Long) = {
        configuration.setMinimumUploadPartSize(l)
        configuration.setMultipartUploadThreshold(l.toInt)
      }

      val transferManager = new TransferManager(client)
      transferManager.setConfiguration(configuration)
      transferManager
    }.flatMap { transferManager: TransferManager =>
      putStreamMultiPartWithTransferManager(transferManager, stream, readLimit, tick, metadata) map { upload =>
        try     upload()
        finally transferManager.shutdownNow(false)
      }
    }.map(p => S3UploadResult(p.getETag, p.getVersionId))
  }

  /** cache and pass your own transfer manager if you need to run lots of uploads */
  def putStreamMultiPartWithTransferManager(transferManager: TransferManager, stream: InputStream, readLimit: Int, tick: Long => Unit, metadata: ObjectMetadata): S3Action[() => UploadResult] = {
    S3Action { client: AmazonS3Client =>
      // start the upload and wait for the result
      val r = new PutObjectRequest(bucket, key, stream, metadata)
      r.getRequestClientOptions.setReadLimit(readLimit)
      val upload = transferManager.upload(r)
      upload.addProgressListener(new ProgressListener {
        def progressChanged(e: ProgressEvent) {
          tick(e.getBytesTransferred)
        }
      })
      () => upload.waitForUploadResult()
    }.onResult(_.prependErrorMessage(s"Could not put stream to S3://$render using the transfer manager"))
  }

// -------------- Other

  def delete: S3Action[Unit] =
    S3Action(_.deleteObject(bucket, key))
      .onResult(_.prependErrorMessage(s"Could not delete S3://$render"))

  /**
   * Download a file in multiparts
   *
   * The tick method can be used inside hadoop to notify progress
   */
  def withStreamMultipart(maxPartSize: BytesQuantity, f: InputStream => ResultT[IO, Unit], tick: () => Unit): S3Action[Unit] = for {
    client   <- S3Action.client
    requests <- createRequests(maxPartSize)
    _ <- Aws.fromResultT(requests.traverseU(z => {
      ResultT.safe[IO, Unit](tick()) >>
      ResultT.using(ResultT.safe(client.getObject(z)))(zz => f(zz.getObjectContent))
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

  def objectContentSink(f: InputStream => ResultT[IO, Unit]): Sink[Task, S3Object] =
    io.channel((s3Object: S3Object) => ResultT.toTask(f(s3Object.getObjectContent)))
}
