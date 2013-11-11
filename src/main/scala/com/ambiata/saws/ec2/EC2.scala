package com.ambiata.saws
package ec2

import scalaz._, Scalaz._
import scala.collection.JavaConversions._
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.{SecurityGroup => AwsSecurityGroup, _}
import core.AwsAttempt, AwsAttempt.safe


/** Wrapper for Java EC2 client. */
case class EC2(client: AmazonEC2Client) {
  def createSecurityGroup(group: SecurityGroup) =
    EC2SecurityGroups.ensure(group).run(client) match {
      case (_, attempt) => attempt
    }

  def updateSecurityGroupIngress(group: SecurityGroup) = (for {
    sg <- EC2SecurityGroups.findByNameOrFail(group.name, None)
    _  <- EC2SecurityGroups.rules(group, sg)
  } yield ()).run(client) match {
    case (_, attempt) => attempt
  }
}

/** Sydney-region EC2 client. */
object EC2 {
  val Ec2Endpoint = "ec2.ap-southeast-2.amazonaws.com"

  def apply(): EC2 = {
    val c = new AmazonEC2Client()
    c.setEndpoint(Ec2Endpoint)
    EC2(c)
  }

}
