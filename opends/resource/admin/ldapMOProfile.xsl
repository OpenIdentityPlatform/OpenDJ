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
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:adm="http://www.opends.org/admin"
  xmlns:ldap="http://www.opends.org/admin-ldap">
  <xsl:import href="java-utilities.xsl" />
  <xsl:import href="preprocessor.xsl" />
  <xsl:import href="property-types.xsl" />
  <xsl:output method="text" encoding="us-ascii" />
  <!--
    Document parsing.
  -->
  <xsl:template match="/">
    <xsl:if
      test="not($this/adm:profile[@name='ldap']/ldap:object-class/ldap:name) and not($this-is-root)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('No object class found for managed object definition ', $this-name)" />
      </xsl:message>
    </xsl:if>
    <xsl:value-of
      select="concat('objectclass=',
                     normalize-space($this/adm:profile[@name='ldap']/ldap:object-class/ldap:name),
                     '&#xa;')" />
    <xsl:for-each select="$this-all-properties">
      <xsl:sort select="@name" />
      <xsl:if
        test="not(adm:profile[@name='ldap']/ldap:attribute/ldap:name)">
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('No attribute type found for property ', @name, ' in managed object definition ', $this-name)" />
        </xsl:message>
      </xsl:if>
      <xsl:value-of
        select="concat('attribute.',
                       normalize-space(@name),
                       '=',
                       normalize-space(adm:profile[@name='ldap']/ldap:attribute/ldap:name),
                       '&#xa;')" />
    </xsl:for-each>
    <xsl:for-each select="$this-all-relations">
      <xsl:sort select="@name" />
      <xsl:if test="not(adm:profile[@name='ldap']/ldap:rdn-sequence)">
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('No RDN sequence found for relation ', @name, ' in managed object definition ', $this-name)" />
        </xsl:message>
      </xsl:if>
      <xsl:value-of
        select="concat('rdn.',
                       normalize-space(@name),
                       '=',
                       normalize-space(adm:profile[@name='ldap']/ldap:rdn-sequence),
                       '&#xa;')" />
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
