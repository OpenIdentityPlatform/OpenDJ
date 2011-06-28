#!/usr/bin/python

# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License, Version 1.0 only
# (the "License").  You may not use this file except in compliance
# with the License.
# 
# You can obtain a copy of the license at
# trunk/opends/resource/legal-notices/CDDLv1_0.txt
# or http://forgerock.org/license/CDDLv1.0.html.
# See the License for the specific language governing permissions
# and limitations under the License.
# 
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at
# trunk/opends/resource/legal-notices/CDDLv1_0.txt.  If applicable,
# add the following below this CDDL HEADER, with the fields enclosed
# by brackets "[]" replaced with your own identifying information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#      Copyright 2011 ForgeRock AS.

class person_entry:
  def __init__(self, rdn, suffix):
    self.userDn = '%s,ou=People,%s' \
                  % (rdn, suffix)
    self.suffix = suffix
    self.listAttr = []
    self.listAttr.append('objectclass:top')
    self.listAttr.append('objectclass:organizationalperson')
    self.listAttr.append('objectclass:inetorgperson')
    self.listAttr.append('objectclass:person')
  def getDn(self):
    return self.userDn
  def getSuffix(self):
    return self.suffix
  def getAttrList(self):
    return self.listAttr
  def addAttr(self, attrType, attrValue):
    self.listAttr.append('%s:%s' % (attrType, attrValue))

