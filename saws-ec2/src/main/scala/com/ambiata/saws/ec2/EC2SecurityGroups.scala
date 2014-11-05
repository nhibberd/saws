package com.ambiata.saws
package ec2

import com.ambiata.com.amazonaws.services.ec2.AmazonEC2Client
import com.ambiata.com.amazonaws.services.ec2.model.{SecurityGroup => AwsSecurityGroup, _}
import com.ambiata.saws.core._
import com.ambiata.saws.ec2._
import scala.collection.JavaConverters._
import scalaz._, Scalaz._

object EC2SecurityGroups {
  def list: EC2Action[List[AwsSecurityGroup]] =
    EC2Action(client =>
      client.describeSecurityGroups.getSecurityGroups.asScala.toList)

  def findByName(name: String, vpc: Option[String]): EC2Action[Option[AwsSecurityGroup]] =
    list.map(_.find(sg => sg.getGroupName == name && vpc.forall(_ == sg.getVpcId)))

  def findByNameOrFail(name: String, vpc: Option[String]): EC2Action[AwsSecurityGroup] =
    findByName(name, vpc).flatMap({
      case None =>
        EC2Action.fail(s"Could not locate security group <$name>")
      case Some(group) =>
        group.pure[EC2Action]
    })

  def ensure(group: SecurityGroup): EC2Action[AwsSecurityGroup] = for {
    current <- findByName(group.name, group.vpc)
    sg      <- current match {
      case None     => create(group)
      case Some(sg) => sg.pure[EC2Action]
    }
    _       <- rules(group, sg)
  } yield sg

  def create(group: SecurityGroup): EC2Action[AwsSecurityGroup] = for {
    _  <- EC2Action(client =>
            client.createSecurityGroup({
              val request = new CreateSecurityGroupRequest(group.name, group.desc)
              group.vpc.foreach(request.setVpcId(_))
              request
            }))
    _  <- AwsLog.CreateSecurityGroup(group.name).log
    sg <- findByNameOrFail(group.name, group.vpc)
  } yield sg

  // FIX This can probably race, consider filtering revoke/authorize to minimize churn.
  def rules(group: SecurityGroup, sg: AwsSecurityGroup) = for {
    _ <- revoke(sg.getGroupId, sg.getIpPermissions.asScala.toList.map(safeIpPermissionCopy))
    _ <- authorize(sg.getGroupId, group.ingressRules, group.vpc)
    _ <- AwsLog.UpdateSecurityGroup(group.name).log
  } yield ()

  def revoke(groupId: String, ipPermissions: List[IpPermission]): EC2Action[Unit] =
    EC2Action(client =>
      if (!ipPermissions.isEmpty)
        client.revokeSecurityGroupIngress(
          (new RevokeSecurityGroupIngressRequest)
          .withGroupId(groupId)
          .withIpPermissions(ipPermissions.asJava)))

  // FIX This is dodge, overly complex because we don't have our own IpPermission structure... Sort.
  def authorize(groupId: String, ipPermissions: List[IpPermission], vpcId: Option[String]): EC2Action[Unit] = for {
    perms <- ipPermissions.traverse(perm => vpcId match {
               case None        => perm.pure[EC2Action]
               case Some(_)     => perm.getUserIdGroupPairs.asScala.toList.traverse(pair =>
                 Option(pair.getGroupName) match {
                   case None        => pair.pure[EC2Action]
                   case Some(name)  => for {
                     sg <- findByNameOrFail(name, vpcId)
                   } yield (new UserIdGroupPair().withGroupId(sg.getGroupId))
                 }
               ).map(pairs =>
                 new IpPermission()
                   .withIpProtocol(perm.getIpProtocol)
                   .withFromPort(perm.getFromPort)
                   .withToPort(perm.getToPort)
                   .withIpRanges(perm.getIpRanges)
                   .withUserIdGroupPairs(pairs.asJava))
             })
    _     <- EC2Action(client =>
              client.authorizeSecurityGroupIngress(
                (new AuthorizeSecurityGroupIngressRequest)
                  .withGroupId(groupId)
                  .withIpPermissions(perms.asJava)))
  } yield ()

  private def safeIpPermissionCopy(ipp: IpPermission): IpPermission =
    new IpPermission()
      .withIpProtocol(ipp.getIpProtocol)
      .withFromPort(ipp.getFromPort)
      .withToPort(ipp.getToPort)
      .withIpRanges(ipp.getIpRanges)
      .withUserIdGroupPairs(ipp.getUserIdGroupPairs.asScala.map(ugp =>
        (new UserIdGroupPair)
          .withGroupId(ugp.getGroupId)).asJava)

}
