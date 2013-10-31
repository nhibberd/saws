package com.ambiata.saws
package iam

import com.amazonaws.services.s3.AmazonS3Client


/** Sydney-region S3 client. */
object S3 {
  val S3Endpoint = "s3-ap-southeast-2.amazonaws.com"

  def apply(): AmazonS3Client = {
    val c = new AmazonS3Client()
    c.setEndpoint(S3Endpoint)
    c
  }
}