<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="1.0" 
xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
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
 !      Copyright 2006-2008 Sun Microsystems, Inc.
 ! -->

<xsl:template match="/">

  <!--- Test Report Header Variables -->
  <xsl:variable name="identification"     select="qa/functional-tests/identification"/>
  <xsl:variable name="version"  select="$identification/version"/>
  <xsl:variable name="buildid"  select="$identification/buildid"/>
  <xsl:variable name="revision"  select="$identification/revision"/>
  <xsl:variable name="testcase"     select="qa/functional-tests/results/testgroup/testsuite/testcase"/>
  <xsl:variable name="total-tests"  select="count($testcase)"/>
  <xsl:variable name="pass-tests"   select="count($testcase[@result='pass'])"/>
  <xsl:variable name="fail-tests"   select="count($testcase[@result='fail'])"/>
  <xsl:variable name="inconc-tests" select="count($testcase[@result='unknown'])"/>
  
  <xsl:element name="html">
  
  <xsl:element name="head">

  <link rel="SHORTCUT ICON" href="https://opends.dev.java.net/public/images/opends_favicon.gif" />
  <style type="text/css">
/* <![CDATA[ */
 @import "/branding/css/tigris.css";
 @import "/branding/css/inst.css";
 /* ]]> */
  </style>
  <link rel="stylesheet" type="text/css" href="/branding/css/print.css" media="print" />
  <link rel="stylesheet" href="https://opends.dev.java.net/public/css/opends.css" type="text/css" />

    <xsl:element name="title">
      <xsl:value-of select="concat('Test Report for OpenDS ',$version)"/>
    </xsl:element>
  
  </xsl:element>
  
  <table class="tertmasttable" width="100%" cellspacing="0">
    <tbody>
      <tr>
        <td><div class="collectionheader"><xsl:value-of select="concat('Test Report for OpenDS ',$version)"/></div></td>
        <td width="10%"><a href="https://opends.dev.java.net/"><img src="https://opends.dev.java.net/public/images/opends_logo_sm.png" alt="OpenDS Logo" width="104" height="33" border="0" align="middle" /></a> </td>
      </tr>
    </tbody>
  </table>
  
  <!-- Overall Test Percentage -->
  <xsl:variable name="percent-tests">
    <xsl:choose>
      <xsl:when test="$total-tests &gt; 0">
        <xsl:value-of select="round((($pass-tests div $total-tests) * 100) - 0.5)"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="0"/>
      </xsl:otherwise>     
    </xsl:choose>
  </xsl:variable>

  <!-- Shaded Line -->
  <xsl:element name="hr">
    <xsl:attribute name="noshade">
      <xsl:value-of select="'noshade'"/>
    </xsl:attribute>
    <xsl:attribute name="size">
      <xsl:value-of select="1"/>
    </xsl:attribute>
  </xsl:element>

  <!-- Percentage Result -->
  <xsl:element name="table">
    <xsl:attribute name="width">
      <xsl:value-of select="'100%'"/>
    </xsl:attribute>
    <xsl:attribute name="bgcolor">
      <xsl:choose>
        <xsl:when test="$percent-tests &lt; 80">
          <xsl:value-of select="'red'" />
        </xsl:when>
        <xsl:when test="$percent-tests &lt; 90">
          <xsl:value-of select="'yellow'" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="'lightgreen'" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:attribute>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="font">
          <xsl:attribute name="size">
            <xsl:value-of select="'+2'"/>
          </xsl:attribute>
          <xsl:value-of select="concat($percent-tests,'%')"/>
        </xsl:element>
      </xsl:element>
    </xsl:element>
  </xsl:element>

  <!-- Shaded Line -->
  <xsl:element name="hr">
    <xsl:attribute name="noshade">
      <xsl:value-of select="'noshade'"/>
    </xsl:attribute>
    <xsl:attribute name="size">
      <xsl:value-of select="1"/>
    </xsl:attribute>
  </xsl:element>

  <!-- Identification -->
  <xsl:element name="table">
    <xsl:attribute name="width">
      <xsl:value-of select="'100%'"/>
    </xsl:attribute>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Build'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Revision'"/>
        </xsl:element>
      </xsl:element>          
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Platform'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'JVM'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Total'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Pass'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Fail'"/>
        </xsl:element>
      </xsl:element>
            <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Inconclusive'"/>
        </xsl:element>
      </xsl:element>
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$identification/buildid"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$identification/revision"/>
        </xsl:element>
      </xsl:element>          
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$identification/os-label"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$identification/jvm-label"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$total-tests"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$pass-tests"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$fail-tests"/>
        </xsl:element>
      </xsl:element>
            <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$inconc-tests"/>
        </xsl:element>
      </xsl:element>
    </xsl:element>
  </xsl:element>
  
  <!-- Shaded Line -->
  <xsl:element name="hr">
    <xsl:attribute name="noshade">
      <xsl:value-of select="'noshade'"/>
    </xsl:attribute>
    <xsl:attribute name="size">
      <xsl:value-of select="1"/>
    </xsl:attribute>
  </xsl:element>

  <xsl:element name="body">

    <!-- Test Results Table -->
    <xsl:element name="table">
      <xsl:attribute name="width">
        <xsl:value-of select="'100%'"/>
      </xsl:attribute>
      <xsl:attribute name="border">
        <xsl:value-of select="1"/>
      </xsl:attribute>
      <xsl:attribute name="cellpadding">
        <xsl:value-of select="2"/>
      </xsl:attribute>
      
      <xsl:element name="tr">
        <xsl:attribute name="bgcolor">
          <xsl:value-of select="'lightblue'"/>
        </xsl:attribute>
	
        <xsl:element name="th">
	  <xsl:value-of select="'Testcase'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'Start Time'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'End Time'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'Duration'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'Result'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'Test Logs'"/>
	</xsl:element>
	<xsl:element name="th">
	  <xsl:value-of select="'Server Logs'"/>
	</xsl:element>

      </xsl:element>

    <xsl:for-each select="$testcase">

      <xsl:element name="tr">
        <xsl:attribute name="bgcolor">
          <xsl:choose>
	    <xsl:when test="@result='fail'">
	      <xsl:value-of select="'red'"/>
	    </xsl:when>
	    <xsl:when test="@result='unknown'">
	      <xsl:value-of select="'yellow'"/>
	    </xsl:when>
	  </xsl:choose>
	</xsl:attribute>
	
        <!-- Test Name -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'left'"/>
          </xsl:attribute>
          <xsl:value-of select="@name"/>
        </xsl:element>
        
        <!-- Start Time -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="@start"/>
        </xsl:element>
        
        <!-- Stop Time -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="@stop"/>
        </xsl:element>
        
        <!-- Duration -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="@duration"/>
        </xsl:element>
        
        <!-- Result -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="@result"/>
        </xsl:element>
        
        <!-- Test Logs -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="'-'"/>
        </xsl:element>
        
        <!-- Server Logs -->
        <xsl:element name="td">
          <xsl:attribute name="align">
            <xsl:value-of select="'center'"/>
          </xsl:attribute>
          <xsl:value-of select="'-'"/>
        </xsl:element>
        
      </xsl:element>
      
    </xsl:for-each>

    </xsl:element>

  <!-- Shaded Line -->
  <xsl:element name="hr">
    <xsl:attribute name="noshade">
      <xsl:value-of select="'noshade'"/>
    </xsl:attribute>
    <xsl:attribute name="size">
      <xsl:value-of select="1"/>
    </xsl:attribute>
  </xsl:element>

  <!-- Additional Information -->
  <xsl:element name="h2">
    <xsl:value-of select="'Additional Information'"/>
  </xsl:element>

  <xsl:variable name="tests-dir" select="$identification/tests-dir"/>
    
  <xsl:element name="table">
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:element name="b">
          <xsl:value-of select="'Test Archive: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$tests-dir"/>
        </xsl:element>
      </xsl:element>          
    </xsl:element>
  </xsl:element>
  
  <!-- Shaded Line -->
  <xsl:element name="hr">
    <xsl:attribute name="noshade">
      <xsl:value-of select="'noshade'"/>
    </xsl:attribute>
    <xsl:attribute name="size">
      <xsl:value-of select="1"/>
    </xsl:attribute>
  </xsl:element>
  
  </xsl:element>
      
  </xsl:element>

</xsl:template>

</xsl:stylesheet>
