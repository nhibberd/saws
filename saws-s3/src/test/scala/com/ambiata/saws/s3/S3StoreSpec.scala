package com.ambiata.saws.s3

import java.util.UUID

import com.amazonaws.services.s3.AmazonS3Client
import com.ambiata.mundane.control._
import com.ambiata.mundane.io._
import com.ambiata.mundane.testing.Paths._
import com.ambiata.mundane.testing.{Paths, Entry}
import com.ambiata.mundane.testing.ResultTIOMatcher._
import com.ambiata.saws.core.S3Action
import org.specs2._
import org.specs2.matcher.Parameters
import scodec.bits.ByteVector
import scala.io.Codec
import scalaz.{Store => _, _}, Scalaz._
import scalaz.effect.IO
import org.specs2.specification._

// FIX Workout how this test can be pulled out and shared with posix/s3/hdfs.
class S3StoreSpec extends Specification with ScalaCheck { def is = sequential ^ skipAllIf(true) ^ s2"""
  S3 Store Usage
  ==============

  list all files paths                            $list
  list all files paths from a sub path            $listSubPath
  list direct files                               $listFiles
  list direct files from a sub path               $listFilesSubPath
  list directories                                $listDirs
  list directories from a sub path                $listDirsSubPath
  filter listed paths                             $filter
  find path in root (thirdish)                    $find
  find path in root (first)                       $findfirst
  find path in root (last)                        $findlast

  exists                                          $exists
  not exists                                      $notExists

  delete                                          $delete
  deleteAll                                       $deleteAll

  move                                            $move
  move and read                                   $moveRead
  copy                                            $copy
  copy and read                                   $copyRead
  mirror                                          $mirror

  moveTo                                          $moveTo
  copyTo                                          $copyTo
  mirrorTo                                        $mirrorTo

  checksum                                        $checksum
  read / write bytes                              $bytes
  read / write strings                            $strings
  read / write utf8 strings                       $utf8Strings
  read / write lines                              $lines
  read / write utf8 lines                         $utf8Lines
  ${Step(client.shutdown)}
  """

  override implicit def defaultParameters: Parameters =
    new Parameters(minTestsOk = 3, workers = 1, maxDiscardRatio = 100)

  lazy val tmp1 = DirPath.unsafe(System.getProperty("java.io.tmpdir", "/tmp")) </> FileName.unsafe(s"StoreSpec.${UUID.randomUUID}")
  lazy val tmp2 = DirPath.unsafe(System.getProperty("java.io.tmpdir", "/tmp")) </> FileName.unsafe(s"StoreSpec.${UUID.randomUUID}")
  lazy val store     = S3Store("ambiata-test-view-exp", DirPath.unsafe("s3storespec/store"), client, tmp1)
  lazy val alternate = S3Store("ambiata-test-view-exp", DirPath.unsafe("s3storespec/alternate"), client, tmp2)
  lazy val client = new AmazonS3Client

  def list =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      store.listAll must beOkLike((_:List[FilePath]).toSet must_== filepaths.toSet) })

  def listSubPath =
    propNoShrink((paths: Paths) => clean(paths.map(_ prepend "sub")) { filepaths =>
      store.list(DirPath.Empty </> "sub") must beOkLike((_:List[FilePath]).toSet must_== filepaths.map(_.fromRoot).toSet) })

  def listFiles =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      store.listFiles(DirPath.Empty) must beOkLike((_:List[FilePath]).toSet must_== filepaths.filter(f => f.rootname.basename == f.basename).toSet) })

  def listFilesSubPath =
    propNoShrink((paths: Paths) => clean(paths.map(_ prepend "sub")) { filepaths =>
      store.listFiles(DirPath.Empty </> "sub") must beOkLike((_:List[FilePath]).toSet must_== filepaths.map(_.fromRoot).filter(f => f.rootname.basename == f.basename).toSet) })

  def listDirs =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      store.listDirs(DirPath.Empty) must beOkLike((_:List[DirPath]).toSet must_== filepaths.map(_.rootname).toSet) })

  def listDirsSubPath =
    propNoShrink((paths: Paths) => clean(paths.map(_ prepend "sub")) { filepaths =>
      store.listDirs(DirPath.Empty </> "sub") must beOkLike((_:List[DirPath]).toSet must_== filepaths.map(_.fromRoot.rootname).toSet)
    })

  def filter =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      val first = filepaths.head
      val last = filepaths.last
      val expected = if (first == last) List(first) else List(first, last)
      store.filterAll(x => x == first || x == last) must beOkLike(paths => paths must contain(allOf(expected:_*))) })

  def find =
    propNoShrink((paths: Paths) => paths.entries.length >= 3 ==> { clean(paths) { filepaths =>
      val third = filepaths.drop(2).head
      store.findAll(_ == third) must beOkValue(Some(third)) } })

  def findfirst =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      store.findAll(x => x == filepaths.head) must beOkValue(Some(filepaths.head)) })

  def findlast =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      store.findAll(x => x == filepaths.last) must beOkValue(Some(filepaths.last)) })

  def exists =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      filepaths.traverseU(store.exists) must beOkLike(_.forall(identity)) })

  def notExists =
    propNoShrink((paths: Paths) => store.exists(DirPath.unsafe("root") </> "i really don't exist") must beOkValue(false))

  def delete =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      val first = filepaths.head
      (store.delete(first) >> filepaths.traverseU(store.exists)) must beOkLike(x => !x.head && x.tail.forall(identity)) })

  def deleteAll =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      (store.deleteAllFromRoot >> filepaths.traverseU(store.exists)) must beOkLike(x => !x.tail.exists(identity)) })

  def move =
    propNoShrink((m: Entry, n: Entry) => clean(Paths(m :: Nil)) { _ =>
      (store.move(m.full.toFilePath, n.full.toFilePath) >>
        store.exists(m.full.toFilePath).zip(store.exists(n.full.toFilePath))) must beOkValue(false -> true) })

  def moveRead =
    propNoShrink((m: Entry, n: Entry) => clean(Paths(m :: Nil)) { _ =>
      (store.move(m.full.toFilePath, n.full.toFilePath) >>
        store.utf8.read(n.full.toFilePath)) must beOkValue(m.value.toString) })

  def copy =
    propNoShrink((m: Entry, n: Entry) => clean(Paths(m :: Nil)) { _ =>
      (store.copy(m.full.toFilePath, n.full.toFilePath) >>
        store.exists(m.full.toFilePath).zip(store.exists(n.full.toFilePath))) must beOkValue(true -> true) })

  def copyRead =
    propNoShrink((m: Entry, n: Entry) => clean(Paths(m :: Nil)) { _ =>
      (store.copy(m.full.toFilePath, n.full.toFilePath) >>
        store.utf8.read(m.full.toFilePath).zip(store.utf8.read(n.full.toFilePath))) must beOkLike({ case (in, out) => in must_== out }) })

  def mirror =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      store.mirror(DirPath.Empty, DirPath.unsafe("mirror")) >> store.list(DirPath.unsafe("mirror")) must
        beOkLike((_:List[FilePath]).toSet must_== filepaths.toSet) })

  def moveTo =
    propNoShrink((m: Entry, n: Entry) => clean(Paths(m :: Nil)) { _ =>
      (store.moveTo(alternate, m.full.toFilePath, n.full.toFilePath) >>
        store.exists(m.full.toFilePath).zip(alternate.exists(n.full.toFilePath))) must beOkValue(false -> true) })

  def copyTo =
    propNoShrink((m: Entry, n: Entry) => clean(Paths(m :: Nil)) { _ =>
      (store.copyTo(alternate, m.full.toFilePath, n.full.toFilePath) >>
        store.exists(m.full.toFilePath).zip(alternate.exists(n.full.toFilePath))) must beOkValue(true -> true) })

  def mirrorTo =
    propNoShrink((paths: Paths) => clean(paths) { filepaths =>
      store.mirrorTo(alternate, DirPath.Empty, DirPath.unsafe("mirror")) >> alternate.list(DirPath.unsafe("mirror")) must
        beOkLike((_:List[FilePath]).toSet must_== filepaths.toSet) })

  def checksum =
    propNoShrink((m: Entry) => clean(Paths(m :: Nil)) { _ =>
      store.checksum(m.full.toFilePath, MD5) must beOkValue(Checksum.string(m.value.toString, MD5)) })

  def bytes =
    propNoShrink((m: Entry, bytes: Array[Byte]) => clean(Paths(m :: Nil)) { _ =>
      (store.bytes.write(m.full.toFilePath, ByteVector(bytes)) >> store.bytes.read(m.full.toFilePath)) must beOkValue(ByteVector(bytes)) })

  def strings =
    propNoShrink((m: Entry, s: String) => clean(Paths(m :: Nil)) { _ =>
      (store.strings.write(m.full.toFilePath, s, Codec.UTF8) >> store.strings.read(m.full.toFilePath, Codec.UTF8)) must beOkValue(s) })

  def utf8Strings =
    propNoShrink((m: Entry, s: String) => clean(Paths(m :: Nil)) { _ =>
      (store.utf8.write(m.full.toFilePath, s) >> store.utf8.read(m.full.toFilePath)) must beOkValue(s) })

  def lines =
    propNoShrink((m: Entry, s: List[Int]) => clean(Paths(m :: Nil)) { _ =>
      (store.lines.write(m.full.toFilePath, s.map(_.toString), Codec.UTF8) >> store.lines.read(m.full.toFilePath, Codec.UTF8)) must beOkValue(s.map(_.toString)) })

  def utf8Lines =
    propNoShrink((m: Entry, s: List[Int]) => clean(Paths(m :: Nil)) { _ =>
      (store.linesUtf8.write(m.full.toFilePath, s.map(_.toString)) >> store.linesUtf8.read(m.full.toFilePath)) must beOkValue(s.map(_.toString)) })

  def files(paths: Paths): List[FilePath] =
    paths.entries.map(e => e.full.toFilePath.asRelative).sortBy(_.path)

  def create(paths: Paths): ResultT[IO, Unit] =
    paths.entries.traverseU(e =>
      S3.writeLines("ambiata-test-view-exp", (e prepend "s3storespec/store").path+"/"+e.value, Seq(e.value.toString))).evalT.void

  def clean[A](paths: Paths)(run: List[FilePath] => A): A = {
    try {
      create(paths).run.unsafePerformIO
      run(files(paths))
    }
    finally {
      (S3.deleteAll("ambiata-test-view-exp", "s3storespec") >>
        S3Action.fromResultT(Directories.delete(tmp1)) >>
        S3Action.fromResultT(Directories.delete(tmp2))).execute(client).unsafePerformIO
    }
  }

  implicit class ToDirPath(s: String) {
    def toDirPath: DirPath = DirPath.unsafe(s)
  }

  implicit class ToFilePath(s: String) {
    def toFilePath: FilePath = FilePath.unsafe(s)
  }

  def s3IsAccessible =
    S3.isS3Accessible.execute(new AmazonS3Client).unsafePerformIO.isOk

}
