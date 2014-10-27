package com.ambiata.saws.s3

import java.security.MessageDigest

import com.ambiata.saws.core._
import com.ambiata.saws.testing._
import com.ambiata.saws.testing.AwsMatcher._
import org.specs2._

import scala.io.{Source, Codec}
import scalaz.{Name =>_,_}, Scalaz._, effect._, effect.Effect._

class S3BucketsSpec extends Specification with ScalaCheck { def is = s2"""

Versioning
==========

  The versioning life-cycle is fairly difficult to test as enabling versioning
  can never really be undone, and also enabling versioning on a test bucket
  would have some fairly poor cost outcomes. The best we can do is, make sure
  a dedicated 'versioning' test bucket exists, then go through an enable/disable
  process.  $versioning


"""

  val conf = Clients.s3

  def bucket: String =
    Option(System.getenv("AWS_TEST_VERSION_BUCKET")).getOrElse("ambiata-dev-version")

  def versioning = (for {
    _  <- S3Buckets.ensure(bucket)
    _  <- S3Buckets.disableVersioning(bucket)
    s0 <- S3Buckets.isVersioned(bucket)
    _  <- S3Buckets.enableVersioning(bucket)
    s1 <- S3Buckets.isVersioned(bucket)
    _  <- S3Buckets.disableVersioning(bucket)
  } yield s0 -> s1) must beOkValue(false -> true)

}
