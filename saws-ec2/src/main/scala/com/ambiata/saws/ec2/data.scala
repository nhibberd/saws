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
  elasticIp: Option[String] = None,
  configure: Option[String] = None
)

sealed trait EC2ImageCardinality
case object SingletonEC2Image extends EC2ImageCardinality
case object MultitonEC2Image extends EC2ImageCardinality


sealed abstract class EC2Devices {
  def mapping: List[(String, String)]
}
case object EC2Devices0 extends EC2Devices {
  val mapping = Nil
}
case object EC2Devices1 extends EC2Devices {
  val mapping = List(
    "/dev/xvdb" -> "ephemeral0"
  )
}
case object EC2Devices2 extends EC2Devices {
  val mapping = List(
    "/dev/xvdb" -> "ephemeral0",
    "/dev/xvdc" -> "ephemeral1"
  )
}
case object EC2Devices4 extends EC2Devices {
  val mapping = List(
    "/dev/xvdb" -> "ephemeral0",
    "/dev/xvdc" -> "ephemeral1",
    "/dev/xvdd" -> "ephemeral2",
    "/dev/xvde" -> "ephemeral3"
  )
}

sealed abstract class EC2InstanceSize(val size: String, val devices: EC2Devices)

object EC2InstanceSize {
  lazy val instanceMappings: Map[String, EC2InstanceSize] = {
    List(
      T1Micro
    , M3Medium
    , M3Large
    , M3XLarge
    , M32XLarge
    , M1Small
    , M1Medium
    , M1Large
    , M1XLarge
    , C3Large
    , C3XLarge
    , C32XLarge
    , C34XLarge
    , C38XLarge
    , C1Medium
    , C1XLarge
    , R3Large
    , R3XLarge
    , R32XLarge
    , R34XLarge
    , R38XLarge
    , M2XLarge
    , M22XLarge
    , M24XLarge
    ).map(x => x.size -> x).toMap
  }

  def apply(size: String): Option[EC2InstanceSize] =
    instanceMappings.get(size)
}

// Micro
case object T1Micro extends EC2InstanceSize("t1.micro", EC2Devices0)

// General Purpose - Current Generation
case object M3Medium extends EC2InstanceSize("m3.medium", EC2Devices1)
case object M3Large extends EC2InstanceSize("m3.large", EC2Devices1)
case object M3XLarge extends EC2InstanceSize("m3.xlarge", EC2Devices2)
case object M32XLarge extends EC2InstanceSize("m3.2xlarge", EC2Devices2)

// General Purpose - Previous Generation
case object M1Small extends EC2InstanceSize("m1.small", EC2Devices1)
case object M1Medium extends EC2InstanceSize("m1.medium", EC2Devices1)
case object M1Large extends EC2InstanceSize("m1.large", EC2Devices2)
case object M1XLarge extends EC2InstanceSize("m1.xlarge", EC2Devices2)

// Compute Optimised - Current Generation
case object C3Large extends EC2InstanceSize("c3.large", EC2Devices2)
case object C3XLarge extends EC2InstanceSize("c3.xlarge", EC2Devices2)
case object C32XLarge extends EC2InstanceSize("c3.2xlarge", EC2Devices2)
case object C34XLarge extends EC2InstanceSize("c3.4xlarge", EC2Devices2)
case object C38XLarge extends EC2InstanceSize("c3.8xlarge", EC2Devices2)

// Compute Optimised - Previous Generation
case object C1Medium extends EC2InstanceSize("c1.medium", EC2Devices1)
case object C1XLarge extends EC2InstanceSize("c1.xlarge", EC2Devices4)

// Memory Optimised - Current Generation
case object R3Large extends EC2InstanceSize("r3.large", EC2Devices1)
case object R3XLarge extends EC2InstanceSize("r3.xlarge", EC2Devices1)
case object R32XLarge extends EC2InstanceSize("r3.2xlarge", EC2Devices1)
case object R34XLarge extends EC2InstanceSize("r3.4xlarge", EC2Devices1)
case object R38XLarge extends EC2InstanceSize("r3.8xlarge", EC2Devices2)

// Memory Optimised - Previous Generation
case object M2XLarge extends EC2InstanceSize("m2.xlarge", EC2Devices2)
case object M22XLarge extends EC2InstanceSize("m2.2xlarge", EC2Devices2)
case object M24XLarge extends EC2InstanceSize("m2.4xlarge", EC2Devices2)
