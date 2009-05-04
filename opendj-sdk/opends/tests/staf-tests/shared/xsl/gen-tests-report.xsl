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
<xsl:param name="suite">''</xsl:param>

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
  
  <!--- Test Report Header Variables -->
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
  <xsl:variable name="count-tests"    select="count($testcase)"/>
  <xsl:variable name="total-tests"    select="count($testcase[@suite=$suite])"/>
  <xsl:variable name="pass-tests"     select="count($testcase[@result='pass' and @suite=$suite])"/>
  <xsl:variable name="fail-tests"     select="count($testcase[@result='fail' and @suite=$suite])"/>
  <xsl:variable name="inconc-tests"   select="count($testcase[@result='unknown' and @suite=$suite])"/>
  <xsl:variable name="kfail-tests"    select="count($testcase[@suite=$suite]/issues)"/>
  
  <xsl:element name="html">
  
  <xsl:element name="head">

  <style type="text/css">
/* <![CDATA[ */
 @import "/branding/css/tigris.css";
 @import "/branding/css/inst.css";
 /* ]]> */
  </style>
  <xsl:element name="link">
    <xsl:attribute name="rel">
      <xsl:value-of select="'stylesheet'"/>
    </xsl:attribute>
    <xsl:attribute name="type">
      <xsl:value-of select="'text/css'"/>
    </xsl:attribute>
    <xsl:attribute name="href">
      <xsl:value-of select="'/branding/css/print.css'"/>
    </xsl:attribute>
    <xsl:attribute name="media">
      <xsl:value-of select="'print'"/>
    </xsl:attribute>
  </xsl:element>
  <link rel="stylesheet" href="../../reports/opends.css" type="text/css" />

    <xsl:element name="title">
      <xsl:value-of select="concat('Test Case Report for OpenDS ',$version)"/>
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
        <td><div class="collectionheader"><xsl:value-of select="concat('Test Case Report for OpenDS ',$version)"/></div></td>
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
          <xsl:value-of select="'Test Case'"/>
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
          <xsl:value-of select="'Issues'"/>
        </xsl:element>
      </xsl:element>

      <xsl:for-each select="$testcase[@suite = $suite]">

        <xsl:variable name="suitename" select="translate(@suite, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"/>
        <xsl:variable name="tcname" select="normalize-space(@shortname)"/>

        <xsl:element name="tr">
          <xsl:attribute name="bgcolor">
            <xsl:choose>
              <xsl:when test="@result = 'pass'">
                <xsl:value-of select="'lightgreen'" />
              </xsl:when>
              <xsl:when test="issues">
                <xsl:value-of select="'yellow'" />
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="'red'" />
              </xsl:otherwise>
            </xsl:choose>
          </xsl:attribute>

          <!-- Test Name -->
          <xsl:element name="td">
            <xsl:attribute name="align">
              <xsl:value-of select="'left'"/>
            </xsl:attribute>
            <xsl:element name="a">
              <xsl:attribute name="href">
                <xsl:value-of select="concat($url,$tests-dir,'/testlogs/',$groupdir,'/',$suitename,'-log.html#',@shortname)"/>
              </xsl:attribute>
              <xsl:value-of select="@shortname"/>
            </xsl:element>
          </xsl:element>
        
          <!-- Start Time -->
          <xsl:element name="td">
            <xsl:attribute name="align">
              <xsl:value-of select="'center'"/>
            </xsl:attribute>
            <xsl:value-of select="@start"/>
          </xsl:element>
      
          <!-- End Time -->
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
            <xsl:value-of select="concat(@duration,'s')"/>
          </xsl:element>
      
          <!-- Result -->
          <xsl:element name="td">
            <xsl:attribute name="align">
              <xsl:value-of select="'center'"/>
            </xsl:attribute>
            <xsl:value-of select="@result"/>
          </xsl:element>

          <!-- Issue -->
          <xsl:element name="td">
            <xsl:attribute name="align">
              <xsl:value-of select="'center'"/>
            </xsl:attribute>
            <xsl:for-each select="issues/issue">
              <xsl:value-of select="' '"/>
              <xsl:element name="a">
                <xsl:attribute name="href">
                  <xsl:value-of select="concat('https://opends.dev.java.net/issues/show_bug.cgi?id=',@id)"/>
                </xsl:attribute>
                <xsl:value-of select="@id"/>
              </xsl:element>
            </xsl:for-each>
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
