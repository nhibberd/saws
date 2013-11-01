package com.ambiata.saws
package iam

import scalaz._, Scalaz._
import scala.collection.JavaConversions._
import org.specs2.Specification
import org.specs2.specification._
import org.specs2.matcher.{Matcher, ThrownExpectations}
import com.amazonaws.services.s3.model._
import java.io.File
import s3._
import IamPolicy._
import testing._
import testing.AssumedApiRunner._
import testing.AwsAttemptMatcher._
import com.decodified.scalassh._


class RoleS3PolicySpec extends Specification with ThrownExpectations with Tables { def is = sequential ^ s2"""

  S3 Policies
  ===========
  ${Step(createFiles)}
  - can use policies to control a role's access to buckets and objects $ex
  ${Step(removeFiles)}

"""

  val Ec2InstanceName = "ci.only.centos6"
  val Login = "jenkins-ci"
  val Password = "4jenkins,ci only"
  val SydneyRegion = "ap-southeast-2"
  val apiRunner = AssumedApiRunner(Ec2InstanceName, Login, Password, SydneyRegion)

  val TestBucket = "ambiata-dev-iam-test"
  val OtherTestBucket = "ambiata-dev-iam-test-other"

  lazy val s3 = S3()
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


  def ex = {
    "policy"                                     | "read key1" | "read key2" | "write key1" | "write key2" |
    S3ReadPathPolicy(TestBucket)                 ! beGranted   ! beGranted   ! beDenied     ! beDenied     |
    S3ReadPathPolicy(s"$TestBucket/$path1")      ! beGranted   ! beDenied    ! beDenied     ! beDenied     |
    S3WritePathPolicy(TestBucket)                ! beDenied    ! beDenied    ! beGranted    ! beGranted    |
    S3WritePathPolicy(s"$TestBucket/$path1")     ! beDenied    ! beDenied    ! beGranted    ! beDenied     |
    S3ReadWritePathPolicy(TestBucket)            ! beGranted   ! beGranted   ! beGranted    ! beGranted    |
    S3ReadWritePathPolicy(s"$TestBucket/$path1") ! beGranted   ! beDenied    ! beGranted    ! beDenied     |> {

      (policy, rk1, rk2, wk1, wk2) => {

        def commandResults: Seq[Validated[CommandResult]] = Seq(
          apiRunner.getObject(TestBucket, key1),
          apiRunner.getObject(TestBucket, key2),
          apiRunner.getObject(OtherTestBucket, key1),
          apiRunner.putObject(TestBucket, key1, ""),
          apiRunner.putObject(TestBucket, key2, ""),
          apiRunner.putObject(OtherTestBucket, key1, "")
        )
        val expected = Seq(rk1, rk2, beDenied, wk1, wk2, beDenied)

        (iam.clearRolePolicies(CiRole) >> iam.addRolePolicy(CiRole, policy)) must beSuccessful
        commandResults must contain(exactly(expected: _*)).inOrder.eventually(retries = 8, sleep = 5.seconds)
      }
    }
  }
}
