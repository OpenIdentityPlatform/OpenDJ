<?xml version="1.0" encoding="ISO-8859-1"?>
<!--
 ! CDDL HEADER START
 !
 ! The contents of this file are subject to the terms of the
 ! Common Development and Distribution License, Version 1.0 only
 ! (the "License").  You may not use this file except in compliance
 ! with the License.
 !
 ! You can obtain a copy of the license at
 ! trunk/opends/resource/legal-notices/OpenDS.LICENSE
 ! or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 ! See the License for the specific language governing permissions
 ! and limitations under the License.
 !
 ! When distributing Covered Code, include this CDDL HEADER in each
 ! file and include the License file at
 ! trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 ! add the following below this CDDL HEADER, with the fields enclosed
 ! by brackets "[]" replaced with your own identifying information:
 !      Portions Copyright [yyyy] [name of copyright owner]
 !
 ! CDDL HEADER END
 !
 !      Copyright 2008 Sun Microsystems, Inc.
 ! -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:output 
  method="html"
  encoding="ISO-8859-1"
  doctype-public="-//W3C//DTD HTML 4.01//EN"
  doctype-system="http://www.w3.org/TR/html4/strict.dtd"
  indent="yes" />

<!-- ================= MAIN ================ -->
<xsl:template match="systemTestRoot">
  <html>
    <head>
      <link href="result.css" rel="stylesheet" type="text/css"></link>
    </head>
    <body>
    
    <script type="text/javascript" language="javascript">
      function ShowHideListElement(eSrc)
      {
      eSrc = document.getElementById(eSrc);
      eSrc.style.display = ("block" == eSrc.style.display ? "none" : "block");
      }
    </script>
    
    <h1>System Test Report</h1>
    <xsl:apply-templates select="summary" />
    <xsl:apply-templates select="topology"/>
    <xsl:apply-templates select="phase" />
  </body></html>
</xsl:template>

<!-- ================= Manage phase node ================ -->
<xsl:template match="phase">
    <p>
    <xsl:variable name="phaseName" select="normalize-space(@name)"/>
    <!-- Display title -->
    <h2>
      Phase 
      <font color="blue">
        <xsl:value-of select="$phaseName" />
      </font>
    <i>
      <xsl:text> started at </xsl:text>
      <xsl:value-of select="@date" />
      <xsl:choose>
        <xsl:when test="$phaseName = 'scheduler'">
          / duration <xsl:value-of select="@duration" />
        </xsl:when>
      </xsl:choose>
    </i>
    <br/>
    </h2>
    
    <!-- Call specific display depending on the phase -->
    <xsl:choose>
      <xsl:when test="$phaseName = 'installation'">
        <a name="installation"/>
        <xsl:call-template name="parsePhase">
          <xsl:with-param name="phaseName" select="'Installation'" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$phaseName = 'generateLdif'">
        <a name="generateLdif"/>
        <xsl:call-template name="parsePhase">
          <xsl:with-param name="phaseName" select="'GenerateLdif'" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$phaseName = 'preconfiguration'">
        <a name="preconfiguration"/>
        <xsl:call-template name="parsePhase">
          <xsl:with-param name="phaseName" select="'PreConfiguration'" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$phaseName = 'configuration'">
        <a name="configuration"/>
        <xsl:call-template name="parsePhase">
          <xsl:with-param name="phaseName" select="'Configuration'" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$phaseName = 'postconfiguration'">
        <a name="postconfiguration"/>
        <xsl:call-template name="parsePhase">
          <xsl:with-param name="phaseName" select="'PostConfiguration'" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$phaseName = 'scheduler'">
        <a name="scheduler"/>
        <xsl:call-template name="parsePhase">
          <xsl:with-param name="phaseName" select="'Scheduler'" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$phaseName = 'verdict'">
        <a name="verdict"/>
        <xsl:call-template name="parsePhase">
          <xsl:with-param name="phaseName" select="'Verdict'" />
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <span style="color: rgb(255, 0, 0); font-weight: bold;"> 
          ERROR :Unknown phase name <xsl:value-of select="$phaseName"/>
        </span>
      </xsl:otherwise>
    </xsl:choose>
    <br/>
     <xsl:apply-templates select="phaseResult" />
    </p>
</xsl:template>


<!-- ********************************************************************* -->
<!-- ***********************  SUMMARY PHASE   **************************** -->
<!-- ********************************************************************* -->
<xsl:template match="summary">
  <p>
    <a name="summary"/>
    <h2>Summary</h2>
    
    <xsl:apply-templates select="scenario" />
    
    <table id="summaryTable">
      <xsl:apply-templates select="phaseSummmary" />
    </table>
  </p>
 </xsl:template>
 
<!-- ================= Display scenario informations ============ -->
<xsl:template match="scenario">
  <b>Scenario name</b> : <xsl:value-of select="normalize-space(@name)"/> <br/>
  <b>Description</b> : <xsl:value-of 
                            select="normalize-space(.)"/> <br/>
  <b>Duration</b> : <xsl:value-of 
                            select="normalize-space(@duration)"/> <br/>
  <br/>
 </xsl:template>

<!-- ================= Display each phase status ================ -->
<xsl:template match="phaseSummmary">
  <tr><td>
    <xsl:variable name="name" select="normalize-space(@name)"/>
    <a href="#{$name}"><xsl:value-of select="$name" /> </a>
  </td>
  <td>
    <xsl:variable name="result" select="normalize-space(@result)"/>
    <b>
    <xsl:choose>
      <xsl:when test="$result='0'">
       <span class="pass">PASS</span>
      </xsl:when>
      <xsl:otherwise>
       <span class="fail">FAIL</span>
      </xsl:otherwise>
     </xsl:choose>
     </b>
  </td></tr>
</xsl:template>


<!-- ********************************************************************* -->
<!-- ***********************  TOPOLOGY PHASE     ************************* -->
<!-- ********************************************************************* -->

<!-- =================  ================ -->
<xsl:template match="topology">
  <p>
    <a name="topology"/>
    <h2>Topology</h2>
    <xsl:apply-templates select="instances" />
    <xsl:apply-templates select="suffixes" />
    <xsl:apply-templates select="schedulerParser" />
    <xsl:call-template name="parseChildPhase" />
  </p>
</xsl:template>

<!-- =================  ================ -->
<xsl:template match="instances">
  
  <table>
  <th> </th>
  <xsl:for-each select="child::*">
    <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
    <xsl:choose>
      <xsl:when test="$nodeName = 'instance'">
        <th><xsl:value-of select="normalize-space(@name)"/></th>
      </xsl:when>
    </xsl:choose>
  </xsl:for-each>
  <!-- PRODUCT -->
  <tr>
    <td class="bgcolth"> Product </td>
    <xsl:for-each select="child::*">
    <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
    <xsl:choose>
      <xsl:when test="$nodeName = 'instance'">
          <td><xsl:value-of select="normalize-space(@product)"/></td>
      </xsl:when>
    </xsl:choose>
    </xsl:for-each>
  </tr>
  <!-- BUILD -->
  <tr>
    <td class="bgcolth"> Build </td>
    <xsl:for-each select="child::*">
    <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
    <xsl:choose>
      <xsl:when test="$nodeName = 'instance'">
          <td><xsl:value-of select="normalize-space(buildId)"/></td>
      </xsl:when>
    </xsl:choose>
    </xsl:for-each>
  </tr>
  <!-- HOST -->
  <tr>
    <td class="bgcolth"> Host </td>
    <xsl:for-each select="child::*">
    <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
    <xsl:choose>
      <xsl:when test="$nodeName = 'instance'">
          <td><xsl:value-of select="normalize-space(host)"/></td>
      </xsl:when>
    </xsl:choose>
    </xsl:for-each>
  </tr>
  <!-- OS -->
  <tr>
    <td class="bgcolth"> OS </td>
    <xsl:for-each select="child::*">
    <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
    <xsl:choose>
      <xsl:when test="$nodeName = 'instance'">
          <td><xsl:value-of select="normalize-space(os)"/></td>
      </xsl:when>
    </xsl:choose>
    </xsl:for-each>
  </tr>
  <!-- JAVA -->
  <tr>
    <td class="bgcolth"> Build </td>
    <xsl:for-each select="child::*">
    <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
    <xsl:choose>
      <xsl:when test="$nodeName = 'instance'">
          <td><xsl:value-of select="normalize-space(jvm)"/></td>
      </xsl:when>
    </xsl:choose>
    </xsl:for-each>
  </tr>
  <!-- LDAP PORT -->
  <tr>
    <td class="bgcolth"> Ldap Port </td>
    <xsl:for-each select="child::*">
    <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
    <xsl:choose>
      <xsl:when test="$nodeName = 'instance'">
          <td><xsl:value-of select="normalize-space(port/ldap)"/></td>
      </xsl:when>
    </xsl:choose>
    </xsl:for-each>
  </tr>
  <!-- SYNCHRO DATE -->
  <tr>
    <td class="bgcolth"> Date 4 synchronization </td>
    <xsl:for-each select="child::*">
    <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
    <xsl:choose>
      <xsl:when test="$nodeName = 'instance'">
          <td><xsl:value-of select="normalize-space(synchroDate)"/></td>
      </xsl:when>
    </xsl:choose>
    </xsl:for-each>
  </tr>

  </table><br/><br/>

<!--
  <table>
      <th> Instance </th>
      <th> Product </th>
      <th> Build </th>
      <th> Host </th>
      <th> OS </th>
      <th> Jvm </th>
      <th> LDAP port </th>
      <th> InstallDir </th>
-->  
  <!-- child is instance node only -->
<!--
  <xsl:for-each select="child::*">
    <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
    <xsl:choose>
      <xsl:when test="$nodeName = 'instance'">
        <tr class="bgcol1">
          <td><xsl:value-of select="normalize-space(@name)"/> </td>
          <td><xsl:value-of select="normalize-space(@product)"/></td>
          <td><xsl:value-of select="normalize-space(buildId/.)"/></td>
          <td><xsl:value-of select="normalize-space(host/.)"/> </td>
          <td><xsl:value-of select="normalize-space(os/.)"/> </td>
          <td><xsl:value-of select="normalize-space(javaVersion/.)"/> </td>
          <td><xsl:value-of select="normalize-space(port/ldap/.)"/></td>
          <td><xsl:value-of select="normalize-space(installDir/.)"/></td>
        </tr>
      </xsl:when>
      <xsl:otherwise>
        <tr>
          <td>
            <span style="color: rgb(255, 0, 0); font-weight: bold;">
                  ERROR :Unknown phase name <xsl:value-of select="$nodeName"/>
                  using product 
            </span>
          </td>
        </tr>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:for-each>
  </table> <br/><br/>
-->
</xsl:template>

<!-- ================= Manage suffixes node ================ -->
<xsl:template match="suffixes">
  <table>
    <tr>
      <th>Suffix name</th>
      <th>Nb of Entries</th>
      <th>Instances / init rule</th>
    </tr>
    
    <!-- child is instance node only -->
    <xsl:for-each select="child::*">
      <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
      <xsl:choose>
        <xsl:when test="$nodeName = 'suffix'">
          <tr class="bgcol1">
            <td><xsl:value-of select="normalize-space(@name)"/> </td>
            <td>
              <xsl:value-of select="normalize-space(numberOfEntries/.)"/>
            </td>
            <td>
            <xsl:for-each select="child::topology/instance">
              <xsl:variable name="iName" select="normalize-space(@name)"/>
              <xsl:variable name="iInitRule"
                            select="normalize-space(@initRule)"/>
              <xsl:value-of select="$iName"/>
              /
              <xsl:value-of select="$iInitRule"/>
              <br/>
            </xsl:for-each>
            </td>
          </tr>
        </xsl:when>
        <xsl:otherwise>
          <span style="color: rgb(255, 0, 0); font-weight: bold;">
            ERROR :Unknown phase name <xsl:value-of select="$nodeName"/>
          </span>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
    
  </table><br/><br/>
</xsl:template>

<!-- ====== Manage scheduler child for topology phase ====== -->
<xsl:template match="schedulerParser">
  <table>
    <tr>
      <th>Modules</th>
      <th>Clients</th>
    </tr>
    
  <!-- child is instance node only -->
  <xsl:for-each select="child::*">
    <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
    <xsl:variable name="moduleEnabled" select="normalize-space(@enabled)"/>
    <xsl:choose>
      <xsl:when test="$nodeName = 'module'">
        <tr class="bgcol1">
          <td>
            <xsl:choose>
              <xsl:when test="$moduleEnabled = 'true'">
                <xsl:value-of select="normalize-space(@name)"/> (enabled)
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="normalize-space(@name)"/> (disabled)
              </xsl:otherwise>
            </xsl:choose>
          </td>
          <td>
            <xsl:apply-templates select="client" />
          </td>
        </tr>
      </xsl:when>
      <xsl:otherwise>
        <span style="color: rgb(255, 0, 0); font-weight: bold;">
          ERROR :Unknown phase name <xsl:value-of select="$nodeName"/>
        </span>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:for-each>

  </table><br/><br/>
</xsl:template>

<!-- ============ Manage client node for topology phase ============= -->
<xsl:template match="client">
  <xsl:value-of select="normalize-space(@name)"/> 
  
  <xsl:variable name="stop" select="normalize-space(@stop)"/>
  <xsl:choose>
    <xsl:when test="$stop = 'ERROR_not_defined'">
      (<xsl:value-of select="normalize-space(@start)"/> -
        dependency <xsl:value-of select="normalize-space(@dependency)"/>)
    </xsl:when>
    <xsl:otherwise>
      (<xsl:value-of select="normalize-space(@start)"/> -
        <xsl:value-of select="normalize-space(@stop)"/>)
    </xsl:otherwise>
  </xsl:choose>

  <br/>
</xsl:template>

<!-- ********************************************************************* -->
<!-- ***********************  GENERIC         **************************** -->
<!-- ********************************************************************* -->

<!-- =================  ================ -->
<xsl:template name="parsePhase">
  <xsl:param name="phaseName"/>
  
  * <a href="#summary"> Move up to Summary</a>
  <br/>
  * <a onclick="ShowHideListElement('{$phaseName}')">
    <span class="showLink"> Clic me to show/hide details</span>
  </a>
  <p id="{$phaseName}" style="display: none">
    <ul>
    
    <!--== Child of phase can be a module, operation, instance node ==-->
    <xsl:for-each select="child::*">
      <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
      <xsl:choose>
        <!--== module node ==-->
        <xsl:when test="$nodeName = 'module'">
          <xsl:call-template name="displayModule" />
        </xsl:when>
        <!--== step node ==-->
        <xsl:when test="$nodeName = 'step'">
          <xsl:call-template name="displayStep" />
          <br/>
        </xsl:when>
        <!--== operation node ==-->
        <xsl:when test="$nodeName = 'operation'">
          <xsl:call-template name="displayOperation" />
          <br/>
        </xsl:when>
        <!--== instance node ==-->
        <xsl:when test="$nodeName = 'instance'">
          <xsl:variable name="instanceName" select="normalize-space(@name)"/>
          <xsl:variable name="instanceHost" select="normalize-space(@host)"/>
          <xsl:variable name="instancePort" select="normalize-space(@port)"/>
          <xsl:variable name="instanceProduct" 
                        select="normalize-space(@product)"/>
          
          <li>
            <b>
              <xsl:value-of select="$phaseName"/> for instance 
              <font color="blue"><xsl:value-of select="$instanceName"/></font> 
              on <xsl:value-of select="$instanceHost"/> :
              <xsl:value-of select="$instancePort"/>
              (<xsl:value-of select="$instanceProduct" />)
            </b><br/>
            
            <xsl:call-template name="parseChildPhase" />
            
          </li><br/><br/>
        </xsl:when>
        <!--== client node ==-->
        <xsl:when test="$nodeName = 'client'">
          <xsl:variable name="clientName" select="normalize-space(@name)"/>
          <xsl:variable name="clientHost" select="normalize-space(@host)"/>
          <xsl:variable name="clientId" select="normalize-space(@id)"/>
          <li>
            <b>
              <xsl:value-of select="$phaseName"/> for client
              <font color="blue"><xsl:value-of select="$clientName"/></font>
              (id <xsl:value-of select="$clientId"/>) 
              on <xsl:value-of select="$clientHost"/>
            </b><br/>
            
            <xsl:call-template name="parseChildPhase" />
            
          </li><br/><br/>
        </xsl:when>
        <!--== phaseResult node ==-->
        <xsl:when test="$nodeName = 'phaseResult'">
        </xsl:when>
        <!--== else... ==-->
        <xsl:otherwise>
          <span style="color: rgb(255, 0, 0); font-weight: bold;">
            ERROR : Unknown node name <xsl:value-of select="$nodeName"/> 
          </span><br/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
    </ul>
    
  * <a onclick="ShowHideListElement('{$phaseName}')">
    <span class="showLink">Clic me to show/hide details</span>
  </a><br/>
  * <a href="#summary">Move up to Summary</a>
  </p>
</xsl:template>


<!-- ================= Manage phase node childs ================ -->
<xsl:template name="parseChildPhase">
  <!--== Generic sub childs can be a operation/operationResult/... nodes ==-->
  <ul>
    <xsl:for-each select="child::*"> 
      <xsl:variable name="nodeName" select="normalize-space(local-name(.))"/>
      <xsl:choose>
        <xsl:when test="$nodeName = 'step'">
          <xsl:call-template name="displayStep" />
        </xsl:when>
        <xsl:when test="$nodeName = 'operation'">
          <xsl:call-template name="displayOperation" />
        </xsl:when>
        <xsl:when test="$nodeName = 'operationResult'">
          <xsl:call-template name="displayOperationResult" />
        </xsl:when>
        <xsl:when test="$nodeName = 'message'">
          <xsl:call-template name="displayMessage" />
        </xsl:when>
        <xsl:when test="$nodeName = 'client'">
          <xsl:call-template name="displayClient" />
        </xsl:when>
        
        <!--== Nothing to do, just for node recognition ==-->
        <xsl:when test="$nodeName = 'instances'">
        </xsl:when>
        <xsl:when test="$nodeName = 'suffixes'">
        </xsl:when>
        <xsl:when test="$nodeName = 'phaseResult'">
        </xsl:when>
        <xsl:when test="$nodeName = 'schedulerParser'">
        </xsl:when>
        <xsl:when test="$nodeName = 'copyClient'">
        </xsl:when>
        <xsl:when test="$nodeName = 'clientResult'">
        </xsl:when>
        <xsl:otherwise>
          <span style="color: rgb(255, 0, 0); font-weight: bold;"> 
            ERROR : unknown node name <xsl:value-of select="$nodeName"/> 
          </span><br/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
  </ul>

</xsl:template>


<!-- ================= Manage module node ================ -->
<xsl:template name="displayModule">
  
  <li> <b>Module</b> : <xsl:value-of select="normalize-space(@name)"/> <br/>
      <xsl:call-template name="parseChildPhase" />
  </li><br/>
</xsl:template>

<!-- ================= Manage client node ================ -->
<xsl:template name="displayClient">
  <xsl:variable name="clientName" select="normalize-space(@name)"/>
  <xsl:variable name="clientHost" select="normalize-space(@host)"/>
  <li>
    <xsl:variable name="i" select="count(preceding::client)"/>
    <a onclick="ShowHideListElement('{$i}')">
      <span class="showLink">
        <xsl:value-of select="$clientName"/> on 
        <xsl:value-of select="$clientHost"/>
      </span>
    </a>
    <p id="{$i}" style="display: none">
      <xsl:call-template name="parseChildPhase" />
    </p>
    client ends with status :
      <xsl:variable name="clientStatus" 
                    select="normalize-space(clientResult/@status)"/>
      <xsl:choose>
        <xsl:when test="$clientStatus='SUCCESS'">
          <b><span class="pass">SUCCESS</span></b>
        </xsl:when>
        <xsl:otherwise>
          <b><span class="fail">FAIL</span></b>
        </xsl:otherwise>
      </xsl:choose>
      
  </li><br/>
</xsl:template>

<!-- ================= Manage step node ================ -->
<xsl:template name="displayStep">
  <xsl:variable name="stepName" select="normalize-space(@name)"/>
  <li>
    <b>Step : <xsl:value-of select="$stepName" /> </b><br/>
    <xsl:call-template name="parseChildPhase" />
  </li>
</xsl:template>

<!-- ================= Manage operation node ================ -->
<xsl:template name="displayOperation">
  <xsl:variable name="opName" select="normalize-space(@name)"/>
  <xsl:variable name="opDate" select="normalize-space(@date)"/>
  <li>
    <b><xsl:value-of select="$opName"/></b>
       <i>@ <xsl:value-of select="$opDate"/></i><br/>
    <xsl:call-template name="parseChildPhase" />
  </li>
</xsl:template>

<!-- ================= Manage operationResult node ================ -->
<xsl:template name="displayOperationResult">
  <xsl:variable name="rc" select="normalize-space(@returncode)"/>
  <xsl:variable name="exprc" select="normalize-space(@expected)"/>
  <xsl:variable name="result" select="normalize-space(.)"/>
  <xsl:variable name="status" select="normalize-space(@status)"/>
  
  <b>
    <xsl:choose>
      <xsl:when test="$status='SUCCESS'">
        <span class="pass"><xsl:value-of select="$status"/></span>
        [
         return code <xsl:value-of select="$rc" />,
         expected <xsl:value-of select="$exprc" />
        ] <br/>
      </xsl:when>
      <xsl:when test="$status='ERROR'">
        <span class="fail"><xsl:value-of select="$status"/></span>
        [
         return code <xsl:value-of select="$rc" />,
         expected <xsl:value-of select="$exprc" />,
         <xsl:value-of select="$result" /> 
        ] <br/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$status"/>
      </xsl:otherwise>
    </xsl:choose>
  </b>
  
  <xsl:call-template name="parseChildPhase" />
</xsl:template>


<!-- ================= Manage message node     ================ -->
<xsl:template name="displayMessage">
  <xsl:value-of select="." /> <br/>
  <xsl:call-template name="parseChildPhase" />
</xsl:template>


<!-- ================= Manage phaseResult node ================ -->
<xsl:template match="phaseResult">
  <span class="phaseResult">
    Phase ends with status :
    <xsl:variable name="result" select="normalize-space(@errNum)"/>
    <xsl:choose>
      <xsl:when test="$result='0'">
       <span class="pass">PASS</span>
      </xsl:when>
      <xsl:otherwise>
       <span class="fail">FAIL</span>
      </xsl:otherwise>
     </xsl:choose>

  </span>
</xsl:template>





</xsl:stylesheet>


