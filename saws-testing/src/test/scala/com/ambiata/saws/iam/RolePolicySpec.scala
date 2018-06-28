package com.ambiata.saws.iam

import com.ambiata.com.amazonaws.services.s3.model._
import com.ambiata.mundane.testing.ResultMatcher._
import com.ambiata.saws.core._
import com.ambiata.saws.iam.InlinePolicy._
import com.ambiata.saws.s3._
import com.ambiata.saws.testing.AssumedApiRunner._
import com.ambiata.saws.testing._

import com.decodified.scalassh._
import java.io.File
import org.specs2.Specification
import org.specs2.specification._
import org.specs2.matcher.{Matcher, ThrownExpectations}
import scala.collection.JavaConversions._
import scalaz._, Scalaz._


class RolePolicySpec extends IntegrationSpec with ThrownExpectations with Tables { def is = sequential ^ s2"""

  EC2 Policies
  ============
  - can add an 'EC2 full access' policy to a role $exEc2


  EMR Policies
  ============
  - can add an 'EMR full access' policy to a role $exEmr

"""

  def exEc2 = iam.updateRolePolicies(CiRole, List(allowEc2FullAccess)) must beOk

  def exEmr = iam.updateRolePolicies(CiRole, allowEmrFullAccess) must beOk

  lazy val iam = IAM()
  val CiRole = "ci.ci.test"
}
