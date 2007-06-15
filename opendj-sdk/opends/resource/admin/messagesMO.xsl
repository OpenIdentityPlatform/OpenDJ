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
    <!--
      Generate user friendly names.
    -->
    <xsl:value-of
      select="concat('user-friendly-name=', $this-ufn, '&#xa;')" />
    <xsl:value-of
      select="concat('user-friendly-plural-name=', $this-ufpn, '&#xa;')" />
    <!--
      Pull out the managed object synopsis (mandatory).
    -->
    <xsl:if test="not($this/adm:synopsis)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('No synopsis found for managed object definition ', $this-name)" />
      </xsl:message>
    </xsl:if>
    <xsl:value-of
      select="concat('synopsis=', normalize-space($this/adm:synopsis), '&#xa;')" />
    <!--
      Pull out the managed object description (optional).
    -->
    <xsl:if test="$this/adm:description">
      <xsl:value-of
        select="concat('description=', normalize-space($this/adm:description), '&#xa;')" />
    </xsl:if>
    <!--
      Process tag definitions if this is the root configuration.
    -->
    <xsl:if test="$this-is-root">
      <xsl:for-each select="$this/adm:tag-definition">
        <xsl:sort select="@name" />
        <xsl:value-of
          select="concat('tag.', @name, '.synopsis=', normalize-space(adm:synopsis), '&#xa;')" />
      </xsl:for-each>
    </xsl:if>
    <!--
      Process each property definition.
    -->
    <xsl:for-each select="$this-all-properties">
      <xsl:sort select="@name" />
      <!--
        Pull out the property definition synopsis (mandatory).
      -->
      <xsl:if test="not(adm:synopsis)">
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('No synopsis found for property ', @name, ' in managed object definition ', $this-name)" />
        </xsl:message>
      </xsl:if>
      <xsl:value-of
        select="concat('property.', normalize-space(@name), '.synopsis=', normalize-space(adm:synopsis), '&#xa;')" />
      <!--
        Pull out the property definition description (optional).
      -->
      <xsl:if test="adm:description">
        <xsl:value-of
          select="concat('property.', normalize-space(@name), '.description=', normalize-space(adm:description), '&#xa;')" />
      </xsl:if>
      <!--
        Process alias default behavior synopsis.
      -->
      <xsl:if test="adm:default-behavior/adm:alias">
        <xsl:if
          test="not(adm:default-behavior/adm:alias/adm:synopsis)">
          <xsl:message terminate="yes">
            <xsl:value-of
              select="concat('No alias default behavior synopsis found for property ', @name, ' in managed object definition ', $this-name)" />
          </xsl:message>
        </xsl:if>
        <xsl:value-of
          select="concat('property.', normalize-space(@name), '.default-behavior.alias.synopsis=', normalize-space(adm:default-behavior/adm:alias/adm:synopsis), '&#xa;')" />
      </xsl:if>
      <!--
        Process requires admin action synopsis if present.
      -->
      <xsl:if test="adm:requires-admin-action/*/adm:synopsis">
        <xsl:value-of
          select="concat('property.', normalize-space(@name), '.requires-admin-action.synopsis=', normalize-space(adm:requires-admin-action/*/adm:synopsis), '&#xa;')" />
      </xsl:if>
      <!--
        Process syntax related descriptions.
      -->
      <xsl:choose>
        <xsl:when test="adm:syntax/adm:integer">
          <!--
            Process integer syntax unit synopsis (optional).
          -->
          <xsl:if test="adm:syntax/adm:integer/adm:synopsis">
            <xsl:value-of
              select="concat('property.', normalize-space(@name), '.syntax.integer.unit-synopsis=', normalize-space(adm:syntax/adm:integer/adm:synopsis), '&#xa;')" />
          </xsl:if>
        </xsl:when>
        <xsl:when test="adm:syntax/adm:string/adm:pattern">
          <!--
            Process string syntax pattern synopsis (mandatory if pattern defined).
          -->
          <xsl:if
            test="not(adm:syntax/adm:string/adm:pattern/adm:synopsis)">
            <xsl:message terminate="yes">
              <xsl:value-of
                select="concat('No string pattern synopsis found for property ', @name, ' in managed object definition ', $this-name)" />
            </xsl:message>
          </xsl:if>
          <xsl:value-of
            select="concat('property.', normalize-space(@name), '.syntax.string.pattern.synopsis=', normalize-space(adm:syntax/adm:string/adm:pattern/adm:synopsis), '&#xa;')" />
        </xsl:when>
        <xsl:when test="adm:syntax/adm:enumeration">
          <!--
            Process enumeration value synopsis (mandatory).
          -->
          <xsl:for-each select="adm:syntax/adm:enumeration/adm:value">
            <xsl:sort select="@name" />
            <xsl:if test="not(adm:synopsis)">
              <xsl:message terminate="yes">
                <xsl:value-of
                  select="concat('No synopsis found for enumeration value ', @name, ' for property ', ../../../@name, ' in managed object definition ', $this-name)" />
              </xsl:message>
            </xsl:if>
            <xsl:value-of
              select="concat('property.', normalize-space(../../../@name), '.syntax.enumeration.value.', @name,'.synopsis=', normalize-space(adm:synopsis), '&#xa;')" />
          </xsl:for-each>
        </xsl:when>
      </xsl:choose>
    </xsl:for-each>
    <!--
      Process each relation definition.
    -->
    <xsl:for-each select="$this-all-relations">
      <xsl:sort select="@name" />
      <!--
        Generate user friendly names.
      -->
      <xsl:value-of
        select="concat('relation.', normalize-space(@name), '.user-friendly-name=')" />
      <xsl:call-template name="name-to-ufn">
        <xsl:with-param name="value" select="@name" />
      </xsl:call-template>
      <xsl:value-of select="'&#xa;'" />
      <xsl:if test="adm:one-to-many">
        <xsl:value-of
          select="concat('relation.', normalize-space(@name), '.user-friendly-plural-name=')" />
        <xsl:call-template name="name-to-ufn">
          <xsl:with-param name="value"
            select="adm:one-to-many/@plural-name" />
        </xsl:call-template>
        <xsl:value-of select="'&#xa;'" />
      </xsl:if>
      <!--
        Pull out the relation definition synopsis (mandatory).
      -->
      <xsl:if test="not(adm:synopsis)">
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('No synopsis found for relation ', @name, ' in managed object definition ', $this-name)" />
        </xsl:message>
      </xsl:if>
      <xsl:value-of
        select="concat('relation.', normalize-space(@name), '.synopsis=', normalize-space(adm:synopsis), '&#xa;')" />
      <!--
        Pull out the relation definition description (optional).
      -->
      <xsl:if test="adm:description">
        <xsl:value-of
          select="concat('relation.', normalize-space(@name), '.description=', normalize-space(adm:description), '&#xa;')" />
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
