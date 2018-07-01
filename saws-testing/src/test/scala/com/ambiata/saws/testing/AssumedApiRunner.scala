package com.ambiata.saws
package testing

import scalaz._, Scalaz._
import org.specs2.matcher._
import scala.collection.JavaConversions._
import com.ambiata.saws.core._
import com.amazonaws.services.ec2.model._
import com.decodified.scalassh._
import ec2._


/** Runs AWS commands (i.e. calls APIs) from an arbitrary EC2 instance. */
case class AssumedApiRunner(assumedInstanceName: String, login: String, password: String, region: String) {
  def listBuckets() =
    ssh(listBucketsCmd)

  def listBucketsCmd =
    s"aws --region $region s3api list-buckets"

  def createBucket(bucket: String) =
    ssh(createBucketCmd(bucket))

  def createBucketCmd(bucket: String) =
    s"aws --region $region s3api create-bucket --bucket '$bucket'"

  def listObjects(bucket: String) =
    ssh(listObjectsCmd(bucket))

  def listObjectsCmd(bucket: String) =
    s"aws --region $region s3api list-objects --bucket $bucket"

  def putObject(bucket: String, key: String, value: String) =
    ssh(putObjectCmd(bucket, key, value))

  def putObjectCmd(bucket: String, key: String, value: String) =
    s"aws --region $region s3api put-object --bucket '$bucket' --key '$key'"

  def getObject(bucket: String, key: String) =
    ssh(getObjectCmd(bucket, key))

  def getObjectCmd(bucket: String, key: String) =
    s"aws --region $region s3api get-object --bucket '$bucket' --key '$key' /tmp/outfile"

  lazy val dns: Validated[String] = {
    val reservations: List[Reservation] = Clients.ec2.describeInstances.getReservations.toList
    val instances: List[Instance] = reservations.flatMap(_.getInstances)
    val instance =
      instances.find(_.getTags.exists(t => t.getKey == "Name" && t.getValue == assumedInstanceName))
        .toRightDisjunction(s"AwsCommandRunner instance '$assumedInstanceName' not found")
    instance.map(_.getPublicDnsName).toEither
  }

  lazy val config = PasswordLogin(login, SimplePasswordProducer(password))

  def ssh(cmd: String): Validated[CommandResult] =
    for {
      host <- dns
      result <- SSH(host, HostConfig(config, hostKeyVerifier = HostKeyVerifiers.DontVerify))(_.exec(cmd))
    } yield (result)

  def sshCmds(cmds: List[String]): Validated[List[CommandResult]] =
    dns.flatMap(h => SSH(h, HostConfig(config, hostKeyVerifier = HostKeyVerifiers.DontVerify))(c => SSH.Result(cmds.map(cmd => c.exec(cmd)).sequence)))
}


object AssumedApiRunner extends MustMatchers {
  def beGranted: Matcher[CommandResult] =
    (result: CommandResult) => result.stdErrAsString().isEmpty must beTrue

  def beDenied: Matcher[CommandResult] =
    (result: CommandResult) => result.stdErrAsString().isEmpty must beFalse
}


/**
 *    yum install wget -y
 *    wget https://bitbucket.org/pypa/setuptools/raw/bootstrap/ez_setup.py
 *    wget https://raw.github.com/pypa/pip/master/contrib/get-pip.py
 *    python ez_setup.py
 *    python get-pip.py
 *    pip install awscli
 *
 *    curl http://169.254.169.254/latest/meta-data/iam/security-credentials/specs2-role-1
 *    export AWS_ACCESS_KEY_ID="..."
 *    export AWS_SECRET_ACCESS_KEY="..."
 *    export AWS_SECURITY_TOKEN=
 *
 *    user:      ....
 *    password:  ....
 *
 * aws --region ap-southeast-2 s3api list-buckets
 */
