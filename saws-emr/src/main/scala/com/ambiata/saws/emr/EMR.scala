package com.ambiata.saws
package emr

import com.ambiata.com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient
import com.ambiata.com.amazonaws.services.elasticmapreduce.model.{Cluster => EMRCluster, Instance => EMRInstance, _}
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
  terminationProtected: Boolean,
  tags: List[(String, String)]
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
      .withTags(tags.map({ case (k, v) => new Tag(k, v) }).asJava)

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

  def describeCluster(clusterId: String): EMRAction[EMRCluster] =
    EMRAction(client => client.describeCluster(new DescribeClusterRequest().withClusterId(clusterId)).getCluster)

  def clusterInstances(clusterId: String, groups: String*): EMRAction[List[EMRInstance]] =
    EMRAction(client =>
      client.listInstances(new ListInstancesRequest().withClusterId(clusterId).withInstanceGroupTypes(groups: _*)).getInstances.asScala.toList)

  def terminateCluster(clusterIds: String*): EMRAction[Unit] =
    EMRAction(client =>
      client.terminateJobFlows(new TerminateJobFlowsRequest(clusterIds.asJava)))

  /** Add steps to an existing cluster, returning the step IDs. */
  def addSteps(clusterId: String, steps: List[Step]): EMRAction[List[String]] = {
    EMRAction(client => {
      val result = client.addJobFlowSteps(new AddJobFlowStepsRequest(clusterId, steps.map(_.asStepConfig).asJava))
      result.getStepIds.asScala.toList
    })
  }

  def describeStep(clusterId: String, stepId: String): EMRAction[StepStatus] = {
    EMRAction(client => {
      val result = client.describeStep(new DescribeStepRequest().withClusterId(clusterId).withStepId(stepId))
      result.getStep.getStatus
    })
  }
}
