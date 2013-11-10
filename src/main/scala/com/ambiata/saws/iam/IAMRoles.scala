package com.ambiata.saws
package iam

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._
import com.ambiata.saws.core._
import com.ambiata.saws.iam._
import scala.collection.JavaConverters._

// FIX follow ensure/create pattern, add AwsLog.
object IAMRoles {
  def create(role: Role): IAMAction[Unit] =
    AwsAction.attemptWithClient(client =>
      IAM(client).createRole(role))
}
