package com.ambiata.saws
package iam

import scalaz._, Scalaz._
import scala.collection.JavaConverters._
import com.ambiata.saws.core._
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.{InstanceProfile => AwsInstanceProfile, _}
import com.ambiata.mundane.control.Result
import Result.safe


/** Wrapper for Java IAM client. */
case class IAM(client: AmazonIdentityManagementClient) {

  /** Returns true if a role with the specified name exists. */
  def roleExists(roleName: String): Result[Boolean] =
    safe { client.listRoles(new ListRolesRequest().withMaxItems(1000)).getRoles.asScala.exists(_.getRoleName == roleName) }


  /** Creates a new EC2 assumed role and add its policies. */
  def createRole(role: Role): Result[Unit] = {
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

    for {
      exists <- roleExists(role.name)
      _      <- if (!exists) safe { client.createRole(roleReq) } >> addRolePolicies(role.name, role.policies)
                else         updateRolePolicies(role.name, role.policies)
      _      <- createInstanceProfile(role)
    } yield ()
  }

  /** Delete an IAM role and all of its policies. */
  def deleteRole(roleName: String): Result[Unit] = {
    clearRolePolicies(roleName) >>
    deleteInstanceProfile(roleName) >>
    safe {
      client.deleteRole((new DeleteRoleRequest).withRoleName(roleName))
      ()
    }
  }


  /** Add a policy to an IAM role. */
  def addRolePolicy(roleName: String, policy: Policy): Result[Unit] = {
    val policyReq = (new PutRolePolicyRequest())
      .withRoleName(roleName)
      .withPolicyName(policy.name)
      .withPolicyDocument(policy.document)
    safe {
      client.putRolePolicy(policyReq)
      ()
    }
  }


  /** Add multiple policies to an IAM role. */
  def addRolePolicies(roleName: String, policies: List[Policy]): Result[Unit] =
    policies.traverse((p: Policy) => addRolePolicy(roleName, p)).map(_ => ())


  /** Remove all policies from a role then add a set of new policies. */
  def updateRolePolicies(roleName: String, policies: List[Policy]): Result[Unit] =
    clearRolePolicies(roleName) >>
    addRolePolicies(roleName, policies)


  /** Remove all the policies from a given role. */
  def clearRolePolicies(roleName: String): Result[Unit] = {
    val listReq = (new ListRolePoliciesRequest).withRoleName(roleName)
    def deleteReq(p: String) = (new DeleteRolePolicyRequest()).withRoleName(roleName).withPolicyName(p)

    for {
      policies <- safe { client.listRolePolicies(listReq).getPolicyNames.asScala.toList }
      _        <- policies.traverse(p => safe { client.deleteRolePolicy(deleteReq(p)) })
    } yield ()
  }

  /** Create an instance profile with attached roles. */
  def getInstanceProfile(name: String): Result[Option[AwsInstanceProfile]] =
    safe {
      client
        .listInstanceProfiles(new ListInstanceProfilesRequest().withMaxItems(1000))
        .getInstanceProfiles.asScala
        .find(_.getInstanceProfileName == name)
    }

  /** Create an instance profile with attached roles. */
  def instanceProfileExists(name: String): Result[Boolean] =
    getInstanceProfile(name).map(_.isDefined)

  /** Create an instance profile with attached roles. */
  def deleteInstanceProfile(name: String) = for {
    exists <- instanceProfileExists(name)
    _      <- exists.whenM {
      safe { client.deleteInstanceProfile((new DeleteInstanceProfileRequest).withInstanceProfileName(name)) }
    }
  } yield ()

  /** Create an instance profile with attached roles. */
  def createInstanceProfile(role: Role) = for {
    current <- getInstanceProfile(role.name)
    profile <- current match {
      case None => safe {
        client.createInstanceProfile(
          (new CreateInstanceProfileRequest)
            .withInstanceProfileName(role.name)).getInstanceProfile
      }
      case Some(profile) =>
        profile.point[Result]
    }
    _ <- profile.getRoles.asScala.exists(_.getRoleName == role.name).unlessM { safe {
      client.addRoleToInstanceProfile(
        (new AddRoleToInstanceProfileRequest)
          .withInstanceProfileName(role.name)
          .withRoleName(role.name))
    } }
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
