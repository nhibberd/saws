package com.ambiata.saws
package ec2

import com.ambiata.com.amazonaws.services.ec2.AmazonEC2Client
import com.ambiata.com.amazonaws.services.ec2.model._
import com.ambiata.saws.core._

import scala.collection.JavaConverters._
import scalaz._, Scalaz._

object EC2IPs {
  def list: EC2Action[List[Address]] =
    EC2Action(_.describeAddresses.getAddresses.asScala.toList)

  def findByIp(ip: String): EC2Action[Option[Address]] =
    list.map(_.find(_.getPublicIp == ip))

  def findByIpOrFail(ip: String): EC2Action[Address] =
    findByIp(ip).flatMap({
      case None =>
        EC2Action.fail(s"Could not locate elastic ip <$ip>")
      case Some(address) =>
        address.pure[EC2Action]
    })

  def associate(ip: String, instance: String): EC2Action[Unit] = for {
    address <- findByIpOrFail(ip)
    _       <- associateWith(address.getAllocationId, instance)
  } yield ()

  def associateWith(allocationId: String, instance: String): EC2Action[Unit] =
    EC2Action(client =>
      client.associateAddress(
        (new AssociateAddressRequest)
          .withAllocationId(allocationId)
          .withInstanceId(instance))).void

  def disassociate(address: Address): EC2Action[Unit] =
    Option(address.getAssociationId) match {
      case None    => EC2Action.ok(())
      case Some(a) => EC2Action(client => client.disassociateAddress(
        (new DisassociateAddressRequest)
          .withAssociationId(a)))
    }

  def allocate(): EC2Action[String] =
    EC2Action(client =>
      client.allocateAddress(
        (new AllocateAddressRequest)
          .withDomain(DomainType.Vpc)
      ).getPublicIp)
}
