package com.ambiata.saws
package ses

import com.ambiata.saws.ses.ContentType._
import scalaz._, Scalaz._, \&/._
import org.specs2._, specification._, matcher._

class EmailSpec extends Specification with ScalaCheck { def is = sequential ^ s2"""
  Email Usage
  ===========

  At the moment, this test requires additional manual verification steps by
  checking bounce messages and attachments. This is not ideal, but still
  demonstrates usage and verifies our ability to send emails successfully.
  To manually check, subscribe to test-ops@ambiata.com group (you will probably
  want label and skip inbox from anything send to test-ops@ambiata.com)

    Can send messages                             $message
    Can send messages that will bounce            $bounced
    Can send messages that will auto respond      $respond
    Can send messages that will complain          $complaint
    Can send messages to suppressed emails        $suppression
    Can send raw messages without attachments     $noattachment
    Can send raw messages with attachments        $attachment
    Can send raw messages that bounce             $bounceattachment

"""

  def message =
    Email.send(email("message").copy(
      to      = List(Success)
    )).toEither must beRight

  def bounced =
    Email.send(email("bounced").copy(
      to      = List(Bounce)
    )).toEither must beRight

  def respond =
    Email.send(email("respond").copy(
      to      = List(OutOfOffice)
    )).toEither must beRight

  def complaint =
    Email.send(email("complaint").copy(
      to      = List(Complaint)
    )).toEither must beRight

  def suppression =
    Email.send(email("suppression").copy(
      to      = List(Suppression)
    )).toEither must beRight

  def noattachment =
    Email.sendWithAttachments(email("noattachment").copy(
      to      = List(Success)
    ), List()).toEither must beRight

  def attachment =
    Email.sendWithAttachments(email("attachment").copy(
      to      = List(Success)
    ), List(Attachment.data("test.txt", ContentType.text.plain, "hello".getBytes))).toEither must beRight

  def bounceattachment =
    Email.sendWithAttachments(email("bounceattachment").copy(
      to      = List(Bounce)
    ), List(Attachment.data("test.txt", ContentType.text.plain, "hello".getBytes))).toEither must beRight

  def Success =
    "success@simulator.amazonses.com"

  def Bounce =
    "bounce@simulator.amazonses.com"

  def OutOfOffice =
    "ooto@simulator.amazonses.com"

  def Complaint =
    "complaint@simulator.amazonses.com"

  def Suppression =
    "suppressionlist@simulator.amazonses.com"

  def email(test: String) = Message.text(
    from    = "test-ops@ambiata.com",
    to      = List(),
    subject = s"Decibel Test Case <$test>",
    text    = s"This is a test email for test case <$test> @ ${new java.util.Date}."
  )
}
