package com.ambiata.saws
package ec2

import scalaz._, Scalaz._
import scala.collection.JavaConversions._
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.{SecurityGroup => AwsSecurityGroup, _}
import core.AwsAttempt, AwsAttempt.safe


/** Wrapper for Java IAM client. */
case class EC2(client: AmazonEC2Client) {

  /** Return details for a specified security group, if it exists. */
  def securityGroup(name: String): AwsAttempt[Option[AwsSecurityGroup]] =
    safe {
      client.describeSecurityGroups().getSecurityGroups.find(_.getGroupName == name)
    }


  /** Create a security group. Do nothing if the security group already exists. */
  def createSecurityGroup(group: SecurityGroup): AwsAttempt[Either[AwsSecurityGroup, CreateSecurityGroupResult]] = {
    securityGroup(group.name) >>= { sg =>
      safe {
        sg match {
          case None =>
            Right(client.createSecurityGroup({
              val request = new CreateSecurityGroupRequest(group.name, group.desc)
              group.vpc.foreach(request.setVpcId(_))
              request
            }))
          case Some(current) =>
            Left(current)
        }
      }
    }
  }


  /** Revoke all existing permissions then apply specified ingress rules for a given security group. */
  def updateSecurityGroupIngress(group: SecurityGroup): AwsAttempt[Unit] = {
    securityGroup(group.name) >>= { sg =>
      safe {
        sg foreach { existing =>
          existing.getIpPermissions.map(safeIpPermissionCopy) foreach { ipp => revokeSecurityGroupIngress(group.name, ipp) }
          group.ingressRules foreach { ipp => authorizeSecurityGroupIngress(group.name, ipp) }
        }
      }
    }
  }


  /** Authorize an ingress rules for a given security group. */
  def authorizeSecurityGroupIngress(group: String, ipPermission: IpPermission): AwsAttempt[Unit] = {
    safe {
      client.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(group, List(ipPermission)))
    }
  }


  /** Revoke an ingress rules for a given security group. */
  def revokeSecurityGroupIngress(group: String, ipPermission: IpPermission): AwsAttempt[Unit] = {
    safe {
      client.revokeSecurityGroupIngress(new RevokeSecurityGroupIngressRequest(group, List(ipPermission)))
    }
  }


  /** Copy all fields except group and user ID as these fields are assigned by AWS so can not be used as part of
    * a revoke request. */
  private def safeIpPermissionCopy(ipp: IpPermission): IpPermission =
    new IpPermission()
      .withIpProtocol(ipp.getIpProtocol)
      .withFromPort(ipp.getFromPort)
      .withToPort(ipp.getToPort)
      .withIpRanges(ipp.getIpRanges)
      .withUserIdGroupPairs(ipp.getUserIdGroupPairs.map(ugp => (new UserIdGroupPair).withGroupName(ugp.getGroupName)))

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
