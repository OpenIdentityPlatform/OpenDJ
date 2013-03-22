<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<!--
 ! CDDL HEADER START
 !
 ! The contents of this file are subject to the terms of the
 ! Common Development and Distribution License, Version 1.0 only
 ! (the "License").  You may not use this file except in compliance
 ! with the License.
 ! 
 ! You can obtain a copy of the license at
 ! trunk/opends/resource/legal-notices/CDDLv1_0.txt
 ! or http://forgerock.org/license/CDDLv1.0.html.
 ! See the License for the specific language governing permissions
 ! and limitations under the License.
 ! 
 ! When distributing Covered Code, include this CDDL HEADER in each
 ! file and include the License file at
 ! trunk/opends/resource/legal-notices/CDDLv1_0.txt.  If applicable,
 ! add the following below this CDDL HEADER, with the fields enclosed
 ! by brackets "[]" replaced with your own identifying information:
 !      Portions Copyright [yyyy] [name of copyright owner]
 !
 ! CDDL HEADER END
 !
 !      Copyright 2011-2013 ForgeRock AS.
 ! -->

<xsl:output method="xml" indent="yes" />
<xsl:template match="/">
<testsuites>
  <xsl:variable name="ftpath"           select="/qa/functional-tests"/>
  <xsl:variable name="id"               select="$ftpath/identification"/>
  <xsl:variable name="results"          select="$ftpath/results"/>
  <xsl:variable name="testgroup"        select="$results/testgroup"/>
  <xsl:variable name="testsuite"        select="$testgroup/testsuite"/>
  <xsl:variable name="testcase"         select="$testsuite/testcase"/>
  <xsl:variable name="total-tests"      select="count($testcase)"/>
  <xsl:variable name="pass-tests"       select="count($testcase[@result='pass'])"/>
  <xsl:variable name="kfail-tests"      select="count($testcase/issues)"/>
  <xsl:variable name="inconc-tests"     select="count($testcase[@result='unknown'])"/>
  <xsl:variable name="fail-tests"       select="count($testcase[@result='fail']) - $kfail-tests"/>
  <testsuite name="opendj.tests.functional"
             time="0"
             tests="{$total-tests}"
             errors="{$inconc-tests}"
             failures="{$fail-tests}"
             skipped="{$kfail-tests}">

    <xsl:for-each select="$testcase">
      <xsl:variable name="issue">
        <xsl:value-of select="issues/issue/@id"/>
      </xsl:variable>
      <xsl:variable name="message" select="'no message'"/>
      <testcase classname="{ancestor::testgroup[1]/@name}.{ancestor::testsuite[1]/@shortname}" 
      			name="{@shortname}"
      			time="{@duration}">
        <xsl:if test="contains(@result, 'unknown')">
          <error>
            <xsl:value-of select="'log inconclusive'"/>
          </error>
        </xsl:if>
        <xsl:if test="contains(@result, 'fail') and string-length($issue) = 0">
          <failure>
            <xsl:value-of select="'log fail'"/>
          </failure>
        </xsl:if>
        <xsl:if test="contains(@result, 'fail') and string-length($issue) &gt; 0">
          <skipped>
            <xsl:value-of select="$issue"/>
          </skipped>
        </xsl:if>
      </testcase>
    </xsl:for-each>
  
  </testsuite>
</testsuites>
</xsl:template>
</xsl:stylesheet>

