package com.ambiata.saws.s3

import org.specs2.mutable.Specification
import com.ambiata.mundane.io.FilePath

class S3PathSpec extends Specification {

  "conversions" >> {
    val path = FilePath("ambiata-dist/test/directory/")
    S3Path.bucket(path) === "ambiata-dist"
    S3Path.key(path)    === "test/directory/"

    S3Path.filePath("ambiata-dist", "test/directory/") === FilePath("ambiata-dist/test/directory/")
  }

}
