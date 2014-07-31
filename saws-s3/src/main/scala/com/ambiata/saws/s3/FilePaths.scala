package com.ambiata.saws.s3

import com.ambiata.mundane.io.{DirPath, FilePath}

object S3Path {
  def unapply(path: String): Option[FilePath] =
    if (path.startsWith("s3://")) Some(FilePath.unsafe(path.replace("s3://", "")))
    else None

  /**
   * Conversion methods between bucket/key and FilePath
   */
  def bucket(path: FilePath) =
    bucketAndKey(path)._1

  def key(path: FilePath) =
    bucketAndKey(path)._2

  def bucketAndKey(path: FilePath): (String, String) =
    (path.rootname.dirs.map(_.name).mkString("/"), path.fromRoot.path)

  def filePath(bucket: String, key: String): FilePath =
    FilePath.unsafe(s"$bucket/$key")

  def bucket(path: DirPath) =
    bucketAndKey(path)._1

  def key(path: DirPath) =
    bucketAndKey(path)._2

  def bucketAndKey(path: DirPath): (String, String) =
    bucketAndKey(path.toFilePath)

  def dirPath(bucket: String, key: String): DirPath =
    DirPath.unsafe(s"$bucket/$key")

}

object HdfsPath {
  def unapply(path: String): Option[FilePath] =
    if (path.startsWith("hdfs://")) Some(FilePath.unsafe(path.replace("hdfs://", "")))
    else None
}

object LocalPath {
  def unapply(path: String): Option[FilePath] =
    if (path.startsWith("file://")) Some(FilePath.unsafe(path.replace("file://", "")))
    else                            Some(FilePath.unsafe(path))
}
