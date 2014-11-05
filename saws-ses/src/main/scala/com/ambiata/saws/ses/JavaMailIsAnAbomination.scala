package com.ambiata.saws
package ses

import com.ambiata.com.amazonaws.services.simpleemail.model.{Message => AwsMessage, _}

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.ByteBuffer
import java.util.Properties

import javax.mail.{Message => JMessage, _}
import javax.mail.Message.RecipientType
import javax.mail.internet._
import javax.activation._

import scala.collection.JavaConverters._

import ContentType._

object JavaMailIsAnAbomination {
  def raw(message: Message, attachments: List[Attachment]): RawMessage = {
    val out = new RawMessage
    out.setData(ByteBuffer.wrap(bytes(message, attachments)))
    out
  }

  def bytes(message: Message, attachments: List[Attachment]): Array[Byte] = {
    val out = new ByteArrayOutputStream
    build(message, attachments).writeTo(out)
    out.toByteArray
  }

  def build(message: Message, attachments: List[Attachment]): MimeMessage = {
    val session = Session.getInstance(new Properties(), null)
    val mime = new MimeMessage(session)
    val parts = new MimeMultipart

    mime.setFrom(new InternetAddress(message.from))
    mime.setReplyTo(Array(address(message.from)))
    mime.setSender(address(message.bouncer.getOrElse(message.from)))

    recipients(mime, RecipientType.TO, message.to)
    recipients(mime, RecipientType.CC, message.cc)
    recipients(mime, RecipientType.BCC, message.bcc)

    mime.setSubject(message.subject)
    message.body.a.foreach(body(parts, text.plain, _))
    message.body.b.foreach(body(parts, text.html, _))

    attachments.map(attachment(parts, _))

    mime.setContent(parts)
    mime
  }

  def body(parts: MimeMultipart, contentType: ContentType, content: String): Unit =
    part(parts) { p => p.setContent(content, contentType.value) }

  def attachment(parts: MimeMultipart, attachment: Attachment): Unit =
    part(parts) { p =>
      p.setFileName(attachment.name)
      p.setDataHandler(
        new DataHandler(attachment.data.fold(
         MemoryDataSource(attachment.name, attachment.contentType.value, _),
           new FileDataSource(_)))) }

  def part(parts: MimeMultipart)(f: MimeBodyPart => Unit): Unit = {
    val p = new MimeBodyPart
    f(p)
    parts.addBodyPart(p)
  }

  def recipients(mime: MimeMessage, recipientType: RecipientType, recipients: List[String]) =
    mime.setRecipients(recipientType, recipients.map(address).toArray)

  def address(s: String): Address =
    new InternetAddress(s)
}
