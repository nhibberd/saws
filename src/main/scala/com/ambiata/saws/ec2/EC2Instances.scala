package com.ambiata.saws
package ec2

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.{SecurityGroup => AwsSecurityGroup, _}
import com.ambiata.saws.core._
import com.ambiata.saws.iam._
import com.owtelse.codec.Base64

import scala.collection.JavaConverters._
import scalaz._, Scalaz._

// FIX split this. It is a mess. Sorry.
object EC2Instances {
  def run(env: String, image: EC2Image, count: Int, keypair: Option[String]): EC2Action[Reservation] = for {
    subnet      <- image.vpc.traverse(EC2Subnets.findByVpc).map(_.flatten)
    group       <- EC2SecurityGroups.findByNameOrFail(image.group, image.vpc)
    reservation <- start(image, group, subnet, count, keypair)
    _           <- EC2Tags.tags(reservation.getInstances.asScala.toList.map(_.getInstanceId), image.tags ++ List(
                     "Name" -> s"${env}.${image.flavour}.${EC2Tags.stamp}"
                   ))
    _           <- waitForReady(reservation.getInstances.asScala.toList.map(_.getInstanceId))
    // FIX add check for ssh ready
    _           <- associate(image, reservation)
    _           <- AwsAction.log(AwsLog.StartInstance(image.flavour))
  } yield reservation

  def start(image: EC2Image, group: AwsSecurityGroup, subnet: Option[Subnet], count: Int, keypair: Option[String]): EC2Action[Reservation] =
    AwsAction.withClient((client: AmazonEC2Client) =>
      client.runInstances({
        val request = new RunInstancesRequest(image.ami, count, count)
        request.setInstanceType(image.size.size)
        request.setSecurityGroupIds(List(group.getGroupId).asJava)
        keypair.foreach(request.setKeyName)
        image.profile.foreach(p => request.setIamInstanceProfile(new IamInstanceProfileSpecification().withName(p.name)))
        subnet.foreach(s => request.setSubnetId(s.getSubnetId))
        image.configure.foreach(script => request.setUserData(userData(script)))
        request.setBlockDeviceMappings(image.devices.map({
          case (dev, virt) => new BlockDeviceMapping().withDeviceName(dev).withVirtualName(virt)
        }).asJava)
        request
       }).getReservation)

  def userData(script: String) = {
    val data = s"""MIME-Version: 1.0
                  |Content-Type: text/x-shellscript
                  |
                  |#!/bin/sh
                  |$script
                  |""".stripMargin
    Base64.encode(data.getBytes("UTF-8"), "UTF-8")
  }

  def associate(image: EC2Image, reservation: Reservation): EC2Action[Unit] =
    (image.elasticIp, reservation.getInstances.asScala.toList) match {
      case (None, _) =>
        ().pure[EC2Action]
      case (Some(ip), List(instance)) if image.cardinality == SingletonEC2Image =>
        EC2IPs.associate(ip, instance.getInstanceId).as(())
      case (Some(_),  _) =>
        AwsAction.fail[AmazonEC2Client, Unit]("Something went wrong or is misconfigured, can't associate IP with multiple machines.")
    }

  def waitForReady(instanceIds: List[String]): EC2Action[Unit] = for {
    statuses <- status(instanceIds)
    _        <- (statuses.forall(_.getInstanceState.getName == "running") && statuses.length == instanceIds.length).unlessM((for {
      _ <- Thread.sleep(5000).pure[EC2Action]
      _ <- waitForReady(instanceIds)
    } yield ()): EC2Action[Unit])
  } yield ()

  def status(instanceIds: List[String]): EC2Action[List[InstanceStatus]] =
    AwsAction.withClient(client =>
      client.describeInstanceStatus(
        (new DescribeInstanceStatusRequest)
          .withInstanceIds(instanceIds.asJava)).getInstanceStatuses.asScala.toList)

  def list: EC2Action[List[Instance]] =
    AwsAction.withClient((client: AmazonEC2Client) =>
     client.describeInstances.getReservations.asScala.toList.flatMap(_.getInstances.asScala))

  def stop(instanceIds: List[String]): EC2Action[Unit] =
    EC2Action(client => client.stopInstances((new StopInstancesRequest).withInstanceIds(instanceIds.asJava)))

  def waitForStop(instanceIds: List[String]): EC2Action[Unit] = for {
    statuses <- EC2Instances.status(instanceIds)
    _        <- statuses.isEmpty.unlessM((for {
      _ <- Thread.sleep(5000).pure[EC2Action]
      _ <- waitForStop(instanceIds)
    } yield ()): EC2Action[Unit])
  } yield ()
}

sealed trait EC2ImageCardinality
case object SingletonEC2Image extends EC2ImageCardinality
case object MultitonEC2Image extends EC2ImageCardinality

sealed abstract class EC2InstanceSize(val size: String)
case object T1Micro extends EC2InstanceSize("t1.micro")
case object M1Small extends EC2InstanceSize("m1.small")
case object M1Medium extends EC2InstanceSize("m1.medium")
case object M1Large extends EC2InstanceSize("m1.large")
case object M1XLarge extends EC2InstanceSize("m1.xlarge")
case object M2XLarge extends EC2InstanceSize("m2.xlarge")
case object M22XLarge extends EC2InstanceSize("m2.2xlarge")
case object M23XLarge extends EC2InstanceSize("m2.3xlarge")
case object M24XLarge extends EC2InstanceSize("m2.4xlarge")
case object M3XLarge extends EC2InstanceSize("m3.xlarge")
case class ExplicitEC2InstanceSize(s: String) extends EC2InstanceSize(s)


case class EC2Image(
  flavour: String,
  cardinality: EC2ImageCardinality,
  ami: String,
  group: String,
  size: EC2InstanceSize,
  tags: List[(String, String)] = Nil,
  profile: Option[Role] = None,
  vpc: Option[String] = None,
  devices: List[(String, String)] = Nil,
  elasticIp: Option[String] = None,
  configure: Option[String] = None
)
