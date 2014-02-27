package com.ambiata.saws
package ses

case class ContentType(value: String)

object ContentType {
  object text {
    def plain = ContentType("text/plain")
    def html = ContentType("text/html")
    def csv = ContentType("text/csv")
    def psv = ContentType("text/psv")
  }
  object application {
    def excel = ContentType("application/vnd.ms-excel")
    def json = ContentType("application/json")
    def zip = ContentType("application/zip")
  }
}
