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
#      Copyright 2007-2008 Sun Microsystems, Inc.

__version__ = "$Revision$"
# $Source$

# public symbols
__all__ = [ "format_testcase",
            "directory_server_information", 
            "test_time", 
            "report_generation", 
            "compare_file", 
            "is_windows_platform", 
            "create_property_table", 
            "compare_property_table", 
            "exception_thrown",
            "directory_server",
            "test_env",
            "staf_service" ]

class format_testcase:
  'Format the Test name objects'
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

class directory_server_information:
  'Container for Information about Directory Servers'
  def __init__(self):
    self.line=''
    self.key=''
    self.value=''
    self.VersionList=[]
    self.SystemList=[]
    self.ServerDict={}
    self.SystemDict={}

  def getServerVersion(self,string):
    return string.replace("OpenDS Directory Server ","")

  def getServerBuildId(self,string):
    return string.replace("Build ","")

  def getServerOSName(self,string):
    return string[0:string.find(' ')].strip()

  def getServerArch(self,string):
    return string[string.rfind(' ') +1:len(string)].strip()

  def getServerJavaMajorMinor(self,string):
    return string[0:string.rfind('.')]

  def getServerValueFromString(self,string):
    return string[string.find(':') +1:len(string)].strip()

  def getServerValueFromKey(self,string,result):
    return result[string]

class test_time:
  'Simple time related manipulation objects'
  def __init__(self):
    self.seconds=0
    self.minutes=0
    self.hours=0

  def timeToSeconds(self,clocktime):
    self.hours,self.minutes,self.seconds=clocktime.split(':')
    return int(self.hours)*7200+int(self.minutes)*60+int(self.seconds)

class basic_utils:
  'Some simple basic utilities'
  def printKey(self,keypair):
    self.key=keypair.keys()[0]
    return self.key
  def printKeyValue(self,keypair):
    self.key=keypair.keys()[0]
    self.keyvalue=keypair[self.key]
    return self.keyvalue

class report_generation:
  'Test Report Generation'

  def transformReport(self,stylesheet,xml,output,params):
    from java.io import FileInputStream
    from java.io import FileOutputStream
    from java.io import ByteArrayOutputStream

    from javax.xml.transform import TransformerFactory
    from javax.xml.transform.stream import StreamSource
    from javax.xml.transform.stream import StreamResult

    self.xsl   = FileInputStream("%s" % stylesheet)
    self.xml   = FileInputStream("%s" % xml)
    self.html  = FileOutputStream("%s" % output)

    try:
      self.xslSource   = StreamSource(self.xsl)
      self.tfactory    = TransformerFactory.newInstance()
      self.xslTemplate = self.tfactory.newTemplates(self.xslSource)
      self.transformer = self.xslTemplate.newTransformer()

      self.source = StreamSource(self.xml)
      self.result = StreamResult(self.html)

      for self.key,self.value in params.items():
        self.transformer.setParameter(self.key, self.value)

      self.transformer.transform(self.source, self.result)
    finally:
      self.xsl.close()
      self.xml.close()
      self.html.close()

class exception_thrown:
  'User defined exceptions handlers'
  def __init__(self):
    self.message = ''
    self.flag = ''

class compare_file:
  'Compare two files'
  
  def __init__(self, file1, file2, diffFile):
    self.file1    = file1
    self.file2    = file2
    self.diffFile = diffFile

  def genDiff(self):
    from org.tmatesoft.svn.core.wc import *
    from java.io import File
    from java.io import FileOutputStream

    diff = DefaultSVNDiffGenerator()
    diff.displayFileDiff("", 
                         File("%s" % self.file1), 
                         File("%s" % self.file2),
                         self.file1, 
                         self.file2, 
                         "text/plain", 
                         "text/plain",
                         FileOutputStream(File("%s" % self.diffFile)))

    try:
      ret_str = ""
      diff_file = open(self.diffFile, "r")
      for line in diff_file.readlines():
        ret_str = ret_str + line
      return ret_str
    finally:
      diff_file.close()

def is_windows_platform(host):
    from java.lang import Boolean
    from com.ibm.staf import STAFHandle
    from com.ibm.staf import STAFResult
    import re

    handle = STAFHandle("varHandle") 
    res = handle.submit2(host, "VAR", "GET SYSTEM VAR STAF/Config/OS/Name")

    winPattern=re.compile('win', re.IGNORECASE)
    if (winPattern.search(res.result) != None):
      return Boolean.TRUE
    else:
      return Boolean.FALSE

def create_property_table(output, separator):
    table = {}

    for line in output.splitlines():
      key = line.split(separator)[0].strip()
      try:
        value = line.split(separator)[1].strip()
      except IndexError:
        value = ''
      table[key] = value

    return table

def compare_property_table(refTable, newTable):
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

        if refTable[refKey] != newTable[refKey]:
          result = result + 'ERROR: Value for ' + refKey 
          result = result + ' should be the same.\n'

    return result

class directory_server:
  'Container to hold DS instance objects'
  def __init__(self):
    self.location=''
    self.host=''
    self.port=''
    self.dn=''
    self.password=''

  def location(self,location):
    return location

  def host(self,name):
    return name

  def port(self,port):
    return port

  def dn(self,dn):
    return dn

  def password(self,pswd):
    return pswd

  def suffix(self,sfx):
    return sfx

class staf_service:  

  def __init__(self,host,name):
    from com.ibm.staf import STAFHandle
    from com.ibm.staf import STAFResult
    from com.ibm.staf import STAFMarshallingContext

    __handle = STAFHandle("varHandle")

    __cmd = 'QUERY SERVICE %s' % name
    __res = __handle.submit2(host, "SERVICE", __cmd)
    __context = STAFMarshallingContext.unmarshall(__res.result)
    __entryMap = __context.getRootObject()

    self.name=__entryMap['name']
    self.library=__entryMap['library']
    self.executable=__entryMap['executable']
    self.options=__entryMap['options']
    self.parms=__entryMap['parameters']

  def get_library(self):
    return self.library

  def get_name(self):
    return self.name

  def get_executable(self):
    return self.executable

  def get_options(self):
    return self.options

  def get_parms(self):
    return self.parms

class test_env:
  'Container to hold test environment instance objects'
  def __init__(self):
    self.environment=''
  
  class staf:
    'Container to hold staf objects'
    def __init__(self,host):
      from com.ibm.staf import STAFHandle
      from com.ibm.staf import STAFResult

      __handle = STAFHandle("varHandle")
      __cmd = 'Version'
      __res = __handle.submit2(host, "MISC", __cmd)      
      self.version=__res.result

      self.name='STAF'

      __cmd = 'GET SYSTEM VAR STAF/Config/STAFRoot'
      __res = __handle.submit2(host, "VAR", __cmd)
      self.root=__res.result

    def get_version(self):
      return self.version

    def get_name(self):
      return self.name

    def get_root(self):
      return self.version

  class stax(staf_service):
    'Container to hold stax service objects'
    def __init__(self,host,name='STAX'):
      from com.ibm.staf import STAFHandle
      from com.ibm.staf import STAFResult

      if hasattr(staf_service, '__init__'):
        staf_service.__init__(self,host,name)

      __handle = STAFHandle("varHandle")

      __cmd = 'Version'
      __res = __handle.submit2(host, "STAX", __cmd)      
      self.version=__res.result

    def get_version(self):
      return self.version

  class event(staf_service):
    'Container to hold event service objects'
    def __init__(self,host,name='EVENT'):
      from com.ibm.staf import STAFHandle
      from com.ibm.staf import STAFResult

      if hasattr(staf_service, '__init__'):
        staf_service.__init__(self,host,name)

      __handle = STAFHandle("varHandle")
      __cmd = 'Version'
      __res = __handle.submit2(host, "EVENT", __cmd)      
      self.version=__res.result

    def get_version(self):
      return self.version

  class eventmanager(staf_service):
    'Container to hold event manager service objects'
    def __init__(self,host,name='EVENTMANAGER'):
      from com.ibm.staf import STAFHandle
      from com.ibm.staf import STAFResult

      if hasattr(staf_service, '__init__'):
        staf_service.__init__(self,host,name)

      __handle = STAFHandle("varHandle")
      __cmd = 'Version'
      __res = __handle.submit2(host, "EVENTMANAGER", __cmd)      
      self.version=__res.result

    def get_version(self):
      return self.version

  class email(staf_service):
    'Container to hold email service objects'
    def __init__(self,host,name='EMAIL'):
      from com.ibm.staf import STAFHandle
      from com.ibm.staf import STAFResult
      from com.ibm.staf import STAFMarshallingContext

      if hasattr(staf_service, '__init__'):
        staf_service.__init__(self,host,name)

      __handle = STAFHandle("varHandle")
      __cmd = 'Version'
      __res = __handle.submit2(host, "EMAIL", __cmd)      
      self.version=__res.result
      self.recipients= 'TODO: get from config.py'

      __res = __handle.submit2(host, "EMAIL", "LIST SETTINGS")
      __context = STAFMarshallingContext.unmarshall(__res.result)
      __entryMap = __context.getRootObject()

      self.server=__entryMap['mailServer']
      self.port=__entryMap['port']

    def get_version(self):
      return self.version

    def get_recipients(self):
      return self.recipients

    def get_server(self):
      return self.server

    def get_port(self):
      return self.port

  class http(staf_service):
    'Container to hold http service objects'
    def __init__(self,host,name='HTTP'):
      from com.ibm.staf import STAFHandle
      from com.ibm.staf import STAFResult

      if hasattr(staf_service, '__init__'):
        staf_service.__init__(self,host,name)

      __handle = STAFHandle("varHandle")
      __res = __handle.submit2(host, "HTTP", "Version")
      self.version=__res.result

    def get_version(self):
      return self.version

  class dsml(staf_service):
    'Container to hold dsml service objects'
    def __init__(self,host,name='DSML'):
      from com.ibm.staf import STAFHandle
      from com.ibm.staf import STAFResult

      if hasattr(staf_service, '__init__'):
        staf_service.__init__(self,host,name)

      __handle = STAFHandle("varHandle")

      self.version='Unknown'

    def get_version(self):
      return self.version

  class source:
    'Container to hold source data instance objects'
    def __init__(self,dir):
      self.directory=dir
      self.data='%s/functional-tests/shared/data' % dir
      self.common='%s/shared' % dir
      self.java='%s/java' % self.common

    def get_directory(self):
      return self.directory

    def get_data(self):
      return self.data

    def get_common(self):
      return self.common

    def get_java(self):
      return self.java

  class logs:
    'Container to hold test log instance objects'
    def __init__(self,dir):
      self.directory=dir
      self.tests='%s/testlogs' % dir
      self.reports='%s/reports' % dir
      self.sut='%s/sutlogs' % dir

    def get_directory(self):
      return self.directory

    def get_tests(self):
      return self.tests

    def get_reports(self):
      return self.reports

    def get_sut(self):
      return self.sut

  class data:
    'Container to hold local and remote test data instance objects'
    def __init__(self,dir):
      self.directory=dir
      self.testdata='%s/testdata' % dir
      self.java='%s/java' % self.testdata
      self.data='%s/data' % self.testdata
      self.temp='%s/temp'  % dir
      self.reldatadir='testdata/data'
      self.reljavadir='testdata/java'

    def get_directory(self):
      return self.directory

    def get_testdata(self):
      return self.testdata

    def get_java(self):
      return self.java

    def get_data(self):
      return self.data

    def get_temp(self):
      return self.temp
 
    def get_reldatadir(self):
      return self.directory

    def get_reljavadir(self):
      return self.directory

