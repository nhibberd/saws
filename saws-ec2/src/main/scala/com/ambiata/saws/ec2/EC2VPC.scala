package com.ambiata.saws
package ec2

import com.ambiata.com.amazonaws.services.ec2.AmazonEC2Client
import com.ambiata.com.amazonaws.services.ec2.model._
import com.ambiata.saws.core._

import scala.collection.JavaConverters._
import scalaz._, Scalaz._

object EC2VPC {
  def list: EC2Action[List[Vpc]] =
    EC2Action(client => client.describeVpcs.getVpcs.asScala.toList)

  def findByName(name: String): EC2Action[Option[Vpc]] =
    list.map(_.find(_.getTags.asScala.toList.map(t => (t.getKey, t.getValue)).contains(("Name" -> name))))

  def findByNameOrFail(name: String): EC2Action[Vpc] =
    findByName(name).flatMap({
      case None =>
        EC2Action.fail(s"Could not locate vpc <$name>")
      case Some(vpc) =>
        vpc.pure[EC2Action]
    })

  def ensure(name: String): EC2Action[Vpc] = for {
    current <- findByName(name)
    vpc     <- current match {
      case None      => create(name)
      case Some(vpc) => vpc.pure[EC2Action]
    }
  } yield vpc

  def create(name: String): EC2Action[Vpc] = for {
    vpc     <- EC2Action(client => client.createVpc(
                (new CreateVpcRequest)
                  .withCidrBlock("10.0.0.0/16")
                  .withInstanceTenancy(Tenancy.Default)).getVpc)
    _       <- EC2Tags.tag(vpc.getVpcId, List("Name" -> name))
    _       <- AwsLog.CreateVPC(name).log
  } yield vpc
}
