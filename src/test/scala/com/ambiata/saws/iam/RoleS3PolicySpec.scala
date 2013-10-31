package com.ambiata.saws
package iam

import scala.collection.JavaConversions._
import org.specs2.Specification
import org.specs2.specification._
import org.specs2.matcher.ThrownExpectations
import com.amazonaws.services.identitymanagement.model._
import com.amazonaws.services.s3.model._
import java.io.File


class RoleS3PolicySpec extends Specification with BeforeExample with ThrownExpectations { def is = sequential ^ s2"""

  S3 Policies
  ===========
  - can grant a role no access to a bucket $e1
  - can grant a role read access to a bucket $e2
  - can grant a role read access to a path within a bucket $e3
  - can grant a role write access to a bucket $e4
  - can grant a role write access to a path within a bucket $e5
  - can grant a role read/write access to a bucket $e6
  - can grant a role read/write access to a path within a bucket $e7


"""

  import AssumedApiRunner._
  import IamPolicy._

  val Ec2InstanceName = "ci.only.centos6"
  val Login = "jenkins-ci"
  val Password = "4jenkins,ci only"
  val SydneyRegion = "ap-southeast-2"
  val apiRunner = AssumedApiRunner(Ec2InstanceName, Login, Password, SydneyRegion)

  val TestBucket = "ambiata-dev-iam-test"
  val OtherTestBucket = "ambiata-dev-iam-test-other"

  lazy val s3 = S3()


  def e1 = {
    val key = "foo"
    apiRunner.getObject(TestBucket, key) must beDenied
    apiRunner.putObject(TestBucket, key, "") must beDenied
  }


  def e2 = {
    // Put an object and test that it can't be read or written
    val key = "foo"
    val tmpFile = File.createTempFile(key, "tmp")
    s3.putObject(new PutObjectRequest(TestBucket, key, tmpFile))

    apiRunner.getObject(TestBucket, key) must beDenied
    apiRunner.putObject(TestBucket, key, "") must beDenied

    // Add read access to the bucket and test that the file can be read
    putPolicy(S3ReadPathPolicy(TestBucket))

    apiRunner.getObject(TestBucket, key) must beGranted
    apiRunner.putObject(TestBucket, key, "") must beDenied
  }


  def e3 = {
    // Put 2 objects at 2 different paths in a bucket + test that neither can be
    // read or written
    val (path1, path2) = ("path1", "path2")
    val (file1, file2) = ("foo", "bar")
    val (key1, key2) = (s"$path1/$file1", s"$path2/$file2")

    val tmpFile1 = File.createTempFile(file1, "tmp")
    val tmpFile2 = File.createTempFile(file2, "tmp")

    s3.putObject(new PutObjectRequest(TestBucket, key1, tmpFile1))
    s3.putObject(new PutObjectRequest(TestBucket, key2, tmpFile2))

    apiRunner.getObject(TestBucket, key1) must beDenied
    apiRunner.putObject(TestBucket, key1, "") must beDenied
    apiRunner.getObject(TestBucket, key2) must beDenied
    apiRunner.putObject(TestBucket, key2, "") must beDenied

    // Add read access to the first path and test that only the first file can be read
    putPolicy(S3ReadPathPolicy(s"$TestBucket/$path1"))

    apiRunner.getObject(TestBucket, key1) must beGranted
    apiRunner.putObject(TestBucket, key1, "") must beDenied
    apiRunner.getObject(TestBucket, key2) must beDenied
    apiRunner.putObject(TestBucket, key2, "") must beDenied
  }


  def e4 = {
    // Put an object and test that it can't be read or written
    val key = "foo"
    val tmpFile = File.createTempFile(key, "tmp")
    s3.putObject(new PutObjectRequest(TestBucket, key, tmpFile))

    apiRunner.getObject(TestBucket, key) must beDenied
    apiRunner.putObject(TestBucket, key, "") must beDenied

    // Add write access to the bucket and test that the file can be written
    // but not read
    putPolicy(S3WritePathPolicy(TestBucket))

    apiRunner.getObject(TestBucket, key) must beDenied
    apiRunner.putObject(TestBucket, key, "") must beGranted
  }


  def e5 = {
    // Put 2 objects at 2 different paths in a bucket + test that neither can be
    // read or written
    val (path1, path2) = ("path1", "path2")
    val (file1, file2) = ("foo", "bar")
    val (key1, key2) = (s"$path1/$file1", s"$path2/$file2")

    val tmpFile1 = File.createTempFile(file1, "tmp")
    val tmpFile2 = File.createTempFile(file2, "tmp")

    s3.putObject(new PutObjectRequest(TestBucket, key1, tmpFile1))
    s3.putObject(new PutObjectRequest(TestBucket, key2, tmpFile2))

    apiRunner.getObject(TestBucket, key1) must beDenied
    apiRunner.putObject(TestBucket, key1, "") must beDenied
    apiRunner.getObject(TestBucket, key2) must beDenied
    apiRunner.putObject(TestBucket, key2, "") must beDenied

    // Add read access to the first path and test that only the first file can be read
    putPolicy(S3WritePathPolicy(s"$TestBucket/$path1"))

    apiRunner.getObject(TestBucket, key1) must beDenied
    apiRunner.putObject(TestBucket, key1, "") must beGranted
    apiRunner.getObject(TestBucket, key2) must beDenied
    apiRunner.putObject(TestBucket, key2, "") must beDenied
  }


  def e6 = {
    // Put an object and test that it can't be read or written
    val key = "foo"
    val tmpFile = File.createTempFile(key, "tmp")
    s3.putObject(new PutObjectRequest(TestBucket, key, tmpFile))

    apiRunner.getObject(TestBucket, key) must beDenied
    apiRunner.putObject(TestBucket, key, "") must beDenied

    // Add read/write access to the bucket and test that the file can be read/written
    putPolicy(S3ReadWritePathPolicy(TestBucket))

    apiRunner.getObject(TestBucket, key) must beGranted
    apiRunner.putObject(TestBucket, key, "") must beGranted
  }


  def e7 = {
    // Put 2 objects at 2 different paths in a bucket + test that neither can be
    // read or written
    val (path1, path2) = ("path1", "path2")
    val (file1, file2) = ("foo", "bar")
    val (key1, key2) = (s"$path1/$file1", s"$path2/$file2")

    val tmpFile1 = File.createTempFile(file1, "tmp")
    val tmpFile2 = File.createTempFile(file2, "tmp")

    s3.putObject(new PutObjectRequest(TestBucket, key1, tmpFile1))
    s3.putObject(new PutObjectRequest(TestBucket, key2, tmpFile2))

    apiRunner.getObject(TestBucket, key1) must beDenied
    apiRunner.putObject(TestBucket, key1, "") must beDenied
    apiRunner.getObject(TestBucket, key2) must beDenied
    apiRunner.putObject(TestBucket, key2, "") must beDenied

    // Add read/write access to the first path and test that only the first file can be read/written
    putPolicy(S3ReadWritePathPolicy(s"$TestBucket/$path1"))

    apiRunner.getObject(TestBucket, key1) must beGranted
    apiRunner.putObject(TestBucket, key1, "") must beGranted
    apiRunner.getObject(TestBucket, key2) must beDenied
    apiRunner.putObject(TestBucket, key2, "") must beDenied
  }


  /** Applying a new policy is not instantaneous. Wait for a short period of time for the change
    * to have effect. */
  def waitForPolicyApplication() {
    Thread.sleep(40 * 1000)
  }


  lazy val iam = IAM()
  val CiRole = "ci-only-role"

  /** Put a new policy for the CI role. */
  def putPolicy(policy: IamPolicy) {
    IamPolicy.putRolePolicy(iam, CiRole, policy)
    waitForPolicyApplication()
  }


  /** Reset CI role by removing all of its policies. */
  def resetCiRole() {
    val policies = iam.listRolePolicies((new ListRolePoliciesRequest).withRoleName(CiRole)).getPolicyNames
    policies foreach { policy => iam.deleteRolePolicy((new DeleteRolePolicyRequest()).withRoleName(CiRole).withPolicyName(policy)) }
  }


  /** Remove all objects from the test buckets. */
  def cleanTestBuckets() {
    Seq(TestBucket, OtherTestBucket) foreach { bucket =>
      val keys = s3.listObjects(bucket).getObjectSummaries.map(_.getKey)
      keys foreach { key => s3.deleteObject(bucket, key)}
    }
  }


  def before {
    cleanTestBuckets()
    resetCiRole()
    waitForPolicyApplication()
  }
}