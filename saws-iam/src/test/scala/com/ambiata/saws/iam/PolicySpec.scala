package com.ambiata.saws
package iam

import com.ambiata.saws.iam.Policy._
import org.specs2.{ScalaCheck, Specification}

class PolicySpec extends Specification with ScalaCheck {def is = s2"""

  entire bucket - no path:            $entireBucket
  entire bucket - passed slash:       $extraSlash
  two paths given:                    $twoPaths
  bucket/path:                        $bucketAndPath
  2buckets:                           $multiBuckets

"""

  def expectedPolicy(s3Actions: String, s3Arns: String, bucket: String)=
    s"""|{
        |  "Version": "2012-10-17",
        |  "Statement": [
        |    {
        |      "Action": [ ${s3Actions} ],
        |      "Resource": [ ${s3Arns} ],
        |      "Effect": "Allow"
        |    },
        |    {
        |      "Action": [ "s3:ListBucket" ],
        |      "Resource": [ "arn:aws:s3:::${bucket}" ],
        |
        |      "Effect": "Allow"
        |    }
        |  ]
        |}""".stripMargin


  def expectedBucketsPolicy(s3Actions: String, s3Arns: String, buckets: String)=
    s"""|{
        |  "Version": "2012-10-17",
        |  "Statement": [
        |    {
        |      "Action": [ ${s3Actions} ],
        |      "Resource": [ ${s3Arns} ],
        |      "Effect": "Allow"
        |    },
        |    {
        |      "Action": [ "s3:ListBucket" ],
        |      "Resource": [ ${buckets} ],
        |
        |      "Effect": "Allow"
        |    }
        |  ]
        |}""".stripMargin

  def entireBucket = allowS3ReadPath("ambiata-dist/", constrainList = false)
    .document must beEqualTo(expectedPolicy("\"s3:GetObject\"", "\"arn:aws:s3:::ambiata-dist/*\"", "ambiata-dist")).ignoreSpace

  def extraSlash = allowS3ReadPath("ambiata-dist", Seq("/"), constrainList = false)
    .document must beEqualTo(expectedPolicy("\"s3:GetObject\"", "\"arn:aws:s3:::ambiata-dist/*\"", "ambiata-dist")).ignoreSpace

  def twoPaths = allowS3ReadPath("ambiata-dist", Seq("goat", "chicken"), constrainList = false)
    .document must beEqualTo(expectedPolicy("\"s3:GetObject\"", "\"arn:aws:s3:::ambiata-dist/goat/*\", \"arn:aws:s3:::ambiata-dist/chicken/*\"", "ambiata-dist")).ignoreSpace

  def bucketAndPath = allowS3ReadPath("ambiata-dist/test", constrainList = false)
    .document must beEqualTo(expectedPolicy("\"s3:GetObject\"", "\"arn:aws:s3:::ambiata-dist/test/*\"", "ambiata-dist")).ignoreSpace

  def multiBuckets = allowS3ReadBuckets(Seq("ambiata-dist", "ambiata-dispensary"))
    .document must beEqualTo(expectedBucketsPolicy("\"s3:GetObject\"", "\"arn:aws:s3:::ambiata-dist/*\", \"arn:aws:s3:::ambiata-dispensary/*\"", "\"arn:aws:s3:::ambiata-dist\", \"arn:aws:s3:::ambiata-dispensary\"")).ignoreSpace

}
