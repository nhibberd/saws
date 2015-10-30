package com.ambiata.saws.cw

import scalaz._, Scalaz._

/**
 * Namespace for uploading metrics
 */
case class Namespace private(name: String) extends AnyVal {
  def append(other: Namespace): Namespace =
    Namespace(name+"/"+other.name)
}

object Namespace {

  /**
   * Namespaces must respect the CloudWatch format. Supposedly
   * anything matching "[^:].*" but actually non-ascii characters are not supported
   *
   * @see http://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_GetMetricStatistics.html
   */
  def fromString(n: String): CloudWatchError \/ Namespace =
    if (n.forall(isAsciiNoControl)) Namespace(n).right
    else                            NamespaceFormatError(n).left

  def isAsciiNoControl(c: Char): Boolean = {
    val intCode = Char.char2int(c)
    intCode >= 32 && intCode <= 126
  }

  def unsafe(n: String): Namespace =
    Namespace(n)
}

