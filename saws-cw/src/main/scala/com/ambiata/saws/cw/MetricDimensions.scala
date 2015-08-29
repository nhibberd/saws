package com.ambiata.saws.cw

import com.ambiata.com.amazonaws.services.cloudwatch.model.MetricDatum
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

  def setDimensionsPrefixes(dimensions: List[MetricDimension]): List[MetricDatum] => List[MetricDatum] =
    (metricData: List[MetricDatum]) => {
      metricData.flatMap { md =>
        dimensions.inits.filter(_.nonEmpty).map { dims =>
          cloneMetricData(md).withDimensions(dims.map(_.toDimension).asJavaCollection)
        }.toList
      }
    }

  def setAllDimensions(dimensions: List[MetricDimension]): List[MetricDatum] => List[MetricDatum] =
    (metricData: List[MetricDatum]) =>
      metricData.map(_.withDimensions(dimensions.map(_.toDimension).asJavaCollection))

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
