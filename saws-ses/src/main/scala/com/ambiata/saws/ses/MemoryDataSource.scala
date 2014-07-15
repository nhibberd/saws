package com.ambiata.saws
package ses

import javax.activation._

case class MemoryDataSource(name: String, contentType: String, data: Array[Byte]) extends DataSource {
  def getContentType = contentType
  def getInputStream = new java.io.ByteArrayInputStream(data)
  def getName = name
  def getOutputStream = sys.error(s"Can't write to in memory data source [$name].")
}
