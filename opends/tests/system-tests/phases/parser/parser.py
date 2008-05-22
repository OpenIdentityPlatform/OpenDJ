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


from java.io import File
from java.io import StringReader
from org.xml.sax import InputSource
from org.xml.sax import SAXParseException
from org.xml.sax.helpers import DefaultHandler
from javax.xml.parsers import DocumentBuilderFactory
from javax.xml.parsers import DocumentBuilder
from org.w3c.dom import Document
from org.w3c.dom import Element
from org.w3c.dom import Node
from org.w3c.dom import NodeList


#============================================================================
#
# GLOBAL VARIABLES
# Warning : some of global variables are also defined in main_run.xml file

NOT_DEFINED = 'ERROR_not_defined'
TRUE = 0
FALSE = 1

#============================================================================
#
# CLASS DEFINITION


#
# Class for phases
#
class Phase:
  "Describes the phase of a system test run"
  def __init__(self, name):
    self.name       = name
    self.run        = 'false'
    self.start      = NOT_DEFINED
    self.stop       = NOT_DEFINED
    self.errNum     = NOT_DEFINED
    self.percentage = NOT_DEFINED
    
  def getName(self):
    return self.name
    
  def setRun(self,run):
    self.run = run
    
  def getRun(self):
    return self.run
    
  def setStartTime(self,start):
    self.start = start
    
  def getStartTime(self):
    return self.start
    
  def setStopTime(self,stop):
    self.stop = stop
    
  def getStopTime(self):
    return self.stop
    
  def setErrNum(self,errNum):
    self.errNum = errNum
    
  def getErrNum(self):
    return self.errNum
    
  def setPercentage(self,percentage):
    self.percentage = percentage
    
  def getPercentage(self):
    return self.percentage



#
# Class for suffix
#

###########################
class Scenario:
  "Describes the scenario main informations"
  def __init__(self, name, description):
    self.name         = name
    self.description  = description
    self.durationUnit = NOT_DEFINED
    self.durationTime = NOT_DEFINED
    
  def getName(self):
    return self.name
    
  def getDescription(self):
    return self.description
    
  def getDurationUnit(self):
    return self.durationUnit
    
  def setDurationUnit(self,durationUnit):
    self.durationUnit = durationUnit
    
  def getDurationTime(self):
    return self.durationTime
    
  def setDurationTime(self,durationTime):
    self.durationTime = durationTime


###########################
class SubordinateTemplate:
  "Describes entry data information for ldif file"
  def __init__(self, cType, cNb):
    self.cType = cType
    self.cNb   = cNb
    
  def getType(self):
    return self.cType
    
  def getNb(self):
    return self.cNb


###########################
class Branch:
  """Describes branch data information for ldif file :
   [name, [list_of_branch], [list_of_leaf]]"""
  def __init__(self, name, subtrees, subordinateTemplates):
    self.name = name
    self.subtrees = subtrees
    self.subordinateTemplates = subordinateTemplates
  
  def getName(self):
    return self.name
    
  def getSubtrees(self):
    return self.subtrees
    
  def getSubordinateTemplates(self):
    return self.subordinateTemplates


###########################
class SuffixTopology:
  "Describes the topology (list of instances,..) of a suffix"
  def __init__(self, name, initRule, instanceSourceName):
    self.name               = name
    self.initRule           = initRule
    self.instanceSourceName = instanceSourceName
    self.instanceRef        = NOT_DEFINED
    
  def getName(self):
    return self.name
    
  def getInstanceRef(self):
    return self.instanceRef
    
  def setInstanceRef(self,instanceRef):
    self.instanceRef = instanceRef
    
  def getInitRule(self):
    return self.initRule
    
  def getInstanceSourceName(self):
    return self.instanceSourceName


###########################
class Suffix:
  """Describes suffix information, 
   tree is a list of Branch objectclasses
   topology is a list of SuffixTopology objectclasses"""
  def __init__(self, sid, dn, topology, nbOfEntries, tree, ldifFile):
    self.sid         = sid
    self.dn          = dn
    self.topology    = topology
    self.nbOfEntries = nbOfEntries
    self.tree        = tree
    self.ldifFile    = ldifFile
  
  def getId(self):
    return self.sid
    
  def getSuffixDn(self):
    return self.dn
    
  def getTopology(self):
    return self.topology
    
  def setTopology(self,topology):
    self.topology = topology
    
  def getElementFromTopology(self, instanceName):
    "Return the instance objectclass of the instance name given in parameter"
    found = FALSE
    for cElement in self.topology:
      if cElement.getName() == instanceName:
        found = TRUE
        break
    return [found,cElement]
    
  def getNbOfEntries(self):
    return self.nbOfEntries
    
  def getTree(self):
    return self.tree
    
  def getLdifFile(self):
    return self.ldifFile


#
# Class for opendsTuning 
#

###########################
class OpendsTuning:
  "Describes tuning informations for OpenDS instance"
  def __init__(self,isJava,xms,xmx,xxNewSize,xxMaxNewSize,\
               xxSurvivorRatio,xxPermSize,xxMaxPermSize,xxUseConcMarkSweepGC,\
               databaseCachePercentage,replicationPurgeDelay):
    self.isJava                  = isJava
    self.javaArgs                = NOT_DEFINED
    self.xms                     = xms
    self.xmx                     = xmx
    self.xxNewSize               = xxNewSize
    self.xxMaxNewSize            = xxMaxNewSize
    self.xxSurvivorRatio         = xxSurvivorRatio
    self.xxPermSize              = xxPermSize
    self.xxMaxPermSize           = xxMaxPermSize
    self.xxUseConcMarkSweepGC    = xxUseConcMarkSweepGC
    self.databaseCachePercentage = databaseCachePercentage
    self.replicationPurgeDelay   = replicationPurgeDelay
    
  def getIsJava(self):
    return self.isJava
    
  def getJavaArgs(self):
    return self.javaArgs
    
  def setJavaArgs(self,javaArgs):
    self.javaArgs = javaArgs
    
  def getXms(self):
    return self.xms
    
  def getXmx(self):
    return self.xmx
    
  def getXxNewSize(self):
    return self.xxNewSize
    
  def getXxMaxNewSize(self):
    return self.xxMaxNewSize
    
  def getXxSurvivorRatio(self):
    return self.xxSurvivorRatio
    
  def getXxPermSize(self):
    return self.xxPermSize
    
  def getXxMaxPermSize(self):
    return self.xxMaxPermSize
    
  def getXxUseConcMarkSweepGC(self):
    return self.xxUseConcMarkSweepGC
    
  def getDatabaseCachePercentage(self):
    return self.databaseCachePercentage
    
  def getReplicationPurgeDelay(self):
    return self.replicationPurgeDelay

#
# Class for instance 
#

###########################
class Instance:
  "Describes a generic LDAP instance"
  def __init__(self, iid, name, product, role, host, installDir, tarball,\
               portLDAP):
    self.iid         = iid
    self.name        = name
    self.product     = product
    self.role        = role
    self.host        = host
    self.installDir  = installDir
    self.tarball     = tarball
    self.portLDAP    = portLDAP
    self.os          = NOT_DEFINED
    self.buildId     = NOT_DEFINED
    self.binDir      = NOT_DEFINED
    self.synchroDate = NOT_DEFINED
    self.logDir      = NOT_DEFINED
    
  def getId(self):
    return self.iid
    
  def setId(self,iid):
    self.iid = iid
    
  def getName(self):
    return self.name
    
  def getProduct(self):
    return self.product
    
  def getRole(self):
    return self.role
    
  def getHost(self):
    return self.host
    
  def setHost(self,host):
    self.host = host
    
  def getInstallDir(self):
    return self.installDir
    
  def getTarball(self):
    return self.tarball
    
  def getLDAPPort(self):
    return self.portLDAP
    
  def getOs(self):
    return self.os
    
  def setOs(self,os):
    self.os = os
    
  def getBuildId(self):
    return self.buildId
    
  def setBuildId(self,buildId):
    self.buildId = buildId
    
  def getBinDir(self):
    return self.binDir
    
  def setBinDir(self,binDir):
    self.binDir = binDir
    
  def getSynchroDate(self):
    return self.synchroDate
    
  def setSynchroDate(self,synchroDate):
    self.synchroDate = synchroDate
    
  def getLogDir(self):
    return self.logDir
    
  def setLogDir(self,logDir):
    self.logDir = logDir

###########################
class OpendsInstance(Instance):
  "Describes an opends Instance"
  def __init__(self, iid, name, product, role, host, installDir, tarball, \
               portLDAP, portLDAPS, portJMX, portREPL, \
               sslEnabled, certificate, startTlsEnabled, \
               secureReplication,tuning):
    # from instance object
    self.iid             = iid
    self.name            = name
    self.product         = product
    self.role            = role
    self.host            = host
    self.installDir      = installDir
    self.tarball         = tarball
    self.portLDAP        = portLDAP
    # specific to opends instance
    self.portLDAPS       = portLDAPS
    self.portJMX         = portJMX
    self.portREPL        = portREPL
    self.javaVersion     = NOT_DEFINED
    self.sslEnabled      = sslEnabled
    self.certificate     = certificate
    self.startTlsEnabled = startTlsEnabled
    self.secureReplication = secureReplication
    self.tuning          = tuning
    
  def getLDAPSPort(self):
    return self.portLDAPS
    
  def getJMXPort(self):
    return self.portJMX
    
  def getREPLPort(self):
    return self.portREPL
    
  def getJavaVersion(self):
    return self.javaVersion
    
  def setJavaVersion(self,javaVersion):
    self.javaVersion = javaVersion
    
  def getIsSslEnabled(self):
    return self.sslEnabled
    
  def getCertificate(self):
    return self.certificate
    
  def getIsStartTlsEnabled(self):
    return self.startTlsEnabled
  
  def getSecureReplication(self):
    return self.secureReplication
  
  def getTuning(self):
    return self.tuning

#
# Class for client
#

###########################
class Client:
  "Describes a ldap client"
  def __init__(self, iid, name, host, params):
    self.iid        = iid
    self.name       = name
    self.host       = host
    self.params     = params
    self.start      = NOT_DEFINED
    self.stop       = NOT_DEFINED
    self.dependency = NOT_DEFINED
    self.result     = NOT_DEFINED
    self.logDir     = NOT_DEFINED
    self.startDate  = NOT_DEFINED
    self.stopDate   = NOT_DEFINED
    
  def getId(self):
    return self.iid
    
  def getName(self):
    return self.name
    
  def getHost(self):
    return self.host
    
  def setHost(self,host):
    self.host = host
    
  def getParams(self):
    return self.params
    
  def getStart(self):
    return self.start
    
  def setStart(self,start):
    self.start = start
    
  def getStop(self):
    return self.stop
    
  def setStop(self,stop):
    self.stop = stop
    
  def getDependency(self):
    return self.dependency
    
  def setDependency(self,dependency):
    self.dependency = dependency
    
  def getResult(self):
    return self.result
    
  def setResult(self,result):
    self.result = result
    
  def getLogDir(self):
    return self.logDir
    
  def setLogDir(self,logDir):
    self.logDir = logDir
    
  def getStartDate(self):
    return self.startDate
    
  def setStartDate(self,startDate):
    self.startDate = startDate
    
  def getStopDate(self):
    return self.stopDate
    
  def setStopDate(self,stopDate):
    self.stopDate = stopDate


###########################
class Module:
  "Describes a module that contains clients"
  def __init__(self, name, enabled, clients):
    self.name    = name
    self.enabled = enabled
    self.clients = clients
    
  def getName(self):
    return self.name
    
  def getEnabled(self):
    return self.enabled
    
  def getClients(self):
    return self.clients


#============================================================================
#
# FUNCTIONS
#


def _getPropValue(myNode):
  "This function get the first node text value of a node"
  try:
    propValueNode = myNode.getFirstChild()
    if (propValueNode.getNodeType() == Node.TEXT_NODE or
        propValueNode.getNodeType() == Node.COMMENT_NODE):
      #out = '%s' % (myNode.getNodeName())
      out = '%s' % (propValueNode.getNodeValue())
    else:
     out = 'ERROR node has not a text children node type or is empty, should be' % \
          (myNode.getNodeName())
  except AttributeError:
    out = NOT_DEFINED
  return out

def _getAttributeNode(myNode,myAttributeName):
  cAttrNodes = myNode.getAttributes()
  cAttrNode = cAttrNodes.getNamedItem(myAttributeName)
  if (myNode.hasAttributes() and cAttrNode != None):
    cAttrValue = cAttrNode.getNodeValue()
  else:
    cAttrValue = NOT_DEFINED
  return cAttrValue



#============================================================================
#
# PARSER
#
def main(document):
  
  #document = STAXResult
  
  # Change code here to parse the document as you desire.
  # The code shown here is just an example for parsing a STAX xml document
  
  root = document.getDocumentElement()
  children = root.getChildNodes()
  
  msg              = ''
  globalParameters = []
  instances        = []
  suffix           = []
  clientPhases     = []
  cId              = 0
  
  for i in range(children.getLength()):
    cId += 1
    thisChild = children.item(i)
    
    #
    # Parsing global parameters node
    #
    if (thisChild.getNodeType() == Node.ELEMENT_NODE and
        thisChild.getNodeName() == 'globalParameters'):
      
      result = parseGlobalParameters(thisChild)
      msg = '%s \n %s' % (msg,result[0])
      scenario   = result[1]
      opendsZip  = result[2]
      domain     = result[3]
    
    #
    # Parsing instance node
    #
    elif (thisChild.getNodeType() == Node.ELEMENT_NODE and
        thisChild.getNodeName() == 'instance'):
      
      cName = _getAttributeNode(thisChild,'name')
      if cName == NOT_DEFINED:
        msg = '%s\n ERROR: cant get instance name attribute, required' % msg
        return [msg,[],[]]
      cProduct = _getAttributeNode(thisChild,'product')
      if cProduct == NOT_DEFINED:
        msg = '%s\n ERROR: cant get instance product attribute, required'% msg
        return [msg,[],[]]
      cRole = _getAttributeNode(thisChild,'role')
      if cRole == NOT_DEFINED:
        msg = '%s\n ERROR: cant get instance role attribute, required'% msg
        return [msg,[],[]]
      
      #
      # opends instance parsing
      #
      if cProduct == 'opends':
        result = parseOpenDs(cId,cName,cProduct,cRole,opendsZip,thisChild)
        msg = '%s \n %s' % (msg,result[0])
        instances.append(result[1])
      
      else:
        msg = '%s\n ERROR: unknown product %s' % (msg, cProduct)
    
    
    #
    # Parsing suffix node
    #
    elif (thisChild.getNodeType() == Node.ELEMENT_NODE and
        thisChild.getNodeName() == 'suffix'):
      
      cSuffixName = _getAttributeNode(thisChild,'dn')
      
      result = parseSuffix(cId,cSuffixName,thisChild)
      msg = '%s \n %s' % (msg,result[0])
      # restriction 1 suffix node
      suffix = result[1]
    
    
    #
    # Parsing scheduler node : should be only one node
    #
    elif (thisChild.getNodeType() == Node.ELEMENT_NODE and
        thisChild.getNodeName() == 'scheduler'):
      
      result       = parseScheduler(thisChild)
      msg          = '%s \n %s' % (msg,result[0])
      phases       = result[1]
      durationUnit = result[2]
      durationTime = result[3]
    
    
    #
    # Parsing other nodes...
    #
    elif thisChild.getNodeType() == Node.COMMENT_NODE:
      # Do nothing
      continue
    
    elif thisChild.getNodeType() == Node.ELEMENT_NODE:
      msg = '%s\n Found %s element' % (msg, thisChild.getNodeName())
  
  
  # End of parsing
  
  
  #
  # Set duration time and unit to scenario object
  #
  scenario.setDurationTime(durationTime)
  scenario.setDurationUnit(durationUnit)
  
  #
  # Set replication server host/port/installdir used by this suffix
  #
  for cSuffixInstance in suffix.getTopology():
    for cInstance in instances:
      if cInstance.getName() == cSuffixInstance.getName():
        cSuffixInstance.setInstanceRef(cInstance)
      
  return [msg,instances,suffix,phases,scenario,domain]
  # end of main function  
  
  
  
  
#============================================================================
#
# Parse children and get information for opends instance 
#
def parseOpenDs(cId,cName,cProduct,cRole,opendsZip,thisChild):
  msg              = ''
  cHost            = 'localhost'
  cInstallDir      = NOT_DEFINED
  cPortLDAP        = '1389'
  cPortLDAPS       = '1636'
  cPortJMX         = '1390'
  cPortREPL        = '1391'
  cSslEnabled      = 'false'
  cCertificate     = NOT_DEFINED
  cStartTlsEnabled = 'false'
  cSecureReplication  = 'false'
  cIsJava          = 'false'
  cXms             = NOT_DEFINED
  cXmx             = NOT_DEFINED
  cXxNewSize       = NOT_DEFINED
  cXxMaxNewSize    = NOT_DEFINED
  cXxSurvivorRatio = NOT_DEFINED
  cXxPermSize      = NOT_DEFINED
  cXxMaxPermSize   = NOT_DEFINED
  cXxUseConcMarkSweepGC       = NOT_DEFINED
  cDatabaseCachePercentage    = NOT_DEFINED
  cReplicationPurgeDelay      = NOT_DEFINED
  cReplicationPurgeDelayUnit  = NOT_DEFINED
  
  #
  # Parsing second level : host,ports,...
  #
  if thisChild.hasChildNodes():
    
    subChildren = thisChild.getChildNodes()
    
    for j in range(subChildren.getLength()):
      
      thisSubChild = subChildren.item(j)
      
      if (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'host'):
        cHost = _getPropValue(thisSubChild)
        
      elif (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'installDir'):
        cInstallDir = _getPropValue(thisSubChild)
        
      elif (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'ports'):
          
        # Get instance port values
        if thisSubChild.hasChildNodes():
          portList = thisSubChild.getChildNodes()
          for k in range(portList.getLength()):
            thisPort = portList.item(k)
            if (thisPort.getNodeType() == Node.ELEMENT_NODE and
                thisPort.getNodeName() == 'ldap'):
              cPortLDAP = _getPropValue(thisPort)
            elif (thisPort.getNodeType() == Node.ELEMENT_NODE and
                thisPort.getNodeName() == 'ldaps'):
              cPortLDAPS = _getPropValue(thisPort)
            elif (thisPort.getNodeType() == Node.ELEMENT_NODE and
                thisPort.getNodeName() == 'jmx'):
              cPortJMX = _getPropValue(thisPort)
            elif (thisPort.getNodeType() == Node.ELEMENT_NODE and
                thisPort.getNodeName() == 'replicationServer'):
              cPortREPL = _getPropValue(thisPort)
            # must be at the end of the if case
            elif (thisPort.getNodeType() == Node.TEXT_NODE or
                  thisPort.getNodeType() == Node.COMMENT_NODE):
              # text node information,skip, no need
              continue
            else:
              msg = '%s\n ERROR: instance %s : unknown port node name %s' % \
                    (msg, cName, thisPort.getNodeName())
      
      elif (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'security'):
      
        cSslEnabled      = _getAttributeNode(thisSubChild,'sslEnabled')
        cCertificate     = _getAttributeNode(thisSubChild,'certificate')
        cStartTlsEnabled = _getAttributeNode(thisSubChild,'startTlsEnabled')
        cSecureReplication = _getAttributeNode(thisSubChild,'secureReplication')
      
      elif (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'tuning'):
          
        # Get tuning values
        if thisSubChild.hasChildNodes():
          tuningList = thisSubChild.getChildNodes()
          for k in range(tuningList.getLength()):
            thisTuning = tuningList.item(k)
            if (thisTuning.getNodeType() == Node.ELEMENT_NODE and
                thisTuning.getNodeName() == 'java'):
              cIsJava = 'true'
              cXms = _getAttributeNode(thisTuning,'xms')
              cXmx = _getAttributeNode(thisTuning,'xmx')
              cXxNewSize = _getAttributeNode(thisTuning,'xxNewSize')
              cXxMaxNewSize = _getAttributeNode(thisTuning,'xxMaxNewSize')
              cXxSurvivorRatio = _getAttributeNode(thisTuning,
                                                   'xxSurvivorRatio')
              cXxPermSize = _getAttributeNode(thisTuning,'xxPermSize')
              cXxMaxPermSize = _getAttributeNode(thisTuning,'xxMaxPermSize')
              cXxUseConcMarkSweepGC = _getAttributeNode(thisTuning,
                                                        'xxUseConcMarkSweepGC')
              
            elif (thisTuning.getNodeType() == Node.ELEMENT_NODE and
                thisTuning.getNodeName() == 'databaseCachePercentage'):
              cDatabaseCachePercentage = _getPropValue(thisTuning)
              
            elif (thisTuning.getNodeType() == Node.ELEMENT_NODE and
                thisTuning.getNodeName() == 'replicationPurgeDelay'):
              cReplicationPurgeDelayUnit = _getAttributeNode(thisTuning,'unit')
              if cReplicationPurgeDelayUnit == NOT_DEFINED:
                msg = '%s\n ERROR: instance %s: unknown unit purge delay'%msg
              cReplicationPurgeDelay = _getPropValue(thisTuning)
              cReplicationPurgeDelay = '%s %s' % (cReplicationPurgeDelay,\
                                                  cReplicationPurgeDelayUnit)
              
      # must be at the end of the if case
      elif (thisSubChild.getNodeType() == Node.TEXT_NODE or
            thisSubChild.getNodeType() == Node.COMMENT_NODE):
        # text node information,skip, no need
        continue
      else:
        msg = '%s\n ERROR: instance %s : unknown instance property named %s' %\
              (msg, cName, thisSubChild.getNodeName())
        
    # end for
  #
  # no second level
  #
  else:
    msg = '%s\n ERROR: instance %s : no children for instance node' % \
          (msg,cName)
  
  
  cOpendsTuning = OpendsTuning(cIsJava,cXms,cXmx,cXxNewSize,cXxMaxNewSize,\
                              cXxSurvivorRatio,cXxPermSize,cXxMaxPermSize,\
                              cXxUseConcMarkSweepGC,\
                              cDatabaseCachePercentage,\
                              cReplicationPurgeDelay)
  
  #
  # extract the name of zip and add it to the installDir path
  #
  # 1. Remove the file path
  _list = opendsZip.split('/')
  _file = _list.pop()
  # 2. Get the name of the file without the extension
  _fileName  = _file.split('.')
  _extension = _fileName.pop()
  _fileName  = '.'.join(_fileName)
  
  cInstallDir = '%s/%s/%s' % (cInstallDir,cName,_fileName)
  return [msg,OpendsInstance(cId,cName,cProduct,cRole,cHost,cInstallDir,\
                             opendsZip,\
                             cPortLDAP,cPortLDAPS,cPortJMX,cPortREPL,\
                             cSslEnabled,cCertificate,cStartTlsEnabled,\
                             cSecureReplication,cOpendsTuning)]




#============================================================================
#
# Parse children and get information for suffix node 
#
def parseSuffix(cId,cSuffixName,thisChild):
  msg                = ''
  cSuffixReplServers = NOT_DEFINED
  cNbOfEntries       = NOT_DEFINED
  cBranches          = NOT_DEFINED
  cLdifFile          = NOT_DEFINED
  
  #
  # Parsing second level : instanceList,numberOfEntries,...
  #
  if thisChild.hasChildNodes():

    subChildren = thisChild.getChildNodes()

    for j in range(subChildren.getLength()):
    
      cInstanceSourceName = NOT_DEFINED
      thisSubChild = subChildren.item(j)
        
      if (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'topology'):
        
        cSuffixReplServers = []
        
        # Get instance names
        if thisSubChild.hasChildNodes():
          instanceList = thisSubChild.getChildNodes()
          for k in range(instanceList.getLength()):
            thisInstance = instanceList.item(k)
            
            if (thisInstance.getNodeType() == Node.ELEMENT_NODE and
                thisInstance.getNodeName() == 'element'):
              cInstanceName = _getAttributeNode(thisInstance,'instanceName')
              cInstanceInitRule = _getAttributeNode(thisInstance,'initRule')
              cInstanceSourceName = _getAttributeNode(thisInstance, \
                                                      'instanceSourceName')
              if cInstanceInitRule.lower() == "totalupdate":
                if cInstanceSourceName == NOT_DEFINED:
                  msg = '%s\n ERROR: parseSuffix(): initRule=totalUpdate' % msg
                  msg = '%s, and should have also instanceSource attribute'%msg
                  msg = '%s which has been not found' % msg
              cSuffixReplServers.append(SuffixTopology(cInstanceName,\
                                                       cInstanceInitRule,\
                                                       cInstanceSourceName))
              
            # must be at the end of the if case
            elif (thisInstance.getNodeType() == Node.TEXT_NODE or
                  thisInstance.getNodeType() == Node.COMMENT_NODE):
              # text node information,skip, no need
              continue
            else:
              msg = '%s\n ERROR: parseSuffix(): unknown node named %s' % \
                    (msg, thisInstance.getNodeName())
              
        else:
          msg = '%s\n ERROR: parseSuffix(): %s node should have child node %s'%\
                (msg,thisSubChild.getNodeName())
          
      elif (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
            thisSubChild.getNodeName() == 'ldifFile'):
        cLdifFile = _getPropValue(thisSubChild)
        cLdifFile = cLdifFile.strip()
        
      # parsing suffix TREE
      elif (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
            thisSubChild.getNodeName() == 'tree'):
        
        if thisSubChild.hasChildNodes():
          cNbOfEntries = _getAttributeNode(thisSubChild,'nbOfEntries')
          cResult = getSuffixTree('root',cNbOfEntries,thisSubChild)
          cBranches = cResult[1]
          msg = '%s\n%s' % (msg,cResult[0])
          
        else:
          msg = '%s\n ERROR: parseSuffix(): %s node should have child node %s'%\
                (msg,thisSubChild.getNodeName())
          
          
      # must be at the end of the if case
      elif (thisSubChild.getNodeType() == Node.TEXT_NODE or
            thisSubChild.getNodeType() == Node.COMMENT_NODE):
        # text node information,skip, no need
        continue
      else:
        msg = '%s\n ERROR: parseSuffix(): unknown suffix property named %s' % \
              (msg, thisSubChild.getNodeName())
      
  #
  # no subchildren for suffix node
  #
  else:
    msg = '%s\n ERROR: parseSuffix() : no children for suffix node' % msg
  
  return [msg,Suffix(cId,cSuffixName,cSuffixReplServers,\
                     cNbOfEntries,cBranches,cLdifFile)]
  
  
  
#============================================================================
#
# Get tree information 
# a branch has :
#   a name
#   some branches (0 or more)
#   some subordinateTemplate (0 or more)
def getSuffixTree(cBranchName,nbOfEntries,cNode):
  msg = ''
  # cSubordinateTemplates is a list of subordinateTemplates a node can have
  cSubordinateTemplates = []
  # cBranches is a list of branches a node can have
  cBranches = []
  nodeList = cNode.getChildNodes()
  
  for n in range(nodeList.getLength()):
    
    thisNode = nodeList.item(n)
    
    if (thisNode.getNodeType() == Node.ELEMENT_NODE and
        thisNode.getNodeName() == 'branch'):
      
      cResult = getSuffixTree(_getAttributeNode(thisNode,'name'),\
                              nbOfEntries,thisNode)
      cBranches.append(cResult[1])
      msg = '%s\n%s' % (msg,cResult[0])
      
    # get subordinateTemplate if exist
    # if percentage, calculate the nb of entries, 
    # else get nb of entries directly
    elif (thisNode.getNodeType() == Node.ELEMENT_NODE and
          thisNode.getNodeName() == 'subordinateTemplate'):
      cPerc = NOT_DEFINED
      cType = _getAttributeNode(thisNode,'type')
      cPerc = _getAttributeNode(thisNode,'percentage')
      if cPerc != NOT_DEFINED:
        cPerc = (int(cPerc) * int(nbOfEntries)) / 100
      else:
        cPerc = _getAttributeNode(thisNode,'nb')
      cSubordinateTemplates.append(SubordinateTemplate(cType,cPerc))
      
    # must be at the end of the if case
    elif (thisNode.getNodeType() == Node.TEXT_NODE or
          thisNode.getNodeType() == Node.COMMENT_NODE):
      # text node information,skip, no need
      continue
    else:
      msg = '%s\n ERROR: getSuffixTree() : unknown node named %s' % \
            (msg, thisNode.getNodeName())
      
  if cBranches == []:
    cBranches = [NOT_DEFINED]
  if cSubordinateTemplates == []:
    cSubordinateTemplates = [NOT_DEFINED]
  
  return [msg,Branch(cBranchName,cBranches,cSubordinateTemplates)]



#============================================================================
#
# displayTree : display the data tree of a suffix with XML format
def getSuffixDataForXML(suffixName,cBranch):
  msg         = ''
  cBranchName = cBranch.getName()
  if cBranchName == 'root':
    msg = "%s\n <tree>" % msg
  else:
    msg = "%s\n <branch name=\"%s\">" %(msg,cBranchName)
  
  # leaf
  cLeafs = cBranch.getSubordinateTemplates()
  if (cLeafs[0] != NOT_DEFINED):
    for cLeaf in cLeafs:
      msg = '%s\n<subordinateTemplate type=\"%s\"' % (msg,cLeaf.getType())
      msg = '%s nb=\"%s\"></subordinateTemplate>'  % (msg,cLeaf.getNb())
  # subBranches
  subBranches = cBranch.getSubtrees()
  if subBranches[0] != NOT_DEFINED:
    for cSubBranch in subBranches:
      msg = '%s\n%s' % (msg,getSuffixDataForXML(suffixName,cSubBranch))
  
  
  if cBranchName == 'root':
    msg = "%s\n </tree>" % msg
  else:
    msg = "%s\n </branch>" % msg
  
  return msg


#============================================================================
#
# getSuffixDataForMakeLDIF : display the data tree of a suffix 
# using makedlif format
def getSuffixDataForMakeLDIF(suffixName,nbOfEntries,cBranch):
  msg = ''
  cBranchName = cBranch.getName()
  if cBranchName == 'root':
    cBranchName = suffixName
  
  msg = "%s\nbranch: %s " % (msg,cBranchName)
  
  # leaf
  cLeafs = cBranch.getSubordinateTemplates()
  if (cLeafs[0] != NOT_DEFINED):
    for cLeaf in cLeafs:
      msg = '%s\nsubordinateTemplate: %s:%s' % (msg,cLeaf.getType(), \
                                                cLeaf.getNb())
  
  # subBranches
  subBranches = cBranch.getSubtrees()
  if subBranches[0] != NOT_DEFINED:
    for cSubBranch in subBranches:
      msg = '%s\n%s' % (msg,\
            getSuffixDataForMakeLDIF(suffixName,nbOfEntries,cSubBranch))
  
  return msg



#============================================================================
#
# Parse children and get information for scheduler node 
#
def parseScheduler(thisChild):
  msg = ''
  scheduler = []
  
  #
  # Parsing second level : duration,module,...
  #
  if thisChild.hasChildNodes():
    
    subChildren = thisChild.getChildNodes()
    
    for j in range(subChildren.getLength()):
      
      cClients = NOT_DEFINED
      thisSubChild = subChildren.item(j)
      
      if (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'duration'):
        
        durationUnit = _getAttributeNode(thisSubChild,'unit')
        durationTime = _getPropValue(thisSubChild)
        
      elif (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'module'):
        
        cClients = []
        cModuleName = _getAttributeNode(thisSubChild,'name')
        cModuleEnabled = _getAttributeNode(thisSubChild,'enabled')
        
        result = parseClients(thisSubChild)
        msg = '%s \n %s' % (msg,result[0])
        cClients = result[1]
        
        scheduler.append(Module(cModuleName,cModuleEnabled,cClients))
        
      # must be at the end of the if case
      elif (thisSubChild.getNodeType() == Node.TEXT_NODE or
            thisSubChild.getNodeType() == Node.COMMENT_NODE):
        # text node information,skip, no need
        continue
      else:
        msg = '%s\n ERROR: parseScheduler() : unknown property named %s' % \
              (msg, thisSubChild.getNodeName())
  
  #
  # no subchildren
  #
  else:
    msg = '%s\n ERROR: parseScheduler() : no children for scheduler node' % msg
  
  return [msg,scheduler,durationUnit,durationTime]



#============================================================================
#
# Parse children and get information for a list of clients 
#
def parseClients(thisChild):
  msg = ''
  clients = []
  
  #
  # Parsing second level : phase,...
  #
  if thisChild.hasChildNodes():
    
    subChildren = thisChild.getChildNodes()
    
    for j in range(subChildren.getLength()):
    
      thisSubChild = subChildren.item(j)
      
      if (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'client'):
        
        name       = NOT_DEFINED
        host       = NOT_DEFINED
        start      = NOT_DEFINED
        stop       = NOT_DEFINED
        dependency = NOT_DEFINED
        clientId   = NOT_DEFINED
        params     = []
        
        name       = _getAttributeNode(thisSubChild,'name')
        host       = _getAttributeNode(thisSubChild,'host')
        start      = _getAttributeNode(thisSubChild,'start')
        stop       = _getAttributeNode(thisSubChild,'stop')
        dependency = _getAttributeNode(thisSubChild,'dependencyId')
        clientId   = _getAttributeNode(thisSubChild,'id')
        
        # Get phase sub-nodes information
        if thisSubChild.hasChildNodes():
          nodeList = thisSubChild.getChildNodes()
          for k in range(nodeList.getLength()):
            thisNode = nodeList.item(k)
            
            if (thisNode.getNodeType() == Node.ELEMENT_NODE):
              param = [ thisNode.getNodeName(), _getPropValue(thisNode) ]
              params.append(param)
            
            # must be at the end of the if case
            elif (thisNode.getNodeType() == Node.TEXT_NODE or
                  thisNode.getNodeType() == Node.COMMENT_NODE):
              # text node information,skip, no need
              continue
            else:
              msg = '%s\n ERROR: parseClients(): unknown node named %s' % \
                    (msg, thisNode.getNodeName())
          
        else:
          msg = '%s\n ERROR: parseClients(): %s node should have child %s' % \
                (msg,thisSubChild.getNodeName())
        
        cClient = Client(clientId,name,host,params)
        if (start == NOT_DEFINED):
          msg = '%s\n ERROR: parseClients(): client %s:' % (msg,name)
          msg = '%s start attribute required' % msg
        else:
          cClient.setStart(start)
        if (stop != NOT_DEFINED):
          cClient.setStop(stop)
        if (dependency != NOT_DEFINED):
          cClient.setDependency(dependency)
        
        clients.append(cClient)
        
      # must be at the end of the if case
      elif (thisSubChild.getNodeType() == Node.TEXT_NODE or
            thisSubChild.getNodeType() == Node.COMMENT_NODE):
        # text node information,skip, no need
        continue
      else:
        msg = '%s\n ERROR: parseClients() : unknown property named %s' % \
              (msg, thisSubChild.getNodeName())
      
  #
  # no subchildren for suffix node
  #
  else:
    msg = '%s\n ERROR: parseClients() : no children for clients node' % (msg)
  
  return [msg,clients]



#============================================================================
#
# Parse global parameters node 
#
def parseGlobalParameters(thisChild):
  msg        = ''
  result     = []
  scenario   = NOT_DEFINED
  opendsZip  = NOT_DEFINED
  domain     = NOT_DEFINED
  
  #
  # Parsing second level
  #
  if thisChild.hasChildNodes():
    
    subChildren = thisChild.getChildNodes()
    
    for j in range(subChildren.getLength()):
    
      thisSubChild = subChildren.item(j)
      
      if (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'opendsZip'):
        opendsZip = _getPropValue(thisSubChild)
        
      elif (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'domain'):
        domain = _getPropValue(thisSubChild)
        
      elif (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'scenario'):
        cResult = parseScenario(thisSubChild)
        scenario = cResult[1]
        msg = '%s\n%s' % (msg,cResult[0])
      
      
      # must be at the end of the if case
      elif (thisSubChild.getNodeType() == Node.TEXT_NODE or
            thisSubChild.getNodeType() == Node.COMMENT_NODE):
        # text node information,skip, no need
        continue
      else:
        msg = '%s\n ERROR: parseGlobalParameters(): unknown node named %s' % \
              (msg, thisSubChild.getNodeName())
        
  #
  # no subchildren for globalParameters node
  #
  else:
    msg = '%s\n ERROR: parseGlobalParameters(): no child for this node' % msg
    
    
  if (opendsZip == NOT_DEFINED):
    msg = '%s\n ERROR: parseGlobalParameters() : opendsZip not defined' % (msg)

  return [msg,scenario,opendsZip,domain]



#============================================================================
#
# Parse global parameters node 
#
def parseScenario(thisChild):
  preMsg      = 'ERROR: parseGlobalParameters():'
  msg         = ''
  result      = []
  name        = NOT_DEFINED
  description = NOT_DEFINED
  
  #
  # Parsing second level
  #
  if thisChild.hasChildNodes():
    
    subChildren = thisChild.getChildNodes()
    
    for j in range(subChildren.getLength()):
    
      thisSubChild = subChildren.item(j)
      
      if (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'name'):
        name = _getPropValue(thisSubChild)
        
      elif (thisSubChild.getNodeType() == Node.ELEMENT_NODE and
          thisSubChild.getNodeName() == 'description'):
        description = _getPropValue(thisSubChild)
        
      
      # must be at the end of the if case
      elif (thisSubChild.getNodeType() == Node.TEXT_NODE or
            thisSubChild.getNodeType() == Node.COMMENT_NODE):
        # text node information,skip, no need
        continue
      else:
        msg = '%s\n ERROR: parseGlobalParameters(): unknown node named %s' % \
              (msg, thisSubChild.getNodeName())
        
  #
  # no subchildren for globalParameters node
  #
  else:
    msg = '%s\n %s no child for this node' % (preMsg,msg)
    
    
  if (name == NOT_DEFINED):
    msg = '%s\n %s scenario name not defined' % (preMsg,msg)
  if (description == NOT_DEFINED):
    msg = '%s\n %s scenario description not defined' % (preMsg,msg)

  return [msg,Scenario(name,description)]



