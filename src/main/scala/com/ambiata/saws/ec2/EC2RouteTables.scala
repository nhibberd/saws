package com.ambiata.saws
package ec2

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import com.ambiata.saws.core._
import com.ambiata.saws.ec2._

import scala.collection.JavaConverters._
import scalaz._, Scalaz._

object EC2RouteTables {
  def list: EC2Action[List[RouteTable]] =
    AwsAction.withClient(client =>
      client.describeRouteTables.getRouteTables.asScala.toList)

  def findByVpc(vpc: String): EC2Action[Option[RouteTable]] =
    list.map(_.sortBy(_.getAssociations.size).reverse.find(_.getVpcId == vpc))

  def findByVpcOrFail(vpc: String): EC2Action[RouteTable] =
    findByVpc(vpc).flatMap({
      case None         => AwsAction.fail(s"Could not locate route table in vpc <$vpc>")
      case Some(routes) => routes.pure[EC2Action]
    })
}
