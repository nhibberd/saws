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
    AwsAction.withClient(client =>
      client.describeInternetGateways.getInternetGateways.asScala.toList)

  def findByName(name: String): EC2Action[Option[InternetGateway]] =
    list.map(_.find(_.getTags.asScala.toList.map(t => (t.getKey, t.getValue)).contains(("Name" -> name))))

  def ensure(name: String, vpc: Vpc): EC2Action[InternetGateway] = for {
    current <- findByName(name)
    subnet  <- current match {
      case None         => create(name, vpc)
      case Some(subnet) => subnet.pure[EC2Action]
    }
  } yield subnet

  // FIX should this also ensure that RouteTable has an entry for gateway?
  def create(name: String, vpc: Vpc): EC2Action[InternetGateway] = for {
    gateway <- AwsAction.withClient((client: AmazonEC2Client) => {
                 val gateway = client.createInternetGateway().getInternetGateway
                 client.attachInternetGateway(
                   (new AttachInternetGatewayRequest)
                     .withInternetGatewayId(gateway.getInternetGatewayId)
                     .withVpcId(vpc.getVpcId))
                 gateway})
    _      <- EC2Tags.tag(gateway.getInternetGatewayId, List("Name" -> name))
    _      <- AwsAction.log(AwsLog.CreateInternetGateway(name))
  } yield gateway

}
