package com.ambiata.saws.s3

import com.ambiata.mundane.io.FilePath

/**
 * Conversion methods between bucket/key and FilePath
 */
object FilePaths {

  def bucket(path: FilePath) =
    bucketAndKey(path)._1

  def key(path: FilePath) =
    bucketAndKey(path)._2

  def bucketAndKey(path: FilePath): (String, String) = {
    val withoutS3 = new FilePath(path.path.replace("s3://", ""))
    (withoutS3.rootname.path, withoutS3.fromRoot.path)
  }

  def filePath(bucket: String, key: String): FilePath =
    FilePath(s"$bucket/$key")

}
