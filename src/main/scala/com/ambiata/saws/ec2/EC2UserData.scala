package com.ambiata.saws
package ec2

import com.owtelse.codec.Base64

object EC2UserData {
  def script(script: String) = {
    val data = s"""Content-Type: multipart/mixed; boundary="===============2633016704916973363=="
                  |MIME-Version: 1.0
                  |
                  |--===============2633016704916973363==
                  |Content-Type: text/x-shellscript; charset="us-ascii"
                  |MIME-Version: 1.0
                  |Content-Transfer-Encoding: 7bit
                  |Content-Disposition: attachment; filename="bootstrap"
                  |
                  |#!/bin/sh
                  |
                  |$script
                  |
                  |
                  |--===============2633016704916973363==--
                  |""".stripMargin
    Base64.encode(data.getBytes("UTF-8"), "UTF-8")
  }
}
