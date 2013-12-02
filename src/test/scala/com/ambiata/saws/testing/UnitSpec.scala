package com.ambiata.saws.testing

import org.specs2.Specification
import org.specs2.specification.Fragments

abstract class UnitSpec extends Specification {
  override def map(fs: =>Fragments) = section("unit") ^ super.map(fs)
}
