package com.ambiata.saws
package iam

import org.specs2.Specification
import org.specs2.specification._
import org.specs2.matcher._
import com.amazonaws.services.identitymanagement.model._
import scala.collection.JavaConversions._


class RoleLifecycleSpec extends Specification with BeforeAfterExample with ThrownExpectations { def is = sequential ^ s2"""

  Role Lifecycle
  ==============
  - can create an IAM role that doesn't already exist $e1
  - can create an IAM role that already exists $e2
  - can delete an IAM role that doesn't already exist $e3
  - can delete an IAM role that already exists $e4


"""


  lazy val iam = IAM()

  // TODO - refactor out
  def e1 = {
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

    val roleReq = (new CreateRoleRequest()).withRoleName(IamRole()).withAssumeRolePolicyDocument(Ec2AssumedRolePolicy)
    iam.createRole(roleReq)

    iam.listRoles.getRoles.find(_.getRoleName == roleReq.getRoleName) must beSome
  }


  def e2 = {
    pending
  }


  def e3 = {
    pending
  }


  def e4 = {
    pending
  }


  /** Helpers for constructing security group names. All security group names contain a unique index and clients
    * must ensure that a given security group is only dependent on security groups with lower indexes. This ensures
    * that security groups are destroyed in the correct order.
    */
  object IamRole {
    val env = "specs2"
    val RoleName = s"$env\\-role-(.*)".r

    var i = 0

    /** Create a unique role name. */
    def apply(): String = {
      val name = s"$env-role-$i"
      i += 1
      name
    }

    /** Remove all roles that match the specs environment identifier. */
    def removeAll() {
      val specRoles = iam.listRoles.getRoles.map(_.getRoleName).filter(RoleName.findFirstMatchIn(_).isDefined)
      specRoles foreach { r =>
        iam.listRolePolicies((new ListRolePoliciesRequest).withRoleName(r)).getPolicyNames foreach { p =>
          iam.deleteRolePolicy((new DeleteRolePolicyRequest).withRoleName(r).withPolicyName(p))
        }
        iam.deleteRole((new DeleteRoleRequest).withRoleName(r))
      }
    }

  }

  def before {
    IamRole.removeAll()
  }

  def after {
    IamRole.removeAll()
  }
}