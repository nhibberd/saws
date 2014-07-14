package com.ambiata.saws.s3

import com.ambiata.mundane.io.FilePath

object S3Path {
  def unapply(path: String): Option[FilePath] =
    if (path.startsWith("s3://")) Some(FilePath(path.replace("s3://", "")))
    else None

  /**
   * Conversion methods between bucket/key and FilePath
   */
  def bucket(path: FilePath) =
    bucketAndKey(path)._1

  def key(path: FilePath) =
    bucketAndKey(path)._2

  def bucketAndKey(path: FilePath): (String, String) =
    (path.rootname.path, path.fromRoot.path)

  def filePath(bucket: String, key: String): FilePath =
    FilePath(s"$bucket/$key")
}

object HdfsPath {
  def unapply(path: String): Option[FilePath] =
    if (path.startsWith("hdfs://")) Some(FilePath(path.replace("hdfs://", "")))
    else None
}

object LocalPath {
  def unapply(path: String): Option[FilePath] =
    if (path.startsWith("file://")) Some(FilePath(path.replace("file://", "")))
    else                            Some(FilePath(path))
}
