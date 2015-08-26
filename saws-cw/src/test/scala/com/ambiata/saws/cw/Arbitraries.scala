package com.ambiata.saws.cw

import com.ambiata.com.amazonaws.services.cloudwatch.model.MetricDatum
import com.ambiata.disorder._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone._
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.scalacheck._

object Arbitraries {

  implicit def ArbitraryDataUnit: Arbitrary[DataUnit] =
    Arbitrary(Gen.oneOf(DataUnit.allUnits))


  implicit def StatisticsMetricsArbitrary: Arbitrary[StatisticsMetrics] =
    Arbitrary {
      for {
        stats <- arbitrary[Statistics]
        ds    <- arbitrary[MetricDimensions]
        ts    <- arbitrary[Timestamp]
      } yield StatisticsMetrics(stats, ds, timestamp = ts.value)
    }

  // statistics must not be empty
  implicit def StatisticsArbitrary: Arbitrary[Statistics] =
    Arbitrary { sized { n =>
        for {
          names <- Gen.oneOf(Corpus.muppets.permutations.take(n+1).toSeq)
          data  <- listOfN(n+1, arbitrary[StatisticsData])
        } yield Statistics(Map(names zip data: _*))
      }
    }

  implicit def StatisticsDataArbitrary: Arbitrary[StatisticsData] =
    Arbitrary {
      for {
        value <- arbitrary[PositiveInt].map(_.value.toDouble)
        unit  <- arbitrary[DataUnit]
      } yield StatisticsData(value, unit)
    }

  implicit def NamespaceArbitrary: Arbitrary[Namespace] =
    Arbitrary(S.words.map(Namespace.fromString).map(_.toOption).suchThat(_.isDefined).map(_.get))

  implicit def MetricDimensionArbitrary: Arbitrary[MetricDimension] =
    Arbitrary {
      for {
        n <- Gen.oneOf(Corpus.simpsons)
        v <- Gen.oneOf(Corpus.weather)
      } yield MetricDimension(n, v)
    }

  // make sure the dimension names are unique
  // and the total number of dimensions is less than 10
  def genDimensions: Gen[List[MetricDimension]] =
    Gen.nonEmptyListOf(arbitrary[MetricDimension]).map(_.groupBy(_.name).map(_._2.head).toList.take(10))

  implicit def MetricDimensionsArbitrary: Arbitrary[MetricDimensions] =
    Arbitrary {
      for {
        b  <- arbitrary[Boolean]
        ds <- genDimensions
      } yield if (b) ExactMetricDimensions(ds) else PrefixesMetricDimensions(ds)
    }

  implicit def ExactMetricDimensionsArbitrary: Arbitrary[ExactMetricDimensions] =
    Arbitrary(genDimensions.map(ExactMetricDimensions.apply))

  implicit def AggregateMetricDimensionsArbitrary: Arbitrary[PrefixesMetricDimensions] =
    Arbitrary(genDimensions.map(PrefixesMetricDimensions.apply))

  implicit def MetricDatumArbitrary: Arbitrary[MetricDatum] =
    Arbitrary {
      for {
        n  <- Gen.oneOf(Corpus.simpsons)
        sd <- arbitrary[StatisticsData]
      } yield StatisticsMetrics.createMetricDatum((n, sd))
    }

  implicit def DateTimeArbitrary: Arbitrary[DateTime] =
    Arbitrary(arbitrary[PositiveLongSmall].map(l => new DateTime(l.value).withMillisOfDay(0).withZone(UTC)))

  implicit def TimestampArbitrary: Arbitrary[Timestamp] =
    Arbitrary(arbitrary[PositiveLongSmall].map(l => Timestamp(DateTime.now.minusMillis(l.value.toInt).withMillisOfDay(0).withZone(UTC))))

  case class Timestamp(value: DateTime)
}


