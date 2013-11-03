package com.ambiata.saws
package iam

import scalaz._, Scalaz._
import org.specs2.Specification
import org.specs2.specification._
import org.specs2.matcher._
import testing.AwsAttemptMatcher._


class RoleLifecycleSpec extends Specification with BeforeAfterExample with ThrownExpectations { def is = sequential ^ s2"""

  Role Lifecycle
  ==============
  - can create an IAM role that doesn't already exist $e1
  - can create an IAM role that already exists $e2
  - can not delete an IAM role that doesn't already exist $e3
  - can delete an IAM role that already exists $e4

"""

  val roleName = "role-lifecycle-spec"
  lazy val iam = IAM()

  def e1 = {
    val steps =
      iam.createRole(roleName) >>
      iam.roleExists(roleName)
    steps must beSuccessful
    steps.toEither must beRight(true)
  }

  def e2 = {
    val steps =
      iam.createRole(roleName).replicateM(2) >>
      iam.roleExists(roleName)
    steps must beSuccessful
    steps.toEither must beRight(true)
  }

  def e3 = {
    iam.deleteRole(roleName) must beSuccessful.not
    iam.roleExists(roleName).toEither must beRight(false)
  }

  def e4 = {
    val steps =
      iam.createRole(roleName) >>
      iam.deleteRole(roleName) >>
      iam.roleExists(roleName)
    steps must beSuccessful
    steps.toEither must beRight(false)
  }

  def before {
    iam.deleteRole(roleName)
  }

  def after {
    iam.deleteRole(roleName)
  }
}