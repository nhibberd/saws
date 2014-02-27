package com.ambiata.saws
package ses

case class ContentType(value: String)

object ContentType {
  object text {
    def plain = ContentType("text/plain")
    def html  = ContentType("text/html")
    def csv   = ContentType("text/csv")
    def psv   = ContentType("text/psv")
  }
  object image {
    def jpeg = ContentType("image/jpeg")
    def gif  = ContentType("image/gif")
    def png  = ContentType("image/png")
    def tiff = ContentType("image/tiff")
  }
  object application {
    def excel      = ContentType("application/vnd.ms-excel")
    def powerpoint = ContentType("application/vnd.ms-powerpoint")
    def json       = ContentType("application/json")
    def word       = ContentType("application/msword")
    def zip        = ContentType("application/zip")
    def gzip       = ContentType("application/x-gzip")
    def pdf        = ContentType("application/pdf")
  }
}
