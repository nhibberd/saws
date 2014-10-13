package com.ambiata.saws.s3

import org.specs2._

class S3AddressSpec extends Specification with ScalaCheck { def is = s2"""

Test that we can remove common prefix's from S3Data
------

 Can successfully remove common prefix              $succesfullCommonPrefix
 Will return None when there is no common prefix    $noCommonPrefix
 Can handle a bucket as the common prefix           $bucketPrefix
 Will return None when there is no common bucket    $differentBuckets

"""

  def succesfullCommonPrefix = {
    val s3 = S3Address("bucket","a/b/c/d/e")
    val foo = S3Address("bucket","a/b/c")
    s3.removeCommonPrefix(foo) must_== Some("d/e")
  }

  def noCommonPrefix = {
    val s3 = S3Address("bucket","a/b/c/d/e")
    val foo = S3Address("bucket","a/b/e")
    s3.removeCommonPrefix(foo) must_== None
  }

  def bucketPrefix = {
    val s3 = S3Address("bucket","a")
    val foo = S3Address("bucket","")
    s3.removeCommonPrefix(foo) must_== Some("a")
  }

  def differentBuckets = {
    val s3 = S3Address("bucket","a")
    val foo = S3Address("buckset","")
    s3.removeCommonPrefix(foo) must_== None
  }
}
