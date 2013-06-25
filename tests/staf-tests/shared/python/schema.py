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
#      Copyright 2009 Sun Microsystems, Inc.





# Global variable containing the list of servers ("Server" class instances) deployed



# Define ChangelogServer class
class OIDDict:
  """OIDDict is a dictionary class that help lookup OID <-> litteral name
  of both objeclasses, and attributtypes"""
  
  def __init__(self, schema=None):
    self.attrDict = {}
    self.objDict = {}
    self.sup = {}
    self.may = {}
    self.must = {}
    self.allmay = []
    self.allmust = []

  def _getOID(self, line):
    """given a schema entry definition for objectclass/attributtype
    return the tuple (OID,List of names)
    the List of aliases starts from list of names[1:] when exist. for ex :
       attributeTypes: ( 2.5.4.4 NAME ( 'sn' 'surname' ) SUP name X-ORIGIN 'RFC 4519' )
       (2.5.4.4,['sn','surname']
    More details : https://www.opends.org/wiki/page/AttributeTypeDescriptionFormat
    """
    pNdx = line.find('(')
    nNdx = line.find('NAME',pNdx)
    OID = line[pNdx+1:nNdx].strip()

    # populate the NAME to OID : "dict" dictionary
    NAMES = self._getStr(line,'NAME')
    if NAMES:
      if line.startswith('objectClasses:'):
        # TODO encoded schema is not handled for now
        self.objDict.update({OID:NAMES})
        for name in NAMES:
          self.objDict.update({name:OID})
      elif line.startswith('attributeTypes:'):
         # TODO encoded schema is not handled for now
        self.attrDict.update({OID:NAMES})
        for name in NAMES:
          self.attrDict.update({name:OID})
   # populate SUP and MUST / MAY, : "sup", "may", "must" dictionaries
    if line.startswith('objectClasses:'):
      r = self._getStr(line,'SUP')
      if r:
        self.sup.update({NAMES[0]:r})
      r = self._getStr(line,'MUST')
      if r:
        self.must.update({NAMES[0]:r})
        for m in r:
          if not m in self.allmust:
            self.allmust.append(m)
      r = self._getStr(line,'MAY')
      if r:
        self.may.update({NAMES[0]:r})
        for m in r:
          if not m in self.allmay:
            self.allmay.append(m)

    return OID, NAMES

  def _getStr(self, line, myStr, right=None):
    """extract a list of attributes for a given myStr section.
    The section should contain () when multivalued.
    If another section comes after it starts with a Upercase.
    example MUST (sn cn) MAY ( description ... )

    line : line to parse
    myStr : name of the section ex(MAY)
    right : right boundary,
            if None, function will figure out end of section"""
    left = line.find(myStr)
    if left == -1:
      return None

    if not right:
      right = len(line)
    lpNdx = line.find('(', left)
    if lpNdx > 0:
      spaces=line[left+len(myStr) : lpNdx]
      if len(spaces.strip()) == 0:
        right = line.find(')',lpNdx)
        left = lpNdx + 1
      else:
        left = left+len(myStr)+1
    else:
      left = left+len(myStr)
    strs = line[left:right]
    realStrs = []
    for s in strs.split(' '):
      if len(s) > 0:
          if s[0] >= 'A' and s[0] <= 'Z':
            break
          elif s[0] != '$' and s[0] != '|':
            if s[0] == '\'' and s[-1] == '\'':
              s = s[1:-1]
            realStrs.append(s.lower())
    return realStrs

  def getMust(self, objectclassname):
    """will return the attributes the objectclassname MUST implement"""
    if self.must.has_key(objectclassname):
      ret = self.must.get(objectclassname)
    else:
      ret = []
    for h in self.getHierarchy(objectclassname):
      # avoiding duplication of MUSTs
      ret.extend([e for e in self.getMust(h) if ret.count(e) == 0])
    return ret

  def getMay(self, objectclassname):
    """will return the attributes the objectclassname MAY implement"""
    if self.may.has_key(objectclassname):
      ret = self.may.get(objectclassname)
    else:
      ret = []
    for h in self.getHierarchy(objectclassname):
      # avoiding duplication of MAYs
      ret.extend([e for e in self.getMay(h) if ret.count(e) == 0])
    return ret

  def getSup(self, objectclassname):
    """will return the objectclassname that this objectclassname inherit"""
    if objectclassname == 'top':
      return None
    else:
      ret = self.sup.get(objectclassname)
      return ret[0]

  def getHierarchy(self, objectclassname):
    hierachy = []
    up = self.getSup(objectclassname)
    while up:
      if hierachy.count(up) == 0:
        hierachy.append(up)
        up = self.getSup(up)
    return hierachy


  def parseSchema(self, ref_content):
    """get the schema as a string
    lookit up line by line, extracting OID/literal name for objectclasses
    and attributetypes only."""
    lines=[]
    line=''
    for f in ref_content.splitlines():
      if len(line) == 0 and \
         not (f.startswith("objectClasses") or \
              f.startswith("attributeTypes")):
        # not handled for now
        continue
      elif len(line) > 0 and len(f) > 0 and f[0].isspace():
          # line continuation aggregated into 'line'
          line += f[1:]

      elif f.startswith("objectClasses") or f.startswith("attributeTypes"):
        if len(line) > 0:
          lines.append(line)
          # populate the OID <-> Names dictionary
          self._getOID(line)
        line = f[:-1]
        line = f
    if len(line) > 0:
      # parsing the last line
      self._getOID(line)
      lines.append(line)
    f=open('/tmp/lines.ldif','w')
    f.write('\n'.join(lines))
    f.close()

if __name__ == '__main__':
   """get example schema.ldif file with :
      ldapsearch -b 'cn=schema' -Dcn=directory\ manager -s base -wpassword objectclass=* objectClasses attributeTypes > /tmp/schema.ldif
   """
   objectClassesFileName='/tmp/schema.ldif'
   f = open(objectClassesFileName)
   fc = f.readlines()
   f.close()
   oidDict = OIDDict()
   oidDict.parseSchema(''.join(fc))
   print '[ Objectclasses dictionary ]'.center(80, '-')
   for k,v in oidDict.objDict.items():
     print "%s\t%s"%(k,v)
   print '[ AttributeTypes dictionary ]'.center(80, '-')
   for k,v in oidDict.attrDict.items():
     print "%s\t%s"%(k,v)
   print '[ must ]'.center(80, '-')
   for k,v in oidDict.must.items():
     print "%s\t%s"%(k,v)
   print '[ may ]'.center(80, '-')
   for k,v in oidDict.may.items():
     print "%s\t%s"%(k,v)
   print '[ sup ]'.center(80, '-')
   for k,v in oidDict.sup.items():
     print "%s\t%s"%(k,v)
   for cn in ['rFC822LocalPart','inetOrgPerson','top','doMain','2.5.6.7','BLAH']:
     print cn.center(80, '-')
     try:
       print 'SUP'.center(40,'.')
       print 'SUP',oidDict.getSup(cn)
       print 'HIERARCHY',oidDict.getHierarchy(cn)
       print 'MUST'.center(40,'.')
       print 'MUST',oidDict.getMust(cn)
       print 'MAY'.center(40,'.')
       print 'MAY',oidDict.getMay(cn)
     except Exception, e:
       print e.message
   print '[ all must ]'.center(80,'-')
   mustSize = 0
   for m in oidDict.allmust:
     mustSize += len(m)
   print 'got %s MUSTs size = %sKb' % (len(oidDict.allmust),mustSize/1024.0)
   print oidDict.allmust
   print '[ all may ]'.center(80,'-')
   maySize = 0
   for m in oidDict.allmay:
     maySize += len(m)
   print 'got %s MAYs size = %sKb' % (len(oidDict.allmay),maySize/1024.0)
   print oidDict.allmay
