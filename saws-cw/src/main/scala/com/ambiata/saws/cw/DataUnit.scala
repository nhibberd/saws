package com.ambiata.saws.cw

import scalaz._, Scalaz._

/**
 * @see the definition of data units on
 *      http://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_MetricDatum.html
 */
sealed trait DataUnit {
  def render: String
}
case object NoneDataUnit       extends DataUnit { def render = "None"               }
case object Count              extends DataUnit { def render = "Count"              }
case object CountPerSecond     extends DataUnit { def render = "Count/Second"       }
case object Percent            extends DataUnit { def render = "Percent"            }
case object Bytes              extends DataUnit { def render = "Bytes"              }
case object Kilobytes          extends DataUnit { def render = "Kilobytes"          }
case object Megabytes          extends DataUnit { def render = "Megabytes"          }
case object Gigabytes          extends DataUnit { def render = "Gigabytes"          }
case object Terabytes          extends DataUnit { def render = "Terabytes"          }
case object Bits               extends DataUnit { def render = "Bits"               }
case object Kilobits           extends DataUnit { def render = "Kilobits"           }
case object Megabits           extends DataUnit { def render = "Megabits"           }
case object Gigabits           extends DataUnit { def render = "Gigabits"           }
case object Terabits           extends DataUnit { def render = "Terabits"           }
case object Seconds            extends DataUnit { def render = "Seconds"            }
case object Microseconds       extends DataUnit { def render = "Microseconds"       }
case object Milliseconds       extends DataUnit { def render = "Milliseconds"       }
case object BytesPerSecond     extends DataUnit { def render = "Bytes/Second"       }
case object KilobytesPerSecond extends DataUnit { def render = "Kilobytes/Second"   }
case object MegabytesPerSecond extends DataUnit { def render = "Megabytes/Second"   }
case object GigabytesPerSecond extends DataUnit { def render = "Gigabytes/Second"   }
case object TerabytesPerSecond extends DataUnit { def render = "Terabytes/Second"   }
case object BitsPerSecond      extends DataUnit { def render = "Bits/Second"        }
case object KilobitsPerSecond  extends DataUnit { def render = "Kilobits/Second"    }
case object MegabitsPerSecond  extends DataUnit { def render = "Megabits/Second"    }
case object GigabitsPerSecond  extends DataUnit { def render = "Gigabits/Second"    }
case object TerabitsPerSecond  extends DataUnit { def render = "Terabits/Second"    }

object DataUnit {

  def allUnits: List[DataUnit] = List(
      NoneDataUnit
    , Count
    , CountPerSecond
    , Percent
    , Bytes
    , Kilobytes
    , Megabytes
    , Gigabytes
    , Terabytes
    , Bits
    , Kilobits
    , Megabits
    , Gigabits
    , Terabits
    , Seconds
    , Microseconds
    , Milliseconds
    , BytesPerSecond
    , KilobytesPerSecond
    , MegabytesPerSecond
    , GigabytesPerSecond
    , TerabytesPerSecond
    , BitsPerSecond
    , KilobitsPerSecond
    , MegabitsPerSecond
    , GigabitsPerSecond
    , TerabitsPerSecond
  )

  def parse(s: String): String \/ DataUnit = s match {
    case "None"               => NoneDataUnit.right
    case "Count"              => Count.right
    case "Count/Second"       => CountPerSecond.right
    case "Percent"            => Percent.right
    case "Bytes"              => Bytes.right
    case "Kilobytes"          => Kilobytes.right
    case "Megabytes"          => Megabytes.right
    case "Gigabytes"          => Gigabytes.right
    case "Terabytes"          => Terabytes.right
    case "Bits"               => Bits.right
    case "Kilobits"           => Kilobits.right
    case "Megabits"           => Megabits.right
    case "Gigabits"           => Gigabits.right
    case "Terabits"           => Terabits.right
    case "Seconds"            => Seconds.right
    case "Microseconds"       => Microseconds.right
    case "Milliseconds"       => Milliseconds.right
    case "Bytes/Second"       => BytesPerSecond.right
    case "Kilobytes/Second"   => KilobytesPerSecond.right
    case "Megabytes/Second"   => MegabytesPerSecond.right
    case "Gigabytes/Second"   => GigabytesPerSecond.right
    case "Terabytes/Second"   => TerabytesPerSecond.right
    case "Bits/Second"        => BitsPerSecond.right
    case "Kilobits/Second"    => KilobitsPerSecond.right
    case "Megabits/Second"    => MegabitsPerSecond.right
    case "Gigabits/Second"    => GigabitsPerSecond.right
    case "Terabits/Second"    => TerabitsPerSecond.right
    case s                    => s"Not a valid data unit: $s".left
  }

}

