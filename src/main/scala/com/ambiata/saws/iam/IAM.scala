package com.ambiata.saws
package iam

import scalaz._, Scalaz._
import scala.collection.JavaConversions._
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.{InstanceProfile => AwsInstanceProfile, _}
import core.AwsAttempt, AwsAttempt.safe


/** Wrapper for Java IAM client. */
case class IAM(client: AmazonIdentityManagementClient) {

  /** Returns true if a role with the specified name exists. */
  def roleExists(roleName: String): AwsAttempt[Boolean] =
    safe { client.listRoles.getRoles.exists(_.getRoleName == roleName) }


  /** Creates a new EC2 assumed role and add its policies. */
  def createRole(role: Role): AwsAttempt[Unit] = {
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

    val roleReq = (new CreateRoleRequest()).withRoleName(role.name).withAssumeRolePolicyDocument(Ec2AssumedRolePolicy)

    roleExists(role.name) >>= { exists =>
      if (!exists) safe { client.createRole(roleReq) } >> addRolePolicies(role.name, role.policies)
      else         updateRolePolicies(role.name, role.policies)
    }
  }

  /** Delete an IAM role and all of its policies. */
  def deleteRole(roleName: String): AwsAttempt[Unit] = {
    clearRolePolicies(roleName) >>
    safe { client.deleteRole((new DeleteRoleRequest).withRoleName(roleName)) }
  }


  /** Add a policy to an IAM role. */
  def addRolePolicy(roleName: String, policy: Policy): AwsAttempt[Unit] = {
    val policyReq = (new PutRolePolicyRequest())
      .withRoleName(roleName)
      .withPolicyName(policy.name)
      .withPolicyDocument(policy.document)
    safe { client.putRolePolicy(policyReq) }
  }


  /** Add multiple policies to an IAM role. */
  def addRolePolicies(roleName: String, policies: List[Policy]): AwsAttempt[Unit] =
    policies.traverse((p: Policy) => addRolePolicy(roleName, p)).map(_ => ())


  /** Remove all policies from a role then add a set of new policies. */
  def updateRolePolicies(roleName: String, policies: List[Policy]): AwsAttempt[Unit] =
    clearRolePolicies(roleName) >>
    addRolePolicies(roleName, policies)


  /** Remove all the policies from a given role. */
  def clearRolePolicies(roleName: String): AwsAttempt[Unit] = {
    val listReq = (new ListRolePoliciesRequest).withRoleName(roleName)
    def deleteReq(p: String) = (new DeleteRolePolicyRequest()).withRoleName(roleName).withPolicyName(p)

    for {
      policies <- safe { client.listRolePolicies(listReq).getPolicyNames.toList }
      _        <- policies.traverse(p => safe { client.deleteRolePolicy(deleteReq(p)) })
    } yield ()
  }

  /** Create an instance profile with attached roles. */
  def getInstanceProfile(name: String): AwsAttempt[Option[AwsInstanceProfile]] =
    safe {
      Option(client.getInstanceProfile(
        (new GetInstanceProfileRequest)
          .withInstanceProfileName(name))
        .getInstanceProfile)
    }

  /** Create an instance profile with attached roles. */
  def createInstanceProfile(profile: InstanceProfile) = for {
    _ <- safe {
        client.createInstanceProfile(
          (new CreateInstanceProfileRequest)
            .withInstanceProfileName(profile.name))
      }
    _ <- profile.roles.traverse(role => safe {
        client.addRoleToInstanceProfile(
          (new AddRoleToInstanceProfileRequest)
            .withInstanceProfileName(profile.name)
            .withRoleName(role.name)
        )
      })
  } yield ()
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


/** An IAM role. */
case class Role(name: String, policies: List[Policy])

/** An IAM instance profile */
case class InstanceProfile(name: String, roles: List[Role])
