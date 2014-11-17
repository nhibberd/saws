package com.ambiata.saws.s3

import java.util.UUID

import com.ambiata.mundane.control._
import com.ambiata.saws.core._
import com.ambiata.saws.s3.TemporaryS3._

import scalaz._, Scalaz._, effect._

case class TemporaryS3Address(s3: S3Address) {
  def clean: ResultT[IO, Unit] =
    s3.delete.executeT(conf)
}

case class TemporaryS3Prefix(s3: S3Prefix) {
  def clean: ResultT[IO, Unit] =
    s3.delete.executeT(conf)
}

object TemporaryS3 {
  val conf = Clients.s3

  implicit val TemporaryS3AddressResource = new Resource[TemporaryS3Address] {
    def close(temp: TemporaryS3Address) = temp.clean.run.void
  }

  implicit val TemporaryS3PrefixResource = new Resource[TemporaryS3Prefix] {
    def close(temp: TemporaryS3Prefix) = temp.clean.run.void
  }

  def withS3Address[A](f: S3Address => ResultTIO[A]): ResultTIO[A] =
    runWithS3Address(S3Address(testBucket, s3TempPath))(f)

  def runWithS3Address[A](s3: S3Address)(f: S3Address => ResultTIO[A]): ResultTIO[A] =
    ResultT.using(TemporaryS3Address(s3).pure[ResultTIO])(tmp => f(tmp.s3))

  def withS3Prefix[A](f: S3Prefix => ResultTIO[A]): ResultTIO[A] =
    runWithS3Prefix(S3Prefix(testBucket, s3TempPath))(f)

  def runWithS3Prefix[A](s3: S3Prefix)(f: S3Prefix => ResultTIO[A]): ResultTIO[A] =
    ResultT.using(TemporaryS3Prefix(s3).pure[ResultTIO])(tmp => f(tmp.s3))


  def testBucket: String = Option(System.getenv("AWS_TEST_BUCKET")).getOrElse("ambiata-dev-view")

  def s3TempPath: String = s"tests/temporary-${UUID.randomUUID()}"
}
