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
    Global parameter: the sub-package name. Either 'meta', 'client', or 'server'.
  -->
  <xsl:param name="type" select="'.'" />
  <!-- 
    Main document parsing template.
  -->
  <xsl:template match="/">
    <xsl:call-template name="copyright-notice" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of select="'/**&#xa;'" />
    <xsl:choose>
      <xsl:when test="$type='meta'">
        <xsl:call-template name="add-java-comment">
          <xsl:with-param name="indent-text" select="' *'" />
          <xsl:with-param name="content"
            select="concat('Provides introspection interfaces for the ',
                           normalize-space(adm:package/adm:synopsis),
                           ' This package provides access to meta-',
                           'information about the managed objects, their ',
                           'properties, their relationships with other ',
                           'managed objects, and their inheritance model.')" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$type='client'">
        <xsl:call-template name="add-java-comment">
          <xsl:with-param name="indent-text" select="' *'" />
          <xsl:with-param name="content"
            select="concat('Provides client-side interfaces for querying ',
                           'and managing the ',
                           normalize-space(adm:package/adm:synopsis),
                           ' Applications can use the interfaces defined ',
                           'within this package to retrieve, list, create, ',
                           'and remove managed objects, as well as query ',
                           'and update their properties.')" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$type='server'">
        <xsl:call-template name="add-java-comment">
          <xsl:with-param name="indent-text" select="' *'" />
          <xsl:with-param name="content"
            select="concat('Provides server-side interfaces for accessing ',
                           'the ', normalize-space(adm:package/adm:synopsis),
                           ' Components within the server can use the ',
                           'interfaces defined within this package to query ',
                           'the properties of the managed objects and ',
                           'register to be notified when managed objects are ',
                           'added, removed, or modified.')" />
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Invalid package-info sub-package name: ', $type)" />
        </xsl:message>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:value-of select="' */&#xa;'" />
    <xsl:value-of
      select="concat('package ', adm:package/@name, '.', $type, ';&#xa;')" />
  </xsl:template>
</xsl:stylesheet>
