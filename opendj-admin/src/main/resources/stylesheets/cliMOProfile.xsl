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
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:adm="http://www.opends.org/admin"
  xmlns:cli="http://www.opends.org/admin-cli">
  <xsl:import href="preprocessor.xsl" />
  <xsl:output method="text" encoding="us-ascii" />
  <!--
    Document parsing.
  -->
  <xsl:template match="/">
    <!--
      Determine if the managed object is for customization.
    -->
    <xsl:choose>
      <xsl:when
        test="$this/adm:profile[@name='cli']/cli:managed-object/@custom='true'">
        <xsl:value-of select="'is-for-customization=true&#xa;'" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="'is-for-customization=false&#xa;'" />
      </xsl:otherwise>
    </xsl:choose>
    <!--
      Process each relation definition.
    -->
    <xsl:for-each select="$this-all-relations">
      <xsl:sort select="@name" />
      <!--
        Generate the naming argument override if present
      -->
      <xsl:value-of
        select="concat('relation.', @name,
                       '.naming-argument-override=',
                       adm:profile[@name='cli']/cli:relation/@naming-argument-override,
                       '&#xa;')" />
      <!--
        Generate list of properties which should be displayed by default in list-xxx operations.
      -->
      <xsl:value-of
        select="concat('relation.', @name, '.list-properties=')" />
      <xsl:for-each
        select="adm:profile[@name='cli']/cli:relation/cli:default-property">
        <xsl:value-of select="@name" />
        <xsl:if test="current() != last()">
          <xsl:value-of select="','" />
        </xsl:if>
      </xsl:for-each>
      <xsl:value-of select="'&#xa;'" />
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
