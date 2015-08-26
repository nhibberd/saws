package com.ambiata.saws.cw

import org.specs2._
import org.scalacheck._
import Arbitraries._

class DataUnitSpec extends Specification with ScalaCheck { def is = s2"""

  Parsing / rendering $roundTrip

"""

  def roundTrip = prop { unit: DataUnit =>
    DataUnit.parse(unit.render).toEither must beRight(unit)
  }

}
