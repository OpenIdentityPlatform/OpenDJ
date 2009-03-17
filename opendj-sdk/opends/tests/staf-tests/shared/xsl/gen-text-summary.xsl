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
 !      Copyright 2007-2008 Sun Microsystems, Inc.
 ! -->

<xsl:output method="text"/>

<xsl:template match="/">
  <xsl:apply-templates select="qa"/>
</xsl:template>

<xsl:template match="qa">
  <xsl:apply-templates select="stress-tests"/>
  <xsl:apply-templates select="functional-tests"/>
</xsl:template>

<xsl:template match="stress-tests">
  <xsl:call-template name="main"/>
</xsl:template>

<xsl:template match="functional-tests">
  <xsl:call-template name="main"/>
</xsl:template>

<xsl:template name="main">

  <!-- Test Report Header -->
  <xsl:variable name="id"               select="identification"/>
  <xsl:variable name="sut"              select="$id/sut"/>
  <xsl:value-of select="concat('tests-dir: ', normalize-space($id/tests-dir),'&#xa;')"/>

  <!-- Test Case Totals -->
  <xsl:variable name="testcase"     select="results/testgroup/testsuite/testcase"/>
  <xsl:variable name="total"  select="count($testcase)"/>
  <xsl:variable name="pass"   select="count($testcase[@result='pass'])"/>
  <xsl:variable name="fail"   select="count($testcase[@result='fail'])"/>
  <xsl:variable name="inconc" select="count($testcase[@result='unknown'])"/>

  <!-- Overall Test Percentage -->
  <xsl:variable name="percent">
    <xsl:choose>
      <xsl:when test="$total &gt; 0">
        <xsl:value-of select="round((($pass div $total) * 100) - 0.5)"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="0"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>

  <xsl:value-of select="concat('result: ', normalize-space($percent),'&#xa;')"/>
  <xsl:value-of select="concat('pass: ', normalize-space($pass),'&#xa;')"/>
  <xsl:value-of select="concat('fail: ', normalize-space($fail),'&#xa;')"/>
  <xsl:value-of select="concat('inconc: ', normalize-space($inconc),'&#xa;')"/>
  <xsl:value-of select="concat('total: ', normalize-space($total),'&#xa;')"/>
  <xsl:value-of select="concat('sut-version: ', normalize-space($sut/version),'&#xa;')"/>
  <xsl:value-of select="concat('sut-buildid: ', normalize-space($sut/buildid),'&#xa;')"/>
  <xsl:value-of select="concat('sut-revision: ', normalize-space($sut/revision),'&#xa;')"/>
  <xsl:value-of select="concat('os-hostname: ', normalize-space($sut/hostname),'&#xa;')"/>
  <xsl:value-of select="concat('os-platform: ', normalize-space($sut/platform),'&#xa;')"/>
  <xsl:value-of select="concat('os-label: ', normalize-space($sut/os-label),'&#xa;')"/>
  <xsl:value-of select="concat('jvm-version: ', normalize-space($sut/jvm-version),'&#xa;')"/>
  <xsl:value-of select="concat('jvm-label: ', normalize-space($sut/jvm-label),'&#xa;')"/>
  <xsl:value-of select="concat('jvm-vendor: ', normalize-space($sut/jvm-vendor),'&#xa;')"/>
  <xsl:value-of select="concat('jvm-arch: ', normalize-space($sut/jvm-arch),'&#xa;')"/>

</xsl:template>

</xsl:stylesheet>
