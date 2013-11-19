package com.ambiata.saws
package ec2

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.{SecurityGroup => AwsSecurityGroup, _}
import com.ambiata.saws.core._
import com.ambiata.saws.iam._
import com.owtelse.codec.Base64

import scala.collection.JavaConverters._
import scalaz._, Scalaz._


object EC2Images {
  lazy val AmbiataProdUserId = "645187277599"

  def createImage(instanceId: String, name: String): EC2Action[String] =
    EC2Action(client => client.createImage(new CreateImageRequest(instanceId, name)).getImageId)

  def describeImages(imageIds: List[String]): EC2Action[List[Image]] =
    EC2Action(client => client.describeImages(
      new DescribeImagesRequest().withImageIds(imageIds.asJava)).getImages.asScala.toList).safe.orElse(List())

  def isAvailable(imageId: String): EC2Action[Boolean] =
    describeImages(List(imageId)).map(_.find(_.getImageId == imageId).exists(_.getState.toLowerCase == "available"))

  def waitForAvailable(imageId: String): EC2Action[Unit] = for {
    ok    <- isAvailable(imageId)
    _     <- ok.unlessM((for {
      _ <- Thread.sleep(5000).pure[EC2Action]
      _ <- waitForAvailable(imageId)
      } yield ()): EC2Action[Unit])
  } yield ()

  def addProdLaunchPermission(imageId: String): EC2Action[Unit] =
    EC2Action(client => client.modifyImageAttribute(
      (new ModifyImageAttributeRequest)
        .withImageId(imageId)
        .withLaunchPermission(
          (new LaunchPermissionModifications)
            .withAdd(
              (new LaunchPermission)
                .withUserId(AmbiataProdUserId)))))
}
