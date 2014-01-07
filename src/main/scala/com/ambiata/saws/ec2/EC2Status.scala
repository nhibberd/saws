package com.ambiata.saws
package ec2

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.{SecurityGroup => AwsSecurityGroup, _}
import com.ambiata.saws.core._
import com.ambiata.saws.iam._
import com.ambiata.saws.tooling._

import scala.collection.JavaConverters._
import scalaz._, Scalaz._

object EC2Status {
  def waitForReady(instanceIds: List[String]): EC2Action[Unit] =
    Wait.waitFor(areRunning(instanceIds),
      Some(s"""Waiting for instances to start ${instanceIds.mkString("")}"""))

  def waitForStop(instanceIds: List[String]): EC2Action[Unit] =
    Wait.waitFor(areRunning(instanceIds),
      Some(s"""Waiting for instances to stop ${instanceIds.mkString("")}"""))

  def areRunning(instanceIds: List[String]): EC2Action[Boolean] =
    status(instanceIds).map(statuses =>
        statuses.forall(_.getInstanceState.getName == "running") &&
          statuses.length == instanceIds.length)

  def areStopped(instanceIds: List[String]): EC2Action[Boolean] =
    status(instanceIds).map(statuses => statuses.isEmpty)

  def status(instanceIds: List[String]): EC2Action[List[InstanceStatus]] =
    EC2Action(client =>
      client.describeInstanceStatus(
        (new DescribeInstanceStatusRequest)
          .withInstanceIds(instanceIds.asJava)).getInstanceStatuses.asScala.toList)
}
