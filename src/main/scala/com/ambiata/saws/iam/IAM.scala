package com.ambiata.saws
package iam

import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient


/** Sydney-region IAM client. */
object IAM {
  val IamEndpoint = "https://iam.amazonaws.com"

  def apply(): AmazonIdentityManagementClient = {
    val c = new AmazonIdentityManagementClient()
    c.setEndpoint(IamEndpoint)
    c
  }
}