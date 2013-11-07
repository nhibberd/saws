package com.ambiata.saws
package core

import scalaz._, Scalaz._

case class AwsAction[A, +B](run: A => (Vector[AwsLog], AwsAttempt[B])) {
  def map[C](f: B => C): AwsAction[A, C] =
    flatMap[C](f andThen AwsAction.ok)

  def flatMap[C](f: B => AwsAction[A, C]): AwsAction[A, C] =
    AwsAction[A, C](a => run(a) match {
      case (log, AwsAttempt(\/-(b))) =>
        f(b).run(a) match {
          case (log2, result) => (log ++ log2, result)
        }
      case (log, AwsAttempt(-\/(e))) =>
        (log, AwsAttempt(-\/(e)))
    }).safe

  def attempt[C](f: B => AwsAttempt[C]): AwsAction[A, C] =
    flatMap(f andThen AwsAction.attempt)

  def safe: AwsAction[A, B] =
    AwsAction(a => AwsAttempt.safe(run(a)) match {
      case AwsAttempt(-\/(err)) => (Vector(), AwsAttempt(-\/(err)))
      case AwsAttempt(\/-(ok))   => ok
    })
}

object AwsAction {
  def config[A]: AwsAction[A, A] =
    AwsAction(a => (Vector(), AwsAttempt.ok(a)))

  def ok[A, B](strict: B): AwsAction[A, B] =
    value(strict)

  def attempt[A, B](value: AwsAttempt[B]): AwsAction[A, B] =
    AwsAction(_ => (Vector(), value))

  def value[A, B](value: => B): AwsAction[A, B] =
    AwsAction(_ => (Vector(), AwsAttempt.ok(value)))

  def exception[A, B](t: Throwable): AwsAction[A, B] =
    AwsAction(_ => (Vector(), AwsAttempt.exception(t)))

  def fail[A, B](message: String): AwsAction[A, B] =
    AwsAction(_ => (Vector(), AwsAttempt.fail(message)))

  def error[A, B](message: String, t: Throwable): AwsAction[A, B] =
    AwsAction(_ => (Vector(), AwsAttempt.error(message, t)))

  def withClient[A, B](f: A => B): AwsAction[A, B] =
    config[A].map(f)

  def attemptWithClient[A, B](f: A => AwsAttempt[B]): AwsAction[A, B] =
    config[A].attempt(f)

  def log[A](message: AwsLog): AwsAction[A, Unit] =
    AwsAction(_ => (Vector(message), AwsAttempt.ok(())))

  implicit def AwsActionMonad[A]: Monad[({ type l[a] = AwsAction[A, a] })#l] =
    new Monad[({ type L[a] = AwsAction[A, a] })#L] {
      def point[B](v: => B) = AwsAction.ok(v)
      def bind[B, C](m: AwsAction[A, B])(f: B => AwsAction[A, C]) = m.flatMap(f)
    }
}
