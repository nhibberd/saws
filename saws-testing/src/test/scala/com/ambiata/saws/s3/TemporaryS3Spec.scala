package com.ambiata.saws.s3

import com.ambiata.saws.testing.TemporaryS3
import com.ambiata.mundane.testing.ResultTIOMatcher._
import TemporaryS3._
import org.specs2.Specification

import scalaz.{Store => _}

class TemporaryS3Spec extends Specification { def is = s2"""

 TemporaryS3 should clean up its own resources
 =============================================

   single address               $singleAddress         ${tag("aws")}
   prefix                       $prefix                ${tag("aws")}

"""

  def singleAddress = {
    val p = S3Address(testBucket, s3TempPath)
    (for {
      x <- TemporaryS3.runWithS3Address(p)(s3 => for {
        _ <- s3.put("").executeT(TemporaryS3.conf)
        e <- s3.exists.executeT(TemporaryS3.conf)
      } yield e)
      y <- p.exists.executeT(TemporaryS3.conf)
    } yield (x, y)) must beOkValue((true,false))
  }
  
  def prefix = {
    val p = S3Prefix(testBucket, s3TempPath)
    (for {
      x <- TemporaryS3.runWithS3Prefix(p)(s3 =>  for {
        _ <- (s3 | "foo").put("").executeT(TemporaryS3.conf)
        e <- s3.exists.executeT(TemporaryS3.conf)
        f <- (s3 | "foo").exists.executeT(TemporaryS3.conf)
      } yield e -> f)
    z <- p.exists.executeT(TemporaryS3.conf)
    y <- (p | " foo").exists.executeT(TemporaryS3.conf)
    } yield (x, z -> y)) must beOkValue((true,true), (false, false))
  }
}
