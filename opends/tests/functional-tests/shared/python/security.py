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
#      Portions Copyright 2006-2007 Sun Microsystems, Inc.

__version__ = "$Revision$"
# $Source$

# public symbols
__all__ = [ "write_ldaps_ldif_file" ]

def write_ldaps_ldif_file(path, port):
    ldif_file = open(path + "/ldaps_port.ldif","w")

    ldif_file.write("dn: cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config\n")
    ldif_file.write("objectclass: top\n")
    ldif_file.write("objectclass: ds-cfg-connection-handler\n")
    ldif_file.write("objectclass: ds-cfg-ldap-connection-handler\n")
    ldif_file.write("cn: LDAPS Connection Handler\n")
    ldif_file.write("ds-cfg-connection-handler-class: org.opends.server.protocols.ldap.LDAPConnectionHandler\n")
    ldif_file.write("ds-cfg-connection-handler-enabled: true\n")
    ldif_file.write("ds-cfg-listen-address: 0.0.0.0\n")
    
    ldif_file.write("ds-cfg-listen-port: ")
    ldif_file.write(port)
    ldif_file.write("\n")
    
    ldif_file.write("ds-cfg-allow-ldapv2: true\n")
    ldif_file.write("ds-cfg-keep-stats: true\n")
    ldif_file.write("ds-cfg-use-tcp-keepalive: true\n")
    ldif_file.write("ds-cfg-use-tcp-nodelay: true\n")
    ldif_file.write("ds-cfg-allow-tcp-reuse-address: true\n")
    ldif_file.write("ds-cfg-send-rejection-notice: true\n")
    ldif_file.write("ds-cfg-max-request-size: 5 mb\n")
    ldif_file.write("ds-cfg-num-request-handlers: 2\n")
    ldif_file.write("ds-cfg-allow-start-tls: false\n")
    ldif_file.write("ds-cfg-use-ssl: true\n")
    ldif_file.write("ds-cfg-ssl-client-auth-policy: optional\n")
    ldif_file.write("ds-cfg-ssl-cert-nickname: server-cert\n")

    ldif_file.close()


