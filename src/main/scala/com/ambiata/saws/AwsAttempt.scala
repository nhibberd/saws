package com.ambiata.saws

import scalaz._
import scala.util.{Failure, Success, Try}


/** Return type used for calls made against AWS APIs. */
object AwsAttempt {
  type AwsAttempt[A] = String \/ A

  def apply[A](thunk: => A): AwsAttempt[A] = Try(thunk) match {
    case Success(a) => \/-(a)
    case Failure(e) => -\/(e.getMessage)
  }
}