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
  <xsl:import href="java-utilities.xsl" />
  <xsl:output method="text" encoding="us-ascii" />
  <!-- 
    Main document parsing template.
  -->
  <xsl:template match="/">
    <xsl:choose>
      <xsl:when test="adm:root-managed-object">
        <xsl:value-of
          select="'org.forgerock.opendj.server.config.meta.RootCfgDefn&#xa;'" />
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
