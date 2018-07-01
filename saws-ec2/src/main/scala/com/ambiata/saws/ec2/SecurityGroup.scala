package com.ambiata.saws
package ec2

import com.amazonaws.services.ec2.model.{SecurityGroup => AwsSecurityGroup, _}


/** Configuration settings for a AWS Security Group. */
case class SecurityGroup (
  name: String,
  desc: String,
  ingressRules: List[IpPermission],
  egressRules: List[IpPermission] = Nil,
  vpc: Option[String] = None
)

object SecurityGroup {
  /** Construct an Ingress rule for SSH access originating from any IP address. */
  val publicSshIngress: IpPermission =
    (new IpPermission).withFromPort(22).withToPort(22).withIpProtocol("tcp").withIpRanges("0.0.0.0/0")

  /** Construct an Ingress rule for SSH access originating from a specific source security group. */
  def privateSshIngress(sourceGroup: SecurityGroup): IpPermission =
    (new IpPermission)
      .withFromPort(22)
      .withToPort(22)
      .withIpProtocol("tcp")
      .withUserIdGroupPairs((new UserIdGroupPair).withGroupName(sourceGroup.name))
}
