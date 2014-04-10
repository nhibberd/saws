package com.ambiata.saws
package core

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient
import scalaz._, Scalaz._, \&/._
import scalaz.effect._
import com.ambiata.mundane.control._

case class Aws[R, +A](runT: ActionIO[Vector[AwsLog], R, A]) {
  def contramap[B](f: B => R): Aws[B, A] =
    Aws(ActionT(r => runT.runT(f(r))))

  def map[B](f: A => B): Aws[R, B] =
    Aws(runT.map(f))

  def flatMap[B](f: A => Aws[R, B]): Aws[R, B] =
    Aws(runT.flatMap(a => f(a).runT))

  def flatMapError[AA >: A](f: These[String, Throwable] => Aws[R, AA]): Aws[R, AA] =
    Aws(runT.flatMapError[AA](t => f(t).runT))

  def onResult[B](f: Result[A] => Result[B]): Aws[R, B] =
    Aws(runT.onResult(f))

  def mapError(f: These[String, Throwable] => These[String, Throwable]): Aws[R, A] =
    Aws(runT.mapError(f))

  def run(r: R): IO[(Vector[AwsLog], Result[A])] =
    runT.run(r)

  def execute(r: R): IO[Result[A]] =
    runT.execute(r)

  def executeT(r: R): ResultT[IO, A] =
    runT.executeT(r)

  def evalWithLog(implicit client: Client[R]): IO[(Log, Result[A])] =
    run(client.get())

  def eval(implicit client: Client[R]): IO[Result[A]] =
    execute(client.get())

  def evalT(implicit client: Client[R]): ResultT[IO, A] =
    executeT(client.get())

  def liftAws[RR](implicit select: Select[RR, R]): Aws[RR, A] =
    contramap(Select[RR, R].contra)

  def |||[AA >: A](otherwise: => Aws[R, AA]): Aws[R, AA] =
    Aws(runT ||| otherwise.runT)

  def orElse[AA >: A](otherwise: => AA): Aws[R, AA] =
    Aws(runT.orElse(otherwise))

  // FIX Retry and flush are a port from old code, they need to be tidied up and simplified.
  //     They exist as they are now, because I really don't understand what they are meant
  //     to do.

  def retry(i: Int, lf: (Int, These[String, Throwable]) => Vector[AwsLog] = (_,_) => Vector()): Aws[R, A] =
    Aws[R, A](ActionT(r => ResultT[({ type l[+a] = WriterT[IO, Vector[AwsLog], a] })#l, A](WriterT[IO, Vector[AwsLog], Result[A]](runT.runT(r).run.run.flatMap(aa => aa match {
      case (log, Ok(b))    => (log, Result.ok(b)).pure[IO]
      case (log, Error(e)) => if(i > 0) retry(i - 1, lf).runT.runT(r).run.run.map(rr => rr match {
        case (nlog, nattp) => (log ++ lf(i, e) ++ nlog, nattp)
      }) else (log ++ lf(i, e), Result.these(e)).pure[IO]
    })))))

  /** after running the action, print the last log message to the console */
  // FIX this looks unusual, should it print everything and take it out of writer?
  def flush: Aws[R, A] =
    Aws[R, A](ActionT(r => ResultT[({ type l[+a] = WriterT[IO, Vector[AwsLog], a] })#l, A](WriterT[IO, Vector[AwsLog], Result[A]](runT.runT(r).run.run.flatMap({
      case (log, a) =>
        IO { log.lastOption.foreach(println) }.as((log, a))
    })))))
}


object Aws {
  def withClient[R, A](f: R => A): Aws[R, A] =
    Aws(ActionT.reader(f))

  def option[R, A](a: R => A): Aws[R, Option[A]] =
    withClient[R, A](a).map(Option.apply)

  def result[R, A](f: R => Result[A]): Aws[R, A] =
    Aws(ActionT.result[IO, Vector[AwsLog], R, A](f))

  def io[R, A](f: R => IO[A]): Aws[R, A] =
    Aws(ActionT(r => ResultT[({ type l[+a] = WriterT[IO, Vector[AwsLog], a] })#l, A](WriterT[IO, Vector[AwsLog], Result[A]](f(r).map(a => (Vector[AwsLog](), Result.ok(a)))))))

  def resultT[R, A](f: R => ResultT[IO, A]): Aws[R, A] =
    Aws(ActionT.resultT(f))

  def safe[R, A](a: => A): Aws[R, A] =
    Aws(ActionT.safe[IO, Vector[AwsLog], R, A](a))

  def ok[R, A](a: => A): Aws[R, A] =
    Aws(ActionT.ok[IO, Vector[AwsLog], R, A](a))

  def exception[R, A](t: Throwable): Aws[R, A] =
    Aws(ActionT.exception[IO, Vector[AwsLog], R, A](t))

  def fail[R, A](message: String): Aws[R, A] =
    Aws(ActionT.fail[IO, Vector[AwsLog], R, A](message))

  def error[R, A](message: String, t: Throwable): Aws[R, A] =
    Aws(ActionT.error[IO, Vector[AwsLog], R, A](message, t))

  def these[R, A](both: These[String, Throwable]): Aws[R, A] =
    Aws(ActionT.these[IO, Vector[AwsLog], R, A](both))

  def fromDisjunction[R, A](either: These[String, Throwable] \/ A): Aws[R, A] =
    Aws(ActionT.fromDisjunction(either))

  def fromDisjunctionString[R, A](either: String \/ A): Aws[R, A] =
    Aws(ActionT.fromDisjunctionString(either))

  def fromDisjunctionThrowable[R, A](either: Throwable \/ A): Aws[R, A] =
    Aws(ActionT.fromDisjunctionThrowable(either))

  def fromIO[R, A](v: IO[A]): Aws[R, A] =
    io(_ => v)

  def fromIOResult[R, A](v: IO[Result[A]]): Aws[R, A] =
    fromResultT(ResultT(v))

  def fromResultT[R, A](v: ResultT[IO, A]): Aws[R, A] =
    resultT(_ => v)

  def log[R](l: AwsLog): Aws[R, Unit] =
    Aws(ActionT(r =>
      ResultT[({ type l[+a] = WriterT[IO, Vector[AwsLog], a] })#l, Unit](
        WriterT[IO, Vector[AwsLog], Result[Unit]](
          (Vector[AwsLog](l), Result.ok(())).pure[IO]))))

  def unlog[A, B](result: (A, B)): B =
    result._2

  implicit def AwsMonad[R]: MonadIO[({ type l[a] = Aws[R, a] })#l] =
    new MonadIO[({ type l[a] = Aws[R, a] })#l] {
      def liftIO[A](a: IO[A]) = Aws.io(_ => a)
      def point[A](a: => A) = ok(a)
      def bind[A, B](a: Aws[R, A])(f: A => Aws[R, B]) = a flatMap f
    }
}

trait AwsSupport[R] {
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

  def fromIO[A](v: IO[A]): Aws[R, A] =
    Aws.fromIO[R, A](v)

  def fromIOResult[A](v: IO[Result[A]]): Aws[R, A] =
    Aws.fromIOResult[R, A](v)

  def fromResultT[A](v: ResultT[IO, A]): Aws[R, A] =
    Aws.fromResultT[R, A](v)
}

object EC2Action extends AwsSupport[AmazonEC2Client]
object S3Action extends AwsSupport[AmazonS3Client]
object IAMAction extends AwsSupport[AmazonIdentityManagementClient]
object EMRAction extends AwsSupport[AmazonElasticMapReduceClient]
