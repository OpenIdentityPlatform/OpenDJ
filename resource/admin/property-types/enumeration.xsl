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
  !
  !      Copyright 2008 Sun Microsystems, Inc.
  ! -->
<xsl:stylesheet version="1.0" xmlns:adm="http://www.opends.org/admin"
  xmlns:admpp="http://www.opends.org/admin-preprocessor"
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
        select="'org.opends.server.admin.EnumPropertyDefinition'" />
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
