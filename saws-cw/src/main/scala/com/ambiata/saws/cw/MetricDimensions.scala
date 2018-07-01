package com.ambiata.saws.cw

import com.amazonaws.services.cloudwatch.model.MetricDatum
import scala.collection.JavaConverters._
import scalaz._, Scalaz._

object MetricDimensions {

  /** check if a list of dimensions is respecting the CloudWatch constraints */
  def checkMetricDataDimensions(dimensions: List[MetricDatum]): CloudWatchError \/ List[MetricDatum] =
    dimensions.traverseU(checkMetricDatumDimensions)

  /** check if a list of dimensions is respecting the CloudWatch constraints */
  def checkMetricDatumDimensions(md: MetricDatum): CloudWatchError \/ MetricDatum = {
    val ds = md.getDimensions.asScala.map(MetricDimension.fromDimension).toList

    if (ds.groupBy(_.name).size != ds.size) (DimensionsWithSameName(ds): CloudWatchError).left[MetricDatum]
    else if (ds.size > 10)                  (TooManyDimensions(ds): CloudWatchError).left[MetricDatum]
    else                                    md.right
  }

  /**
   * for each data point add similar data points for all prefixes of the list of dimensions
   */
  def setDimensionsPrefixes(dimensions: List[MetricDimension]): List[MetricDatum] => List[MetricDatum] =
    (metricData: List[MetricDatum]) => {
      metricData.flatMap { md =>
        dimensions.inits.filter(_.nonEmpty).map { dims =>
          cloneMetricData(md).withDimensions(dims.map(_.toDimension).asJavaCollection)
        }.toList
      }
    }

  /**
   * for each data point add similar data points set precisely this list of dimensions
   */
  def setAllDimensions(dimensions: List[MetricDimension]): List[MetricDatum] => List[MetricDatum] =
    (metricData: List[MetricDatum]) =>
      metricData.map(_.withDimensions(dimensions.map(_.toDimension).asJavaCollection))

  /**
   * Group all data points by equal name/value/unit
   * The take the longest list of dimensions create a new data point with this list +
   * new dimensions
   */
  def extendLongestPrefix(dimensions: List[MetricDimension]): List[MetricDatum] => List[MetricDatum] =
    (metricData: List[MetricDatum]) => {
      val byNameValueUnit: Map[(String, Double, String), List[MetricDatum]] =
        metricData.groupBy(nameValueUnit)

      val mostDimensions: List[MetricDatum] =
        byNameValueUnit.map(_._2.maxBy(_.getDimensions.size)).toList

      metricData ++ mostDimensions.map { md =>
        val cloned = cloneMetricData(md)
        val ds = cloned.getDimensions
        // who doesn't like mutation?
        ds.addAll(dimensions.map(_.toDimension).asJavaCollection)
        cloned
      }
    }
  
  def nameValueUnit: MetricDatum => (String, Double, String) = (md: MetricDatum) =>
    (md.getMetricName, md.getValue, md.getUnit)

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
