package com.ambiata.saws
package core

import scalaz._, Scalaz._

trait MonadS3[F[_]] { def liftS3[A](f: S3Action[A]): F[A] }
trait MonadEC2[F[_]] { def liftEC2[A](f: EC2Action[A]): F[A] }
trait MonadIAM[F[_]] { def liftIAM[A](f: IAMAction[A]): F[A] }
trait MonadEMR[F[_]] { def liftEMR[A](f: EMRAction[A]): F[A] }
