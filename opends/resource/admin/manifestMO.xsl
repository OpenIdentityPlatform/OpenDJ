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
  !      Portions Copyright 2007 Sun Microsystems, Inc.
  ! -->
<xsl:stylesheet version="1.0" xmlns:adm="http://www.opends.org/admin"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:import href="java-utilities.xsl" />
  <xsl:output method="text" encoding="us-ascii" />
  <!-- 
    Main document parsing template.
  -->
  <xsl:template match="/">
    <xsl:choose>
      <xsl:when test="adm:root-managed-object">
        <xsl:value-of
          select="'org.opends.server.admin.std.meta.RootCfgDefn&#xa;'" />
      </xsl:when>
      <xsl:when test="adm:managed-object">
        <xsl:value-of
          select="normalize-space(adm:managed-object/@package)" />
        <xsl:value-of select="'.meta.'" />
        <xsl:call-template name="name-to-java">
          <xsl:with-param name="value"
            select="normalize-space(adm:managed-object/@name)" />
        </xsl:call-template>
        <xsl:value-of select="'CfgDefn&#xa;'" />
      </xsl:when>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
