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
#      Copyright 2008 Sun Microsystems, Inc.

def writeScalablePersonTemplate(fileFd):
  fileFd.write('\n\n\
template: scalablePerson\n\
rdnAttr: cn\n\
uid: <sequential>\n\
objectclass: top\n\
objectclass: scalablePerson\n\
sn: <last>\n\
givenName: <first>\n\
cn: {givenName} {sn}\n\
displayName: {cn}\n\
preferredLanguage: <list:english,french,german,spanish,chinese>\n\
telephoneNumber: <random:telephone>\n\
postalAddress: {cn}$<random:numeric:5> <file:streets> Street$<file:cities>, <file:states> <random:numeric:5>\n\
labeledURI: http://www.france.sun.com/{uid}\n\
mobile: <random:telephone>\n\
userPassword: <random:alphanumeric:8>\n\
jpegPhoto:: <random:base64:500>\n\
ntUserDomainId: <list:IPLANET,SUNONE,SUNLABS,SARATOGA>\n\
ntUserFlags: <random:numeric:1>\n\
ntUserUnitsPerWeek:  <random:numeric:1>\n\
\n')

