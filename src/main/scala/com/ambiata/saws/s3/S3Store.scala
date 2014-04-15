package com.ambiata.saws.s3

import com.amazonaws.services.s3.AmazonS3Client
import com.ambiata.saws.core._
import com.ambiata.mundane.control._
import com.ambiata.mundane.io._
import com.ambiata.mundane.data._
import com.ambiata.mundane.store._
import java.util.UUID
import java.io.{InputStream, OutputStream}
import java.io.{PipedInputStream, PipedOutputStream}
import scala.io.Codec
import scalaz.{Store => _, _}, Scalaz._, scalaz.stream._, scalaz.concurrent._, effect.IO, effect.Effect._, \&/._
import scodec.bits.ByteVector

case class S3Store(bucket: String, base: FilePath, client: AmazonS3Client, cache: FilePath) extends Store[ResultTIO] with ReadOnlyStore[ResultTIO] {
  def local =
    PosixStore(cache)

  def readOnly: ReadOnlyStore[ResultTIO] =
    this

  def normalize(key: String): String =
    key.replace(base.path + "/", "")

  def list(prefix: FilePath): ResultT[IO, List[FilePath]] =
    S3.listKeys(bucket, (base </> prefix).path).executeT(client).map(_.map(normalize).sorted).map(_.map(_.toFilePath))

  def filter(prefix: FilePath, predicate: FilePath => Boolean): ResultT[IO, List[FilePath]] =
    list(prefix).map(_.filter(predicate))

  def find(prefix: FilePath, predicate: FilePath => Boolean): ResultT[IO, Option[FilePath]] =
    list(prefix).map(_.find(predicate))

  def exists(path: FilePath): ResultT[IO, Boolean] =
    S3.exists(bucket, (base </> path).path).executeT(client)

  def delete(path: FilePath): ResultT[IO, Unit] =
    S3.deleteObject(bucket, (base </> path).path).executeT(client)

  def deleteAll(prefix: FilePath): ResultT[IO, Unit] =
    list(prefix).flatMap(_.traverseU(delete)).void

  def move(in: FilePath, out: FilePath): ResultT[IO, Unit] =
    copy(in, out) >> delete(in)

  def copy(in: FilePath, out: FilePath): ResultT[IO, Unit] =
    S3.copyFile(bucket, (base </> in).path, bucket, (base </> out).path).executeT(client).void

  def mirror(in: FilePath, out: FilePath): ResultT[IO, Unit] = for {
    paths <- list(in)
    _     <- paths.traverseU({ source =>
      val destination = out </> source.path.replace(in.path + "/", "")
      copy(source, destination)
    })
  } yield ()

  def moveTo(store: Store[ResultTIO], src: FilePath, dest: FilePath): ResultT[IO, Unit] =
    copyTo(store, src, dest) >> delete(src)

  def copyTo(store: Store[ResultTIO], src: FilePath, dest: FilePath): ResultT[IO, Unit] =
    unsafe.withInputStream(src) { in =>
      store.unsafe.withOutputStream(dest) { out =>
        Streams.pipe(in, out) }}

  def mirrorTo(store: Store[ResultTIO], in: FilePath, out: FilePath): ResultT[IO, Unit] = for {
    paths <- list(in)
    _     <- paths.traverseU({ source =>
      val destination = out </> source.path.replace(in.path + "/", "")
      copyTo(store, source, destination)
    })
  } yield ()

  def checksum(path: FilePath, algorithm: ChecksumAlgorithm): ResultT[IO, Checksum] =
    S3.withStream(bucket, (base </> path).path, in => Checksum.stream(in, algorithm)).executeT(client)

  val bytes: StoreBytes[ResultTIO] = new StoreBytes[ResultTIO] {
    def read(path: FilePath): ResultT[IO, ByteVector] =
      s3 { S3.getBytes(bucket, (base </> path).path).map(ByteVector.apply) }

    def write(path: FilePath, data: ByteVector): ResultT[IO, Unit] =
      s3 { S3.putBytes(bucket, (base </> path).path, data.toArray).void }

    def source(path: FilePath): Process[Task, ByteVector] =
      scalaz.stream.io.chunkR(client.getObject(bucket, (base </> path).path).getObjectContent).evalMap(_(1024 * 1024))

    def sink(path: FilePath): Sink[Task, ByteVector] =
      io.resource(Task.delay(new PipedOutputStream))(out => Task.delay(out.close))(
        out => io.resource(Task.delay(new PipedInputStream))(in => Task.delay(in.close))(
          in => Task.now((bytes: ByteVector) => Task.delay(out.write(bytes.toArray)))).toTask)
  }

  val strings: StoreStrings[ResultTIO] = new StoreStrings[ResultTIO] {
    def read(path: FilePath, codec: Codec): ResultT[IO, String] =
      s3 { S3.getString(bucket, (base </> path).path, codec.name) }

    def write(path: FilePath, data: String, codec: Codec): ResultT[IO, Unit] =
      s3 { S3.putString(bucket, (base </> path).path, data, codec.name).void }
  }

  val utf8: StoreUtf8[ResultTIO] = new StoreUtf8[ResultTIO] {
    def read(path: FilePath): ResultT[IO, String] =
      strings.read(path, Codec.UTF8)

    def write(path: FilePath, data: String): ResultT[IO, Unit] =
      strings.write(path, data, Codec.UTF8)

    def source(path: FilePath): Process[Task, String] =
      bytes.source(path) |> scalaz.stream.text.utf8Decode

    def sink(path: FilePath): Sink[Task, String] =
      bytes.sink(path).map(_.contramap(s => ByteVector.view(s.getBytes("UTF-8"))))
  }

  val lines: StoreLines[ResultTIO] = new StoreLines[ResultTIO] {
    def read(path: FilePath, codec: Codec): ResultT[IO, List[String]] =
      strings.read(path, codec).map(_.lines.toList)

    def write(path: FilePath, data: List[String], codec: Codec): ResultT[IO, Unit] =
      strings.write(path, Lists.prepareForFile(data), codec)

    def source(path: FilePath, codec: Codec): Process[Task, String] =
      scalaz.stream.io.linesR(client.getObject(bucket, (base </> path).path).getObjectContent)(codec)

    def sink(path: FilePath, codec: Codec): Sink[Task, String] =
      bytes.sink(path).map(_.contramap(s => ByteVector.view(s"$s\n".getBytes(codec.name))))
  }

  val linesUtf8: StoreLinesUtf8[ResultTIO] = new StoreLinesUtf8[ResultTIO] {
    def read(path: FilePath): ResultT[IO, List[String]] =
      lines.read(path, Codec.UTF8)

    def write(path: FilePath, data: List[String]): ResultT[IO, Unit] =
      lines.write(path, data, Codec.UTF8)

    def source(path: FilePath): Process[Task, String] =
      lines.source(path, Codec.UTF8)

    def sink(path: FilePath): Sink[Task, String] =
      lines.sink(path, Codec.UTF8)
  }

  val unsafe: StoreUnsafe[ResultTIO] = new StoreUnsafe[ResultTIO] {
    def withInputStream(path: FilePath)(f: InputStream => ResultT[IO, Unit]): ResultT[IO, Unit] =
      ResultT.using(S3.getObject(bucket, (base </> path).path).executeT(client).map(_.getObjectContent: InputStream))(f)

    def withOutputStream(path: FilePath)(f: OutputStream => ResultT[IO, Unit]): ResultT[IO, Unit] = {
      val unique = UUID.randomUUID.toString.toFilePath
      local.unsafe.withOutputStream(unique)(f) >> local.unsafe.withInputStream(unique)(in =>
        S3.putStream(bucket, (base </> path).path, in, {
          val metadata = S3.ServerSideEncryption
          metadata.setContentLength((local.root </> unique).toFile.length)
          metadata
        }).executeT(client).void)
    }
  }

  def s3[A](thunk: => S3Action[A]): ResultT[IO, A] =
    thunk.executeT(client)
}
