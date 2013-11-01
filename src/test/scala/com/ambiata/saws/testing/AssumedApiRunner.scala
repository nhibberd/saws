package com.ambiata.saws
package testing

import scalaz._, Scalaz._
import org.specs2.matcher._
import scala.collection.JavaConversions._
import com.amazonaws.services.ec2.model._
import com.decodified.scalassh._
import ec2._


/** Runs AWS commands (i.e. calls APIs) from an arbitrary EC2 instance. */
case class AssumedApiRunner(assumedInstanceName: String, login: String, password: String, region: String) {
  def listBuckets() =
    ssh(s"aws --region $region s3api list-buckets")

  def createBucket(bucket: String) =
    ssh(s"aws --region $region s3api create-bucket --bucket '$bucket'")

  def putObject(bucket: String, key: String, value: String) =
    ssh(s"aws --region $region s3api put-object --bucket '$bucket' --key '$key'")

  def getObject(bucket: String, key: String) =
    ssh(s"aws --region $region s3api get-object --bucket '$bucket' --key '$key' /tmp/outfile")

  lazy val dns: Validated[String] = {
    val ec2 = EC2()
    val reservations: List[Reservation] = ec2.describeInstances.getReservations.toList
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
      result <- SSH(host, config)(_.exec(cmd))
    } yield (result)
}


object AssumedApiRunner extends MustMatchers {
  def beGranted: Matcher[Validated[CommandResult]] =
    (result: Validated[CommandResult]) => result.right.map(_.stdErrAsString().isEmpty) must beRight(true)

  def beDenied: Matcher[Validated[CommandResult]] =
    (result: Validated[CommandResult]) => result.right.map(_.stdErrAsString().isEmpty) must beRight(false)
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
 *    export AWS_ACCESS_KEY_ID="ASIAJHMHGM7N6LXQ74VA"
 *    export AWS_SECRET_ACCESS_KEY="mmMyDeTu4nXW82tOd2zaSzs5lt1BE3NLYupuOlBN"
 *    export AWS_SECURITY_TOKEN=
 *
 *    user:      jenkins-ci
 *    password: '4jenkins,ci only'
 *
 * aws --region ap-southeast-2 s3api list-buckets
 * 287208473713
 * 287208473713
 */