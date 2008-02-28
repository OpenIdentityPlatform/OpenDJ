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

def writeOrganizationalPersonTemplate(fileFd):
  fileFd.write('\n\n\
template: organizationalPerson\n\
rdnAttr: cn\n\
objectClass: organizationalPerson\n\
objectClass: person\n\
objectClass: top\n\
sn: <last>\n\
cn: <first> {sn}\n\
userPassword: <random:alphanumeric:8>\n\
description: This is the description for {cn}.\n\
telephoneNumber: <random:telephone>\n\
destinationIndicator: <random:alphanumeric:8>\n\
facsimileTelephoneNumber: <random:telephone>\n\
internationaliSDNNumber: <random:numeric:8>\n\
l: <file:cities>\n\
physicalDeliveryOfficeName: <random:alphanumeric:8>\n\
postOfficeBox: <random:alphanumeric:8>\n\
st: <file:states>\n\
street: <random:numeric:5> <file:streets> Street\n\
postalCode: <random:numeric:5>\n\
postalAddress: {cn}${street}${l}, {st}  {postalCode}\n\
preferredDeliveryMethod: mhs $ telephone\n\
registeredAddress: registeredAddress: <presence:50>{postalAddress}\n\
teletexTerminalIdentifier: <random:alphanumeric:8>\n\
telexNumber: <random:numeric:8> $ <random:numeric:8> $ <random:numeric:8>\n\
title: <list:manager,secretary,engineer>\n\
x121Address: <random:numeric:8>\n\
\n')

