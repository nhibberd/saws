package com.ambiata.saws.cw

/**
 * Statistics collected by applications at the end of their run
 */
case class Statistics(stats: Map[String, StatisticsData]) {

  def isEmpty: Boolean =
    stats.isEmpty

}

/** individual piece of statistic */
case class StatisticsData(value: Double, unit: DataUnit)
