package com.ambiata.saws
package core

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient

case class Select[A, B](contra: A => B)

object Select {
  def apply[A, B](implicit S: Select[A, B]) = implicitly[Select[A, B]]

  implicit def Tuple2Select1[A, B]: Select[(A, B), A] =
    Select(_._1)

  implicit def Tuple2Select2[A, B]: Select[(A, B), B] =
    Select(_._2)

  implicit def Tuple3Select1[A, B, C]: Select[(A, B, C), A] =
    Select(_._1)

  implicit def Tuple3Select2[A, B, C]: Select[(A, B, C), B] =
    Select(_._2)

  implicit def Tuple3Select3[A, B, C]: Select[(A, B, C), C] =
    Select(_._3)

  implicit def Tuple4Select1[A, B, C, D]: Select[(A, B, C, D), A] =
    Select(_._1)

  implicit def Tuple4Select2[A, B, C, D]: Select[(A, B, C, D), B] =
    Select(_._2)

  implicit def Tuple4Select3[A, B, C, D]: Select[(A, B, C, D), C] =
    Select(_._3)

  implicit def Tuple4Select4[A, B, C, D]: Select[(A, B, C, D), D] =
    Select(_._4)

  implicit def Tuple5Select1[A, B, C, D, E]: Select[(A, B, C, D, E), A] =
    Select(_._1)

  implicit def Tuple5Select2[A, B, C, D, E]: Select[(A, B, C, D, E), B] =
    Select(_._2)

  implicit def Tuple5Select3[A, B, C, D, E]: Select[(A, B, C, D, E), C] =
    Select(_._3)

  implicit def Tuple5Select4[A, B, C, D, E]: Select[(A, B, C, D, E), D] =
    Select(_._4)

  implicit def Tuple5Select5[A, B, C, D, E]: Select[(A, B, C, D, E), E] =
    Select(_._5)
}
