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

  def dimensions = prop { dimensions: MetricDimensions =>
    MetricDimensions.checkDimensions(dimensions).toEither must beLeft { e: CloudWatchError =>
      e must beLike { case TooManyDimensions(_) => ok }
    }.when(dimensions.size > 10)
  }

  def dimensionNames = prop { dimensions: MetricDimensions =>
    // duplicate the dimensions
    val ds = dimensions.addDimensions(dimensions.dimensions)

    MetricDimensions.checkDimensions(ds).toEither must beLeft { e: CloudWatchError =>
      e must beLike { case DimensionsWithSameName(_) => ok }
    }.when(ds.size > 0)
  }

  def exact = prop { (ds: ExactMetricDimensions, md: List[MetricDatum]) =>
    ds.setDimensions(md) must contain { d: MetricDatum =>
      d.getDimensions must haveSize(ds.size)
    }.forall
  }

  def prefixes = prop { (ds: PrefixesMetricDimensions, md: MetricDatum) =>
    ds.setDimensions(List(md)).map(_.getDimensions.asScala.toList) must_==
      ds.dimensions.map(_.toDimension).inits.toList.filter(_.nonEmpty)
  }
}

