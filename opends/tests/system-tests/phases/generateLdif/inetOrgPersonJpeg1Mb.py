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

def writeInetOrgPersonJpeg1MbTemplate(fileFd):
  fileFd.write('\n\n\
template: inetOrgPerson_Jpeg_1MB\n\
rdnAttr: uid\n\
objectclass: top\n\
objectclass: person\n\
objectclass: organizationalPerson\n\
objectclass: inetOrgPerson\n\
givenName: <first>\n\
sn: <last>\n\
cn: {givenName} {sn}\n\
initials: {givenName:1}{sn:1}\n\
uid: {givenName}.{sn}\n\
mail: {uid}@[suffix]\n\
userPassword: <random:alphanumeric:8>\n\
telephoneNumber: <random:telephone>\n\
homePhone: <random:telephone>\n\
pager: <random:telephone>\n\
mobile: <random:telephone>\n\
employeeNumber: <sequential>\n\
l: <file:cities>\n\
st: <file:states>\n\
street: <random:numeric:5> <file:streets> Street\n\
postalCode: <random:numeric:5>\n\
postalAddress: {cn}${street}${l}, {st}  {postalCode}\n\
description: This is the description for {cn}.\n\
carLicense: <presence:50><random:alphanumeric:8>\n\
seeAlso: cn=<random:alphanumeric:8>\n\
destinationIndicator: <random:alphanumeric:8>\n\
facsimileTelephoneNumber: <random:telephone>\n\
internationaliSDNNumber: <random:numeric:8>\n\
ou: <random:alphanumeric:8>\n\
physicalDeliveryOfficeName: <random:alphanumeric:8>\n\
postOfficeBox: <random:alphanumeric:8>\n\
preferredDeliveryMethod: mhs $ telephone\n\
registeredAddress: <presence:50>{cn}${street}${l}, {st}  {postalCode}\n\
teletexTerminalIdentifier: <random:alphanumeric:8>\n\
telexNumber: <random:numeric:8> $ <random:numeric:8> $ <random:numeric:8>\n\
title: <random:alphanumeric:8>\n\
x121Address: <random:numeric:8>\n\
businessCategory: <random:alphanumeric:8>\n\
carLicense: <random:alphanumeric:8>\n\
departmentNumber: <random:numeric:2>\n\
displayName: {cn}\n\
employeeType: <random:alphanumeric:8>\n\
homePostalAddress: {postalAddress}\n\
manager: cn=manager,<parentdn>\n\
preferredLanguage: <list:english,french,german,spanish,chinese>\n\
roomNumber: <random:alphanumeric:4>\n\
secretary: cn=secretary,<parentdn>\n\
jpegPhoto: <random:base64:1000000>\n\
\n')
