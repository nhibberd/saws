package com.ambiata.saws
package ec2

import com.ambiata.com.amazonaws.services.ec2.AmazonEC2Client
import com.ambiata.com.amazonaws.services.ec2.model._

import com.ambiata.saws.core._
import com.ambiata.saws.ec2._

import scala.collection.JavaConverters._

object EC2Volumes {
  def create(snapshotId: String, availabilityZone: String): EC2Action[Volume] =
    EC2Action(client =>
      client.createVolume(
        new CreateVolumeRequest(snapshotId, availabilityZone)
          .withVolumeType(VolumeType.Standard)
          .withSize(10)
      ).getVolume)


  def attach(volume: Volume, instance: Instance, device: String) =
    EC2Action(client =>
      client.attachVolume(
        new AttachVolumeRequest(volume.getVolumeId, instance.getInstanceId, device)
      ).getAttachment)

  def detach(volume: Volume, instance: Instance, device: String): EC2Action[Unit] =
    EC2Action(client =>
      client.detachVolume(
        new DetachVolumeRequest(volume.getVolumeId)
         .withInstanceId(instance.getInstanceId)
         .withDevice(device)
      ))
}
