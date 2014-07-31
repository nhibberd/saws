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

case class S3ReadOnlyStore(bucket: String, base: DirPath, client: AmazonS3Client) extends ReadOnlyStore[ResultTIO] {
  def list(prefix: DirPath): ResultT[IO, List[FilePath]] =
    S3.listKeys(bucket, (base </> prefix).path).executeT(client).map(_.map(normalize).sorted).map(_.map(FilePath.unsafe))

  def filter(prefix: DirPath, predicate: FilePath => Boolean): ResultT[IO, List[FilePath]] =
    list(prefix).map(_.filter(predicate))

  def find(prefix: DirPath, predicate: FilePath => Boolean): ResultT[IO, Option[FilePath]] =
    list(prefix).map(_.find(predicate))

  def exists(path: DirPath): ResultT[IO, Boolean] =
    S3.exists(bucket, (base </> path).path).executeT(client)

  def exists(path: FilePath): ResultT[IO, Boolean] =
    S3.exists(bucket, (base </> path).path).executeT(client)

  def checksum(path: FilePath, algorithm: ChecksumAlgorithm): ResultT[IO, Checksum] =
    S3.withStream(bucket, (base </> path).path, in => Checksum.stream(in, algorithm)).executeT(client)

  def normalize(key: String): String =
    key.replace(base.path + "/", "")

  def copyTo(store: Store[ResultTIO], src: FilePath, dest: FilePath): ResultT[IO, Unit] =
    unsafe.withInputStream(src) { in =>
      store.unsafe.withOutputStream(dest) { out =>
        Streams.pipe(in, out) }}

  def mirrorTo(store: Store[ResultTIO], in: DirPath, out: DirPath): ResultT[IO, Unit] = for {
    paths <- list(in)
    _     <- paths.traverseU { source => copyTo(store, source, out </> source) }
  } yield ()

  val bytes: StoreBytesRead[ResultTIO] = new StoreBytesRead[ResultTIO] {
    def read(path: FilePath): ResultT[IO, ByteVector] =
      s3 { S3.getBytes(bucket, (base </> path).path).map(ByteVector.apply) }

    def source(path: FilePath): Process[Task, ByteVector] =
      scalaz.stream.io.chunkR(client.getObject(bucket, (base </> path).path).getObjectContent).evalMap(_(1024 * 1024))
  }

  val strings: StoreStringsRead[ResultTIO] = new StoreStringsRead[ResultTIO] {
    def read(path: FilePath, codec: Codec): ResultT[IO, String] =
      s3 { S3.getString(bucket, (base </> path).path, codec.name) }
  }

  val utf8: StoreUtf8Read[ResultTIO] = new StoreUtf8Read[ResultTIO] {
    def read(path: FilePath): ResultT[IO, String] =
      strings.read(path, Codec.UTF8)

    def source(path: FilePath): Process[Task, String] =
      bytes.source(path) |> scalaz.stream.text.utf8Decode
  }

  val lines: StoreLinesRead[ResultTIO] = new StoreLinesRead[ResultTIO] {
    def read(path: FilePath, codec: Codec): ResultT[IO, List[String]] =
      strings.read(path, codec).map(_.lines.toList)

    def source(path: FilePath, codec: Codec): Process[Task, String] =
      scalaz.stream.io.linesR(client.getObject(bucket, (base </> path).path).getObjectContent)(codec)
  }

  val linesUtf8: StoreLinesUtf8Read[ResultTIO] = new StoreLinesUtf8Read[ResultTIO] {
    def read(path: FilePath): ResultT[IO, List[String]] =
      lines.read(path, Codec.UTF8)

    def source(path: FilePath): Process[Task, String] =
      lines.source(path, Codec.UTF8)
  }

  val unsafe: StoreUnsafeRead[ResultTIO] = new StoreUnsafeRead[ResultTIO] {
    def withInputStream(path: FilePath)(f: InputStream => ResultT[IO, Unit]): ResultT[IO, Unit] =
      ResultT.using(S3.getObject(bucket, (base </> path).path).executeT(client).map(_.getObjectContent: InputStream))(f)
  }

  def s3[A](thunk: => S3Action[A]): ResultT[IO, A] =
    thunk.executeT(client)
}

case class S3Store(bucket: String, base: DirPath, client: AmazonS3Client, cache: DirPath) extends Store[ResultTIO] with ReadOnlyStore[ResultTIO] {
  def local =
    PosixStore(cache)

  val readOnly: ReadOnlyStore[ResultTIO] =
    S3ReadOnlyStore(bucket, base, client)

  def list(prefix: DirPath): ResultT[IO, List[FilePath]] =
    readOnly.list(prefix)

  def filter(prefix: DirPath, predicate: FilePath => Boolean): ResultT[IO, List[FilePath]] =
    readOnly.filter(prefix, predicate)

  def find(prefix: DirPath, predicate: FilePath => Boolean): ResultT[IO, Option[FilePath]] =
    readOnly.find(prefix, predicate)

  def exists(path: FilePath): ResultT[IO, Boolean] =
    readOnly.exists(path)

  def exists(path: DirPath): ResultT[IO, Boolean] =
    readOnly.exists(path)

  def normalize(key: String): String =
    key.replace(base.path + "/", "")

  def checksum(path: FilePath, algorithm: ChecksumAlgorithm): ResultT[IO, Checksum] =
    readOnly.checksum(path, algorithm)

  def delete(path: FilePath): ResultT[IO, Unit] =
    S3.deleteObject(bucket, (base </> path).path).executeT(client)

  def deleteAll(prefix: DirPath): ResultT[IO, Unit] =
    list(prefix).flatMap(_.traverseU(delete)).void

  def move(in: FilePath, out: FilePath): ResultT[IO, Unit] =
    copy(in, out) >> delete(in)

  def moveTo(store: Store[ResultTIO], src: FilePath, dest: FilePath): ResultT[IO, Unit] =
    copyTo(store, src, dest) >> delete(src)

  def copy(in: FilePath, out: FilePath): ResultT[IO, Unit] =
    S3.copyFile(bucket, (base </> in).path, bucket, (base </> out).path).executeT(client).void

  def mirror(in: DirPath, out: DirPath): ResultT[IO, Unit] = for {
    paths <- list(in)
    _     <- paths.traverseU { source => copy(source, out </> source) }
  } yield ()

  def copyTo(store: Store[ResultTIO], src: FilePath, dest: FilePath): ResultT[IO, Unit] =
    readOnly.copyTo(store, src, dest)

  def mirrorTo(store: Store[ResultTIO], in: DirPath, out: DirPath): ResultT[IO, Unit] =
    readOnly.mirrorTo(store, in, out)

  val bytes: StoreBytes[ResultTIO] = new StoreBytes[ResultTIO] {
    def read(path: FilePath): ResultT[IO, ByteVector] =
      readOnly.bytes.read(path)

    def source(path: FilePath): Process[Task, ByteVector] =
      readOnly.bytes.source(path)

    def write(path: FilePath, data: ByteVector): ResultT[IO, Unit] =
      s3 { S3.putBytes(bucket, (base </> path).path, data.toArray).void }

    def sink(path: FilePath): Sink[Task, ByteVector] =
      io.resource(Task.delay(new PipedOutputStream))(out => Task.delay(out.close))(
        out => io.resource(Task.delay(new PipedInputStream))(in => Task.delay(in.close))(
          in => Task.now((bytes: ByteVector) => Task.delay(out.write(bytes.toArray)))).toTask)
  }

  val strings: StoreStrings[ResultTIO] = new StoreStrings[ResultTIO] {
    def read(path: FilePath, codec: Codec): ResultT[IO, String] =
      readOnly.strings.read(path, codec)

    def write(path: FilePath, data: String, codec: Codec): ResultT[IO, Unit] =
      s3 { S3.putString(bucket, (base </> path).path, data, codec.name).void }
  }

  val utf8: StoreUtf8[ResultTIO] = new StoreUtf8[ResultTIO] {
    def read(path: FilePath): ResultT[IO, String] =
      readOnly.utf8.read(path)

    def source(path: FilePath): Process[Task, String] =
      readOnly.utf8.source(path)

    def write(path: FilePath, data: String): ResultT[IO, Unit] =
    strings.write(path, data, Codec.UTF8)

    def sink(path: FilePath): Sink[Task, String] =
      bytes.sink(path).map(_.contramap(s => ByteVector.view(s.getBytes("UTF-8"))))
  }

  val lines: StoreLines[ResultTIO] = new StoreLines[ResultTIO] {
    def read(path: FilePath, codec: Codec): ResultT[IO, List[String]] =
      readOnly.lines.read(path, codec)

    def source(path: FilePath, codec: Codec): Process[Task, String] =
      readOnly.lines.source(path, codec)

    def write(path: FilePath, data: List[String], codec: Codec): ResultT[IO, Unit] =
    strings.write(path, Lists.prepareForFile(data), codec)

    def sink(path: FilePath, codec: Codec): Sink[Task, String] =
      bytes.sink(path).map(_.contramap(s => ByteVector.view(s"$s\n".getBytes(codec.name))))
  }

  val linesUtf8: StoreLinesUtf8[ResultTIO] = new StoreLinesUtf8[ResultTIO] {
    def read(path: FilePath): ResultT[IO, List[String]] =
      readOnly.linesUtf8.read(path)

    def source(path: FilePath): Process[Task, String] =
      readOnly.linesUtf8.source(path)

    def write(path: FilePath, data: List[String]): ResultT[IO, Unit] =
      lines.write(path, data, Codec.UTF8)

    def sink(path: FilePath): Sink[Task, String] =
      lines.sink(path, Codec.UTF8)
  }

  val unsafe: StoreUnsafe[ResultTIO] = new StoreUnsafe[ResultTIO] {
    def withInputStream(path: FilePath)(f: InputStream => ResultT[IO, Unit]): ResultT[IO, Unit] =
      readOnly.unsafe.withInputStream(path)(f)

    def withOutputStream(path: FilePath)(f: OutputStream => ResultT[IO, Unit]): ResultT[IO, Unit] = {
      val unique = FilePath.unsafe(UUID.randomUUID.toString)
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

object S3Store {
  def createReadOnly(path: DirPath): S3Action[S3ReadOnlyStore] =
    createReadOnly(path.rootname.path, path.fromRoot)

  def createReadOnly(bucket: String, base: DirPath): S3Action[S3ReadOnlyStore] =
    S3Action.client.map(c => S3ReadOnlyStore(bucket, base, c))

  def create(path: DirPath, cache: DirPath): S3Action[S3Store] =
    create(path.rootname.path, path.fromRoot, cache)

  def create(bucket: String, base: DirPath, cache: DirPath): S3Action[S3Store] =
    S3Action.client.map(c => S3Store(bucket, base, c, cache))

}