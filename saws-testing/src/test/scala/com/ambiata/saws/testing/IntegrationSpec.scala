package com.ambiata.saws.testing

import org.specs2.Specification
import org.specs2.specification.Fragments

abstract class IntegrationSpec extends Specification {
  override def map(fs: =>Fragments) = section("integration", "aws") ^ super.map(fs)
}
