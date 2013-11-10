package com.ambiata.saws
package ec2

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import com.ambiata.saws.core._
import com.ambiata.saws.ec2._

import scala.collection.JavaConverters._
import scalaz._, Scalaz._

object EC2Subnets {
  def list: EC2Action[List[Subnet]] =
    AwsAction.withClient(client =>
      client.describeSubnets.getSubnets.asScala.toList)

  def findByVpc(vpc: String): EC2Action[Option[Subnet]] =
    list.map(_.find(_.getVpcId == vpc))

  def ensure(vpc: Vpc): EC2Action[Subnet] = for {
    current <- findByVpc(vpc.getVpcId)
    subnet  <- current match {
      case None         => create(vpc)
      case Some(subnet) => subnet.pure[EC2Action]
    }
    _       <- routing(subnet, vpc)
  } yield subnet

  def routing(subnet: Subnet, vpc: Vpc): EC2Action[Unit] = for {
    routes  <- EC2RouteTables.findByVpcOrFail(vpc.getVpcId)
    _       <- AwsAction.withClient((client: AmazonEC2Client) =>
                 client.associateRouteTable(
                   (new AssociateRouteTableRequest)
                     .withRouteTableId(routes.getRouteTableId)
                     .withSubnetId(subnet.getSubnetId)))
  } yield ()

  def create(vpc: Vpc): EC2Action[Subnet] =
    AwsAction.withClient((client: AmazonEC2Client) =>
      client.createSubnet(
        new CreateSubnetRequest(vpc.getVpcId, vpc.getCidrBlock)).getSubnet) <*
      AwsAction.log(AwsLog.CreateSubnet(vpc.getVpcId))
}
