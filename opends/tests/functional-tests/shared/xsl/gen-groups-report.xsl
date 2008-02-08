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
 !      Copyright 2008 Sun Microsystems, Inc.
 ! -->

<xsl:output method="html" version="4.0" encoding="iso-8859-1" indent="yes"/>

<xsl:template match="/">

  <!--- Test Report Header Variables -->
  <xsl:variable name="ft"             select="qa/functional-tests"/>
  <xsl:variable name="identification" select="$ft/identification"/>
  <xsl:variable name="url"            select="normalize-space($identification/tests-url)"/>
  <xsl:variable name="hostname"       select="normalize-space($identification/hostname)"/>
  <xsl:variable name="version"        select="normalize-space($identification/version)"/>
  <xsl:variable name="buildid"        select="normalize-space($identification/buildid)"/>
  <xsl:variable name="revision"       select="normalize-space($identification/revision)"/>
  <xsl:variable name="os"             select="normalize-space($identification/os-label)"/>
  <xsl:variable name="jvm"            select="normalize-space($identification/jvm-label)"/>
  <xsl:variable name="testgroup"      select="$ft/results/testgroup"/>
  <xsl:variable name="testsuite"      select="$testgroup/testsuite"/>
  <xsl:variable name="testcase"       select="$testsuite/testcase"/>
  <xsl:variable name="total-tests"    select="count($testcase)"/>
  <xsl:variable name="pass-tests"     select="count($testcase[@result='pass'])"/>
  <xsl:variable name="fail-tests"     select="count($testcase[@result='fail'])"/>
  <xsl:variable name="inconc-tests"   select="count($testcase[@result='unknown'])"/>
  <xsl:variable name="tests-dir"      select="normalize-space($identification/tests-dir)"/>
  
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
    <xsl:call-template name="setColour">
      <xsl:with-param name="percent" select="$percent-tests"/>
    </xsl:call-template>
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
          <xsl:value-of select="'Host'"/>
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
          <xsl:value-of select="$buildid"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$revision"/>
        </xsl:element>
      </xsl:element>          
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$hostname"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$os"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$jvm"/>
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
          <xsl:value-of select="'Test Group'"/>
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
          <xsl:value-of select="'Total'"/>
        </xsl:element>
        <xsl:element name="th">
          <xsl:value-of select="'Pass'"/>
        </xsl:element>
        <xsl:element name="th">
          <xsl:value-of select="'Fail'"/>
        </xsl:element>
        <xsl:element name="th">
          <xsl:value-of select="'Inconc'"/>
        </xsl:element>
        <xsl:element name="th">
          <xsl:value-of select="'Percent'"/>
        </xsl:element>

      </xsl:element>

      <xsl:for-each select="$testgroup">
        <xsl:sort select="group" order="ascending"/>
        <xsl:variable name="group" select="@name"/>
        <xsl:if test="generate-id(.)=generate-id($testgroup[@name = $group])">

          <xsl:variable name="all-tests" select="$testcase[@group = $group]"/>
          <xsl:variable name="test-num" select="count($all-tests)"/>
          <xsl:variable name="test-pass" select="count($all-tests[@result = 'pass'])"/>
          <xsl:variable name="test-fail" select="count($all-tests[@result = 'fail'])"/>
          <xsl:variable name="test-inc" select="count($all-tests[@result = 'unknown'])"/>
          <xsl:variable name="test-percent" select="round((($test-pass div $test-num) * 100) - 0.5)"/>

          <xsl:variable name="end-time">
            <xsl:for-each select="$all-tests/@stop">

             <xsl:if test="position() = last()">
               <xsl:value-of select="."/>
             </xsl:if>

            </xsl:for-each>
          </xsl:variable>

          <xsl:variable name="groupdir" select="translate(@name, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"/>
          
          <xsl:variable name="duration">
            <xsl:call-template name="countDuration">
              <xsl:with-param name="testList" select="$all-tests"/>
            </xsl:call-template>
          </xsl:variable>

          <xsl:element name="tr">
            <xsl:call-template name="setColour">
              <xsl:with-param name="percent" select="$test-percent"/>
            </xsl:call-template>
  
            <!-- Group Name -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'left'"/>
              </xsl:attribute>
              <xsl:element name="a">
                <xsl:attribute name="href">
                  <xsl:value-of select="concat($url,$tests-dir,'/testlogs/',$groupdir,'/',$groupdir,'.html')"/>
                </xsl:attribute>
                <xsl:value-of select="@name"/>
              </xsl:element>
            </xsl:element>
          
            <!-- Start Time -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'center'"/>
              </xsl:attribute>
              <xsl:value-of select="testsuite/testcase/@start[1]"/>
            </xsl:element>
        
            <!-- End Time -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'center'"/>
              </xsl:attribute>
              <xsl:value-of select="$end-time"/>
            </xsl:element>
        
            <!-- Duration -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'center'"/>
              </xsl:attribute>
              <xsl:value-of select="concat($duration,'s')"/>
            </xsl:element>
        
            <!-- Total-->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'center'"/>
              </xsl:attribute>
              <xsl:value-of select="$test-num"/>
            </xsl:element>
        
            <!-- Pass -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'center'"/>
              </xsl:attribute>
              <xsl:value-of select="$test-pass"/>
            </xsl:element>
        
            <!-- Fail -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'center'"/>
              </xsl:attribute>
              <xsl:value-of select="$test-fail"/>
            </xsl:element>
        
            <!-- Inconclusive -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'center'"/>
              </xsl:attribute>
              <xsl:value-of select="$test-inc"/>
            </xsl:element>
        
            <!-- Percent -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'center'"/>
              </xsl:attribute>
              <xsl:value-of select="concat($test-percent,'%')"/>
            </xsl:element>
        
          </xsl:element>
      
        </xsl:if> 

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

<xsl:template name="countDuration">
  <xsl:param name="testList"/>
    <xsl:choose>
      <xsl:when test="$testList">
        <xsl:variable name="recursive_result">
          <xsl:call-template name="countDuration">
            <xsl:with-param name="testList"
              select="$testList[position() > 1]"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:value-of
          select="number($testList[1]/@duration) + $recursive_result"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="0"/></xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template name="setColour">
  <xsl:param name="percent"/>
  <xsl:attribute name="bgcolor">
    <xsl:choose>
      <xsl:when test="$percent &lt; 70">
        <xsl:value-of select="'red'" />
      </xsl:when>
      <xsl:when test="$percent &lt; 95">
        <xsl:value-of select="'yellow'" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="'lightgreen'" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:attribute>
</xsl:template>
    
</xsl:stylesheet>
