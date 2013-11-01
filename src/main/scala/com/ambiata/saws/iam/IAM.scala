package com.ambiata.saws
package iam

import scalaz._, Scalaz._
import scala.collection.JavaConversions._
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model._
import AwsAttempt._


/** Wrapper for Java IAM client. */
case class IAM(client: AmazonIdentityManagementClient) {

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
