package com.ambiata.saws
package iam


/** IAM policy. */
case class Policy(name: String, document: String)


/** Constructors for different policies. */
object Policy {

  def allowS3ReadPath(path: String): Policy = {
    val name = s"ReadAccessTo_$path".replace('/', '+')
    Policy(name, allowS3PathForActions(path, Seq("GetObject")))
  }

  def allowS3WritePath(path: String): Policy  = {
    val name = s"WriteAccessTo_$path".replace('/', '+')
    Policy(name, allowS3PathForActions(path, Seq("PutObject")))
  }

  def allowS3ReadWritePath(path: String): Policy  = {
    val name = s"ReadWriteAccessTo_$path".replace('/', '+')
    Policy(name, allowS3PathForActions(path, Seq("PutObject", "GetObject")))
  }

  def allowS3PathForActions(path: String, actions: Seq[String]) = {
    val s3Actions = actions.map(a => s""""s3:${a}"""").mkString(",")
    s"""|{
        |  "Version": "2012-10-17",
        |  "Statement": [
        |    {
        |      "Action": [ ${s3Actions} ],
        |      "Resource": [ "arn:aws:s3:::$path/*" ],
        |      "Effect": "Allow"
        |    }
        |  ]
        |}""".stripMargin
  }

  val allowEC2FullAccess: Policy = {
    val doc =
      s"""|{
          |  "Version": "2012-10-17",
          |  "Statement": [
          |    {
          |      "Action": "ec2:*",
          |      "Effect": "Allow",
          |      "Resource": "*"
          |    }
          |  ]
          |}""".stripMargin
    Policy("ec2-full-access", doc)
  }
}