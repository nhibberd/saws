package com.ambiata.saws
package testing

import org.scalacheck.{Arbitrary, Gen, Prop, Properties}, Prop.forAll
import scalaz._, Scalaz._

/**
 * WARNING: can't use scalaz-scalacheck-binding because of specs/scalacheck/scalaz compatibility at the moment
 *
 * This is code from scalaz that has been introduced just so we can test laws. Remove when
 * compatability issues sorted upstream.
 */
object Laws {
  object equal {
    def commutativity[A](implicit A: Equal[A], arb: Arbitrary[A]) = forAll(A.equalLaw.commutative _)

    def reflexive[A](implicit A: Equal[A], arb: Arbitrary[A]) = forAll(A.equalLaw.reflexive _)

    def transitive[A](implicit A: Equal[A], arb: Arbitrary[A]) = forAll(A.equalLaw.transitive _)

    def naturality[A](implicit A: Equal[A], arb: Arbitrary[A]) = forAll(A.equalLaw.naturality _)

    def laws[A](implicit A: Equal[A], arb: Arbitrary[A]) = new Properties("equal") {
      property("commutativity") = commutativity[A]
      property("reflexive") = reflexive[A]
      property("transitive") = transitive[A]
      property("naturality") = naturality[A]
    }
  }

  object applicative {
    def identity[F[_], X](implicit f: Applicative[F], afx: Arbitrary[F[X]], ef: Equal[F[X]]) =
      forAll(f.applicativeLaw.identityAp[X] _)

    def composition[F[_], X, Y, Z](implicit ap: Applicative[F], afx: Arbitrary[F[X]], au: Arbitrary[F[Y => Z]],
                                   av: Arbitrary[F[X => Y]], e: Equal[F[Z]]) = forAll(ap.applicativeLaw.composition[X, Y, Z] _)

    def homomorphism[F[_], X, Y](implicit ap: Applicative[F], ax: Arbitrary[X], af: Arbitrary[X => Y], e: Equal[F[Y]]) =
      forAll(ap.applicativeLaw.homomorphism[X, Y] _)

    def interchange[F[_], X, Y](implicit ap: Applicative[F], ax: Arbitrary[X], afx: Arbitrary[F[X => Y]], e: Equal[F[Y]]) =
      forAll(ap.applicativeLaw.interchange[X, Y] _)

    def mapApConsistency[F[_], X, Y](implicit ap: Applicative[F], ax: Arbitrary[F[X]], afx: Arbitrary[X => Y], e: Equal[F[Y]]) =
      forAll(ap.applicativeLaw.mapLikeDerived[X, Y] _)

    def laws[F[_]](implicit F: Applicative[F], af: Arbitrary[F[Int]],
                   aff: Arbitrary[F[Int => Int]], e: Equal[F[Int]]) = new Properties("applicative") {
      include(functor.laws[F])
      property("identity") = applicative.identity[F, Int]
      property("composition") = applicative.composition[F, Int, Int, Int]
      property("homomorphism") = applicative.homomorphism[F, Int, Int]
      property("interchange") = applicative.interchange[F, Int, Int]
      property("map consistent with ap") = applicative.mapApConsistency[F, Int, Int]
    }
  }

  object functor {
    def identity[F[_], X](implicit F: Functor[F], afx: Arbitrary[F[X]], ef: Equal[F[X]]) =
      forAll(F.functorLaw.identity[X] _)

    def composite[F[_], X, Y, Z](implicit F: Functor[F], af: Arbitrary[F[X]], axy: Arbitrary[(X => Y)],
                                   ayz: Arbitrary[(Y => Z)], ef: Equal[F[Z]]) =
      forAll(F.functorLaw.composite[X, Y, Z] _)

    def laws[F[_]](implicit F: Functor[F], af: Arbitrary[F[Int]], axy: Arbitrary[(Int => Int)],
                   ef: Equal[F[Int]]) = new Properties("functor") {
      property("identity") = identity[F, Int]
      property("composite") = composite[F, Int, Int, Int]
    }
  }

  object monad {
    def rightIdentity[M[_], X](implicit M: Monad[M], e: Equal[M[X]], a: Arbitrary[M[X]]) =
      forAll(M.monadLaw.rightIdentity[X] _)

    def leftIdentity[M[_], X, Y](implicit am: Monad[M], emy: Equal[M[Y]], ax: Arbitrary[X], af: Arbitrary[(X => M[Y])]) =
      forAll(am.monadLaw.leftIdentity[X, Y] _)

    def associativity[M[_], X, Y, Z](implicit M: Monad[M], amx: Arbitrary[M[X]], af: Arbitrary[(X => M[Y])],
                                     ag: Arbitrary[(Y => M[Z])], emz: Equal[M[Z]]) =
      forAll(M.monadLaw.associativeBind[X, Y, Z] _)

    def bindApConsistency[M[_], X, Y](implicit M: Monad[M], amx: Arbitrary[M[X]],
                                      af: Arbitrary[M[X => Y]], emy: Equal[M[Y]]) =
      forAll(M.monadLaw.apLikeDerived[X, Y] _)

    def laws[M[_]](implicit a: Monad[M], am: Arbitrary[M[Int]],
                   af: Arbitrary[Int => M[Int]], ag: Arbitrary[M[Int => Int]], e: Equal[M[Int]]) = new Properties("monad") {
      include(applicative.laws[M])

      property("right identity") = monad.rightIdentity[M, Int]
      property("left identity") = monad.leftIdentity[M, Int, Int]
      property("associativity") = monad.associativity[M, Int, Int, Int]
      property("ap consistent with bind") = monad.bindApConsistency[M, Int, Int]

    }
  }
}
