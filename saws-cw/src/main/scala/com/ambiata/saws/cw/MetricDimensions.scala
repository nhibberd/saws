package com.ambiata.saws.cw

import com.ambiata.com.amazonaws.services.cloudwatch.model.MetricDatum
import scala.collection.JavaConverters._
import scalaz._, Scalaz._
import MetricDimensions._

/**
 * List of metric dimensions
 *
 * They can be applied in different ways to a list of data points
 */
sealed trait MetricDimensions {

  def addDimension(d: MetricDimension): MetricDimensions

  def addDimensions(ds: List[MetricDimension]): MetricDimensions =
    ds.foldLeft(this)(_ addDimension _)

  def dimensions: List[MetricDimension]

  def size: Int =
    dimensions.size

  def setDimensions(metricData: List[MetricDatum]): List[MetricDatum]
}

object MetricDimensions {

  /** check if a list of dimensions is respecting the CloudWatch constraints */
  def checkDimensions(dimensions: MetricDimensions): CloudWatchError \/ MetricDimensions = {
    val ds = dimensions.dimensions

    if (ds.groupBy(_.name).size != ds.size) (DimensionsWithSameName(ds): CloudWatchError).left[MetricDimensions]
    else if (ds.size > 10)                  (TooManyDimensions(ds): CloudWatchError).left[MetricDimensions]
    else                                    dimensions.right
  }

}

/**
 * Metric dimensions are set on metric datum
 * as per the number of inits in the list
 *
 * This allows metric data to be aggregated across dimensions
 */
case class PrefixesMetricDimensions(dimensions: List[MetricDimension]) extends MetricDimensions {
  def addDimension(d: MetricDimension): MetricDimensions =
    copy(dimensions = dimensions :+ d)

  def setDimensions(metricData: List[MetricDatum]): List[MetricDatum] =
    metricData.flatMap { md =>
      dimensions.inits.filter(_.nonEmpty).map { dims =>
        cloneMetricData(md).withDimensions(dims.map(_.toDimension).asJavaCollection)
      }.toList
    }

  /** the beauty of mutability ... */
  def cloneMetricData(md: MetricDatum): MetricDatum =
    (new MetricDatum)
      .withDimensions(md.getDimensions)
      .withMetricName(md.getMetricName)
      .withStatisticValues(md.getStatisticValues)
      .withUnit(md.getUnit)
      .withValue(md.getValue)
      .withTimestamp(md.getTimestamp)

}

/**
 * Exact metric dimensions.
 *
 * The full list of dimensions is attached to a metric data point
 */
case class ExactMetricDimensions(dimensions: List[MetricDimension]) extends MetricDimensions {
  def addDimension(d: MetricDimension): MetricDimensions =
    copy(dimensions = dimensions :+ d)

  def setDimensions(metricData: List[MetricDatum]): List[MetricDatum] =
    metricData.map(_.withDimensions(dimensions.map(_.toDimension).asJavaCollection))

}

