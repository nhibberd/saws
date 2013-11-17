package com.ambiata.saws
package testing

import org.specs2._, specification._, matcher._, execute._
import core.AwsAttempt


object AwsAttemptMatcher extends MustMatchers with StandardResults {
  def beOk[A]: Matcher[AwsAttempt[A]] =
    beOkLike(_ => true)

  def beOkValue[A](expected: A): Matcher[AwsAttempt[A]] =
    beOkLike(_ must_== expected)

  def beOkLike[A](check: A => Result): Matcher[AwsAttempt[A]] =
    (attempt: AwsAttempt[A]) => attempt match {
      case AwsAttempt.Ok(actual) =>
        check(actual)
      case AwsAttempt.ErrorMessage(error) =>
        failure
    }
}
