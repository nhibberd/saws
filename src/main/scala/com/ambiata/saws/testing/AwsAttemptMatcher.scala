package com.ambiata.saws.testing

import org.specs2._
import matcher._
import execute._
import com.ambiata.saws.core.AwsAttempt


object AwsAttemptMatcher extends ThrownExpectations {
  def beOk[A]: Matcher[AwsAttempt[A]] =
    beOkLike(_ => Success())

  def beOkValue[A](expected: A): Matcher[AwsAttempt[A]] =
    beOkLike((actual: A) => new BeEqualTo(expected).apply(createExpectable(actual)).toResult)

  def beOkLike[A](check: A => Result): Matcher[AwsAttempt[A]] = new Matcher[AwsAttempt[A]] {
      def apply[S <: AwsAttempt[A]](attempt: Expectable[S]) = {
        val r = attempt.value match {
          case AwsAttempt.Ok(actual)          => check(actual)
          case AwsAttempt.ErrorMessage(error) => Failure(s"AwsAttempt failed with <${error}>")
        }
        result(r.isSuccess, r.message, r.message, attempt)
      }
    }
}
