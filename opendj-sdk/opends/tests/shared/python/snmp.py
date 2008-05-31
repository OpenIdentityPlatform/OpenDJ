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

__version__ = "$Revision$"
# $Source$

# public symbols
__all__ = [ "create_table_fromoutput",
            "compare_snmp_values",
            "get_handler_count",
            "get_handler_index" ]

def create_table_fromoutput(output):
    table = {}
    separator = '='
    start= 'ds'

    for line in output.splitlines():
      if line.startswith('ds'):
        key = line.split(separator)[0].strip()
        try:
          value = line.split(separator)[1].strip()
        except IndexError:
          value = '-'
        table[key] = value

    return table

def compare_snmp_values(refTable, newTable):
    import re

    result = ''

    refKeys=newTable.keys()
    for refKey in refKeys:
      if not refTable.has_key(refKey):
        result = result + 'ERROR: Entry ' + refKey + ' does not exists'
        result = result + ' in the reference table.\n'

    refKeys=refTable.keys()
    for refKey in refKeys:
      if not newTable.has_key(refKey):
        result = result + 'ERROR: Entry ' + refKey + ' does not exists'
        result = result + ' in the new table.\n'
      else:
        result = result + refKey + '=> expected: ' + refTable[refKey] 
        result = result + ' , result: ' + newTable[refKey] + '\n'

      pattern1 = re.compile('dsApplIfOutBytes.*')
      pattern2 = re.compile('dsApplIfInBytes.*')
      if pattern1.search(refKey) != None or pattern2.search(refKey) != None:
        if refTable[refKey] > newTable[refKey]:
          result = result + 'ERROR: Value for ' + refKey 
          result = result + ' should be greater.\n'
      else:
        if refTable[refKey] != newTable[refKey]:
          result = result + 'ERROR: Value for ' + refKey 
          result = result + ' should be the same.\n'

    return result

def get_handler_count(table):
    import re

    count = 0

    keys=table.keys()
    for key in keys:
      pattern = re.compile('dsApplIfProtocol\..*')
      if pattern.search(key) != None:
        count = count + 1

    return count

def get_handler_index(table, handler):
    import re

    index = 0

    keys=table.keys()
    for key in keys:
      pattern = re.compile('dsApplIfProtocol\..*')
      if pattern.search(key) != None:
        if table[key] == handler:
          regexp = re.compile('^.*\.')
          index = re.sub(regexp, '', key)
          break

    return index
