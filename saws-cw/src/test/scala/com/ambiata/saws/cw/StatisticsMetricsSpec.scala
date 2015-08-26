package com.ambiata.saws.cw

import com.ambiata.disorder.PositiveIntSmall
import org.joda.time.DateTime
import org.specs2.{ScalaCheck, Specification}
import Arbitraries._
import com.ambiata.mundane.testing.RIOMatcher._

import scalaz.\/

class StatisticsMetricsSpec extends Specification with ScalaCheck { def is = s2"""

  the timestamp be not be older than 2 weeks  $timestamp

"""

  def timestamp = prop { n: PositiveIntSmall =>
    StatisticsMetrics.checkTimestamp(DateTime.now.minusWeeks(n.value)) must beOkLike { ts: CloudWatchError \/ DateTime =>
      ts.toEither must beLeft
    }.when(n.value >= 3)
  }

}
