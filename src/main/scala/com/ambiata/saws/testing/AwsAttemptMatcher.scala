package com.ambiata.saws.testing

import org.specs2._, specification._, org.specs2.matcher._, org.specs2.execute._
import com.ambiata.saws.core.AwsAttempt


object AwsAttemptMatcher extends MustMatchers with StandardResults with ThrownExpectations {
  def beOk[A]: Matcher[AwsAttempt[A]] =
    beOkLike(_ => true)

  def beOkValue[A](expected: A): Matcher[AwsAttempt[A]] =
    beOkLike(_ must_== expected)

  def beOkLike[A](check: A => Result): Matcher[AwsAttempt[A]] =
    (attempt: AwsAttempt[A]) => attempt match {
      case AwsAttempt.Ok(actual) =>
        check(actual)
      case AwsAttempt.ErrorMessage(error) =>
        failure(s"AwsAttempt failed with <${error}>")
    }
}
