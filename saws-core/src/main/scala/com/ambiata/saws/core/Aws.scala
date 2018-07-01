package com.ambiata.saws
package core

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient
import scalaz._, Scalaz._, \&/._
import scalaz.concurrent.Task
import scalaz.effect._
import com.ambiata.mundane.control._

case class Aws[R, A](run: R => RIO[(Vector[AwsLog], A)]) {
  def contramap[B](f: B => R): Aws[B, A] =
    Aws(b => run(f(b)))

  def map[B](f: A => B): Aws[R, B] =
    Aws(r => run(r).map(_.map(f)))

  def flatMap[B](f: A => Aws[R, B]): Aws[R, B] =
    Aws(r =>
      run(r).flatMap({
        case (log, a) =>
          f(a).run(r).map({ case (bl, b) => log ++ bl -> b })
      })
    )

  def flatMapError(f: These[String, Throwable] => Aws[R, A]): Aws[R, A] =
    Aws(r => run(r).flatMapError(t => f(t).run(r)))

  def onResult[B](f: Result[A] => Result[B]): Aws[R, B] =
    Aws(r => run(r).on({
      case Ok((l, v)) =>
        RIO.result(f(Result.ok(v))).map(l -> _)
      case Error(e) =>
        RIO.result(f(Result.these(e))).map(Vector.empty -> _)
    }))

  def mapError(f: These[String, Throwable] => These[String, Throwable]): Aws[R, A] =
    Aws(run(_).mapError(f))

  def execute(r: R): RIO[A] =
    run(r).map(_._2)

  def eval(implicit client: Client[R]): RIO[A] =
    execute(client.get())

  def evalWithLog(implicit client: Client[R]): RIO[(Log, A)] =
    run(client.get())

  def liftAws[RR](implicit select: Select[RR, R]): Aws[RR, A] =
    contramap(Select[RR, R].contra)

  def |||(otherwise: => Aws[R, A]): Aws[R, A] =
    Aws(r => run(r) ||| otherwise.run(r))

  def orElse(otherwise: => Aws[R, A]): Aws[R, A] =
    |||(otherwise)

  def retry(i: Int, lf: (Int, These[String, Throwable]) => Aws[R, Unit]): Aws[R, A] =
    Aws[R, A](r => run(r).onError(e =>
      if (i > 0)
        lf(i, e).run(r).map(_._1) >>= (l => retry(i - 1, lf).run(r).map({ case (ll, r) => (l ++ ll) -> r }))
      else RIO.these(e)))

  def debug(tag: String): Aws[R, A] =
    Aws(r => run(r).on(r => RIO.result(r.map({ case(l, a) => println(s"Log ($tag): $l"); (l, a) }))))
}


object Aws {
  def withClient[R, A](f: R => A): Aws[R, A] =
    Aws(r => RIO.safe[(Vector[AwsLog], A)](Vector.empty -> f(r)))

  def option[R, A](a: R => A): Aws[R, Option[A]] =
    withClient[R, A](a).map(Option.apply)

  def result[R, A](f: R => Result[A]): Aws[R, A] =
    Aws(r => RIO.result[(Vector[AwsLog], A)](f(r).map(Vector.empty -> _)))

  def io[R, A](f: R => IO[A]): Aws[R, A] =
    Aws(r => RIO.fromIO(f(r)).map(Vector.empty -> _))

  def unit[R]: Aws[R, Unit] =
    ok(())

  def resultT[R, A](f: R => ResultT[IO, A]): Aws[R, A] =
    Aws(r => RIO.resultT(f(r)).map(Vector.empty -> _))

  def rio[R, A](f: R => RIO[A]): Aws[R, A] =
    Aws(r => f(r).map(Vector.empty -> _))

  def safe[R, A](a: => A): Aws[R, A] =
    Aws(_ => RIO.safe((Vector.empty, a)))

  def ok[R, A](a: => A): Aws[R, A] =
    Aws(_ => RIO.ok((Vector.empty, a)))

  def exception[R, A](t: Throwable): Aws[R, A] =
    Aws(_ => RIO.exception(t))

  def fail[R, A](message: String): Aws[R, A] =
    Aws(_ => RIO.fail(message))

  def error[R, A](message: String, t: Throwable): Aws[R, A] =
    Aws(_ => RIO.error(message, t))

  def these[R, A](both: These[String, Throwable]): Aws[R, A] =
    Aws(_ => RIO.these(both))

  def when[R](v: Boolean, thunk: => Aws[R, Unit]): Aws[R, Unit] =
    if (v) thunk else unit

  def unless[R](v: Boolean, thunk: => Aws[R, Unit]): Aws[R, Unit] =
    when(!v, thunk)

  def fromDisjunction[R, A](either: These[String, Throwable] \/ A): Aws[R, A] =
    Aws(_ => RIO.fromDisjunction(either).map(Vector.empty -> _))

  def fromDisjunctionString[R, A](either: String \/ A): Aws[R, A] =
    Aws(_ => RIO.fromDisjunctionString(either).map(Vector.empty -> _))

  def fromDisjunctionThrowable[R, A](either: Throwable \/ A): Aws[R, A] =
    Aws(_ => RIO.fromDisjunctionThrowable(either).map(Vector.empty -> _))

  def fromIO[R, A](v: IO[A]): Aws[R, A] =
    io(_ => v)

  def using[A: Resource, B <: A, R, C](a: Aws[R, B])(run: B => Aws[R, C]): Aws[R, C] =
    Aws(c => for {
      ra      <- a.run(c)
      (l1, b) = ra
      r       = implicitly[Resource[A]]
      rb      <- run(b).run(c).onError(e => RIO.fromIO(r.close(b)) >> RIO.these(e))
      (l2, c) = rb
      _       <- RIO.fromIO(r.close(b))
    } yield (l1 |+| l2, c)
    )

  def fromIOResult[R, A](v: IO[Result[A]]): Aws[R, A] =
    fromResultT(ResultT(v))

  def fromResultT[R, A](v: ResultT[IO, A]): Aws[R, A] =
    resultT(_ => v)

  def fromRIO[R, A](v: RIO[A]): Aws[R, A] =
    Aws(_ => v.map(Vector.empty -> _))

  def log[R](l: AwsLog): Aws[R, Unit] =
    Aws(r => RIO.ok((Vector(l), ())))

  implicit def AwsMonad[R]: MonadIO[({ type l[a] = Aws[R, a] })#l] =
    new MonadIO[({ type l[a] = Aws[R, a] })#l] {
      def liftIO[A](a: IO[A]) = Aws.io(_ => a)
      def point[A](a: => A) = ok(a)
      def bind[A, B](a: Aws[R, A])(f: A => Aws[R, B]) = a flatMap f
    }

  def addFinalizer[R](finalizer: Aws[R, Unit]): Aws[R, Unit] =
    Aws(r => RIO.addFinalizer(Finalizer(finalizer.run(r).void)).map(Vector.empty -> _))

  def putStrLn[R](msg: String): Aws[R, Unit] =
    Aws(_ => RIO.putStrLn(msg).map(Vector.empty -> _))
}

trait AwsSupport[R] {
  def client: Aws[R, R] =
    apply(identity)

  def apply[A](f: R => A): Aws[R, A] =
    Aws.withClient[R, A](f)

  def option[A](f: R => A): Aws[R, Option[A]] =
    Aws.option(f)

  def result[A](f: R => Result[A]): Aws[R, A] =
    Aws.result[R, A](f)

  def io[A](f: R => IO[A]): Aws[R, A] =
    Aws.io[R, A](f)

  def resultT[A](f: R => ResultT[IO, A]): Aws[R, A] =
    Aws.resultT[R, A](f)

  def rio[A](f: R => RIO[A]): Aws[R, A] =
    Aws.rio[R, A](f)

  def safe[A](a: => A): Aws[R, A] =
    Aws.safe[R, A](a)

  def ok[A](a: => A): Aws[R, A] =
    Aws.ok[R, A](a)

  def exception[A](t: Throwable): Aws[R, A] =
    Aws.exception[R, A](t)

  def fail[A](message: String): Aws[R, A] =
    Aws.fail[R, A](message)

  def error[A](message: String, t: Throwable): Aws[R, A] =
    Aws.error[R, A](message, t)

  def these[A](both: These[String, Throwable]): Aws[R, A] =
    Aws.these[R, A](both)

  def when(v: Boolean, thunk: => Aws[R, Unit]): Aws[R, Unit] =
    Aws.when[R](v, thunk)

  def unless(v: Boolean, thunk: => Aws[R, Unit]): Aws[R, Unit] =
    Aws.unless[R](v, thunk)

  def fromIO[A](v: IO[A]): Aws[R, A] =
    Aws.fromIO[R, A](v)

  def fromIOResult[A](v: IO[Result[A]]): Aws[R, A] =
    Aws.fromIOResult[R, A](v)

  def fromResultT[A](v: ResultT[IO, A]): Aws[R, A] =
    Aws.fromResultT[R, A](v)

  def fromRIO[A](v: RIO[A]): Aws[R, A] =
    Aws.fromRIO[R, A](v)

  def fromTask[A](task: Task[A]): Aws[R, A] =
    task.attemptRun.fold(exception, a => ok(a))

  def addFinalizer(finalizer: Aws[R, Unit]): Aws[R, Unit] =
    Aws.addFinalizer(finalizer)

  def unit: Aws[R, Unit] =
    Aws.unit

  def putStrLn(msg: String): Aws[R, Unit] =
    Aws.putStrLn(msg)

  def using[A: Resource, B <: A, C](a: Aws[R, B])(run: B => Aws[R, C]): Aws[R, C] =
    Aws.using[A, B, R, C](a)(run)
}

object EC2Action extends AwsSupport[AmazonEC2Client]
object S3Action extends AwsSupport[AmazonS3Client]
object IAMAction extends AwsSupport[AmazonIdentityManagementClient]
object EMRAction extends AwsSupport[AmazonElasticMapReduceClient]
object CloudWatchAction extends AwsSupport[AmazonCloudWatchClient]
