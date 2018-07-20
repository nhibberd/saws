package com.ambiata.saws
package iam

sealed trait Policy {
  def name: String
}

/** IAM policy. */
case class InlinePolicy(name: String, document: String) extends Policy

/**
  * Represents a policy that is managed either by AWS (predfinined) or customer created (shared)
  *
  * @param name
  */
case class AwsManagedPolicy(val name: String, val arnPrefix: String = "arn:aws:iam::aws:policy") extends Policy {
  def arn: String = s"$arnPrefix/$name"
}

/**
  * Represents a policy that is managed either by AWS (predfinined) or customer created (shared)
  *
  * @param name
  * @param document
  * @todo NOT IMPLEMENTED YET
  */
case class CustomerManagedPolicy(val name: String, val document: String) extends Policy


/** Constructors for different policies. */
object Policy {
  /** Policy to enable Amazon ECS to manage your cluster. */
  def awsAmazonECSServiceRolePolicy: AwsManagedPolicy = AwsManagedPolicy("AmazonECSServiceRolePolicy", "arn:aws:iam::aws:policy/service-role")

  /** Provides administrative access to Amazon ECS resources and enables ECS features through access to other AWS service resources, including VPCs, Auto Scaling groups, and CloudFormation stacks. */
  def awsAmazonECS_FullAccess: AwsManagedPolicy = AwsManagedPolicy("AmazonECS_FullAccess")

  /** Provides full access to Auto Scaling. */
  def awsAutoScalingFullAccess: AwsManagedPolicy = AwsManagedPolicy("AutoScalingFullAccess")

  /** Create a policy allowing 'GetObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadPath(path: String, constrainList: Boolean): InlinePolicy = {
    val name = s"ReadAccessTo_$path".replace('/', '+')
    InlinePolicy(name, allowS3PathForActions(path, Seq("GetObject"), constrainList))
  }

  /** Create a policy allowing 'GetObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadPath(bucket: String, keys: Seq[String], constrainList: Boolean): InlinePolicy = {
    val name = s"ReadAccessTo_$bucket"
    InlinePolicy(name, allowS3PathForActions(bucket, keys, Seq("GetObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject' and 'ListBucket' for the specified S3 path. */
  def allowS3WritePath(path: String, constrainList: Boolean): Policy  = {
    val name = s"WriteAccessTo_$path".replace('/', '+')
    InlinePolicy(name, allowS3PathForActions(path, Seq("PutObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject' and 'ListBucket' for the specified S3 path. */
  def allowS3WritePath(bucket: String, keys: Seq[String], constrainList: Boolean): Policy  = {
    val name = s"WriteAccessTo_$bucket"
    InlinePolicy(name, allowS3PathForActions(bucket, keys, Seq("PutObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'PutObjectACL' and 'ListBucket' for the specified S3 path. */
  def allowS3WriteAclPath(path: String, constrainList: Boolean): Policy  = {
    val name = s"WriteAccessTo_$path".replace('/', '+')
    InlinePolicy(name, allowS3PathForActions(path, Seq("PutObject", "PutObjectAcl"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'PutObjectACL' and 'ListBucket' for the specified S3 path. */
  def allowS3WriteAclPath(bucket: String, keys: Seq[String], constrainList: Boolean): Policy  = {
    val name = s"WriteAccessTo_$bucket"
    InlinePolicy(name, allowS3PathForActions(bucket, keys, Seq("PutObject", "PutObjectAcl"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'GetObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWritePath(path: String, constrainList: Boolean): Policy  = {
    val name = s"ReadWriteAccessTo_$path".replace('/', '+')
    InlinePolicy(name, allowS3PathForActions(path, Seq("PutObject", "GetObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'GetObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWritePath(bucket: String, keys: Seq[String], constrainList: Boolean): Policy  = {
    val name = s"ReadWriteAccessTo_$bucket"
    InlinePolicy(name, allowS3PathForActions(bucket, keys, Seq("PutObject", "GetObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'PutObjectAcl', 'GetObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWriteAclPath(path: String, constrainList: Boolean): Policy  = {
    val name = s"ReadWriteAccessTo_$path".replace('/', '+')
    InlinePolicy(name, allowS3PathForActions(path, Seq("PutObject", "PutObjectAcl", "GetObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'PutObjectAcl', 'GetObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWriteAclPath(bucket: String, keys: Seq[String], constrainList: Boolean): Policy  = {
    val name = s"ReadWriteAccessTo_$bucket"
    InlinePolicy(name, allowS3PathForActions(bucket, keys, Seq("PutObject", "PutObjectAcl", "GetObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'GetObject', 'DeleteObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWriteDeletePath(path: String, constrainList: Boolean): Policy  = {
    val name = s"ReadWriteDeleteAccessTo_$path".replace('/', '+')
    InlinePolicy(name, allowS3PathForActions(path, Seq("PutObject", "GetObject", "DeleteObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'GetObject', 'DeleteObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWriteDeletePath(bucket: String, keys: Seq[String], constrainList: Boolean): Policy  = {
    val name = s"ReadWriteDeleteAccessTo_$bucket"
    InlinePolicy(name, allowS3PathForActions(bucket, keys, Seq("PutObject", "GetObject", "DeleteObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'PutObjectAcl', 'GetObject', 'DeleteObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWriteAclDeletePath(path: String, constrainList: Boolean): Policy  = {
    val name = s"ReadWriteDeleteAccessTo_$path".replace('/', '+')
    InlinePolicy(name, allowS3PathForActions(path, Seq("PutObject", "PutObjectAcl", "GetObject", "DeleteObject"), constrainList))
  }

  /** Create a policy allowing 'PutObject', 'PutObjectAcl', 'GetObject', 'DeleteObject' and 'ListBucket' for the specified S3 path. */
  def allowS3ReadWriteAclDeletePath(bucket: String, keys: Seq[String], constrainList: Boolean): Policy  = {
    val name = s"ReadWriteDeleteAccessTo_$bucket"
    InlinePolicy(name, allowS3PathForActions(bucket, keys, Seq("PutObject", "PutObjectAcl", "GetObject", "DeleteObject"), constrainList))
  }

  /** Create a policy allowing 'ListBucket' and other S3 actions for the specified S3 path. */
  def allowS3PathForActions(path: String, actions: Seq[String], constrainList: Boolean): String = {
    val bucket = path.takeWhile(_ != '/')
    val key = path.drop(bucket.length + 1)
    allowS3PathForActions(bucket, Seq(key), actions, constrainList)
  }

  def allowS3PathForActions(bucket: String, keys: Seq[String], actions: Seq[String], constrainList: Boolean): String = {
    val s3Actions = actions.map(a => s""""s3:${a}"""").mkString(",")
    val strippedKeys = keys.map { _.stripPrefix("/").stripSuffix("/") }
    val listCondition =
      if (!constrainList)
        ""
      else {
        val patterns = strippedKeys.map(Seq(_, "*").filter(_.nonEmpty) mkString "/")
          .map(x => s""""${x}"""").mkString(", ")
        s"""|"Condition": {
            |  "StringLike": { "s3:prefix": [$patterns] }
            |},""".stripMargin
      }

    val s3Arns = strippedKeys.map( Seq(bucket,_).filter(_.nonEmpty) mkString "/").map ( x => s""""arn:aws:s3:::${x}/*"""" ).mkString( ", " )
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
    InlinePolicy("iam-list-account-aliases", doc)
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
    InlinePolicy("ec2-full-access", doc)
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
    InlinePolicy("ec2-describe-tags", doc)
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
    InlinePolicy("sqs-full-access", doc)
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
    InlinePolicy("ses-full-access", doc)
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
    InlinePolicy("ses-send-access", doc)
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
      InlinePolicy("emr-full-access", doc),
      allowS3ReadPath("elasticmapreduce", false),
      allowS3ReadPath("ap-southeast-2.elasticmapreduce", false)
    )
  }

  /** Create a policy for allowing put metric data to cloudwatch
   */
  val allowCloudWatchPutMetric: Policy = {
    val doc =
      s"""{
         |  "Version": "2012-10-17",
         |  "Statement": [
         |    {
         |      "Action": [
         |        "cloudwatch:PutMetricData"
         |      ],
         |      "Effect": "Allow",
         |      "Resource": "*"
         |    }
         |  ]
         |}""".stripMargin
    InlinePolicy("cloudwatch-put-metric-access", doc)
  }
}