package com.ambiata.saws.s3

import com.ambiata.disorder._
import com.ambiata.saws.core.{AwsSpec => _, _}
import com.ambiata.saws.s3._
import com.ambiata.saws.s3.S3Temporary._
import com.ambiata.saws.testing._
import com.ambiata.saws.testing.Arbitraries._
import com.ambiata.saws.testing.AwsMatcher._
import com.ambiata.mundane.control.RIO
import org.specs2._

import scalaz.{Store => _, _}, Scalaz._

class S3TemporarySpec extends AwsSpec(5) { def is =  s2"""

 TemporaryS3 should clean up its own resources
 =============================================

   single address               $address
   prefix                       $prefix
   no conflicts                 $conflicts

"""
  def address = prop((s3: S3Temporary) => for {
    a <- s3.address
    _ <- a.put("")
    b <- a.exists
    _ <- S3Action.fromRIO(RIO.unsafeFlushFinalizers)
    e <- a.exists
  } yield b -> e ==== true -> false)

  def prefix = prop((s3: S3Temporary, id: Ident) => for {
    p <- s3.prefix
    a = p | id.value
    _ <- a.put("")
    b <- p.exists
    _ <- S3Action.fromRIO(RIO.unsafeFlushFinalizers)
    e <- p.exists
  } yield b -> e ==== true -> false)

  def conflicts = prop((s3: S3Temporary, i: NaturalInt) => i.value > 0 ==> (for {
    l <- (1 to i.value % 100).toList.traverseU(i =>
      if (i % 3 == 0)      s3.address.map(_.render)
      else if (i % 3 == 1) s3.prefix.map(_.render)
      else                 s3.pattern.map(_.render))
  } yield l.distinct ==== l))

}
