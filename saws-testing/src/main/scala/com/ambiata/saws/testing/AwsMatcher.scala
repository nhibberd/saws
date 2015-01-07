package com.ambiata.saws.testing

import com.ambiata.mundane.control._
import com.ambiata.mundane.testing.RIOMatcher._
import com.ambiata.saws.core._
import org.specs2._, matcher._, execute.{Result => SpecsResult, Error => SpecsError, _}, execute.Result.ResultMonoid
import org.scalacheck.Prop

import scalaz.{Success => _, Failure => _, _}, Scalaz._, effect.IO

object AwsMatcher extends ThrownExpectations with ScalaCheckMatchers {
  implicit def AwsAsResult[R: Client, A: AsResult]: AsResult[Aws[R, A]] = new AsResult[Aws[R, A]] {
    def asResult(t: => Aws[R, A]): SpecsResult =
      implicitly[AsResult[RIO[A]]].asResult(t.eval)
  }

  implicit def AwsProp[R: Client, A: AsResult](r: => Aws[R, A]): Prop =
    resultProp(AsResult(r))

  /*  *** automatic value matchers *** */

  def beOk[R, A](implicit client: Client[R]): Matcher[Aws[R, A]] =
    runOk[R, A](_.eval)

  def beOkValue[R, A](expected: A)(implicit client: Client[R]): Matcher[Aws[R, A]] =
    runOkValue[R, A](_.eval)(expected)

  def beOkValueWithLog[R, A](expected: A)(implicit client: Client[R]): Matcher[Aws[R, A]] =
    runOkValue[R, A](_.eval)(expected)

  def beOkLike[R, A](check: A => SpecsResult)(implicit client: Client[R]): Matcher[Aws[R, A]] =
    runOkLike[R, A](_.eval)(check)

  def beResultLike[R, A](check: Result[A] => SpecsResult)(implicit client: Client[R]): Matcher[Aws[R, A]] =
    runResultLike[R, A](_.eval)(check)

  /*  *** manual value matchers *** */

  def runOk[R, A](f: Aws[R, A] => RIO[A]): Matcher[Aws[R, A]] =
    runOkLike[R, A](f)(_ => Success())

  def runOkValue[R, A](f: Aws[R, A] => RIO[A])(expected: A): Matcher[Aws[R, A]] =
    runOkLike[R, A](f)((actual: A) => new BeEqualTo(expected).apply(createExpectable(actual)).toResult)

  def runOkLike[R, A](f: Aws[R, A] => RIO[A])(check: A => SpecsResult): Matcher[Aws[R, A]] =
    runResultLike[R, A](f)({
      case Ok(actual)     => check(actual)
      case Error(error)   => Failure(s"Result failed with <${Result.asString(error)}>")
    })

  def runResultLike[R, A](f: Aws[R, A] => RIO[A])(check: Result[A] => SpecsResult): Matcher[Aws[R, A]] = new Matcher[Aws[R, A]] {
    def apply[S <: Aws[R, A]](attempt: Expectable[S]) = {
      val r = check(f(attempt.value).unsafePerformIO)
      result(r.isSuccess, r.message, r.message, attempt)
    }
  }

  /*  *** automatic value & log  matchers *** */
  def beLogOkValue[R, A](expectedValue: A, expectedLog: Vector[AwsLog])(implicit client: Client[R]): Matcher[Aws[R, A]] =
    runLogOkValue[R, A](_.evalWithLog)(expectedValue, expectedLog)

  def beLogOkLike[R, A](check: (Vector[AwsLog], A) => SpecsResult)(implicit client: Client[R]): Matcher[Aws[R, A]] =
    runLogOkLike[R, A](_.evalWithLog)(check)

  def beLogResultLike[R, A](check: Result[(Vector[AwsLog], A)] => SpecsResult)(implicit client: Client[R]): Matcher[Aws[R, A]] =
    runLogResultLike[R, A](_.evalWithLog)(check)

  /*  *** manual value & log  matchers *** */
  def runLogOkValue[R, A](f: Aws[R, A] => RIO[(Log, A)])(expectedValue: A, expectedLog: Vector[AwsLog]): Matcher[Aws[R, A]] =
    runLogOkLike[R, A](f)((actualLog: Vector[AwsLog], actualValue: A) =>
      new BeEqualTo(expectedValue).apply(createExpectable(actualValue)).toResult |+|
        new BeEqualTo(expectedLog).apply(createExpectable(actualLog)).toResult)

  def runLogOkLike[R, A](f: Aws[R, A] => RIO[(Log, A)])(check: (Vector[AwsLog], A) => SpecsResult): Matcher[Aws[R, A]] =
    runLogResultLike[R, A](f)({
      case Ok((log, actual)) =>
        check(log, actual)
      case Error(error) =>
        Failure(s"""Result failed with <${Result.asString(error)}>""")
    })

  def runLogResultLike[R, A](f: Aws[R, A] => RIO[(Log, A)])(check: Result[(Vector[AwsLog], A)] => SpecsResult): Matcher[Aws[R, A]] = new Matcher[Aws[R, A]] {
    def apply[S <: Aws[R, A]](attempt: Expectable[S]) = {
      val z: Result[(Log, A)] = f(attempt.value).unsafePerformIO
      val r = check(z)
      result(r.isSuccess, r.message, r.message, attempt)
    }
  }
}
