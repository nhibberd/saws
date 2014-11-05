package com.ambiata.saws
package emr

import com.ambiata.com.amazonaws.services.elasticmapreduce.model.ActionOnFailure
import com.ambiata.saws.tooling._
import scalaz._, Scalaz._

object DistCopy {
  def run(clusterId: String, args: List[String]) = for {
    _       <- EMR.describeCluster(clusterId)
    stepIds <- EMR.addSteps(clusterId, List(step(args)))
    _       <- Wait.waitFor(
                 EMR.describeStep(clusterId, stepIds.head).map(s => List("COMPLETED", "CANCELLED", "FAILED", "INTERRUPTED").exists(s.getState === _)),
                    s"Running S3DistCp as step '${stepIds.head}'".some,
                    15000)
    status  <- EMR.describeStep(clusterId, stepIds.head)
  } yield status

  def step(args: List[String]): Step = Step(
    "weiv-distcp",
    ActionOnFailure.CONTINUE,
    "s3://ambiata-dist/hadoop/lib/emr-s3distcp-1.0.jar",
    "",
    args,
    Map.empty
  )
}
