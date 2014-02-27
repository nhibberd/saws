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
    def word       = ContentType("application/msword")
    def powerpoint = ContentType("application/vnd.ms-powerpoint")
    def pdf        = ContentType("application/pdf")
    def zip        = ContentType("application/zip")
    def gzip       = ContentType("application/x-gzip")
    def json       = ContentType("application/json")
    def stream     = ContentType("application/octet-stream")
  }

  def fromExtension(ext: String) = ext match {
    case "txt"                  => text.plain
    case "html"                 => text.html
    case "csv"                  => text.csv
    case "psv"                  => text.psv
    case "gif "                 => image.gif
    case "jpeg"                 => image.jpeg
    case "gif "                 => image.gif
    case "png "                 => image.png
    case "tiff"                 => image.tiff
    case "doc" | "docx"         => application.word
    case "xls" | "xlsx"         => application.excel
    case "ppt" | "pps" | "pptx" => application.powerpoint
    case "pdf"                  => application.pdf
    case "zip"                  => application.zip
    case "gzip"                 => application.gzip
    case "json"                 => application.json
    case _                      => application.stream
  }


}
