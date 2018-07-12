package com.ambiata.saws
package iam

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import com.ambiata.saws.core._
import com.ambiata.saws.iam._
import scala.collection.JavaConverters._

object IAMAlias {
  def list: IAMAction[List[String]] = IAMAction(client =>
    client.listAccountAliases.getAccountAliases.asScala.toList)
}
