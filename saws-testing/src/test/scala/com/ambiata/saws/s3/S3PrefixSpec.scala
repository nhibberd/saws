package com.ambiata.saws.s3

import com.ambiata.disorder._
import com.ambiata.saws.core._
import com.ambiata.saws.testing._
import com.ambiata.saws.testing.Arbitraries._
import com.ambiata.saws.testing.AwsMatcher._
import com.ambiata.saws.s3.S3Temporary._
import com.ambiata.mundane.control._
import com.ambiata.mundane.io._
import com.ambiata.mundane.io.Arbitraries._
import com.ambiata.mundane.io.Temporary._
import com.ambiata.mundane.path._
import org.specs2._

import scalaz.{Name =>_,_}, Scalaz._, effect._, effect.Effect._

class S3PrefixSpec extends AwsScalaCheckSpec(5) { def is = s2"""

 S3 file interactions
 ====================

 It is possible to
   upload multiple files to S3         $upload
   download multiple files from S3     $download
   delete multiple files from S3       $delete
   check existence of prefix on S3     $exists
   check existence can fail on S3      $existsFail
   list all files in prefix on S3      $list
   list all S3Address's in prefix      $listAddress
   get list of file sizes              $fileSizes

 Test that we can remove common prefix's from S3Address
 ======================================================

  Can successfully remove common prefix              $succesfullCommonPrefix
  Will return None when there is no common prefix    $noCommonPrefix

 Support functions
 =========================================

  Can retrieve S3Prefix from uri        $fromUri

"""
  def upload = prop((data: String, s3: S3Temporary, local: LocalTemporary, dp: DistinctPair[Ident]) => for {
    p <- s3.prefix
    d <- S3Action.fromRIO(local.directory)
    _ <- S3Action.fromRIO((d.toLocalPath / Path(dp.first.value)).write(data))
    _ <- S3Action.fromRIO((d.toLocalPath / Path(dp.second.value)).write(data))
    _ <- p.putFiles(d)
    f <- (p | dp.first.value).exists
    s <- (p | dp.second.value).exists
  } yield f -> s ==== true -> true)

  def download = prop((data: DistinctPair[String], key: DistinctPair[Ident], s3: S3Temporary, local: LocalTemporary) => for {
    p <- s3.prefix
    d <- S3Action.fromRIO(local.directory)
    _ <- (p | key.first.value).put(data.first)
    _ <- (p | key.second.value).put(data.second)
    _ <- p.getFiles(d.toLocalPath)
    f <- S3Action.fromRIO((d.toLocalPath / Path(key.first.value)).read)
    s <- S3Action.fromRIO((d.toLocalPath / Path(key.second.value)).read)
  } yield f -> s ==== data.first.some -> data.second.some)

  def delete = prop((s3: S3Temporary, key: Ident) => for {
    p <- s3.prefix
    _ <- (p | key.value).put("")
    a <- (p | key.value).exists
    b <- p.exists
    _ <- p.delete
    d <- p.exists
    e <- (p | key.value).exists
  } yield (a, b, d, e) ==== ((true, true, false, false)))

  def exists = prop((s3: S3Temporary, key: Ident, data: String) => for {
    p <- s3.prefix
    _ <- (p | key.value).put(data)
    e <- p.exists
  } yield e ==== true)

  def existsFail = prop((s3: S3Temporary) => for {
    p <- s3.prefix
    e <- p.exists
  } yield e ==== false)

  def list = prop((s3: S3Temporary, keys: DistinctPair[Ident]) => for {
    p <- s3.prefix
    _ <- (p | keys.first.value).put("")
    _ <- (p | keys.second.value).put("")
    l <- p.listKeys
  } yield l.sorted ==== List((p | keys.first.value).key, (p | keys.second.value).key).sorted)

  def listAddress = prop((s3: S3Temporary, keys: DistinctPair[Ident]) => for {
    p <- s3.prefix
    _ <- (p | keys.first.value).put("")
    _ <- (p | keys.second.value).put("")
    l <- p.listAddress
  } yield l.toSet ==== List((p | keys.first.value), (p | keys.second.value)).toSet)

  def fileSizes = prop((s3: S3Temporary, local: LocalTemporary, dp: DistinctPair[Ident], key: Ident) => for {
    d <- S3Action.fromRIO(local.directory)
    p <- s3.prefix
    f1 <- S3Action.fromRIO((d.toLocalPath / Path(dp.first.value)).write(""))
    f2 <- S3Action.fromRIO((d.toLocalPath / Path(dp.second.value) / Path(key.value)).write(""))
    f <- S3Action.safe(f1.toFile.length())
    k <- S3Action.safe(f2.toFile.length())
    _ <- p.putFiles(d)
    s <- p.size
  } yield s ==== f + k)

  def succesfullCommonPrefix = prop((prefix: S3Prefix, key: String) => {
    val s3 = prefix | key
    s3.removeCommonPrefix(prefix) must_== Some(key)
  })

  def noCommonPrefix = {
    val s3 = S3Prefix("bucket","a/b/c/d/e")
    val foo = S3Prefix("bucket","a/b/e")
    s3.removeCommonPrefix(foo) must_== None
  }


  def fromUri = prop((s3: S3Temporary, key: Ident) => for {
    p <- s3.prefix
    _ <- (p | key.value).put("")
    e <- S3Prefix.fromUri(s"s3://${p.bucket}/${p.prefix}")
  } yield e ==== p.some)
}
