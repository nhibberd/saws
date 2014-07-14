package com.ambiata.saws
package ec2

import com.owtelse.codec.Base64

object EC2UserData {
  def script(script: String) = {
    val cmd = script.split(' ').toList.map(t => "\"" + t + "\"")
    val data = s"""#cloud-config
                  |runcmd:
                  | - [ ${cmd.mkString(", ")} ]""".stripMargin
    Base64.encode(data.getBytes("UTF-8"), "UTF-8")
  }
}
