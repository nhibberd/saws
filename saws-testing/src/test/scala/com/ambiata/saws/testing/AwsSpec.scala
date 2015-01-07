package com.ambiata.saws.testing

import org.specs2.matcher.Parameters
import org.specs2.ScalaCheck
import org.specs2.Specification
import org.specs2.specification.Fragments

abstract class AwsSpec(tests: Int) extends Specification with ScalaCheck {
  override def map(fs: =>Fragments) = section("aws") ^ super.map(fs)

  override implicit def defaultParameters: Parameters =
    new Parameters(minTestsOk = tests, workers = 3)
}
