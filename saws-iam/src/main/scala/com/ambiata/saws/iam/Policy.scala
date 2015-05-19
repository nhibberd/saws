package com.ambiata.saws
package iam


/** IAM policy. */
case class Policy(name: String, document: String)


/** Constructors for different policies. */
object Policy {

  /** Create a policy allowing 'GetObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadPath(path: String, constrainList: Boolean): Policy = {
    val name = s"ReadAccessTo_$path".replace('/', '+')
    Policy(name, allowS3PathForActions(path, Seq("GetObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject' and 'ListBucket' for the specified S3 path. */
  def allowS3WritePath(path: String, constrainList: Boolean): Policy  = {
    val name = s"WriteAccessTo_$path".replace('/', '+')
    Policy(name, allowS3PathForActions(path, Seq("PutObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'PutObjectACL' and 'ListBucket' for the specified S3 path. */
  def allowS3WriteAclPath(path: String, constrainList: Boolean): Policy  = {
    val name = s"WriteAccessTo_$path".replace('/', '+')
    Policy(name, allowS3PathForActions(path, Seq("PutObject", "PutObjectAcl"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'GetObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWritePath(path: String, constrainList: Boolean): Policy  = {
    val name = s"ReadWriteAccessTo_$path".replace('/', '+')
    Policy(name, allowS3PathForActions(path, Seq("PutObject", "GetObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'PutObjectAcl', 'GetObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWriteAclPath(path: String, constrainList: Boolean): Policy  = {
    val name = s"ReadWriteAccessTo_$path".replace('/', '+')
    Policy(name, allowS3PathForActions(path, Seq("PutObject", "PutObjectAcl", "GetObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'GetObject', 'DeleteObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWriteDeletePath(path: String, constrainList: Boolean): Policy  = {
    val name = s"ReadWriteDeleteAccessTo_$path".replace('/', '+')
    Policy(name, allowS3PathForActions(path, Seq("PutObject", "GetObject", "DeleteObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'PutObjectAcl', 'GetObject', 'DeleteObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWriteAclDeletePath(path: String, constrainList: Boolean): Policy  = {
    val name = s"ReadWriteDeleteAccessTo_$path".replace('/', '+')
    Policy(name, allowS3PathForActions(path, Seq("PutObject", "PutObjectAcl", "GetObject", "DeleteObject"), constrainList))
  }

  /** Create a policy allowing 'ListBucket' and other S3 actions for the specified S3 path. */
  def allowS3PathForActions(path: String, actions: Seq[String], constrainList: Boolean) = {
    val s3Actions = actions.map(a => s""""s3:${a}"""").mkString(",")
    val bucket = path.takeWhile(_ != '/')
    val key = path.drop(bucket.length + 1)

    val listCondition =
      if (!constrainList)
        ""
      else
        s"""|"Condition": {
            |  "StringLike": { "s3:prefix": ["$key/*"] }
            |},""".stripMargin

    s"""|{
        |  "Version": "2012-10-17",
        |  "Statement": [
        |    {
        |      "Action": [ ${s3Actions} ],
        |      "Resource": [ "arn:aws:s3:::$path/*" ],
        |      "Effect": "Allow"
        |    },
        |    {
        |      "Action": [ "s3:ListBucket" ],
        |      "Resource": [ "arn:aws:s3:::$bucket" ],
        |      $listCondition
        |      "Effect": "Allow"
        |    }
        |  ]
        |}""".stripMargin
  }



  /** Allow IAM account aliases to be listed. This is important for verifying environments. */
  val allowIAMListAliasAccess: Policy = {
    val doc =
      s"""|{
          |  "Version": "2012-10-17",
          |  "Statement": [
          |    {
          |      "Sid": "Stmt1384734953000",
          |      "Effect": "Allow",
          |      "Action": [
          |        "iam:ListAccountAliases"
          |      ],
          |      "Resource": [
          |        "*"
          |      ]
          |    }
          |  ]
          |}""".stripMargin
    Policy("iam-list-account-aliases", doc)
  }

  /** Create a policy allowing full access to all EC2 actions. */
  val allowEc2FullAccess: Policy = {
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

  /** Create a policy allowing access to describe tags. */
  val allowEc2DescribeTags: Policy = {
    val doc =
      s"""|{
          |  "Version": "2012-10-17",
          |  "Statement": [
          |    {
          |      "Action": "ec2:DescribeTags",
          |      "Effect": "Allow",
          |      "Resource": "*"
          |    }
          |  ]
          |}""".stripMargin
    Policy("ec2-describe-tags", doc)
  }

  /** Create a policy for allowing full acces to SQS.
   */
  val allowSqsFullAccess: Policy = {
    val doc =
      s"""{
         |  "Version": "2012-10-17",
         |  "Statement": [
         |    {
         |      "Action": [
         |        "sqs:*"
         |      ],
         |      "Effect": "Allow",
         |      "Resource": "*"
         |    }
         |  ]
         |}""".stripMargin
    Policy("sqs-full-access", doc)
  }

   /** Create a policy for allowing full access to SES.
   */
  val allowSesFullAccess: Policy = {
    val doc =
      s"""{
         |  "Version": "2012-10-17",
         |  "Statement": [
         |    {
         |      "Action": [
         |        "ses:*"
         |      ],
         |      "Effect": "Allow",
         |      "Resource": "*"
         |    }
         |  ]
         |}""".stripMargin
    Policy("ses-full-access", doc)
  }

  /** Create a policy for allowing send email access to SES.
    */
  val allowSesSendAccess: Policy = {
    val doc =
      s"""{
         |  "Version": "2012-10-17",
         |  "Statement": [
         |    {
         |      "Action": [
         |        "ses:SendEmail", "ses:SendRawEmail"
         |      ],
         |      "Effect": "Allow",
         |      "Resource": "*"
         |    }
         |  ]
         |}""".stripMargin
    Policy("ses-send-access", doc)
  }

  /** Create a policies for allowing full access to all EMR actions as well as read-only access
    * to the 'elasticmapreduce' S3 buckets (for the purpose of running standard EMR bootstrap
    * actions and steps). */
  val allowEmrFullAccess: List[Policy] = {
    val doc =
      s"""|{
          |  "Version": "2012-10-17",
          |  "Statement": [
          |    {
          |      "Action": [
          |        "elasticmapreduce:*",
          |        "ec2:AuthorizeSecurityGroupIngress",
          |        "ec2:CancelSpotInstanceRequests",
          |        "ec2:CreateSecurityGroup",
          |        "ec2:CreateTags",
          |        "ec2:DescribeAvailabilityZones",
          |        "ec2:DescribeInstances",
          |        "ec2:DescribeKeyPairs",
          |        "ec2:DescribeRouteTables",
          |        "ec2:DescribeSecurityGroups",
          |        "ec2:DescribeSpotInstanceRequests",
          |        "ec2:DescribeSubnets",
          |        "ec2:ModifyImageAttribute",
          |        "ec2:ModifyInstanceAttribute",
          |        "ec2:RequestSpotInstances",
          |        "ec2:RunInstances",
          |        "ec2:TerminateInstances",
          |        "cloudwatch:*",
          |        "sdb:*"
          |      ],
          |      "Effect": "Allow",
          |      "Resource": "*"
          |    }
          |  ]
          |}""".stripMargin
    List(
      Policy("emr-full-access", doc),
      allowS3ReadPath("elasticmapreduce", false),
      allowS3ReadPath("ap-southeast-2.elasticmapreduce", false)
    )
  }
}
