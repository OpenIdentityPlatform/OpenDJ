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
    "name" : "opends",
    "version" : "@{version}",
    "attributes"    : {
                        "pkg.summary" : "OpenDS",
                        "info.classification" : "Directory Services",
                        "pkg.description" : "OpenDS is an open source community project\
building a free and comprehensive\
next generation directory service,\
based on LDAP and DSML standards."
                      },

    "dirs"          : {
                       "opends"                    : {"mode" : "0755"},
                      },

    "excludefiles" :  [
	                     "opends/dsml/opends-dsml.war",
	                     "opends/dsml/legal-notices/OpenDS.LICENSE",
		      ],
    "files": {
               "opends/config/messages/password-reset.template"             :   {"preserve":"strawberry"},
               "opends/config/messages/account-unlocked.template"           :   {"preserve":"strawberry"},
               "opends/config/messages/password-expired.template"           :   {"preserve":"strawberry"},
               "opends/config/messages/account-expired.template"            :   {"preserve":"strawberry"},
               "opends/config/messages/account-temporarily-locked.template" :   {"preserve":"strawberry"},
               "opends/config/messages/account-permanently-locked.template" :   {"preserve":"strawberry"},
               "opends/config/messages/password-expiring.template"          :   {"preserve":"strawberry"},
               "opends/config/messages/account-idle-locked.template"        :   {"preserve":"strawberry"},
               "opends/config/messages/account-disabled.template"           :   {"preserve":"strawberry"},
               "opends/config/messages/account-enabled.template"            :   {"preserve":"strawberry"},
               "opends/config/messages/password-changed.template"           :   {"preserve":"strawberry"},
               "opends/config/messages/account-reset-locked.template"       :   {"preserve":"strawberry"},
               "opends/config/MakeLDIF/cities"                              :   {"preserve":"strawberry"},
               "opends/config/MakeLDIF/first.names"                         :   {"preserve":"strawberry"},
               "opends/config/MakeLDIF/last.names"                          :   {"preserve":"strawberry"},
               "opends/config/MakeLDIF/states"                              :   {"preserve":"strawberry"},
               "opends/config/MakeLDIF/example.template"                    :   {"preserve":"strawberry"},
               "opends/config/MakeLDIF/streets"                             :   {"preserve":"strawberry"},
               "opends/config/config.ldif"                                  :   {"preserve":"strawberry"},
               "opends/config/admin-backend.ldif"                           :   {"preserve":"strawberry"},
               "opends/config/java.properties"                              :   {"preserve":"strawberry"},
               "opends/config/wordlist.txt"                                 :   {"preserve":"strawberry"},
               "opends/config/tools.properties"                             :   {"preserve":"strawberry"},
               "opends/config/buildinfo"                                    :   {"preserve":"strawberry"},
             },
    
    "dirtrees" : [
                  "opends"                 
                 ],

    "licenses" : {
                  "opends/legal-notices/OpenDS.LICENSE"        : {"license" : "CDDLv1.0" },
                  "opends/legal-notices/THIRDPARTYREADME.txt"  : {"license" : "Third party" },
                 }
}

