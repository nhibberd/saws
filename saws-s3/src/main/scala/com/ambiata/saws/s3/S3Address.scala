package com.ambiata.saws.s3

import scalaz._, Scalaz._

case class S3Address(bucket: String, key: String) {
  val DELIMITER: String = "/"

  def removeCommonPrefix(data: S3Address): Option[String] = {
    def ll(x: List[String], y: List[String]): Option[String] =
      if (y.zipL(x).forall( { case (l,r) => Some(l) == r} ))
        Some(x.drop(y.size).mkString("/"))
      else
        None

    if (data.bucket.equals(bucket)) {
      val keys = key.split(DELIMITER).toList
      val input = data.key.split(DELIMITER).toList.filter(_.nonEmpty)
      ll(keys, input)
    } else
      None
  }

  def render: String =
    bucket + "/" + key

  def /(suffix: String): S3Address =
    S3Address(bucket, key + "/" + suffix)
}

case class SizedS3S3Address(s3Data: S3Address, size: Long)
