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
 !      Copyright 2008-2009 Sun Microsystems, Inc.
 ! -->

<xsl:output method="html" version="4.0" encoding="iso-8859-1" indent="yes"/>

<xsl:param name="group">''</xsl:param>

<xsl:variable name="groupdir" select="translate($group, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"/>

<xsl:template match="/">
  <xsl:apply-templates select="qa"/>
</xsl:template>

<xsl:template match="qa">
  <xsl:apply-templates select="stress-tests"/>
  <xsl:apply-templates select="functional-tests"/>
</xsl:template>

<xsl:template match="stress-tests">
  <xsl:call-template name="main">
    <xsl:with-param name="tests-type" select="normalize-space('Stress Tests')"/>
  </xsl:call-template>
</xsl:template>

<xsl:template match="functional-tests">
  <xsl:call-template name="main">
    <xsl:with-param name="tests-type" select="normalize-space('Functional Tests')"/>
  </xsl:call-template>
</xsl:template>

<xsl:template name="main">
  <xsl:param name="tests-type"/>

  <!--- Test Suites Report Header Variables -->
  <xsl:variable name="ft"             select="qa/$tests-type"/>
  <xsl:variable name="id"             select="identification"/>
  <xsl:variable name="sut"            select="$id/sut"/>
  <xsl:variable name="mailto"         select="normalize-space($id/mailto)"/>
  <xsl:variable name="tests-dir"      select="normalize-space($id/tests-dir)"/>
  <xsl:variable name="url"            select="normalize-space($id/tests-url)"/>
  <xsl:variable name="hostname"       select="normalize-space($sut/hostname)"/>
  <xsl:variable name="version"        select="normalize-space($sut/version)"/>
  <xsl:variable name="buildid"        select="normalize-space($sut/buildid)"/>
  <xsl:variable name="revision"       select="normalize-space($sut/revision)"/>
  <xsl:variable name="os"             select="normalize-space($sut/os-label)"/>
  <xsl:variable name="jvm"            select="normalize-space($sut/jvm-label)"/>
  <xsl:variable name="testgroup"      select="results/testgroup[translate(@name, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz') = $groupdir]"/>
  <xsl:variable name="testsuite"      select="$testgroup/testsuite"/>
  <xsl:variable name="testcase"       select="$testsuite/testcase"/>
  <xsl:variable name="total-tests"    select="count($testcase)"/>
  <xsl:variable name="pass-tests"     select="count($testcase[@result='pass'])"/>
  <xsl:variable name="fail-tests"     select="count($testcase[@result='fail'])"/>
  <xsl:variable name="inconc-tests"   select="count($testcase[@result='unknown'])"/>
  <xsl:variable name="kfail-tests"    select="count($testcase/issues)"/>
  
  <xsl:element name="html">
  
  <xsl:element name="head">

  <style type="text/css">
/* <![CDATA[ */
 @import "/branding/css/tigris.css";
 @import "/branding/css/inst.css";
 /* ]]> */
  </style>
  <link rel="stylesheet" type="text/css" href="/branding/css/print.css" media="print" />
  <link rel="stylesheet" href="../../reports/opends.css" type="text/css" />

    <xsl:element name="title">
      <xsl:value-of select="concat('Test Suites Report for OpenDS ',$version)"/>
    </xsl:element>
  
  </xsl:element>

  <table class="tertmasttable" width="100%" cellspacing="0">
    <tbody>
      <tr>
        <td align="center"><div class="collectionheader"><xsl:value-of select="$tests-type"/></div></td>
      </tr>
    </tbody>
  </table>
  
  <table class="tertmasttable" width="100%" cellspacing="0">
    <tbody>
      <tr>
        <td><div class="collectionheader"><xsl:value-of select="concat('Test Suites Report for OpenDS ',$version)"/></div></td>
        <td width="10%"><a href="https://opends.dev.java.net/"><img src="../../reports/opends_logo_sm.png" alt="OpenDS Logo" width="104" height="33" border="0" align="middle" /></a> </td>
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
      <xsl:with-param name="red" select="'70'"/>
      <xsl:with-param name="yellow" select="'95'"/>
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
          <xsl:value-of select="'Inconc'"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Kfail'"/>
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
      <xsl:element name="td">
        <xsl:attribute name="align">
          <xsl:value-of select="'center'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="$kfail-tests"/>
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
          <xsl:value-of select="'Test Suite'"/>
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
          <xsl:value-of select="'Kfail'"/>
        </xsl:element>
        <xsl:element name="th">
          <xsl:value-of select="'Percent'"/>
        </xsl:element>

      </xsl:element>

      <xsl:for-each select="$testsuite">
        <xsl:sort select="suite" order="ascending"/>
        <xsl:variable name="suite" select="@name"/>
        <xsl:if test="generate-id(.)=generate-id($testsuite[@name = $suite])">

          <xsl:variable name="all-tests" select="$testcase[@suite = $suite]"/>
          <xsl:variable name="test-num"  select="count($all-tests)"/>
          <xsl:variable name="test-pass" select="count($all-tests[@result = 'pass'])"/>
          <xsl:variable name="test-fail" select="count($all-tests[@result = 'fail'])"/>
          <xsl:variable name="test-inc"  select="count($all-tests[@result = 'unknown'])"/>
          <xsl:variable name="test-kfail" select="count($all-tests[@suite=$suite]/issues)"/>
          <xsl:variable name="test-percent" select="round((($test-pass div $test-num) * 100) - 0.5)"/>
          <xsl:variable name="suitename" select="translate(@name, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"/>

          <xsl:variable name="end-time">
            <xsl:for-each select="$all-tests/@stop">

             <xsl:if test="position() = last()">
               <xsl:value-of select="."/>
             </xsl:if>

            </xsl:for-each>
          </xsl:variable>

          <xsl:variable name="duration">
            <xsl:call-template name="countDuration">
              <xsl:with-param name="testList" select="$all-tests"/>
            </xsl:call-template>
          </xsl:variable>
            
          <xsl:element name="tr">
            <xsl:attribute name="bgcolor">
              <xsl:choose>
                <xsl:when test="$test-percent = '100'">
                  <xsl:value-of select="'lightgreen'" />
                </xsl:when>
                <xsl:when test="$test-fail = $test-kfail">
                  <xsl:value-of select="'yellow'" />
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="'red'" />
                </xsl:otherwise>
              </xsl:choose>
            </xsl:attribute>
  
            <!-- Group Name -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'left'"/>
              </xsl:attribute>
              <xsl:element name="a">
                <xsl:attribute name="href">
                  <xsl:value-of select="concat($url,$tests-dir,'/testlogs/',$groupdir,'/',@shortname,'-report.html')"/>
                </xsl:attribute>
                <xsl:value-of select="@name"/>
              </xsl:element>
            </xsl:element>
          
            <!-- Start Time -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'center'"/>
              </xsl:attribute>
              <xsl:value-of select="testcase/@start[1]"/>
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

            <!-- Kfail -->
            <xsl:element name="td">
              <xsl:attribute name="align">
                <xsl:value-of select="'center'"/>
              </xsl:attribute>
              <xsl:value-of select="$test-kfail"/>
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
        <xsl:element name="a">
          <xsl:attribute name="href">
            <xsl:value-of select="concat($url,$tests-dir)"/>
          </xsl:attribute>
          <xsl:value-of select="concat($url,$tests-dir)"/>
        </xsl:element>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:element name="b">
          <xsl:value-of select="'Product Identification: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:element name="a">
          <xsl:attribute name="href">
            <xsl:value-of select="concat($url,$tests-dir,'/reports/id.html')"/>
          </xsl:attribute>
          <xsl:value-of select="concat($url,$tests-dir,'/reports/id.html')"/>
        </xsl:element>
      </xsl:element>
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:element name="b">
          <xsl:value-of select="'Mail Sent to: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:element name="a">
          <xsl:attribute name="href">
            <xsl:value-of select="concat('mailto:',normalize-space($mailto))"/>
          </xsl:attribute>
          <xsl:value-of select="normalize-space($mailto)"/>
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
  <xsl:param name="red"/>
  <xsl:param name="yellow"/>
  <xsl:attribute name="bgcolor">
    <xsl:choose>
      <xsl:when test="$percent &lt; $red">
        <xsl:value-of select="'red'" />
      </xsl:when>
      <xsl:when test="$percent &lt; $yellow">
        <xsl:value-of select="'yellow'" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="'lightgreen'" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:attribute>
</xsl:template>

</xsl:stylesheet>
