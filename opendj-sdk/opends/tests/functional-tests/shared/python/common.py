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

__version__ = "$Revision$"
# $Source$

# public symbols
__all__ = [ "format_testcase" ]

class format_testcase:
  "Format the Test name objects"
  def group(self,string):
    self.group=string.lower()
    self.group=self.group.strip()
    return '%s' % self.group
  def suite(self,string):
    self.suite=string.lower()
    self.suite=self.suite.strip()
    self.suite=self.suite.replace(' ','-')
    return '%s' % self.suite
  def name(self,string):
    self.name=string.strip()
    self.name=self.name.replace(' ','-')
    return '%s' % self.name
