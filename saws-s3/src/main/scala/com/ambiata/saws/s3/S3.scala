package com.ambiata.saws
package s3

import com.amazonaws.event.{ProgressEvent, ProgressListener}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{ProgressListener => _, ProgressEvent =>_,_}
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.transfer.{TransferManagerConfiguration, TransferManager}
import com.amazonaws.services.s3.transfer.model.UploadResult
import com.ambiata.saws.core._
import com.ambiata.mundane.io._
import com.ambiata.mundane.control._

import java.io._

import scala.collection.JavaConverters._
import scalaz._, Scalaz._
import scalaz.effect._
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scalaz.stream._
import scalaz.concurrent.Task
import Task._
import ResultT._

object S3 {

  def getObject(s3: S3Address): S3Action[S3Object] =
    S3Action(_.getObject(s3.bucket, s3.key)).onResult(_.prependErrorMessage(s"Could not get S3://${s3.render}"))

  def getBytes(s3: S3Address): S3Action[Array[Byte]] =
    withStream(s3, is => Streams.readBytes(is))

  def getString(s3: S3Address, encoding: String = "UTF-8"): S3Action[String] =
    withStream(s3, is => Streams.read(is, encoding))

  def withStreamUnsafe[A](s3: S3Address, f: InputStream => A): S3Action[A] =
    getObject(s3).map(o => f(o.getObjectContent))

  def withStream[A](s3: S3Address, f: InputStream => ResultT[IO, A]): S3Action[A] =
    getObject(s3).flatMap(o => S3Action.fromResultT(f(o.getObjectContent)))

  /**
   * Download a file in multiparts
   *
   * The tick method can be used inside hadoop to notify progress
   */
  def withStreamMultipart(s3: S3Address, maxPartSize: BytesQuantity, f: InputStream => ResultT[IO, Unit], tick: () => Unit): S3Action[Unit] = for {
    client   <- S3Action.client
    requests <- createRequests(s3, maxPartSize)
    task = Process.emitAll(requests)
      .map(request => Task.delay { tick(); client.getObject(request) })
      .sequence(Runtime.getRuntime.availableProcessors)
      .to(objectContentSink(f)).run
    result <- S3Action.fromTask(task)
  } yield result


  def storeObject(s3: S3Address, file: File, mkdirs: Boolean = false): S3Action[File] =
    withStream(s3, Files.writeStream(FilePath.unsafe(file.getAbsolutePath), _)).as(file)

  def readLines(s3: S3Address): S3Action[List[String]] =
    withStream(s3, Streams.read(_)).map(_.lines.toList)

  def downloadFile(s3: S3Address, to: String = "."): S3Action[File] = {
    val destination = DirPath.unsafe(to) </> FilePath.unsafe(s3.key)
    S3.withStream(s3, Files.writeStream(destination, _)).as(destination.toFile)
  }
  def putString(s3: S3Address,  data: String, encoding: String = "UTF-8", metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    putBytes(s3, data.getBytes(encoding), metadata)

  def putBytes(s3: S3Address,  data: Array[Byte], metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    putStream(s3, new ByteArrayInputStream(data), metadata <| (_.setContentLength(data.length)))

  /**
   * Note: when you use this method with a Stream you need to set the contentLength on the metadata object
   * to avoid having the stream materialised fully in memory
   */
  def putFileMultiPart(s3: S3Address, maxPartSize: BytesQuantity, filePath: FilePath, tick: Function0[Unit],
                       metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[UploadResult] = {
    val file = new File(filePath.path)
    val input = new FileInputStream(file)
    // only set the content length if > 10Mb. Otherwise an error will be thrown by AWS because
    // the minimum upload size will be too small
    if (file.length > 10000) metadata.setContentLength(file.length)
    putStreamMultiPart(s3, maxPartSize, input, tick, metadata)
  }

  /**
   * Note: when you use this method with a Stream you need to set the contentLength on the metadata object
   * to avoid having the stream materialised fully in memory
   */
  def putStreamMultiPart(s3: S3Address, maxPartSize: BytesQuantity, stream: InputStream, tick: Function0[Unit],
                         metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[UploadResult] = {
    S3Action { client: AmazonS3Client =>
      // create a transfer manager
      val configuration = new TransferManagerConfiguration
      configuration.setMinimumUploadPartSize(maxPartSize.toBytes.value)
      configuration.setMultipartUploadThreshold(maxPartSize.toBytes.value.toInt)
      val transferManager = new TransferManager(client)
      transferManager.setConfiguration(configuration)
      transferManager
    }.flatMap { transferManager: TransferManager =>
      putStreamMultiPart(s3, transferManager, stream, tick, metadata) map { upload =>
        try     upload()
        finally transferManager.shutdownNow
      }
    }
  }

  /** cache and pass your own transfer manager if you need to run lots of uploads */
  def putStreamMultiPart(s3: S3Address, transferManager: TransferManager, stream: InputStream, tick: Function0[Unit], metadata: ObjectMetadata): S3Action[() => UploadResult] = {
    S3Action { client : AmazonS3Client =>
      // start the upload and wait for the result
      val upload = transferManager.upload(new PutObjectRequest(s3.bucket, s3.key, stream, metadata))
      upload.addProgressListener(new ProgressListener{ def progressChanged(e: ProgressEvent) { tick() }})
      () => upload.waitForUploadResult
    }.onResult(_.prependErrorMessage(s"Could not put stream to S3://${s3.render} using the transfer manager"))
  }

  def putStream(s3: S3Address,  stream: InputStream, metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    S3Action(_.putObject(s3.bucket, s3.key, stream, metadata))
             .onResult(_.prependErrorMessage(s"Could not put stream to S3://${s3.render}"))

  def putFile(s3: S3Address, file: File, metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    S3Action(_.putObject(new PutObjectRequest(s3.bucket, s3.key, file).withMetadata(metadata)))
             .onResult(_.prependErrorMessage(s"Could not put file to S3://${s3.render}"))

  /** If file is a directory, recursivly put all files and dirs under it on S3. If file is a file, put that file on S3. */
  def putFiles(s3: S3Address, file: File, metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[List[PutObjectResult]] =
    if(file.isDirectory)
      file.listFiles.toList.traverse(f => putFiles(S3Address(s3.bucket, s3.key + "/" + f.getName), f, metadata)).map(_.flatten)
    else
      putFile(s3, file, metadata).map(List(_))

  /** copy an object from s3 to s3, without downloading the object */
  // metadata disabled, since it copies the old objects metadata
  def copyFile(fromS3: S3Address, toS3: S3Address /*, metadata: ObjectMetadata = S3.ServerSideEncryption*/): S3Action[CopyObjectResult] =
    S3Action.client.map { client =>
      val metadata =  client.getObjectMetadata(new GetObjectMetadataRequest(fromS3.bucket, fromS3.key))
      metadata.setServerSideEncryption(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
      client.copyObject(new CopyObjectRequest(fromS3.bucket, fromS3.key, toS3.bucket, toS3.key).withNewObjectMetadata(metadata))
    }.onResult(_.prependErrorMessage(s"Could not copy object from S3://${fromS3.render} to S3://${toS3.render}"))

  def writeLines(s3: S3Address, lines: Seq[String], metadata: ObjectMetadata = S3.ServerSideEncryption): S3Action[PutObjectResult] =
    putStream(s3, new ByteArrayInputStream(lines.mkString("\n").getBytes), metadata) // TODO: Fix ram use

  def listSummary(s3: S3Address): S3Action[List[S3ObjectSummary]] =
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
      allObjects(client.listObjects(s3.bucket, directory(s3.key)), List())
    }).flatMap(x => x)

  def listKeys(s3: S3Address): S3Action[List[String]] =
    listSummary(s3).map(_.map(_.getKey))

  def listKeysHead(s3: S3Address): S3Action[List[String]] =
    S3Action(client => {
      val request = new ListObjectsRequest(s3.bucket, directory(s3.key), null, "/", null)
      val common = client.listObjects(request).getCommonPrefixes.asScala.toList
      val prefixes = common.flatMap(_.split("/").lastOption)
      prefixes
    })

  def listBuckets: S3Action[List[Bucket]] =
    S3Action(_.listBuckets.toList).onResult(_.prependErrorMessage(s"Could access the buckets list"))

  def isS3Accessible: S3Action[Unit] =
    listBuckets.map(_ => ()).orElse(S3Action.fail("S3 is not accessible"))

  /** use this method to make sure that a prefix ends with a slash */
  def directory(prefix: String) = prefix + (if (prefix.endsWith("/")) "" else "/")

  def exists(s3: S3Address): S3Action[Boolean] =
    S3Action((client: AmazonS3Client) => try {
        client.getObject(s3.bucket, s3.key)
        S3Action.ok(true)
      } catch {
        case ase: AmazonServiceException =>
          if (ase.getErrorCode == "NoSuchKey") S3Action.ok(false) else S3Action.exception(ase)
        case t: Throwable =>
          S3Action.exception(t)
      }).join

  def existsPrefix(s3: S3Address): S3Action[Boolean] =
    S3Action(client => {
      val request = new ListObjectsRequest(s3.bucket, directory(s3.key), null, "/", null)
      client.listObjects(request).getObjectSummaries.asScala.nonEmpty
    })

  def existsInBucket(bucket: String, filter: String => Boolean): S3Action[Boolean] =
    listSummary(S3Address(bucket, "")).map(_.exists(o => filter(o.getKey)))

  def deleteObject(s3: S3Address): S3Action[Unit] =
    S3Action(_.deleteObject(s3.bucket, s3.key))
             .onResult(_.prependErrorMessage(s"Could not delete S3://${s3.render}"))

  def deleteObjects(bucket: String, f: String => Boolean = (s: String) => true): S3Action[Unit] =
    listSummary(S3Address(bucket, "")).flatMap(_.collect { case o if f(o.getKey) => deleteObject(S3Address(bucket, o.getKey)) }.sequence.map(_ => ()))

  def md5(s3: S3Address): S3Action[String] =
    S3Action(_.getObjectMetadata(s3.bucket, s3.key).getETag)
             .onResult(_.prependErrorMessage(s"Could not get md5 of S3://${s3.render}"))

  def extractTarball(s3: S3Address, local: File, stripLevels: Int): S3Action[File] =
    withStream(s3, Archive.extractTarballStream(_, FilePath.unsafe(local.getAbsolutePath), stripLevels)).as(local)

  def extractTarballFlat(s3: S3Address, local: File): S3Action[File] =
    withStream(s3, Archive.extractTarballStreamFlat(_, FilePath.unsafe(local.getAbsolutePath))).as(local)

  def extractGzip(s3: S3Address, local: File): S3Action[File] =
    withStream(s3, is => Archive.extractGzipStream(is, FilePath.unsafe(local.getAbsolutePath))).as(local)

  def mirror(base: File, s3: S3Address): S3Action[Unit] = for {
    local <- S3Action.fromResultT { Directories.list(DirPath.unsafe(base.getAbsolutePath)) }
    _     <- local.traverse({ source =>
      val destination = source.toFile.getAbsolutePath.replace(base.getAbsolutePath + "/", "")
      S3.putFile(s3 / destination, source.toFile)
    })
  } yield ()

  def deleteAll(s3: S3Address): S3Action[Unit] = for {
    all <- listSummary(s3)
    _   <- all.traverse(obj => deleteObject(S3Address(s3.bucket, obj.getKey)))
  } yield ()

  /** Object metadata that enables AES256 server-side encryption. */
  def ServerSideEncryption: ObjectMetadata = {
    val m = new ObjectMetadata
    m.setServerSideEncryption(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
    m
  }

  /** create a list of multipart requests */
  def createRequests(s3: S3Address, maxPartSize: BytesQuantity): S3Action[Seq[GetObjectRequest]] = for {
    client <- S3Action.client
    metadata = client.getObjectMetadata(s3.bucket, s3.key)
    parts = partition(metadata.getContentLength, maxPartSize.toBytes.value)
  } yield parts.map { case (start, end) => new GetObjectRequest(s3.bucket, s3.key).withRange(start, end) }

  /** partition a number of bytes, going from 0 to totalSize - 1 into parts of size partSize. The last part might be smaller */
  def partition(totalSize: Long, partSize: Long): Seq[(Long, Long)] = {
    val numberOfParts = totalSize / partSize
    val lastPartSize = totalSize % partSize
    (0 until numberOfParts.toInt).map(part => (part * partSize, (part+1) * partSize - 1)) ++
    (if (lastPartSize == 0) Seq() else Seq((totalSize - lastPartSize, totalSize - 1)))
  }

  def objectContentSink(f: InputStream => ResultT[IO, Unit]): Sink[Task, S3Object] =
    io.channel((s3Object: S3Object) => toTask(f(s3Object.getObjectContent)))

  val NoTick: Function0[Unit] = () => ()
}
