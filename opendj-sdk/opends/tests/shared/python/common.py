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
__all__ = [ "format_testcase", "directory_server_information", "test_time", \
            "report_generation" , "compare_file" ]

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

  def transformReport(self,stylesheet,xml,output):
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

      self.transformer.transform(self.source, self.result)
    finally:
      self.xsl.close()
      self.xml.close()
      self.html.close()

  def transformSuitesReport(self,stylesheet,xml,output,params):
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

      self.myAttr  = basic_utils().printKey(params)
      self.myValue = basic_utils().printKeyValue(params)

      self.transformer.setParameter(self.myAttr, self.myValue)
      self.transformer.transform(self.source, self.result)
    finally:
      self.xsl.close()
      self.xml.close()
      self.html.close()

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
