package com.ambiata.saws.iam

import com.ambiata.saws.testing._
import com.ambiata.saws.testing.AwsMatcher._
import org.specs2.specification._
import org.specs2.matcher._
import scalaz._, Scalaz._

class UserLifecycleSpec extends IntegrationSpec with BeforeAfterExample with ThrownExpectations { def is = sequential ^ s2"""

  User Lifecycle
  ==============
  - can create an IAM user that doesn't already exist $e1
  - can create an IAM user that already exists $e2
  - can delete an IAM user that doesn't already exist $e3
  - can delete an IAM user that already exists $e4

"""
  import IAMUsers._
  val user = User("user-lifecycle-spec", Nil)


  def e1 = (create(user) >> userExists(user.name)) must beOkValue(true)

  def e2 = (create(user).replicateM(2) >> userExists(user.name)) must beOkValue(true)

  def e3 = (deleteUser(user.name)) must beOk

  def e4 = (create(user) >> deleteUser(user.name)) must beOk


  def before {
    IAMUsers.deleteUser(user.name).eval.unsafePerformIO
  }

  def after {
    IAMUsers.deleteUser(user.name).eval.unsafePerformIO
  }
}
