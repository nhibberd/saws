package com.ambiata.saws.testing

import org.specs2._
import org.specs2.specification.Fragments

trait IntegrationSpec extends SpecificationLike {
  override def map(fs: =>Fragments) = section("integration", "aws") ^ super.map(fs)
}
