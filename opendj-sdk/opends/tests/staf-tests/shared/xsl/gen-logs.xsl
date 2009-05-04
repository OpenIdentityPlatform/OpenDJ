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
  
  <xsl:element name="html">

    <xsl:variable name="group" select="qa/logs/log/@group"/>
    <xsl:variable name="suite" select="qa/logs/log/@suite"/>
    <xsl:variable name="parent" select="qa/logs/log/@parent"/>
    <xsl:variable name="jobid" select="qa/logs/log/@jobid"/>
  
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
        <xsl:value-of select="concat('Logs for ', $suite, ' test suite')"/>
      </xsl:element>
    
    </xsl:element>
    
    <table class="tertmasttable" width="100%" cellspacing="0">
      <tbody>
        <tr>
          <td><div class="collectionheader"><xsl:value-of select="concat('Logs for ', $suite, ' test suite')"/></div></td>
          <td width="10%"><a href="https://opends.dev.java.net/"><img src="../../reports/opends_logo_sm.png" alt="OpenDS Logo" width="104" height="33" border="0" align="middle" /></a> </td>
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

    <!-- Baby Table -->
    <xsl:element name="table">
      <xsl:attribute name="border">
        <xsl:value-of select="'1'"/>
      </xsl:attribute> 
      <xsl:attribute name="cellpadding">
        <xsl:value-of select="'4'"/>
      </xsl:attribute>
    
      <xsl:element name="tr">
        <xsl:element name="td">
          <xsl:element name="b">
            <xsl:value-of select="'Test Group : '"/>
          </xsl:element>
          <xsl:value-of select="$group"/>
        </xsl:element>
        <xsl:element name="td">
          <xsl:element name="b">
            <xsl:value-of select="'Test Suite : '"/>
          </xsl:element>
          <xsl:value-of select="$suite"/>
        </xsl:element>
        <xsl:element name="td">
          <xsl:element name="b">
            <xsl:value-of select="'ParentID : '"/>
          </xsl:element>
          <xsl:value-of select="$parent"/>
        </xsl:element>
        <xsl:element name="td">
          <xsl:element name="b">
            <xsl:value-of select="'JobID : '"/>
          </xsl:element>
          <xsl:value-of select="$jobid"/>
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

      <xsl:element name="table">
      <xsl:attribute name="border">
        <xsl:value-of select="'1'"/>
      </xsl:attribute>

      <xsl:element name="tr">
        <xsl:element name="th">
          <xsl:value-of select="'Timestamp'"/>
        </xsl:element>
        <xsl:element name="th">
          <xsl:value-of select="'Level'"/>
        </xsl:element>
        <xsl:element name="th">
          <xsl:value-of select="'Message'"/>
        </xsl:element>
      </xsl:element>

      <xsl:variable name="log" select="qa/logs/log"/>
  
      <xsl:for-each select="$log/line">

        <xsl:element name="tr">
          <xsl:element name="td">
            <xsl:value-of select="@timestamp"/>
          </xsl:element>
          <xsl:element name="td">
            <xsl:choose>
              <xsl:when test="@level = 'Start'">              
                <xsl:element name="a">
                  <xsl:attribute name="name">
                    <xsl:value-of select="@tag"/>
                  </xsl:attribute>
                  <xsl:value-of select="@level"/>
                </xsl:element>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="@level"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:element>
          <xsl:element name="td">
            <xsl:value-of select="@message"/>
          </xsl:element>
        </xsl:element>

      </xsl:for-each>
    
      </xsl:element>
      
    </xsl:element>

  </xsl:element>      
</xsl:template>

</xsl:stylesheet>
