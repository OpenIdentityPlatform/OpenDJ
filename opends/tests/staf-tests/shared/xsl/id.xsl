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
  
  <!-- Test Report Header Variables -->
  <xsl:variable name="id"             select="identification"/>
  <xsl:variable name="sut"            select="$id/sut"/>
  <xsl:variable name="testware"       select="$id/testware"/>
  <xsl:variable name="url"            select="normalize-space($id/tests-url)"/>
  <xsl:variable name="tests-dir"      select="normalize-space($id/tests-dir)"/>
  <xsl:variable name="mailto"         select="normalize-space($id/mailto)"/>
  <xsl:variable name="version"        select="normalize-space($sut[@product='opends']/version)"/>
 
  <xsl:element name="html">
  
  <xsl:element name="head">

  <style type="text/css">
/* <![CDATA[ */
 @import "/branding/css/tigris.css";
 @import "/branding/css/inst.css";
 /* ]]> */
  </style>
  <link rel="stylesheet" type="text/css" href="/branding/css/print.css" media="print" />
  <link rel="stylesheet" href="./opends.css" type="text/css" />

    <xsl:element name="title">
      <xsl:value-of select="concat('Identification for OpenDS ',$version)"/>
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
        <td><div class="collectionheader"><xsl:value-of select="concat('Identification for OpenDS ',$version)"/></div></td>
        <td width="10%"><a href="https://opends.dev.java.net/"><img src="./opends_logo_sm.png" alt="OpenDS Logo" width="104" height="33" border="0" align="middle" /></a> </td>
      </tr>
    </tbody>
  </table>
  
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

  <!-- Software Under Test-->
  <xsl:element name="h2">
    <xsl:value-of select="'Software Under Test'"/>
  </xsl:element>

  <!-- OpenDS -->
  <xsl:element name="h3">
    <xsl:value-of select="'OpenDS'"/>
  </xsl:element>

  <!-- OpenDS Table -->  
  <xsl:element name="table">
    <xsl:attribute name="border">
      <xsl:value-of select="'1'"/>
    </xsl:attribute>
    <xsl:attribute name="cellpadding">
      <xsl:value-of select="'1'"/>
    </xsl:attribute>
        <xsl:attribute name="width">
      <xsl:value-of select="'80%'"/>
    </xsl:attribute>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Name: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/name)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Version: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/version)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Build ID: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/buildid)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Revision: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/revision)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'MD5 Sum: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/md5-sum)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Hostname: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/hostname)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Version: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/jvm-version)"/>
      </xsl:element>          
    </xsl:element>
   <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Vendor: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/jvm-vendor)"/>
      </xsl:element>          
    </xsl:element>
   <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Archicture: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/jvm-arch)"/>
      </xsl:element>          
    </xsl:element>
   <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Home: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/jvm-home)"/>
      </xsl:element>          
    </xsl:element>
   <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Binpath: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/jvm-bin)"/>
      </xsl:element>          
    </xsl:element>
   <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Args: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/jvm-args)"/>
      </xsl:element>          
    </xsl:element>
  </xsl:element>

  <!-- DSML Gateway -->
  <xsl:element name="h3">
    <xsl:value-of select="'DSML Gateway'"/>
  </xsl:element>

  <!-- DSML Gateway Table -->  
  <xsl:element name="table">
    <xsl:attribute name="border">
      <xsl:value-of select="'1'"/>
    </xsl:attribute>
    <xsl:attribute name="cellpadding">
      <xsl:value-of select="'1'"/>
    </xsl:attribute>
        <xsl:attribute name="width">
      <xsl:value-of select="'80%'"/>
    </xsl:attribute>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Name: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='dsml']/name)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Version: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/version)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Build ID: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/buildid)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Revision: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/revision)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'MD5 Sum: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='dsml']/md5-sum)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Hostname: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/hostname)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Version: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/jvm-version)"/>
      </xsl:element>          
    </xsl:element>
   <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Vendor: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/jvm-vendor)"/>
      </xsl:element>          
    </xsl:element>
   <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Archicture: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/jvm-arch)"/>
      </xsl:element>          
    </xsl:element>
   <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Java Home: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='opends']/jvm-home)"/>
      </xsl:element>          
    </xsl:element>
   <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'Web Container: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($sut[@product='dsml']/dsml-container)"/>
      </xsl:element>          
    </xsl:element>
  </xsl:element>

  <xsl:element name="br"/>

  <!-- Package Table -->  
  <xsl:element name="table">
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'OpenDS Package: '"/>
        </xsl:element>
      </xsl:element>        
      <xsl:element name="td">
        <xsl:element name="a">
          <xsl:attribute name="href">
            <xsl:value-of select="normalize-space($sut[@product='opends']/server-package)"/>
          </xsl:attribute>
            <xsl:value-of select="normalize-space($sut[@product='opends']/server-package)"/>
          </xsl:element>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'DSML Gateway Package: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:element name="a">
          <xsl:attribute name="href">
            <xsl:value-of select="normalize-space($sut[@product='dsml']/dsml-package)"/>
          </xsl:attribute>
          <xsl:value-of select="normalize-space($sut[@product='dsml']/dsml-package)"/>
        </xsl:element>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'SNMP Package: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:element name="a">
          <xsl:attribute name="href">
            <xsl:value-of select="normalize-space($sut[@product='opends']/snmp-jarfile)"/>
          </xsl:attribute>
          <xsl:value-of select="normalize-space($sut[@product='opends']/snmp-jarfile)"/>
        </xsl:element>
      </xsl:element>          
    </xsl:element>    
  </xsl:element>

  <xsl:element name="br"/>
    
  <!-- Shaded Line -->
  <xsl:element name="hr">
    <xsl:attribute name="noshade">
      <xsl:value-of select="'noshade'"/>
    </xsl:attribute>
    <xsl:attribute name="size">
      <xsl:value-of select="1"/>
    </xsl:attribute>
  </xsl:element>

  <!-- Test Software -->
  <xsl:element name="h2">
    <xsl:value-of select="'Test Software'"/>
  </xsl:element>

  <!-- STAF -->
  <xsl:element name="h3">
    <xsl:value-of select="'STAF'"/>
  </xsl:element>

  <!-- STAF Table-->  
  <xsl:element name="table">
    <xsl:attribute name="border">
      <xsl:value-of select="'1'"/>
    </xsl:attribute>
    <xsl:attribute name="cellpadding">
      <xsl:value-of select="'1'"/>
    </xsl:attribute>
        <xsl:attribute name="width">
      <xsl:value-of select="'80%'"/>
    </xsl:attribute>
    <xsl:element name="tr">
      <xsl:element name="th">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="br"/>
      </xsl:element>
      <xsl:element name="th">
        <xsl:value-of select="'Controller'"/>
      </xsl:element>          
      <xsl:element name="th">
        <xsl:value-of select="'Slave'"/>
      </xsl:element>
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'STAF Host: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($testware/staf/local/hostname)"/>
      </xsl:element>          
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($testware/staf/remote/hostname)"/>
      </xsl:element>
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'STAF Version: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($testware/staf/local/version)"/>
      </xsl:element>          
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($testware/staf/remote/version)"/>
      </xsl:element>          
    </xsl:element>
    <xsl:element name="tr">
      <xsl:element name="td">
        <xsl:attribute name="width">
          <xsl:value-of select="'25%'"/>
        </xsl:attribute>
        <xsl:element name="b">
          <xsl:value-of select="'STAF Root: '"/>
        </xsl:element>
      </xsl:element>
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($testware/staf/local/rootdir)"/>
      </xsl:element>          
      <xsl:element name="td">
        <xsl:value-of select="normalize-space($testware/staf/remote/rootdir)"/>
      </xsl:element>
    </xsl:element>
  </xsl:element>

  <!-- STAF Services -->
  <xsl:element name="h3">
    <xsl:value-of select="concat('STAF Services on ',normalize-space($testware/staf/local/hostname))"/>
  </xsl:element>

  <!-- STAF Services Table-->  
  <xsl:element name="table">
    <xsl:attribute name="border">
      <xsl:value-of select="'1'"/>
    </xsl:attribute>
    <xsl:attribute name="cellpadding">
      <xsl:value-of select="'2'"/>
    </xsl:attribute>
        <xsl:attribute name="width">
      <xsl:value-of select="'80%'"/>
    </xsl:attribute>
    <xsl:element name="tr">
      <xsl:element name="th">
          <xsl:value-of select="'Name'"/>
      </xsl:element>
      <xsl:element name="th">
        <xsl:value-of select="'Version'"/>
      </xsl:element>          
      <xsl:element name="th">
        <xsl:value-of select="'Library'"/>
      </xsl:element>
      <xsl:element name="th">
        <xsl:value-of select="'Executable'"/>
      </xsl:element>
            <xsl:element name="th">
        <xsl:value-of select="'Options'"/>
      </xsl:element>
      <xsl:element name="th">
        <xsl:value-of select="'Params'"/>
      </xsl:element>
    </xsl:element>

    <xsl:for-each select="$testware/service">
    
      <xsl:element name="tr">
        <xsl:element name="td">
          <xsl:element name="b">
            <xsl:value-of select="@name"/>
          </xsl:element>
        </xsl:element>
        <xsl:element name="td">
          <xsl:value-of select="version"/>
        </xsl:element>          
        <xsl:element name="td">
          <xsl:value-of select="library"/>
        </xsl:element>
        <xsl:element name="td">
          <xsl:value-of select="executable"/>
        </xsl:element>
        <xsl:element name="td">
          <xsl:value-of select="options"/>
        </xsl:element>
        <xsl:element name="td">
          <xsl:value-of select="params"/>
        </xsl:element>
      </xsl:element>

    </xsl:for-each>

  </xsl:element>

  <xsl:element name="br"/>

  <!-- Shaded Line -->
  <xsl:element name="hr">
    <xsl:attribute name="noshade">
      <xsl:value-of select="'noshade'"/>
    </xsl:attribute>
    <xsl:attribute name="size">
      <xsl:value-of select="1"/>
    </xsl:attribute>
  </xsl:element>

  <!-- Configuration -->
  <xsl:element name="h2">
    <xsl:value-of select="'Configuration Details'"/>
  </xsl:element>

  <!-- Configuration Table-->  
  <xsl:element name="table">
    <xsl:attribute name="border">
      <xsl:value-of select="'1'"/>
    </xsl:attribute>
    <xsl:attribute name="cellpadding">
      <xsl:value-of select="'1'"/>
    </xsl:attribute>
    <xsl:element name="tr">
      <xsl:element name="th">
          <xsl:value-of select="'Attribute'"/>
      </xsl:element>
      <xsl:element name="th">
        <xsl:value-of select="'Value'"/>
      </xsl:element>          
    </xsl:element>

    <xsl:for-each select="$testware/config">
    
      <xsl:element name="tr">
        <xsl:element name="td">
          <xsl:element name="b">
            <xsl:value-of select="attribute"/>
          </xsl:element>
        </xsl:element>
        <xsl:element name="td">
          <xsl:value-of select="value"/>
        </xsl:element>          
      </xsl:element>

    </xsl:for-each>

  </xsl:element>

  <xsl:element name="br"/>
  
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
            <xsl:value-of select="concat($url,normalize-space($tests-dir))"/>
          </xsl:attribute>
          <xsl:value-of select="normalize-space($tests-dir)"/>
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

</xsl:stylesheet>
