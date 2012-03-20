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
#      Copyright 2007-2009 Sun Microsystems, Inc.
#      Portions Copyright 2011-2012 ForgeRock AS.

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
            "staf_service",
            "get_test_name",
            "parse_stax_result",
            "dn2list",
            "list2dn",
            "dn2rfcmailaddr",
            "java_properties",
            "xmldoc_service" ,
            "xml_create_report" ,
            "group_to_run" ,
            "get_last_attr_from_entry" ,
            "list_matches" ,
            "count_attr" ,
            "host_is_localhost" ,
            "md5_hash" ,
            "value_not_string" ,
            "get_system_uid" ,
            "date_compare" ,
            "tail_logfile"
            ]

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

  def getServerVersion(self,string,productname):
    return string.replace("%s " % productname,"")

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
    from org.tmatesoft.svn.core.wc import DefaultSVNDiffGenerator
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

    handle = STAFHandle("varHandle") 
    res = handle.submit2(host, "VAR", "GET SYSTEM VAR STAF/Config/OS/Name")

    winLower = res.result.lower()
    winNdx = winLower.find("win")
    if winNdx != -1:
      return res.result[winNdx:]
    else:
      return Boolean.FALSE

def create_property_table(output, separator):
    'Create a table from an output'
    
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
    'Compare two tables'
    
    import re

    result = ''

    refKeys = newTable.keys()
    for refKey in refKeys:
      if not refTable.has_key(refKey):
        result = result + 'ERROR: Entry ' + refKey + ' does not exists'
        result = result + ' in the reference table.\n'

    refKeys = refTable.keys()
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
    self.suffix=''
    self.backend=''

  def location(self,location):
    return location

  def host(self,name):
    return name

  def port(self,port):
    return port

  def adminport(self,port):
    return adminport

  def dn(self,dn):
    return dn

  def password(self,pswd):
    return pswd

  def suffix(self,sfx):
    return sfx

  def backend(self,be):
    return be

class staf_service:  
  'Container to hold staf service instance objects'
  def __init__(self,host,name):
    from com.ibm.staf import STAFHandle
    from com.ibm.staf import STAFResult
    from com.ibm.staf import STAFMarshallingContext

    self.name=name
    self.library='Unknown'
    self.executable='Unknown'
    self.options='Unknown'
    self.params='Unknown'

    try:
      __handle = STAFHandle("varHandle")
    except STAFException, e:
      pass

    __cmd = 'QUERY SERVICE %s' % name
    __res = __handle.submit2(host, "SERVICE", __cmd)

    if (__res.rc == 0):
   
      __context = STAFMarshallingContext.unmarshall(__res.result)
      __entryMap = __context.getRootObject()

      self.name=__entryMap['name']
      self.library=__entryMap['library']
      self.executable=__entryMap['executable']
      self.options=__entryMap['options']
      self.params=__entryMap['parameters']

  def get_library(self):
    return self.library

  def get_name(self):
    return self.name

  def get_executable(self):
    return self.executable

  def get_options(self):
    return self.options

  def get_params(self):
    return self.params

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
    def __init__(self,dir,tests_type):
      self.directory=dir
      self.data='%s/%s/shared/data' % (dir,tests_type)
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

    def set_data(self,dir):
      self.data=dir

def get_test_name(name):
  i=2
  __name=''
  __tn=name.split(':')
  while i < len(__tn):
    __name += '%s:' % __tn[i]
    i=i+1
  return __name[0:-1].strip()

def parse_stax_result(result):

  import org.python.core

  if result.__class__ is org.python.core.PyList:
    _unwrapResult=result[1][0]
    
    try:
      _functionString=_unwrapResult[1]
    except AttributeError:
      _functionString='Unable to parse result.'
  elif result.__class__ is org.python.core.PyString:
    _functionString=STAXResult
  else:
    _functionString='Unable to parse result.'

  return _functionString

def dn2list(dn):
  __list=dn.split(',')
  return __list

def list2dn(list):
  return ",".join(list)

def dn2rfcmailaddr(dn):
  __addr=[]
  __list=dn.split(',')
  for __rdn in __list:
    __rhside=__rdn.split('=')[1]
    __addr.append(__rhside)
  return ".".join(__addr).lower()

def java_properties(propFile, toolsName, optionList):
  'Update java.properties file'

  import fileinput
  import string
  import sys

  try:
    file = open(propFile, "r")
    content = file.read()
    file.close()

    newfile = open(propFile, "w")
    for line in content.splitlines():
      if line.startswith(toolsName):
        newline = line.split("=")[0] + "="
        for item in optionList:
          newline = newline + item + " "
        newfile.write("%s\n" % newline)
      else:
        newfile.write("%s\n" % line)
    newfile.close()

    return 0
  except:
    print "Exception:", sys.exc_info()[0]

    return 1

class xmldoc_service:

  def __init__(self):
    self.testgroup=''
    self.testsuite=''
    self.testcase=''
    self.issues=''
    self.issue=''

  def createBlankDocument(self):
    try:
      import sys, traceback
      from javax.xml.parsers import DocumentBuilderFactory
      builderFactory=DocumentBuilderFactory.newInstance()
      return builderFactory.newDocumentBuilder()
    except:
      print "exception: %s" % traceback.format_exception(*sys.exc_info())

  def writeXMLfile(self,doc,xmlfile):
    try:
      import sys, traceback
      from java.io import File
      from javax.xml.transform import TransformerFactory
      from javax.xml.transform import OutputKeys
      from javax.xml.transform.stream import StreamResult
      from javax.xml.transform.dom import DOMSource
      tranFactory = TransformerFactory.newInstance();
      aTransformer = tranFactory.newTransformer();
      aTransformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
      aTransformer.setOutputProperty(OutputKeys.INDENT, "yes")

      src = DOMSource(doc);
      dest = StreamResult(File(xmlfile));
      aTransformer.transform(src, dest);
    except:
      print "exception: %s" % traceback.format_exception(*sys.exc_info())

  def printXMLfile(self,doc):
    try:
        from javax.xml.transform import TransformerFactory
        from javax.xml.transform import OutputKeys
        from javax.xml.transform.stream import StreamSource
        from javax.xml.transform.stream import StreamResult
        from javax.xml.transform.dom import DOMSource
        from java.io import StringWriter

        xmlInput = DOMSource(doc);
        xmlOutput = StreamResult(StringWriter());

        tranFactory = TransformerFactory.newInstance();
        aTransformer = tranFactory.newTransformer();
        aTransformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        aTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
        aTransformer.transform(xmlInput, xmlOutput);

        print xmlOutput.getWriter().toString()
    except:
      print "exception: %s" % traceback.format_exception(*sys.exc_info())

  def parseXMLfile(self,xmlfile):
    try:
      import sys, traceback
      from java.io import FileInputStream
      self.builder= self.createBlankDocument()
      self.input = FileInputStream(xmlfile)
      self.doc = self.builder.parse(self.input)
      self.input.close()
      return self.doc
    except:
      print "exception: %s" % traceback.format_exception(*sys.exc_info())

  def createAttr(self,doc,tag,attr,value):
    try:
      import sys, traceback
      newAttribute= doc.createAttribute(attr)
      newAttribute.setValue('%s' % value)
      tag.setAttributeNode(newAttribute)
    except:
      print "exception: %s" % traceback.format_exception(*sys.exc_info())

  def getElementByAttributeName(self,root,tag,attr,val):
  
    element = root.getElementsByTagName(tag)
  
    i=0
    while i < element.getLength():
      if element.item(i).getAttribute(val) == attr:
        return element.item(i)
      i += 1

def xml_add_text_node(doc,parent,name,ntext):

  node = doc.createElement(name)
  text = doc.createTextNode('%s' % ntext)
  node.appendChild(text)
  parent.appendChild(node)

def xml_create_report(pname,type,path,info,misc,testdir,report):

  xml=xmldoc_service()
        
  builder = xml.createBlankDocument()
  
  doc = builder.newDocument()
  root = doc.createElement("qa")
  doc.appendChild(root)
  ft = doc.createElement("%s" % type)
  root.appendChild(ft);
  
  # Identification
  id = doc.createElement("identification")
  ft.appendChild(id)

  sut = doc.createElement("sut")
  xml.createAttr(doc,sut,"product","opends")
  id.appendChild(sut)

  xml_add_text_node(doc,sut,'name',pname)
  xml_add_text_node(doc,sut,'path',path)
  xml_add_text_node(doc,sut,'version',info['server version'])
  xml_add_text_node(doc,sut,'buildid',info['server buildid'])      
  xml_add_text_node(doc,sut,'revision',info['svn revision'])
  xml_add_text_node(doc,sut,'hostname',info['system name'])
  xml_add_text_node(doc,sut,'platform',info['system os'])
  xml_add_text_node(doc,sut,'jvm-version',info['jvm version'])  
  xml_add_text_node(doc,sut,'jvm-label',misc['jvm label'])
  xml_add_text_node(doc,sut,'jvm-vendor',info['jvm vendor'])
  xml_add_text_node(doc,sut,'jvm-arch',info['jvm architecture'])
  xml_add_text_node(doc,sut,'jvm-args','TBD')
  xml_add_text_node(doc,sut,'jvm-home','TBD')
  xml_add_text_node(doc,sut,'jvm-bin','TDB')
  xml_add_text_node(doc,sut,'os-label',misc['os label'])
  xml_add_text_node(doc,sut,'server-package',misc['server package'])
  xml_add_text_node(doc,sut,'snmp-jarfile',misc['snmp jarfile'])
  xml_add_text_node(doc,sut,'md5-sum','TBD')

  xml_add_text_node(doc,id,'tests-dir',testdir)

  # Test Results
  results = doc.createElement("results")
  ft.appendChild(results)
  
  xml.writeXMLfile(doc,report)

class group_to_run:
  def __init__(self, name):
    self.name = name

  def getName(self):
    return self.name

def get_last_attr_from_entry(result,attribute):

  changeEntry=result[0][1].split("\n")

  attrVal=''
  for changeAttr in changeEntry:
    #print changeAttr
    if changeAttr.startswith(attribute):
      #print 'get_last_attr_from_entry: %s' % changeAttr
      attrVal = changeAttr.replace('%s: ' % attribute,'')

  if attrVal != '' and attrVal[len(attrVal)-1] == ';' :
    lastAttr = attrVal[0:len(attrVal)-1]
  else:
    lastAttr = attrVal

  print 'get_last_attr_from_entry: %s' % lastAttr
  return lastAttr

def list_matches(mylist):

  mycomp = 'True'
  itemnum = 0

  for item in mylist:
    if not item:
      # TODO: list item is empty do WARNING or ERROR
      print "list_matches: WARNING: list item %s is empty." % itemnum
    if item != mylist[0]:
      print "list_matches: False. Match=(%s), Item=(%s)" % (mylist[0],item)
      mycomp = 'False'
    itemnum += 1

  return mycomp

def count_attr(result):

  attrnum = 0
  if result != None:

    for attr in result[0][1].split('\n'):
      if attr.startswith('dn:'):
        print "Hit: attr (%s)" % attr
        attrnum += 1

  return attrnum

def host_is_localhost(hostname):
  from socket import gethostbyname
  if gethostbyname(hostname).startswith('127.0'):
    return 1
  else:
    return 0
  
def hosts_are_same(hostname1,hostname2):
  from socket import gethostbyname
  if hostname1 == hostname2:
    return 1
  else:
    return 0
  
def md5_hash():
  try:
    import hashlib
    m = hashlib.md5()
  except ImportError:
    import md5
    m = md5.new()
  return m
  
def value_not_string(value):
  from org.python.core import PyString,PyList,PyDictionary
  try:
    from org.python.core import PyUnicode
  except ImportError:
    # Unicode is NOT supported in this version of Jython
    print "WARNING: Jython version does not support Unicode."
    if value.__class__ is not PyString:
      return 1
    else:
      return 0

  # Unicode is supported in this version of Jython  
  if value.__class__ is not PyString and value.__class__ is not PyUnicode:
    return 1
  else:
    return 0

def get_system_uid():
  from java.lang import System
  return System.getProperty("user.name")

def date_compare(date1,date2):
  if date1.compareTo(date2) > 0:
    return "Greater"
  elif date1.compareTo(date2) < 0:
    return "Less"
  else:
    return "Equal"

def tail_logfile(log_file,from_time):
  from java.text import SimpleDateFormat
  import re
  
  pattern=re.compile("\[(.*)\]")
  formatter = SimpleDateFormat("dd/MMM/yy:H:m:s Z")
          
  for line in log_file[1].split('\n'):
    mymatch = pattern.match(line)
    if mymatch:
      timestamp=mymatch.group(1)
      timestamp_object = formatter.parse(timestamp)
      if date_compare(from_time,timestamp_object) == 'Less':
        print line
