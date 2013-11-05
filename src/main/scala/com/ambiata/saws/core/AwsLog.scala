package com.ambiata.saws
package core

sealed trait AwsLog

object AwsLog {
  case class CreateRole(name: String) extends AwsLog
  case class CreateBucket(name: String) extends AwsLog
  case class CreateInstanceProfile(name: String) extends AwsLog
  case class CreateSecurityGroup(name: String) extends AwsLog
  case class StartInstance(name: String) extends AwsLog
  case class Message(message: String) extends AwsLog
}
