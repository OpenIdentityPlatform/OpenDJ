#!/usr/bin/env python
# -*- coding: utf-8 -*-
#
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
# by brackets "[]" replaced with your own identifying information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#
#      Copyright 2009 Sun Microsystems, Inc.
#
pkg = {
    "name" : "opends-dsml-gateway",
    "version" : "@{version}",
    "attributes"    : {
                        "pkg.summary" : "OpenDS DSML Gateway",
                        "info.classification" : ":Directory Services",
                        "pkg.description" : "OpenDS supports DSML through a DSML gateway, \
which is implemented as a Web application that can run in an application server. A DSML \
gateway (or DSML-to-LDAP gateway) is a special type of network daemon that is used to \
translate between DSML and LDAP. In general, a DSML gateway accepts DSML requests from \
clients, converts them to LDAP requests that it forwards to a directory server for \
processing. It then translates the LDAP response from the directory server back \
to DSML to return to the client. ",
                        "pkg.detailed_url" :    "https://www.opends.org/wiki",
                        "info.maintainer_url" : "http://www.opends.org/",
                        "info.upstream_url" :   "http://www.opends.org/",
                      },

    "dirs"          : {
                        "opends"      : {"mode" : "0755"},
                        "opends/dsml" : {"mode" : "0755"},
                      },

    "dirtrees" : [ "opends/dsml" ],

    "licenses" : {
                    "opends/dsml/legal-notices/OpenDS.LICENSE"  : {"license" : "CDDLv1.0" },
                 }
}

