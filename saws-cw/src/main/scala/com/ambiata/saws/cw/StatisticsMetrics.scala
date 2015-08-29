package com.ambiata.saws.cw

import com.ambiata.com.amazonaws.services.cloudwatch.model._
import com.ambiata.mundane.control.RIO
import com.ambiata.saws.core.Clients
import org.joda.time._
import scala.collection.JavaConverters._
import scalaz._, Scalaz._
import StatisticsMetrics._

/**
 * This class groups statistics gathered for a given application which we want to
 * publish as CloudWatch metrics.
 *
 * The timestamp is considered global for all the data points
 * and should correspond to the end of the application run
 */
case class StatisticsMetrics(statistics: Statistics, setDimensions: List[MetricDatum] => List[MetricDatum], timestamp: DateTime) {

  def isEmpty: Boolean =
    statistics.isEmpty

  /**
   * @return statistics as metric datum objects for CloudWatch
   *         fail if there are more than 10 dimensions
   *         or if the timestamp is more than 2 weeks in the past
   */
  def toMetricData: RIO[CloudWatchError \/ List[MetricDatum]] =
    checkTimestamp(timestamp).map { ts =>
      for {
        tstamp <- ts
        mds    =  setDimensions(statistics.stats.toList.map(createMetricDatum))
        ds     <- MetricDimensions.checkMetricDataDimensions(mds)
      } yield mds.map(setTimestamp(tstamp))
    }

}

object StatisticsMetrics {

  /**
   * Upload metrics to CloudWatch
   */
  def upload(statistics: StatisticsMetrics, namespace: Namespace): RIO[Unit] = {
    val action =
      // if there are no statistics we should return ok right away
      // otherwise the request will fail
      if (statistics.isEmpty) EitherT.right(RIO.ok(()))
      else
        for {
          rs <- makePutMetricDataRequests(statistics, namespace)
          _  <- rs.traverseU(r => EitherT.right[RIO, CloudWatchError, Unit](RIO.safe(Clients.cw.putMetricData(r))))
        } yield ()

    action.run.flatMap(_.fold(e => RIO.fail(CloudWatchError.render(e)), RIO.ok))
  }

  /**
   * Create requests to publish metric data from statistics
   *
   * Each request can only have 20 metrics maximum
   */
  def makePutMetricDataRequests(stats: StatisticsMetrics, namespace: Namespace): EitherT[RIO, CloudWatchError, List[PutMetricDataRequest]] =
    for {
      metrics <- EitherT(stats.toMetricData)
      grouped =  metrics.grouped(20).toList
    } yield grouped.map(createPutRequest(namespace))

  /**
   * create a put request for a list of maximum 20 metrics
   */
  def createPutRequest(namespace: Namespace)(metrics: List[MetricDatum]): PutMetricDataRequest = {
    val putRequest: PutMetricDataRequest = new PutMetricDataRequest
    putRequest.withNamespace(namespace.name)
    putRequest.withMetricData(metrics.asJavaCollection)
    putRequest
  }

  def checkTimestamp(timestamp: DateTime): RIO[CloudWatchError \/ DateTime] =
    RIO.safe(DateTime.now) map { now =>
      if (Weeks.weeksBetween(timestamp, now).getWeeks <= 2)
        timestamp.right
      else
        TooOldTimestamp(timestamp, now).left
    }

  /** create a metric data point with no timestamp nor dimensions */
  def createMetricDatum(nd: (String, StatisticsData)): MetricDatum = {
    val (name, data) = nd
    val metric = new MetricDatum
    metric.setMetricName(name)
    metric.setValue(data.value)
    metric.setUnit(data.unit.render)
    metric
  }

  def setTimestamp(ts: DateTime) = { md: MetricDatum =>
    md.setTimestamp(ts.toDate)
    md
  }

}
