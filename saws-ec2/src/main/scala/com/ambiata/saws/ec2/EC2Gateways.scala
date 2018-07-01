package com.ambiata.saws
package ec2

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import com.ambiata.saws.core._
import com.ambiata.saws.ec2._

import scala.collection.JavaConverters._
import scalaz._, Scalaz._

object EC2Gateways {
  def list: EC2Action[List[InternetGateway]] =
    EC2Action(_.describeInternetGateways.getInternetGateways.asScala.toList)

  def findByName(name: String): EC2Action[Option[InternetGateway]] =
    list.map(_.find(_.getTags.asScala.toList.map(t => (t.getKey, t.getValue)).contains(("Name" -> name))))

  def ensure(name: String, vpc: Vpc): EC2Action[InternetGateway] = for {
    current <- findByName(name)
    subnet  <- current match {
      case None         => create(name, vpc)
      case Some(subnet) => subnet.pure[EC2Action]
    }
  } yield subnet

  // Question: should this also ensure that RouteTable has an entry for gateway? Yes.
  def create(name: String, vpc: Vpc): EC2Action[InternetGateway] = for {
    routeTable <- EC2RouteTables.findByVpcOrFail(vpc.getVpcId())
    gateway <- EC2Action(client => {
                 val gateway = client.createInternetGateway().getInternetGateway
                 client.attachInternetGateway(
                   (new AttachInternetGatewayRequest)
                     .withInternetGatewayId(gateway.getInternetGatewayId)
                     .withVpcId(vpc.getVpcId))
                 // To get a 'vpc' the route table must be created
                 client.createRoute(new CreateRouteRequest()
                   .withDestinationCidrBlock("0.0.0.0/0") // TODO: different topologies may want to change this
                   .withGatewayId(gateway.getInternetGatewayId)
                   .withRouteTableId(routeTable.getRouteTableId()))
                 gateway})

    _      <- EC2Tags.tag(gateway.getInternetGatewayId, List("Name" -> name))

    // Note: if we create more than one gateway, it will overwrite it. That's ok :)
    _      <- EC2Tags.tag(routeTable.getRouteTableId, List("Name" -> name))
    _      <- AwsLog.CreateInternetGateway(name).log
  } yield gateway

}
