package com.ambiata.saws.iam

import com.ambiata.mundane.control.Result
import com.ambiata.mundane.testing.ResultMatcher._
import com.ambiata.saws.testing._
import org.specs2.specification._
import org.specs2.matcher._
import scalaz._, Scalaz._

class RoleLifecycleSpec extends IntegrationSpec with BeforeAfterExample with ThrownExpectations { def is = sequential ^ s2"""

  Role Lifecycle
  ==============
  - can create an IAM role that doesn't already exist $e1
  - can create an IAM role that already exists $e2
  - can not delete an IAM role that doesn't already exist $e3
  - can delete an IAM role that already exists $e4

"""

  val role = Role("role-lifecycle-spec", Nil)
  lazy val iam = IAM()

  def e1 = {
    val steps =
      iam.createRole(role) >>
      iam.roleExists(role.name)
    steps must beOk
    steps.toEither must beRight(true)
  }

  def e2 = {
    val steps =
      iam.createRole(role).replicateM(2) >>
      iam.roleExists(role.name)
    steps must beOk
    steps.toEither must beRight(true)
  }

  def e3 = {
    iam.deleteRole(role.name) must beOk.not
    iam.roleExists(role.name).toEither must beRight(false).eventually(retries = 8, sleep = 5.seconds)
  }.pendingUntilFixed("This breaks with timing issues, needs further investigation")

  def e4 = {
    val steps =
      (iam.createRole(role) >>
      iam.deleteRole(role.name) >>
      iam.roleExists(role.name)).map(println)
    steps must beOk
    steps.toEither must beRight(false)
  }.pendingUntilFixed("This breaks with timing issues, needs further investigation")

  def before {
    iam.deleteRole(role.name)
  }

  def after {
    iam.deleteRole(role.name)
  }
}
