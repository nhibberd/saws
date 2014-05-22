package com.ambiata.saws.s3

import org.specs2.mutable.Specification
import com.ambiata.mundane.io.FilePath

class FilePathSpec extends Specification {

  "conversions" >> {
    val path = FilePath("s3://ambiata-dist/test/directory/")
    FilePaths.bucket(path) === "ambiata-dist"
    FilePaths.key(path)    === "test/directory/"

    FilePaths.filePath("ambiata-dist", "test/directory/") === FilePath("s3://ambiata-dist/test/directory/")
  }

}
