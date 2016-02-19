package com.ambiata.saws.s3

import com.ambiata.saws.core._
import com.ambiata.mundane.io._, MemoryConversions._

import com.ambiata.com.amazonaws.event.{ProgressEvent, ProgressListener}
import com.ambiata.com.amazonaws.services.s3.AmazonS3Client
import com.ambiata.com.amazonaws.services.s3.model._
import com.ambiata.com.amazonaws.services.s3.transfer.{TransferManagerConfiguration, TransferManager}
import com.ambiata.com.amazonaws.services.s3.transfer.model.UploadResult

import java.io._

import scalaz._, Scalaz._

object S3Operations {
  val DELIMITER: String = "/"

  def render(name: String, bucket: String, s: String): String =
    s"$name($bucket/$s)"

  def concat(one: String, two: String): String =
    if (one.endsWith(DELIMITER) || one.isEmpty) one + two
    else one + DELIMITER + two

  def removeCommonPrefix(bucket: String, prefix: String, removeBucket: String, removePrefix: String): Option[String] = {
    def ll(x: List[String], y: List[String]): Option[String] =
      if (y.zipL(x).forall( { case (l,r) => Some(l) == r} ))
        Some(x.drop(y.size).mkString("/"))
      else
        None

    if (removeBucket.equals(bucket)) {
      val keys = prefix.split(DELIMITER).toList
      val input = removePrefix.split(DELIMITER).toList.filter(_.nonEmpty)
      ll(keys, input)
    } else
      None
  }

  def putFileWithMetaData(bucket: String, key: String, file: LocalFile, metadata: ObjectMetadata): S3Action[S3UploadResult] =
    S3Action(_.putObject(new PutObjectRequest(bucket, key, file.toFile).withMetadata(metadata)))
      .onResult(_.prependErrorMessage(s"Could not put file to s3://$bucket/$key")).map(p => S3UploadResult(p.getETag, p.getVersionId))
  
  def putStreamWithMetadata(bucket: String, key: String, stream: InputStream, readLimit: Int, metadata: ObjectMetadata): S3Action[PutObjectResult] =
    S3Action(client => {
      val r = new PutObjectRequest(bucket, key, stream, metadata)
      r.getRequestClientOptions.setReadLimit(readLimit)
      client.putObject(r)
    }).onResult(_.prependErrorMessage(s"Could not put stream to s3://$bucket/$key"))

  /**
   * Note: when you use this method with a Stream you need to set the contentLength on the metadata object
   * to avoid having the stream materialised fully in memory
   *
   * The minimum maxPartSize is 5mb. If passed less than 5mb, it will be increased to the minimum limit of 5mb
   */
  def putFileMultiPartWithMetadata(bucket: String, key: String, maxPartSize: BytesQuantity, filePath: LocalFile,
                                   tick: Long => Unit, metadata: ObjectMetadata): S3Action[S3UploadResult] = {
    S3Action.safe({
      val file = filePath.toFile
      val length = file.length
      file -> length
    }) >>= {
      case (file, length) =>
        // only set the content length if > 10Mb. Otherwise an error will be thrown by AWS because
        // the minimum upload size will be too small
        if (length > 10.mb.toBytes.value) {
          metadata.setContentLength(length)
          S3Action.safe (new FileInputStream(file)) >>=
            { input => putStreamMultiPartWithMetaData(bucket, key, maxPartSize, input, S3Address.ReadLimitDefault, tick, metadata) }
        }
        else
          putFileWithMetaData(bucket, key, filePath, metadata)
    }
  }

  /**
   * Note: when you use this method with a Stream you need to set the contentLength on the metadata object
   * to avoid having the stream materialised fully in memory
   *
   * The minimum maxPartSize is 5mb. If passed less than 5mb, it will be increased to the minimum limit of 5mb
   */
  def putStreamMultiPartWithMetaData(bucket: String, key: String, maxPartSize: BytesQuantity, stream: InputStream, readLimit: Int,
                                     tick: Long => Unit, metadata: ObjectMetadata): S3Action[S3UploadResult] = {
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
      putStreamMultiPartWithTransferManager(bucket, key, transferManager, stream, readLimit, tick, metadata) map { upload =>
        try     upload()
        finally transferManager.shutdownNow(false)
      }
    }.map(p => S3UploadResult(p.getETag, p.getVersionId))
  }

  /** cache and pass your own transfer manager if you need to run lots of uploads */
  def putStreamMultiPartWithTransferManager(bucket: String, key: String, transferManager: TransferManager, stream: InputStream,
                                            readLimit: Int, tick: Long => Unit, metadata: ObjectMetadata): S3Action[() => UploadResult] = {
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
    }.onResult(_.prependErrorMessage(s"Could not put stream to s3://$bucket/$key using the transfer manager"))
  }
}
