package com.ambiata.saws
package ses

import scalaz._, Scalaz._, \&/._

case class Message(
  to: List[String] = Nil,
  cc: List[String] = Nil,
  bcc: List[String] = Nil,
  reply: List[String] = Nil,
  bouncer: Option[String] = None,
  from: String,
  subject: String,
  body: Message.Text \&/ Message.Html
)

object Message {
  type Text = String
  type Html = String

  def text(
    to: List[String] = Nil,
    cc: List[String] = Nil,
    bcc: List[String] = Nil,
    reply: List[String] = Nil,
    bouncer: Option[String] = None,
    from: String,
    subject: String,
    text: Text
  ) = Message(to, cc, bcc, reply, bouncer, from, subject, This(text))

  def html(
    to: List[String] = Nil,
    cc: List[String] = Nil,
    bcc: List[String] = Nil,
    reply: List[String] = Nil,
    bouncer: Option[String] = None,
    from: String,
    subject: String,
    html: Html
  ) = Message(to, cc, bcc, reply, bouncer, from, subject, That(html))

  def both(
    to: List[String] = Nil,
    cc: List[String] = Nil,
    bcc: List[String] = Nil,
    reply: List[String] = Nil,
    bouncer: Option[String] = None,
    from: String,
    subject: String,
    text: Text,
    html: Html
  ) = Message(to, cc, bcc, reply, bouncer, from, subject, Both(text, html))
}
