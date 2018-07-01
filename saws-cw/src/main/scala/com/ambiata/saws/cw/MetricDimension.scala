package com.ambiata.saws.cw

import com.amazonaws.services.cloudwatch.model.Dimension

/**
 * Scala-friendly Dimension class
 */
case class MetricDimension(name: String, value: String) extends {
  def toDimension: Dimension = {
    val d: Dimension = new Dimension
    d.setName(name)
    d.setValue(value)
    d
  }

  def render: String =
    s"$name=$value"
}

object MetricDimension {
  def fromDimension(d: Dimension): MetricDimension =
    MetricDimension(d.getName, d.getValue)
}

