package com.ambiata
package saws
package s3

import scalaz._, Scalaz._
import java.io.File
import com.amazonaws.services.s3.AmazonS3Client
import scopt._

/**
 * This trait copies files to S3 using arguments passed on the command-line
 */
trait CopyToS3 extends S3Files {

  /**
   * copy a local file / local files to a bucket/key in S3
   */
  def copyFiles(args: Array[String]): String = {

    case class Config(local: String = "", bucket: String = "", key: String = "")

    val parser = new scopt.OptionParser[Config]("scopt") {
      head("\nCopy local files to S3\n")
      opt[String]('l', "local")  action { (l, c) => c.copy(local = l)  } required() text "Local dir/file to copy to S3."
      opt[String]('b', "bucket") action { (b, c) => c.copy(bucket = b) } required() text "S3 bucket"
      opt[String]('k', "key")    action { (k, c) => c.copy(key = k)    } required() text "S3 object key"
      help("help") text "Prints this usage text"
    }

    parser.parse(args, Config()).map { c => import c._
      val file = new File(c.local)
      if(!file.exists) sys.error(s"'${c.local}' doesn't exist!")

      val client = new AmazonS3Client
      uploadFiles(bucket, key, file, client) match {
        case -\/(err)  => sys.error(err)
        case \/-(succ) => s"Upload of '$local' to S3 '$bucket/$key' complete!"
      }
    }.getOrElse(s"""cannot parse arguments ${args.mkString(" ")}""")
  }

}

object CopyToS3 extends CopyToS3

