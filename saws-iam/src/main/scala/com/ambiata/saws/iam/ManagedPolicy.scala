package com.ambiata.saws
package iam

//trait ManagedPolicy extends Policy {
//}

/**
  * Represents a policy that is managed either by AWS (predfinined) or customer created (shared)
  *
  * @param name
  */
case class AwsManagedPolicy(val name: String) extends Policy {
//  override def document: String = {
//    // TODO this could lookup the static content by calling the equivalent of:
//    // aws iam get-policy-version --version-id v1 --policy-arn arn
//    null
//  }

  def arn: String = s"arn:aws:iam::aws:policy/service-role/$name"
}

//object AwsManagedPolicy {
//
//}

/**
  * Represents a policy that is managed either by AWS (predfinined) or customer created (shared)
  *
  * @param name
  * @param document
  * @todo NOT IMPLEMENTED YET
  */
case class CustomerManagedPolicy(val name: String, val document: String) extends Policy

//object CustomerManagedPolicy {
//
//}
