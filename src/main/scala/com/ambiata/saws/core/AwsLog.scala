package com.ambiata.saws
package core

import scalaz._, Scalaz._

sealed trait AwsLog {
  def log[A]: AwsAction[A, Unit] =
    AwsAction.log(this)
}

object AwsLog {
  case class CreateVPC(name: String) extends AwsLog
  case class CreateInternetGateway(name: String) extends AwsLog
  case class CreateRouteTable(name: String) extends AwsLog
  case class CreateSubnet(name: String) extends AwsLog
  case class CreateRole(name: String) extends AwsLog
  case class CreateBucket(name: String) extends AwsLog
  case class CreateInstanceProfile(name: String) extends AwsLog
  case class CreateSecurityGroup(name: String) extends AwsLog
  case class UpdateRole(name: String) extends AwsLog
  case class UpdateBucket(name: String) extends AwsLog
  case class UpdateInstanceProfile(name: String) extends AwsLog
  case class UpdateSecurityGroup(name: String) extends AwsLog
  case class StartInstance(name: String) extends AwsLog
  case class StopInstance(name: String) extends AwsLog
  case class Info(message: String) extends AwsLog
  case class Warn(message: String) extends AwsLog
  case class Debug(message: String) extends AwsLog

  implicit def AwsLogEqual: Equal[AwsLog] =
    Equal.equalA[AwsLog]
}
