package com.ambiata.saws
package ec2

import com.owtelse.codec.Base64

object EC2UserData {
  def script(script: String) = {
    val data = s"""MIME-Version: 1.0
                  |Content-Type: text/x-shellscript
                  |
                  |#!/bin/sh
                  |$script
                  |""".stripMargin
    Base64.encode(data.getBytes("UTF-8"), "UTF-8")
  }
}
