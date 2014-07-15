package com.ambiata.saws
package iam

import com.ambiata.saws.core._
import com.amazonaws.services.identitymanagement.model.{User => AwsUser, _}
import com.amazonaws.auth.{BasicAWSCredentials, AWSCredentials}

import scala.collection.JavaConverters._
import scalaz._, Scalaz._


/** An IAM user. */
case class User(name: String, policies: List[Policy])


object IAMUsers {

  /* Lifecycle */

  /** Create an IAM user add add its policies. */
  def create(user: User): IAMAction[Unit] =
    createUser(user.name) >> updateUserPolicies(user.name, user.policies)

  /** Create an IAM user if it doesn't already exist. */
  def createUser(userName: String): IAMAction[AwsUser] = for {
    user <- findUser(userName)
    u    <- user.map(_.pure[IAMAction]).getOrElse(IAMAction(_.createUser(new CreateUserRequest(userName)).getUser))
  } yield u

  /** Find a specific IAM user. */
  def findUser(userName: String): IAMAction[Option[AwsUser]] =
    IAMAction(_.listUsers.getUsers.asScala.find(_.getUserName == userName))

  /** Check if a specific IAM user exists. */
  def userExists(userName: String): IAMAction[Boolean] =
    findUser(userName).map(_.isDefined)

  /** Delete a given IAM user if it exists. */
  def deleteUser(userName: String): IAMAction[Unit] = for {
    exists <- userExists(userName)
    _      <- if (exists) IAMAction(_.deleteUser(new DeleteUserRequest(userName)))
              else        IAMAction.ok(())
  } yield ()



  /* Policies */

  /** Remove all policies from a given IAM user then add a set of new policies. */
  def updateUserPolicies(userName: String, policies: List[Policy]): IAMAction[Unit] =
    clearUserPolicies(userName) >> addUserPolicies(userName, policies)

  /** Remove all policies for a given IAM user. */
  def clearUserPolicies(userName: String): IAMAction[Unit] = {
    for {
      policies <- IAMAction(_.listUserPolicies(new ListUserPoliciesRequest(userName)).getPolicyNames.asScala.toList)
      _        <- policies.traverseU(p => IAMAction(_.deleteUserPolicy(new DeleteUserPolicyRequest(userName, p))))
    } yield ()
  }

  /** Add multiple policies to a given IAM user. */
  def addUserPolicies(userName: String, policies: List[Policy]): IAMAction[Unit] = for {
    _ <- policies.traverse(addUserPolicy(userName, _))
  } yield ()

  /** Add a single policy to a given IAM user. */
  def addUserPolicy(userName: String, policy: Policy): IAMAction[Unit] =
    IAMAction(_.putUserPolicy(new PutUserPolicyRequest(userName, policy.name, policy.document)))



  /* Access keys */

  /** Create an access and secret keys for an IAM user. */
  def createAccessKey(userName: String): IAMAction[AWSCredentials] = IAMAction(client => {
    val credentials = client.createAccessKey(new CreateAccessKeyRequest().withUserName(userName)).getAccessKey
    new BasicAWSCredentials(credentials.getAccessKeyId, credentials.getSecretAccessKey)
  })

  /** List the access keys for a specific IAM user. */
  def listAccessKeys(userName: String): IAMAction[List[String]] = IAMAction(client => {
    client.listAccessKeys(new ListAccessKeysRequest().withUserName(userName)).getAccessKeyMetadata.asScala.toList.map(_.getAccessKeyId)
  })

  /** Delete the access key associated with an IAM user. */
  def deleteAccessKey(userName: String, accessKey: String): IAMAction[Unit] =
    IAMAction(client => client.deleteAccessKey(new DeleteAccessKeyRequest(accessKey).withUserName(userName)))

  /** Delete all the access keys associated with an IAM user. */
  def deleteAllAccessKeys(userName: String): IAMAction[Unit] = for {
    credentials <- listAccessKeys(userName)
    _           <- credentials.traverse(deleteAccessKey(userName, _))
  } yield ()

}