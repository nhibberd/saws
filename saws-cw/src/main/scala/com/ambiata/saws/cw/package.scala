package com.ambiata.saws

import com.ambiata.mundane.reflect.MacrosCompat

package object cw extends MacrosCompat {

  /** the qualification of NamespaceSyntax seems to be necessary here */
  implicit def stringToNamespaceSyntax(s: String): com.ambiata.saws.cw.NamespaceSyntax =
    macro createNamespaceSyntax

  def createNamespaceSyntax(c: Context)(s: c.Expr[String]): c.Expr[com.ambiata.saws.cw.NamespaceSyntax] = {
    import c.universe._
    s match {
      case Expr(Literal(Constant(v: String))) => c.Expr(q"new NamespaceSyntax(${createNamespaceFromString(c)(v)})")
      case _ => c.abort(c.enclosingPosition, s"Not a literal ${showRaw(s)}")
    }
  }

  implicit class NamespaceSyntax(ns: Namespace) {
    def /(other: Namespace): Namespace =
      ns append other
  }

  implicit def ToNamespace(s: String): Namespace =
    macro create

  def create(c: Context)(s: c.Expr[String]): c.Expr[Namespace] = {
    import c.universe._
    s match {
      case Expr(Literal(Constant(v: String))) => createNamespaceFromString(c)(v)
      case _ => c.abort(c.enclosingPosition, s"Not a literal ${showRaw(s)}")
    }
  }

  private def createNamespaceFromString(c: Context)(s: String): c.Expr[Namespace] = {
    import c.universe._
    Namespace.fromString(s).fold(
      e  => c.abort(c.enclosingPosition, CloudWatchError.render(e)),
      ns => c.Expr(q"Namespace.unsafe(${ns.name})")
    )
  }

}
