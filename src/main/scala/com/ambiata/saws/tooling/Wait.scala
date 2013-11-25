package com.ambiata.saws
package tooling

import com.ambiata.saws.core._
import scalaz._, Scalaz._

object Wait {
  def waitFor[A](check: AwsAction[A, Boolean], message: Option[String] = None, sleep: Int = 5000): AwsAction[A, Unit] = for {
    ok <- check
    _  <- if (ok) AwsAction.ok[A, Unit](()) else for {
      _ <- AwsAction.value[A, Unit](message.foreach(println))
      _ <- AwsAction.value[A, Unit](Thread.sleep(sleep))
      _ <- waitFor[A](check, message, sleep)
    } yield ()
  } yield ()

  def waitForValue[A, B](value: AwsAction[A, Option[B]], message: Option[String] = None, sleep: Int = 5000): AwsAction[A, B] = for {
    maybe  <- value
    result <- maybe match {
      case Some(v) => AwsAction.ok[A, B](v)
      case None    => for {
        _ <- AwsAction.value[A, Unit](message.foreach(println))
        _ <- AwsAction.value[A, Unit](Thread.sleep(sleep))
        v <- waitForValue[A, B](value, message, sleep)
      } yield v
    }
  } yield result
}
