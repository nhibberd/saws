package com.ambiata.saws
package core

import scalaz._, Scalaz._

case class AwsAction[-A, +B](toReaderT: ReaderT[AwsAttempt, A, B]) {
  def run(a: A): AwsAttempt[B] =
    toReaderT.run(a)

  def runOrElse[BB >: B](a: A, otherwise: => BB): BB =
    run(a).getOrElse(otherwise)
}

object AwsAction {
  def safe[A, B](f: A => B): AwsAction[A, B] =
    AwsAction(Kleisli(a => AwsAttempt.safe(f(a))))

  def attempt[A, B](value: => B): AwsAction[A, B] =
    AwsAction(Kleisli(_ => AwsAttempt.safe(value)))

  def ok[A, B](value: B): AwsAction[A, B] =
    AwsAction(Kleisli(_ => AwsAttempt.ok(value)))

  def exception[A, B](t: Throwable): AwsAction[A, B] =
    AwsAction(Kleisli(_ => AwsAttempt.exception(t)))

  def fail[A, B](message: String): AwsAction[A, B] =
    AwsAction(Kleisli(_ => AwsAttempt.fail(message)))

  def error[A, B](message: String, t: Throwable): AwsAction[A, B] =
    AwsAction(Kleisli(_ => AwsAttempt.error(message, t)))
}

trait AwsActionTemplate[A] {
  def safe[B](f: A => B): AwsAction[A, B] =
    AwsAction.safe(f)

  def attempt[B](value: => B): AwsAction[A, B] =
    AwsAction.attempt[A, B](value)

  def ok[B](value: B): AwsAction[A, B] =
    AwsAction.ok[A, B](value)

  def exception[B](t: Throwable): AwsAction[A, B] =
    AwsAction.exception[A, B](t)

  def fail[B](message: String): AwsAction[A, B] =
    AwsAction.fail[A, B](message)

  def error[B](message: String, t: Throwable): AwsAction[A, B] =
    AwsAction.error[A, B](message, t)
}
