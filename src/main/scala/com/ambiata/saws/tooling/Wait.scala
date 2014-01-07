package com.ambiata.saws
package tooling

import com.ambiata.saws.core._
import scalaz._, Scalaz._

object Wait {
  def waitFor[A](check: Aws[A, Boolean], message: Option[String] = None, sleep: Int = 5000): Aws[A, Unit] = for {
    ok <- check
    _  <- if (ok) Aws.ok[A, Unit](()) else for {
      _ <- Aws.safe[A, Unit](message.foreach(println))
      _ <- Aws.safe[A, Unit](Thread.sleep(sleep))
      _ <- waitFor[A](check, message, sleep)
    } yield ()
  } yield ()

  def waitForValue[A, B](value: Aws[A, Option[B]], message: Option[String] = None, sleep: Int = 5000): Aws[A, B] = for {
    maybe  <- value
    result <- maybe match {
      case Some(v) => Aws.ok[A, B](v)
      case None    => for {
        _ <- Aws.safe[A, Unit](message.foreach(println))
        _ <- Aws.safe[A, Unit](Thread.sleep(sleep))
        v <- waitForValue[A, B](value, message, sleep)
      } yield v
    }
  } yield result
}
