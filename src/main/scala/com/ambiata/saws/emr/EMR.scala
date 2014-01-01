package com.ambiata.saws
package emr

import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient
import com.amazonaws.services.elasticmapreduce.model._
import com.ambiata.saws.core._
import com.ambiata.saws.core.EMRAction

import scala.collection.JavaConverters._


sealed trait Market
case object OnDemand extends Market
case class Spot(bidPrice: String) extends Market


case class Instance(ec2Type: String, marketType: Market){
  def asInstanceGroupConfig(roleType: String, count: Int) = {
    val config = new InstanceGroupConfig(roleType, ec2Type, count)
    marketType match {
      case OnDemand       => config.withMarket(MarketType.ON_DEMAND)
      case Spot(bidPrice) => config.withMarket(MarketType.SPOT).withBidPrice(bidPrice)
    }
  }
}


case class BootstrapAction(name: String, path: String, args: List[String]) {
  def asBootstrapActionConfig: BootstrapActionConfig =
    new BootstrapActionConfig(name, new ScriptBootstrapActionConfig(path, args.asJava))
}


case class Step(
  name: String,
  actionOnFailure: ActionOnFailure,
  jar: String,
  mainClass: String,
  args: List[String],
  properties: Map[String, String]
) {
  def asStepConfig: StepConfig =
    new StepConfig(name,
                   new HadoopJarStepConfig()
                      .withJar(jar)
                      .withMainClass(mainClass)
                      .withArgs(args.asJava)
                      .withProperties(properties.map{ case (k, v) => new KeyValue(k, v)}.toList.asJava))
      .withActionOnFailure(actionOnFailure)
}


case class Cluster(
  name: String,
  logUri: String,
  amiVersion: String,
  keypair: String,
  role: Option[String],
  masterInstance: Instance,
  coreInstances: (Instance, Int),
  taskInstances: Option[(Instance, Int)],
  bootstrapActions: List[BootstrapAction],
  steps: List[Step],
  interactive: Boolean,
  terminationProtected: Boolean
)
{

  def asJobFlowRequest: RunJobFlowRequest = {
    val instanceGroups = List(
      masterInstance.asInstanceGroupConfig("MASTER", 1),
      coreInstances._1.asInstanceGroupConfig("CORE", coreInstances._2)
    ) ++
    taskInstances.map(ti => ti._1.asInstanceGroupConfig("TASK", ti._2)).toList


    val instances = new JobFlowInstancesConfig()
      .withEc2KeyName(keypair)
      .withInstanceGroups(instanceGroups.asJava)
      .withTerminationProtected(terminationProtected)
      .withKeepJobFlowAliveWhenNoSteps(interactive)

    val jobFlowReq = new RunJobFlowRequest()
      .withName(name)
      .withLogUri(logUri)
      .withAmiVersion(amiVersion)
      .withInstances(instances)
      .withBootstrapActions(bootstrapActions.map(_.asBootstrapActionConfig).asJava)
      .withSteps(steps.map(_.asStepConfig).asJava)
      .withJobFlowRole(role.orNull)
      .withVisibleToAllUsers(true)

    jobFlowReq
  }
}


object EMR {

  def launchCluster(cluster: Cluster): EMRAction[String] = {
    EMRAction { client =>
      val res = client.runJobFlow(cluster.asJobFlowRequest)
      res.getJobFlowId
    }
  }
}