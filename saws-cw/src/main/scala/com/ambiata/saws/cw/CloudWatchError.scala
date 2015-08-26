package com.ambiata.saws.cw

import org.joda.time.DateTime

/**
 * Specific class of errors to codify what can happen
 * when creating or uploading metrics
 */
sealed trait CloudWatchError

case class TooManyDimensions(dimensions: List[MetricDimension]) extends CloudWatchError
case class TooOldTimestamp(ts: DateTime, now: DateTime) extends CloudWatchError
case class NamespaceFormatError(n: String) extends CloudWatchError
case class DimensionsWithSameName(dimensions: List[MetricDimension]) extends CloudWatchError

object CloudWatchError {

  def render(e: CloudWatchError): String = e match {
    case TooManyDimensions(dims) =>
      s"Too many dimensions for the same metric. Must be <= 10. Got: ${dims.map(_.render).mkString("\n")}"

    case TooOldTimestamp(timestamp, now) =>
      s"Timestamp must specify a time within the past two weeks. Got: $timestamp, now is $now"

    case DimensionsWithSameName(dimensions) =>
      s"No metric may specify the same dimension name twice. Got: ${dimensions.map(_.render).mkString("\n")}"

    case NamespaceFormatError(n) =>
      s"The format for a namespace is [^:].* Got: $n"
  }

}


