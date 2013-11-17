package com.ambiata.saws
package iam

import com.amazonaws.services.identitymanagement.model.{Role => AwsRole, _}
import com.ambiata.saws.core._
import com.ambiata.saws.iam._
import scala.collection.JavaConverters._

object IAMRoles {
  // FIX follow ensure/create pattern, add AwsLog.
  def create(role: Role): IAMAction[Unit] =
    IAMAction(client => IAM(client).createRole(role))

  def list: IAMAction[List[AwsRole]] =
    IAMAction(_.listRoles.getRoles.asScala.toList)

  def findByName(name: String): IAMAction[Option[AwsRole]] =
    list.map(_.find(_.getRoleName == name))

  def exists(name: String): IAMAction[Boolean] =
    findByName(name).map(_.isDefined)
}
