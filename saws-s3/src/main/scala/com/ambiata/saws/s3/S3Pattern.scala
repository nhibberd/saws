package com.ambiata.saws.s3

import com.amazonaws.event.{ProgressEvent, ProgressListener}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.transfer.{TransferManagerConfiguration, TransferManager}
import com.amazonaws.services.s3.transfer.model.UploadResult

import com.ambiata.mundane.control._
import com.ambiata.mundane.data._
import com.ambiata.mundane.io._, MemoryConversions._
import com.ambiata.mundane.path._
import com.ambiata.saws.core.S3Action
import com.ambiata.saws.s3.{S3Operations => Op}
import com.ambiata.saws.s3.S3Pattern._

import java.io._
import scala.io.Codec

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
    Op.render("S3Pattern", bucket, unknown)

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

  def determineAddress: S3Action[S3Address] =
    determine.flatMap {
      case Some(-\/(address)) => S3Action.ok(address)
      case _                  => S3Action.fail(s"$render is not a S3 address")
    }

  def determinePrefix: S3Action[S3Prefix] =
    determine.flatMap {
      case Some(\/-(prefix)) => S3Action.ok(prefix)
      case _                 => S3Action.fail(s"$render is not a S3 prefix")
    }

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
    determine.flatMap({
      case Some(\/-(v)) => v.delete
      case Some(-\/(v)) => v.delete
      case None         => S3Action.unit
    })

  def exists: S3Action[Boolean] =
    determine.flatMap({
      case Some(\/-(v)) => v.exists
      case Some(-\/(v)) => v.exists
      case None         => false.pure[S3Action]
    })

  def withStream[A](f: InputStream => RIO[A]): S3Action[A] =
    determineAddress.flatMap(_.withStream(f))
 
  def withStreamMultipart(maxPartSize: BytesQuantity, f: InputStream => RIO[Unit], tick: () => Unit): S3Action[Unit] =
    determineAddress.flatMap(_.withStreamMultipart(maxPartSize, f, tick))

  def createRequests(maxPartSize: BytesQuantity): S3Action[List[GetObjectRequest]] =
    determineAddress.flatMap(_.createRequests(maxPartSize))

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
    getFile(dir / Path(unknown))

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
    Op.putFileWithMetaData(bucket, unknown, file, metadata)

  def putStream(stream: InputStream): S3Action[PutObjectResult] =
    putStreamWithMetadata(stream, S3Address.ReadLimitDefault, S3.ServerSideEncryption)

  def putStreamWithReadLimit(stream: InputStream, readLimit: Int): S3Action[PutObjectResult] =
    putStreamWithMetadata(stream, readLimit, S3.ServerSideEncryption)

  def putStreamWithMetadata(stream: InputStream, readLimit: Int, metadata: ObjectMetadata): S3Action[PutObjectResult] =
    Op.putStreamWithMetadata(bucket, unknown, stream, readLimit, metadata)

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
    Op.putFileMultiPartWithMetadata(bucket, unknown, maxPartSize, filePath, tick, metadata)

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
    Op.putStreamMultiPartWithMetaData(bucket, unknown, maxPartSize, stream, readLimit, tick, metadata)

  /** cache and pass your own transfer manager if you need to run lots of uploads */
  def putStreamMultiPartWithTransferManager(transferManager: TransferManager, stream: InputStream, readLimit: Int, tick: Long => Unit, metadata: ObjectMetadata): S3Action[() => UploadResult] =
    Op.putStreamMultiPartWithTransferManager(bucket, unknown, transferManager, stream, readLimit, tick, metadata)
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
