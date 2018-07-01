package com.ambiata.saws
package ec2

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._

import com.ambiata.saws.core._
import com.ambiata.saws.ec2._

import scala.collection.JavaConverters._

object EC2Snapshots {
  def ready(snapshotId: String): EC2Action[Boolean] =
    EC2Action(client =>
      client.describeSnapshots(
        new DescribeSnapshotsRequest()
          .withSnapshotIds(snapshotId)
      ).getSnapshots.asScala.forall(_.getState == "completed"))

  def create(volume: Volume, description: String): EC2Action[Snapshot] =
    EC2Action(client =>
      client.createSnapshot(
        new CreateSnapshotRequest(volume.getVolumeId, description)
      ).getSnapshot)


  def register(snapshotId: String, kernelId: String): EC2Action[String] =
    EC2Action(client =>
      client.registerImage(
        new RegisterImageRequest()
          .withArchitecture(ArchitectureValues.X86_64)
          .withKernelId(kernelId)
          .withName("bakery.testing")
          .withRootDeviceName("/dev/sda1")
          .withBlockDeviceMappings(List(
            new BlockDeviceMapping()
              .withDeviceName("/dev/sda1")
              .withEbs(
                new EbsBlockDevice()
                  .withVolumeType(VolumeType.Standard)
                  .withVolumeSize(10)
                  .withDeleteOnTermination(true)
                  .withSnapshotId(snapshotId)
              )
          ).asJava)
      ).getImageId)
}
