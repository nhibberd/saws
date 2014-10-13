package com.ambiata.saws.testing

import com.ambiata.mundane.testing.ResultTIOMatcher._
import com.ambiata.saws.core.Clients
import com.ambiata.saws.s3._
import com.ambiata.saws.testing.TemporaryS3._
import org.specs2.Specification

import scalaz.{Store => _, _}, Scalaz._

class TemporaryS3Spec extends Specification { def is = s2"""

 TemporaryS3 should clean up its own resources
 =============================================

   single file               $singleFile

"""

  def singleFile = {
    val p = S3Address(testBucket, s3TempPath)
    (for {
      x <- TemporaryS3.runWithS3Address(p)(s3 => for {
        _ <- S3.putString(s3, "").executeT(Clients.s3)
        e <- S3.exists(s3).executeT(Clients.s3)
      } yield e)
      y <- S3.exists(p).executeT(Clients.s3)
    } yield (x, y)) must beOkValue((true,false))
  }.pendingUntilFixed("S3:160")
}