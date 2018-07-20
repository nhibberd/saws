package com.ambiata.saws
package iam

import com.ambiata.com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.ambiata.com.amazonaws.services.identitymanagement.model.{InstanceProfile => AwsInstanceProfile, _}
import com.ambiata.mundane.control.Result
import com.ambiata.mundane.control.Result.safe
import com.ambiata.saws.core.IAMAction
import scalaz.Scalaz._
import scalaz._

import scala.collection.JavaConverters._

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
    safe { client.deleteRole((new DeleteRoleRequest).withRoleName(roleName)) }
  }


  /** Add a policy to an IAM role. */
  def addRolePolicy(roleName: String, policy: InlinePolicy): Result[Unit] = {
    val policyReq = (new PutRolePolicyRequest())
      .withRoleName(roleName)
      .withPolicyName(policy.name)
      .withPolicyDocument(policy.document)
    safe { client.putRolePolicy(policyReq) }
  }

  def addRolePolicy(roleName: String, policy: AwsManagedPolicy): Result[Unit] = {
    val policyReq = (new AttachRolePolicyRequest())
      .withRoleName(roleName)
      .withPolicyArn(policy.arn)
    safe { client.attachRolePolicy(policyReq) }
  }

  /** Add multiple policies to an IAM role. */
  def addRolePolicies(roleName: String, policies: List[Policy]): Result[Unit] =
    policies.traverse(
      (p => p match {
        case i: InlinePolicy => addRolePolicy( roleName, i )
        case m: AwsManagedPolicy => addRolePolicy( roleName, m )
        case _ => Result.fail( "no supported policy type " + p.toString )
      }): (Policy => Result[Unit])
    ).map( _ => () )

  /** Remove all policies from a role then add a set of new policies. */
  def updateRolePolicies(roleName: String, policies: List[Policy]): Result[Unit] =
    clearRolePolicies(roleName) >>
    addRolePolicies(roleName, policies)


  /** Remove all the policies from a given role. */
  def clearRolePolicies(roleName: String): Result[Unit] = {
    val listInReq = (new ListRolePoliciesRequest).withRoleName(roleName)
    val listManReq = (new ListAttachedRolePoliciesRequest).withRoleName(roleName)
    def deleteInReq(p: String) = (new DeleteRolePolicyRequest()).withRoleName(roleName).withPolicyName(p)
    def deleteManReq(p: AttachedPolicy) = (new DetachRolePolicyRequest()).withRoleName(roleName).withPolicyArn(p.getPolicyArn)

    for {
      policies <- safe { client.listRolePolicies(listInReq).getPolicyNames.asScala.toList }
      _        <- policies.traverse(p => safe { client.deleteRolePolicy(deleteInReq(p)) })

      managed  <- safe { client.listAttachedRolePolicies(listManReq).getAttachedPolicies.asScala.toList }
      _        <- managed.traverseU(p => safe { client.detachRolePolicy(deleteManReq(p)) })

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

/**
  * An IAM role.
  * @param name role name
  * @param policies list of policies to attach
  * @param trust a list of services or aws accounts that can assume this role (trust policy). a tupl of type: name. e.g. ("Service", "spotfleet.amazonaws.com")
  */
case class Role(name: String, policies: List[Policy], trust: Option[List[(String, String)]] = None)
