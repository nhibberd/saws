package com.ambiata.saws.cw

import com.ambiata.mundane.testing.RIOMatcher._
import com.ambiata.saws.testing.IntegrationSpec
import org.specs2._
import Arbitraries._

class StatisticsMetricsIntegrationSpec extends Specification with ScalaCheck with IntegrationSpec { def is = s2"""

  uploading correct statistics metrics should succeed $upload

"""

  def upload = prop { stats: StatisticsMetrics =>
    StatisticsMetrics.upload(stats, "test") must beOk
  }.set(minTestsOk = 5)
}

