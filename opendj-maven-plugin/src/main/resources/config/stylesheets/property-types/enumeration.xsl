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
  xmlns:admpp="http://opendj.forgerock.org/admin-preprocessor"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <!-- 
    Templates for processing enumeration properties.
  -->
  <xsl:template match="adm:enumeration" mode="java-value-imports">
    <xsl:variable name="pp"
      select="../../adm:profile[@name='preprocessor']" />
    <xsl:element name="import">
      <xsl:choose>
        <xsl:when test="$pp/admpp:first-defined-in">
          <xsl:value-of
            select="concat($pp/admpp:first-defined-in/@package, '.')" />
          <xsl:if test="$pp/admpp:first-defined-in/@name">
            <xsl:value-of select="'meta.'" />
            <xsl:call-template name="name-to-java">
              <xsl:with-param name="value"
                select="$pp/admpp:first-defined-in/@name" />
            </xsl:call-template>
            <xsl:value-of select="'CfgDefn.'" />
          </xsl:if>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of
            select="concat($pp/admpp:last-defined-in/@package, '.')" />
          <xsl:value-of select="'meta.'" />
          <xsl:call-template name="name-to-java">
            <xsl:with-param name="value"
              select="$pp/admpp:last-defined-in/@name" />
          </xsl:call-template>
          <xsl:value-of select="'CfgDefn.'" />
        </xsl:otherwise>
      </xsl:choose>
      <xsl:apply-templates select="." mode="java-value-type" />
    </xsl:element>
  </xsl:template>
  <xsl:template match="adm:enumeration"
    mode="java-definition-imports">
    <xsl:element name="import">
      <xsl:value-of
        select="'org.forgerock.opendj.config.EnumPropertyDefinition'" />
    </xsl:element>
    <xsl:variable name="pp"
      select="../../adm:profile[@name='preprocessor']" />
    <xsl:if test="$pp/admpp:first-defined-in">
      <xsl:element name="import">
        <xsl:value-of
          select="concat($pp/admpp:first-defined-in/@package, '.')" />
        <xsl:if test="$pp/admpp:first-defined-in/@name">
          <xsl:value-of select="'meta.'" />
          <xsl:call-template name="name-to-java">
            <xsl:with-param name="value"
              select="$pp/admpp:first-defined-in/@name" />
          </xsl:call-template>
          <xsl:value-of select="'CfgDefn.'" />
        </xsl:if>
        <xsl:apply-templates select="." mode="java-value-type" />
      </xsl:element>
    </xsl:if>
  </xsl:template>
  <xsl:template match="adm:enumeration" mode="java-value-type">
    <xsl:call-template name="name-to-java">
      <xsl:with-param name="value" select="../../@name" />
    </xsl:call-template>
  </xsl:template>
  <xsl:template match="adm:enumeration" mode="java-definition-type">
    <xsl:value-of select="'EnumPropertyDefinition'" />
  </xsl:template>
  <xsl:template match="adm:enumeration"
    mode="java-definition-generic-type">
    <xsl:apply-templates select="." mode="java-value-type" />
  </xsl:template>
  <xsl:template match="adm:enumeration" mode="java-definition-ctor">
    <xsl:value-of select="'      builder.setEnumClass('" />
    <xsl:apply-templates select="." mode="java-value-type" />
    <xsl:value-of select="'.class);&#xa;'" />
  </xsl:template>
</xsl:stylesheet>
