package com.ambiata.saws
package ses

import com.amazonaws.services.simpleemail._
import com.amazonaws.services.simpleemail.model.{Message => AwsMessage, _}
import com.ambiata.mundane.control._

import scala.collection.JavaConverters._
import scalaz._, Scalaz._, \&/._

object Email {
  def withClient[A](f: AmazonSimpleEmailServiceClient => A): Result[A] =
    Result.safe(f(core.Clients.ses))

  def send(message: Message): Result[MessageId] =
    withClient(client => MessageId(client.sendEmail(
      new SendEmailRequest()
        .withDestination(
          new Destination()
            .withToAddresses(message.to.asJava)
            .withCcAddresses(message.cc.asJava)
            .withBccAddresses(message.bcc.asJava))
        .withMessage(
          new AwsMessage()
            .withSubject(utf8(message.subject))
            .withBody(message.body match {
              case This(text)       => new Body().withText(utf8(text))
              case That(html)       => new Body().withHtml(utf8(html))
              case Both(text, html) => new Body().withText(utf8(text)).withHtml(utf8(html))
            }))
        .withReplyToAddresses((if (message.reply.isEmpty) List(message.from) else message.reply).asJava)
        .withReturnPath(message.bouncer.getOrElse(message.from))
        .withSource(message.from)).getMessageId))

  def sendWithAttachments(message: Message, attachments: List[Attachment]) =
    withClient(client => MessageId(client.sendRawEmail(
      new SendRawEmailRequest(JavaMailIsAnAbomination.raw(message, attachments))
        .withSource(message.bouncer.getOrElse(message.from))).getMessageId))

  def utf8(s: String): Content =
    new Content(s).withCharset("UTF-8")
}
