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
 !      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 ! -->

<xsl:output method="text"/>

<xsl:template match="/">

  <!-- Test Report Header -->
  <xsl:variable name="identification"     select="qa/functional-tests/identification"/>

  <!-- Test Case Totals -->
  <xsl:variable name="testcase"     select="qa/functional-tests/results/testgroup/testsuite/testcase"/>
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
  <xsl:value-of select="concat('sut-version: ', normalize-space($identification/version),'&#xa;')"/>
  <xsl:value-of select="concat('sut-buildid: ', normalize-space($identification/buildid),'&#xa;')"/>
  <xsl:value-of select="concat('sut-revision: ', normalize-space($identification/revision),'&#xa;')"/>
  <xsl:value-of select="concat('os-hostname: ', normalize-space($identification/hostname),'&#xa;')"/>
  <xsl:value-of select="concat('os-platform: ', normalize-space($identification/platform),'&#xa;')"/>
  <xsl:value-of select="concat('os-label: ', normalize-space($identification/os-label),'&#xa;')"/>
  <xsl:value-of select="concat('jvm-version: ', normalize-space($identification/jvm-version),'&#xa;')"/>
  <xsl:value-of select="concat('jvm-label: ', normalize-space($identification/jvm-label),'&#xa;')"/>
  <xsl:value-of select="concat('jvm-vendor: ', normalize-space($identification/jvm-vendor),'&#xa;')"/>
  <xsl:value-of select="concat('jvm-arch: ', normalize-space($identification/jvm-arch),'&#xa;')"/>
  <xsl:value-of select="concat('tests-dir: ', normalize-space($identification/tests-dir),'&#xa;')"/>
</xsl:template>

</xsl:stylesheet>
