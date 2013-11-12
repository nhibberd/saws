package com.ambiata.saws
package ec2

import scalaz._, Scalaz._
import org.specs2._
import specification._
import matcher._
import scala.collection.JavaConversions._
import com.amazonaws.services.ec2.model.{IpPermission, DeleteSecurityGroupRequest}
import com.amazonaws.services.ec2.AmazonEC2Client
import scala.util.Try
import testing.AwsAttemptMatcher._


class SecurityGroupSpec extends Specification with BeforeAfterExample with ThrownExpectations { def is = sequential ^ s2"""

  Creation
  ========
  - can create a security group that doesn't yet exist $e1
  - can create a security group that already exists $e2


  IP Permissions
    ==============
  - can create a security group that only has public SSH access $e3
  - can create a security group that only has private SSH access from another group $e4
  - can update an existing security group to have a new set of IP permissions $e5

"""

  lazy val ec2 = EC2()

  def e1 = {
    val sg = SG()
    val attempt = ec2.createSecurityGroup(sg)
    attempt must beSuccessful
    securityGroupMustExist(ec2.client, sg.name)
  }.pendingUntilFixed("This test makes bad assumptions about group name vs group id, need to revisit how we test end-to-end. Talk to Mark.")

  def e2 = {
    val sg = SG()
    val attempt = ec2.createSecurityGroup(sg).replicateM(2)
    attempt must beSuccessful
    securityGroupMustExist(ec2.client, sg.name)
  }.pendingUntilFixed("This test makes bad assumptions about group name vs group id, need to revisit how we test end-to-end. Talk to Mark.")

  def e3 = {
    val sg = SG().copy(ingressRules = List(SecurityGroup.publicSshIngress))

    val attempt =
      ec2.createSecurityGroup(sg) >>
      ec2.updateSecurityGroupIngress(sg)

    attempt must beSuccessful
    securityGroupMustExist(ec2.client, sg.name)

    val permissions = ipPermissions(ec2.client, sg.name)
    permissions.size must_== 1
    permissions.headOption must beSome(permissionWith("tcp", 22, 22, List("0.0.0.0/0"), Nil))
  }.pendingUntilFixed("This test makes bad assumptions about group name vs group id, need to revisit how we test end-to-end. Talk to Mark.")

  def e4 = {
    val otherGroup = SG("other")
    val sg = SG().copy(ingressRules = List(SecurityGroup.privateSshIngress(otherGroup)))
    val groups = List(otherGroup, sg)

    val attempt =
      groups.traverse(ec2.createSecurityGroup) >>
      groups.traverse(ec2.updateSecurityGroupIngress)

    attempt must beSuccessful
    securityGroupMustExist(ec2.client, sg.name)

    val permissions = ipPermissions(ec2.client, sg.name)
    permissions.size must_== 1
    permissions.headOption must beSome(permissionWith("tcp", 22, 22, Nil, List(otherGroup.name)))
  }.pendingUntilFixed("This test makes bad assumptions about group name vs group id, need to revisit how we test end-to-end. Talk to Mark.")

  def e5 = {
    val otherGroup = SG("other")
    val sg = SG().copy(ingressRules = List(SecurityGroup.privateSshIngress(otherGroup)))
    val groups = List(otherGroup, sg)

    /* Set security policy to private SSH, then update to public SSH. */
    val attempt =
      groups.traverse(ec2.createSecurityGroup) >>
      groups.traverse(ec2.updateSecurityGroupIngress) >>
      ec2.updateSecurityGroupIngress(sg.copy(ingressRules = List(SecurityGroup.publicSshIngress)))

    attempt must beSuccessful
    securityGroupMustExist(ec2.client, sg.name)

    val permissions = ipPermissions(ec2.client, sg.name)
    permissions.size must_== 1
    permissions.headOption must beSome(permissionWith("tcp", 22, 22, List("0.0.0.0/0"), Nil))
  }.pendingUntilFixed("This test makes bad assumptions about group name vs group id, need to revisit how we test end-to-end. Talk to Mark.")


  def securityGroupMustExist(client: AmazonEC2Client, name: String): MatchResult[List[String]] = {
    def allGroups = client.describeSecurityGroups().getSecurityGroups.map(_.getGroupName).toList
    allGroups must contain(name).eventually(retries = 3, sleep = 2.seconds)
  }

  def permissionWith(protocol: String, fromPort: Int, toPort: Int, ipRanges: List[String], userIdGroupPairs: List[String]): Matcher[IpPermission] =
    (p: IpPermission) => {
      p.getIpProtocol must_== protocol
      p.getFromPort must_== fromPort
      p.getToPort must_== toPort
      p.getIpRanges.toList must containTheSameElementsAs(ipRanges)
      p.getUserIdGroupPairs.map(_.getGroupName).toList must containTheSameElementsAs(userIdGroupPairs)
    }

  def ipPermissions(client: AmazonEC2Client, name: String): List[IpPermission] =
    client.describeSecurityGroups().getSecurityGroups.find(_.getGroupName == name).map(_.getIpPermissions).toList.flatten


  /** Helpers for constructing security group names. All security group names contain a unique index and clients
    * must ensure that a given security group is only dependent on security groups with lower indexes. This ensures
    * that security groups are destroyed in the correct order.
    */
  object SG {
    val env = "specs2"
    val SgName = s"$env\\.(.*)\\..*".r
    var i = 0

    /** Create a security group configuration with a unique name. */
    def apply(suffix: String = "sg"): SecurityGroup = {
      val sg = SecurityGroup(s"$env.$i.$suffix", s"$suffix security group $i for $env", Nil, Nil)
      i += 1
      sg
    }

    /** Remove all security groups that match the specs environment identifier, highest index to lowest index. */
    def removeAll() {
      val groups =
        ec2.client.describeSecurityGroups().getSecurityGroups.map(_.getGroupName).filter(SgName.findFirstMatchIn(_).isDefined)
      groups.sortBy(n => -sgIdx(n)) foreach { g => ec2.client.deleteSecurityGroup(new DeleteSecurityGroupRequest(g)) }
    }

    def sgIdx(name: String): Int = {
      val msg = s"Security group '$name' does not contain an index."
      name match {
        case SgName(idx) => Try(idx.toInt).getOrElse(sys.error(msg))
        case _           => sys.error(msg)
      }
    }
  }

  def before {
    SG.removeAll()
  }

  def after {
    SG.removeAll()
  }
}
