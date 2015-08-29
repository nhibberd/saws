package com.ambiata.saws.cw

import com.ambiata.com.amazonaws.services.cloudwatch.model.MetricDatum
import org.specs2._
import Arbitraries._
import scala.collection.JavaConverters._

class MetricDimensionsSpec extends Specification with ScalaCheck { def is = s2"""

 Metric dimensions have some constraints
  only a maximum of 10 dimensions is allowed for metric data $dimensions
  dimension names must be unique                             $dimensionNames

 There are 2 ways to set dimensions on a metric data point
   apply all the dimensions at once                      $exact
   apply all non-empty prefixes of the dimensions list   $prefixes

"""

  def dimensions = prop { md: MetricDatum =>
    MetricDimensions.checkMetricDatumDimensions(md).toEither must beLeft { e: CloudWatchError =>
      e must beLike { case TooManyDimensions(_) => ok }
    }.when(md.getDimensions.size > 10)
  }

  def dimensionNames = prop { md: MetricDatum =>
    // duplicate the dimensions
    val ds = md.getDimensions
    md.withDimensions(ds)

    MetricDimensions.checkMetricDatumDimensions(md).toEither must beLeft { e: CloudWatchError =>
      e must beLike { case DimensionsWithSameName(_) => ok }
    }.when(ds.size > 0)
  }

  def exact = prop { (ds: List[MetricDimension], mds: List[MetricDatum]) =>
    MetricDimensions.setAllDimensions(ds)(mds) must contain { d: MetricDatum =>
      d.getDimensions must haveSize(ds.size)
    }.forall
  }

  def prefixes = prop { (ds: List[MetricDimension], md: MetricDatum) =>
    MetricDimensions.setDimensionsPrefixes(ds)(List(md)).map(_.getDimensions.asScala.toList) must_==
      ds.map(_.toDimension).inits.toList.filter(_.nonEmpty)
  }
}

