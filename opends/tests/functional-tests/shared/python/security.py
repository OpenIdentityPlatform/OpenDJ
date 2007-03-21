#!/usr/bin/python

# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License, Version 1.0 only
# (the "License").  You may not use this file except in compliance
# with the License.
#
# You can obtain a copy of the license at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE
# or https://OpenDS.dev.java.net/OpenDS.LICENSE.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
# add the following below this CDDL HEADER, with the fields enclosed
# information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#
#      Portions Copyright 2007 Sun Microsystems, Inc.

def write_ldaps_ldif_file(path, port):
    ldif_file = open(path + "/ldaps_port.ldif","w")

    ldif_file.write("dn: cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config\n")
    ldif_file.write("changetype: modify\n")
    ldif_file.write("replace: ds-cfg-listen-port\n")
    ldif_file.write("ds-cfg-listen-port: ")
    ldif_file.write(port)
    ldif_file.write("\n")
    ldif_file.write("-\n")
    ldif_file.write("replace: ds-cfg-connection-handler-enabled\n")
    ldif_file.write("ds-cfg-connection-handler-enabled: true\n")
    ldif_file.write("-\n")
    ldif_file.write("replace: ds-cfg-ssl-cert-nickname\n")
    ldif_file.write("ds-cfg-ssl-cert-nickname: server-cert\n")
     
    ldif_file.close()


