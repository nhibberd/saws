package com.ambiata.saws.s3

import scalaz._, Scalaz._

object S3Operations {
  val DELIMITER: String = "/"

  def render(name: String, bucket: String, s: String): String =
    s"$name($bucket/$s)"

  def concat(one: String, two: String): String =
    if (one.endsWith(DELIMITER) || one.isEmpty) one + two
    else one + DELIMITER + two

  def removeCommonPrefix(bucket: String, prefix: String, removeBucket: String, removePrefix: String): Option[String] = {
    def ll(x: List[String], y: List[String]): Option[String] =
      if (y.zipL(x).forall( { case (l,r) => Some(l) == r} ))
        Some(x.drop(y.size).mkString("/"))
      else
        None

    if (removeBucket.equals(bucket)) {
      val keys = prefix.split(DELIMITER).toList
      val input = removePrefix.split(DELIMITER).toList.filter(_.nonEmpty)
      ll(keys, input)
    } else
      None
  }
}
