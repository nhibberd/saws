package com.ambiata.saws
package iam

import scalaz._, Scalaz._
import scala.collection.JavaConversions._
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model._
import AwsAttempt._


/** Wrapper for Java IAM client. */
case class IAM(client: AmazonIdentityManagementClient) {

  /** Returns true if a role with the specified name exists. */
  def roleExists(roleName: String): AwsAttempt[Boolean] = {
    AwsAttempt(client.listRoles.getRoles.exists(_.getRoleName == roleName))
  }

  /** Creates a new EC2 assumed role with no initial policies. */
  def createRole(roleName: String): AwsAttempt[Unit] = {
    val Ec2AssumedRolePolicy =
      """|{
         |  "Version": "2008-10-17",
         |  "Statement": [
         |    {
         |      "Sid": "",
         |      "Effect": "Allow",
         |      "Principal": {
         |        "Service": "ec2.amazonaws.com"
         |      },
         |      "Action": "sts:AssumeRole"
         |    }
         |  ]
         |}""".stripMargin

    val roleReq = (new CreateRoleRequest()).withRoleName(roleName).withAssumeRolePolicyDocument(Ec2AssumedRolePolicy)

    roleExists(roleName) >>= { exists =>
      if (!exists) AwsAttempt(client.createRole(roleReq))
      else         AwsAttempt(())
    }
  }

  /** Delete an IAM role and all of its policies. */
  def deleteRole(roleName: String): AwsAttempt[Unit] = {
    clearRolePolicies(roleName) >>
    AwsAttempt(client.deleteRole((new DeleteRoleRequest).withRoleName(roleName)))
  }


  /** Add a policy to an IAM role. */
  def addRolePolicy(roleName: String, policy: IamPolicy): AwsAttempt[Unit] = {
    val policyReq = (new PutRolePolicyRequest())
      .withRoleName(roleName)
      .withPolicyName(policy.policyName)
      .withPolicyDocument(policy.policyDocument)
    AwsAttempt { client.putRolePolicy(policyReq) }
  }

  /** Remove all the policies from a given role. */
  def clearRolePolicies(roleName: String): AwsAttempt[Unit] = {
    val listReq = (new ListRolePoliciesRequest).withRoleName(roleName)
    def deleteReq(p: String) = (new DeleteRolePolicyRequest()).withRoleName(roleName).withPolicyName(p)

    for {
      policies <- AwsAttempt(client.listRolePolicies(listReq).getPolicyNames.toList)
      _        <- policies.map(p => AwsAttempt(client.deleteRolePolicy(deleteReq(p)))).sequence
    } yield (())
  }
}


/** Sydney-region IAM client. */
object IAM {
  val IamEndpoint = "https://iam.amazonaws.com"

  def apply(): IAM = {
    val c = new AmazonIdentityManagementClient()
    c.setEndpoint(IamEndpoint)
    IAM(c)
  }
}
