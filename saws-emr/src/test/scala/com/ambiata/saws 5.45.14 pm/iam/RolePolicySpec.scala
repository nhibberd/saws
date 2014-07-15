package com.ambiata
package saws
package iam

import scalaz._, Scalaz._
import scala.collection.JavaConversions._
import org.specs2.Specification
import org.specs2.specification._
import org.specs2.matcher.{Matcher, ThrownExpectations}
import com.amazonaws.services.s3.model._
import java.io.File
import s3._
import Policy._
import testing._
import AssumedApiRunner._
import mundane.testing.ResultMatcher._
import com.decodified.scalassh._


class RolePolicySpec extends IntegrationSpec with ThrownExpectations with Tables { def is = sequential ^ s2"""

  S3 Policies
  ===========
  ${Step(createFiles)}
  - can use policies to control a role's access to buckets and objects $exS3
  ${Step(removeFiles)}


  EC2 Policies
  ============
  - can add an 'EC2 full access' policy to a role $exEc2


  EMR Policies
  ============
  - can add an 'EMR full access' policy to a role $exEmr

"""

  def exS3 = {
    "policy"                                           | "read key1" | "read key2" | "write key1" | "write key2" |
    allowS3ReadPath(TestBucket, false)                 ! beGranted   ! beGranted   ! beDenied     ! beDenied     |
    allowS3ReadPath(s"$TestBucket/$path1", false)      ! beGranted   ! beDenied    ! beDenied     ! beDenied     |
    allowS3WritePath(TestBucket, false)                ! beDenied    ! beDenied    ! beGranted    ! beGranted    |
    allowS3WritePath(s"$TestBucket/$path1", false)     ! beDenied    ! beDenied    ! beGranted    ! beDenied     |
    allowS3ReadWritePath(TestBucket, false)            ! beGranted   ! beGranted   ! beGranted    ! beGranted    |
    allowS3ReadWritePath(s"$TestBucket/$path1", false) ! beGranted   ! beDenied    ! beGranted    ! beDenied     |> {

      (policy, rk1, rk2, wk1, wk2) => {

        def commandResults: Validated[List[CommandResult]] = apiRunner.sshCmds(List(
          apiRunner.getObjectCmd(TestBucket, key1),
          apiRunner.getObjectCmd(TestBucket, key2),
          apiRunner.getObjectCmd(OtherTestBucket, key1),
          apiRunner.putObjectCmd(TestBucket, key1, ""),
          apiRunner.putObjectCmd(TestBucket, key2, ""),
          apiRunner.listObjectsCmd(TestBucket),
          apiRunner.listObjectsCmd(OtherTestBucket),
          apiRunner.putObjectCmd(OtherTestBucket, key1, "")
        ))
        val expected = List(rk1, rk2, beDenied, wk1, wk2, beGranted, beDenied, beDenied)

        iam.updateRolePolicies(CiRole, List(policy)) must beOk
        commandResults must beRight(contain(exactly(expected: _*)).inOrder).eventually(retries = 8, sleep = 5.seconds)
      }
    }
  }


  def exEc2 = iam.updateRolePolicies(CiRole, List(allowEc2FullAccess)) must beOk

  def exEmr = iam.updateRolePolicies(CiRole, allowEmrFullAccess) must beOk


  val Ec2InstanceName = "ci.only.centos6"
  val Login           = "jenkins-ci"
  val Password        = "4jenkins,ci only"
  val SydneyRegion    = "ap-southeast-2"
  val apiRunner       = AssumedApiRunner(Ec2InstanceName, Login, Password, SydneyRegion)

  val TestBucket      = "ambiata-dev-iam-test"
  val OtherTestBucket = "ambiata-dev-iam-test-other"

  object s3 { val client = core.Clients.s3 }
  val (path1, path2) = ("path1", "path2")
  val (file1, file2) = ("foo", "bar")
  val (key1, key2) = (s"$path1/$file1", s"$path2/$file2")

  lazy val iam = IAM()
  val CiRole = "ci-only-role"


  def createFiles {
    val tmpFile1 = File.createTempFile(file1, "tmp")
    val tmpFile2 = File.createTempFile(file2, "tmp")
    s3.client.putObject(new PutObjectRequest(TestBucket, key1, tmpFile1))
    s3.client.putObject(new PutObjectRequest(TestBucket, key2, tmpFile2))
    s3.client.putObject(new PutObjectRequest(OtherTestBucket, key1, tmpFile1))
  }


  def removeFiles {
    Seq(TestBucket, OtherTestBucket) foreach { bucket =>
      val keys = s3.client.listObjects(bucket).getObjectSummaries.map(_.getKey)
      keys foreach { key => s3.client.deleteObject(bucket, key)}
    }
  }

}
