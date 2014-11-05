package com.ambiata.saws
package ec2

import com.ambiata.com.amazonaws.services.ec2.AmazonEC2Client
import com.ambiata.com.amazonaws.services.ec2.model._
import com.ambiata.saws.core._

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import scala.collection.JavaConverters._

object EC2Tags {
  def tag(resource: String, tags: List[(String, String)]): EC2Action[Unit] =
    EC2Tags.tags(List(resource), tags)

  def tags(resource: List[String], tags: List[(String, String)]): EC2Action[Unit] =
    EC2Action(client => client.createTags(
      (new CreateTagsRequest)
        .withResources(resource.asJava)
        .withTags(tags.map({ case (key, value) => new Tag(key, value) }).asJava)))

  def stamp: String = {
    val now = new Date
    val formatter = new SimpleDateFormat("yyyyMMddHHmmss")
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"))
    formatter.format(now)
  }
}
