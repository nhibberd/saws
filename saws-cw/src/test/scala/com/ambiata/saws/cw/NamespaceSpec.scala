package com.ambiata.saws.cw

import org.specs2._
import Arbitraries._

class NamespaceSpec extends Specification with ScalaCheck { def is = s2"""

 namespace literals must compile if they are correct $literals
 append namespaces  $append

"""

  def literals = {
    val ns: Namespace = "namespace"
    "namespace1" / "namespace2"
    ok
  }

  def append = prop { (ns1: Namespace, ns2: Namespace) =>
    (ns1 append ns2).name === (ns1.name + "/" + ns2.name)
  }
}
