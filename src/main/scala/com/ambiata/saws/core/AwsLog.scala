package com.ambiata.saws
package core

sealed trait AwsLog

object AwsLog {
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
}
