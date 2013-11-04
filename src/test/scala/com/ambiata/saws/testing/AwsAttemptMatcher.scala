package com.ambiata.saws
package testing

import org.specs2.matcher._
import core.AwsAttempt


object AwsAttemptMatcher extends MustMatchers {

  /** Matcher testing if an attempted AWS API call succeeds. */
  def beSuccessful[T]: Matcher[AwsAttempt[T]] =
    (attempt: AwsAttempt[T]) => attempt.toOption must beSome
}
