package com.ambiata.saws
package ec2

import com.ambiata.saws.iam._

case class EC2Image(
  flavour: String,
  cardinality: EC2ImageCardinality,
  ami: String,
  group: String,
  size: EC2InstanceSize,
  tags: List[(String, String)] = Nil,
  profile: Option[Role] = None,
  vpc: Option[String] = None,
  devices: List[(String, String)] = Nil,
  elasticIp: Option[String] = None,
  configure: Option[String] = None
)

sealed trait EC2ImageCardinality
case object SingletonEC2Image extends EC2ImageCardinality
case object MultitonEC2Image extends EC2ImageCardinality

sealed abstract class EC2InstanceSize(val size: String)
case object T1Micro extends EC2InstanceSize("t1.micro")
case object M1Small extends EC2InstanceSize("m1.small")
case object M1Medium extends EC2InstanceSize("m1.medium")
case object M1Large extends EC2InstanceSize("m1.large")
case object M1XLarge extends EC2InstanceSize("m1.xlarge")
case object M2XLarge extends EC2InstanceSize("m2.xlarge")
case object M22XLarge extends EC2InstanceSize("m2.2xlarge")
case object M23XLarge extends EC2InstanceSize("m2.3xlarge")
case object M24XLarge extends EC2InstanceSize("m2.4xlarge")
case object M3XLarge extends EC2InstanceSize("m3.xlarge")
case class ExplicitEC2InstanceSize(s: String) extends EC2InstanceSize(s)
