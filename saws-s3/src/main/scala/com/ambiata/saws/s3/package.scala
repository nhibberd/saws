package com.ambiata.saws

import com.ambiata.com.amazonaws.services.s3.model._

import scalaz._, Scalaz._, effect._

package object s3 {
  implicit def S3ObjectResource: Resource[S3Object] =
    Resource.resourceFromCloseable[S3Object]
}
