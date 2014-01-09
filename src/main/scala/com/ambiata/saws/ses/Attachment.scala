package com.ambiata.saws
package ses

import scalaz._, Scalaz._

case class Attachment(
  name: String,
  contentType: ContentType,
  data: Array[Byte] \/ java.io.File
)

object Attachment {
  def file(name: String, contentType: ContentType, file: java.io.File) =
    Attachment(name, contentType, file.right)

  def data(name: String, contentType: ContentType, data: Array[Byte]) =
    Attachment(name, contentType, data.left)
}
