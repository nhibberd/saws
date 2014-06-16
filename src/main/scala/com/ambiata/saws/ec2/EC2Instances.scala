package com.ambiata.saws
package ec2

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.{SecurityGroup => AwsSecurityGroup, _}
import com.ambiata.saws.core._
import com.ambiata.saws.iam._
import com.ambiata.saws.tooling._
import com.owtelse.codec.Base64

import scala.collection.JavaConverters._
import scalaz._, Scalaz._
import scala.concurrent._

object EC2Instances {
  def run(env: String, image: EC2Image, count: Int, keypair: Option[String], bid: Option[String]): EC2Action[List[String]] = for {
    subnet      <- image.vpc.traverse(EC2Subnets.findByVpc).map(_.flatten)
    group       <- EC2SecurityGroups.findByNameOrFail(image.group, image.vpc)
    ids         <- bid match {
      case None => start(image, group, subnet, count, keypair)
      case Some(b) => spot(image, group, subnet, count, keypair, b)
    }
    _           <- EC2Tags.tags(ids, image.tags ++ List(
                     "Name" -> s"${env}.${image.flavour}.${EC2Tags.stamp}"
                   ))
    _           <- EC2Status.waitForReady(ids)
    _           <- associate(image, ids)
    _           <- AwsLog.StartInstance(image.flavour).log
  } yield ids

  def start(image: EC2Image, group: AwsSecurityGroup, subnet: Option[Subnet], count: Int, keypair: Option[String]): EC2Action[List[String]] =
    EC2Action(client =>
      client.runInstances({
        val r = new RunInstancesRequest(image.ami, count, count)
                  .withInstanceType(image.size.size)
                  .withSecurityGroupIds(List(group.getGroupId).asJava)
                  .withBlockDeviceMappings(image.size.devices.mapping.map({
                    case (dev, virt) => new BlockDeviceMapping().withDeviceName(dev).withVirtualName(virt)
                  }).asJava)

        keypair.foreach(r.setKeyName)
        image.profile.foreach(p => r.setIamInstanceProfile(new IamInstanceProfileSpecification().withName(p.name)))
        subnet.foreach(s => r.setSubnetId(s.getSubnetId))
        image.configure.foreach(script => r.setUserData(EC2UserData.script(script)))

        r
      }).getReservation.getInstances.asScala.toList.map(_.getInstanceId))

  def spot(image: EC2Image, group: AwsSecurityGroup, subnet: Option[Subnet], count: Int, keypair: Option[String], bid: String): EC2Action[List[String]] = for {
    reqIds <- EC2Action(client =>
      client.requestSpotInstances(
        new RequestSpotInstancesRequest(bid)
          .withInstanceCount(count)
          .withLaunchSpecification({
            val spec = new LaunchSpecification()
              .withImageId(image.ami)
              .withInstanceType(image.size.size)
              .withAllSecurityGroups(List(new GroupIdentifier().withGroupId(group.getGroupId)).asJava)
              .withBlockDeviceMappings(image.size.devices.mapping.map({
                case (dev, virt) => new BlockDeviceMapping().withDeviceName(dev).withVirtualName(virt)
              }).asJava)
            keypair.foreach(spec.setKeyName)
            image.profile.foreach(p => spec.setIamInstanceProfile(new IamInstanceProfileSpecification().withName(p.name)))
            subnet.foreach(s => spec.setSubnetId(s.getSubnetId))
            image.configure.foreach(script => spec.setUserData(EC2UserData.script(script)))
            spec
          })
      ).getSpotInstanceRequests.asScala.toList.map(_.getSpotInstanceRequestId))
    _ <- waitForInstancesOrFault(reqIds)
    reqs <- spots(reqIds)
  } yield reqs.map(r => Option(r.getInstanceId)).flatten

  def waitForInstancesOrFault(reqIds: List[String]): EC2Action[Unit] =
    Wait.waitFor(completeOrFault(reqIds),
      Some(s"""Waiting for spot requests ${reqIds.mkString("")}"""))

  def completeOrFault(reqIds: List[String]): EC2Action[Boolean] =
    spots(reqIds).map(reqs =>
      reqs.forall(x => x.getInstanceId != null || x.getFault != null)).orElse(false)

  def spots(reqIds: List[String]): EC2Action[List[SpotInstanceRequest]] =
    EC2Action(client =>
      client.describeSpotInstanceRequests(
        new DescribeSpotInstanceRequestsRequest()
          .withSpotInstanceRequestIds(reqIds.asJava))
        .getSpotInstanceRequests.asScala.toList)

  def list: EC2Action[List[Instance]] =
    EC2Action(client =>
     client.describeInstances.getReservations.asScala.toList.par.flatMap(_.getInstances.asScala).toList)

  def stop(instanceIds: List[String]): EC2Action[Unit] =
    EC2Action(client =>
      client.stopInstances(
        new StopInstancesRequest()
         .withInstanceIds(instanceIds.asJava)))

  def terminate(instanceIds: List[String]): EC2Action[Unit] =
    EC2Action(client =>
      client.terminateInstances(
        new TerminateInstancesRequest()
          .withInstanceIds(instanceIds.asJava)))

  def findById(instanceId: String): EC2Action[Option[Instance]] =
    list.map(_.find(_.getInstanceId == instanceId))

  def findByIdOrFail(instanceId: String): EC2Action[Instance] =
    findById(instanceId).flatMap({
      case None => EC2Action.fail(s"Could not locate instance <$instanceId>")
      case Some(i) => i.pure[EC2Action]
    })

  def associate(image: EC2Image, ids: List[String]): EC2Action[Unit] =
    (image.elasticIp, ids) match {
      case (None, _) =>
        ().pure[EC2Action]
      case (Some(ip), List(instance)) if image.cardinality == SingletonEC2Image =>
        EC2IPs.associate(ip, instance).as(())
      case (Some(_),  _) =>
        EC2Action.fail[Unit]("Something went wrong or is misconfigured, can't associate IP with multiple machines.")
    }
}
