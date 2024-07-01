<!--
  The contents of this file are subject to the terms of the Common Development and
  Distribution License (the License). You may not use this file except in compliance with the
  License.

  You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
  specific language governing permission and limitations under the License.

  When distributing Covered Software, include this CDDL Header Notice in each file and include
  the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
  Header, with the fields enclosed by brackets [] replaced by your own identifying
  information: "Portions Copyright [year] [name of copyright owner]".

  Copyright 2008 Sun Microsystems, Inc.
  ! -->
<xsl:stylesheet version="1.0" xmlns:adm="http://opendj.forgerock.org/admin"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <!--
    
    
    
    Rules for compiling conditions from their XML definition.
    
    
    
  -->
  <!--
    and condition
  -->
  <xsl:template match="adm:and" mode="compile-condition">
    <xsl:value-of select="'Conditions.and('" />
    <xsl:for-each select="*">
      <xsl:apply-templates select="." mode="compile-condition" />
      <xsl:if test="position() != last()">
        <xsl:value-of select="', '" />
      </xsl:if>
    </xsl:for-each>
    <xsl:value-of select="')'" />
  </xsl:template>
  <!--
    or condition
  -->
  <xsl:template match="adm:or" mode="compile-condition">
    <xsl:value-of select="'Conditions.or('" />
    <xsl:for-each select="*">
      <xsl:apply-templates select="." mode="compile-condition" />
      <xsl:if test="position() != last()">
        <xsl:value-of select="', '" />
      </xsl:if>
    </xsl:for-each>
    <xsl:value-of select="')'" />
  </xsl:template>
  <!--
    not condition
  -->
  <xsl:template match="adm:not" mode="compile-condition">
    <xsl:value-of select="'Conditions.not('" />
    <xsl:apply-templates select="*[1]" mode="compile-condition" />
    <xsl:value-of select="')'" />
  </xsl:template>
  <!--
    implies condition
  -->
  <xsl:template match="adm:implies" mode="compile-condition">
    <xsl:value-of select="'Conditions.implies('" />
    <xsl:apply-templates select="*[1]" mode="compile-condition" />
    <xsl:value-of select="', '" />
    <xsl:apply-templates select="*[2]" mode="compile-condition" />
    <xsl:value-of select="')'" />
  </xsl:template>
  <!--
    contains condition
  -->
  <xsl:template match="adm:contains" mode="compile-condition">
    <xsl:value-of
      select="concat('Conditions.contains(&quot;', @property, '&quot;, &quot;', @value, '&quot;)')" />
  </xsl:template>
  <!--
    is-present condition
  -->
  <xsl:template match="adm:is-present" mode="compile-condition">
    <xsl:value-of
      select="concat('Conditions.isPresent(&quot;', @property, '&quot;)')" />
  </xsl:template>
</xsl:stylesheet>
