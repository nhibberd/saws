package com.ambiata.saws.s3

import java.util.UUID

import com.ambiata.mundane.control._
import com.ambiata.mundane.io.Temporary._
import com.ambiata.saws.core._
import com.ambiata.saws.s3.S3Temporary._
import com.amazonaws.services.s3.AmazonS3Client
import java.util.concurrent.atomic.AtomicInteger
import scalaz._, Scalaz._, effect._

case class S3Temporary(seed: String) {
  private var step: AtomicInteger = new AtomicInteger(0)

  def address: S3Action[S3Address] = {
    val (base, i) = setup
    val path = base / i | seed
    run(base, path.render).as(path)
  }

  def prefix: S3Action[S3Prefix] = {
    val (base, i) = setup
    val path = base / i / seed
    run(base, path.render).as(path)
  }

  def pattern: S3Action[S3Pattern] = {
    val (base, i) = setup
    val path = (base / i / seed).toS3Pattern
    run(base, path.render).as(path)
  }

  /** (base path, divider) */
  def setup: (S3Prefix, String) = {
    val base = S3Prefix(testBucket, uniqueS3Path)
    val incr = step.incrementAndGet.toString
    (base, incr)
  }

  def run(base: S3Prefix, msg: String): S3Action[Unit] =
    addCleanupFinalizer(base, msg) >>
      addPrintFinalizer(msg)

  def addCleanupFinalizer(prefix: S3Prefix, msg: String): S3Action[Unit] =
    if (skipCleanup) forceAddPrintFinalizer(msg)
    else             S3Action.addFinalizer(prefix.delete)

  def addPrintFinalizer(msg: String): S3Action[Unit] =
    if (print) forceAddPrintFinalizer(msg)
    else       S3Action.unit

  def forceAddPrintFinalizer(msg: String): S3Action[Unit] =
    S3Action.addFinalizer(S3Action.putStrLn(s"Temporary: $msg"))

}

object S3Temporary {
  val conf = Clients.s3

  def testBucket: String = Option(System.getenv("AWS_TEST_BUCKET")).getOrElse("ambiata-dev-view")

  def uniqueS3Path: String = s"tests/temporary-${UUID.randomUUID()}"

  def random: S3Temporary =
    S3Temporary(s"tests-${java.util.UUID.randomUUID().toString}")
}
