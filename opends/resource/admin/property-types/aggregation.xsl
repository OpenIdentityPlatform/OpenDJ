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
  <!-- 
    Templates for processing aggregation properties.
  -->
  <xsl:template match="adm:aggregation" mode="java-definition-imports">
    <xsl:element name="import">
      <xsl:call-template name="get-definition-package" />
      <xsl:value-of select="'.client.'" />
      <xsl:call-template name="get-client-type" />
    </xsl:element>
    <xsl:element name="import">
      <xsl:call-template name="get-definition-package" />
      <xsl:value-of select="'.server.'" />
      <xsl:call-template name="get-server-type" />
    </xsl:element>
    <xsl:element name="import">
      <xsl:call-template name="get-definition-package" />
      <xsl:value-of select="'.meta.'" />
      <xsl:call-template name="get-definition-type" />
    </xsl:element>
    <import>org.opends.server.admin.ManagedObjectPath</import>
  </xsl:template>
  <xsl:template match="adm:aggregation" mode="java-value-type">
    <xsl:value-of select="'String'" />
  </xsl:template>
  <xsl:template match="adm:aggregation" mode="java-definition-type">
    <xsl:value-of select="'AggregationPropertyDefinition'" />
  </xsl:template>
  <xsl:template match="adm:aggregation"
    mode="java-definition-generic-type">
    <xsl:call-template name="get-client-type" />
    <xsl:value-of select="', '" />
    <xsl:call-template name="get-server-type" />
  </xsl:template>
  <xsl:template match="adm:aggregation" mode="java-definition-ctor">
    <xsl:if test="not(@parent-path)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('No parent-path defined for aggregation property ', ../../@name)" />
      </xsl:message>
    </xsl:if>
    <xsl:if test="not(@relation-name)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('No relation-name defined for aggregation property ', ../../@name)" />
      </xsl:message>
    </xsl:if>
    <xsl:if
      test="@source-enabled-property-name and not(@target-enabled-property-name)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('source-enabled-property-name defined but target-enabled-property-name undefined in aggregation property ', ../../@name)" />
      </xsl:message>
    </xsl:if>
    <xsl:value-of
      select="concat('      builder.setParentPath(ManagedObjectPath.valueOf(&quot;',
                     normalize-space(@parent-path), '&quot;));&#xa;')" />
    <xsl:value-of
      select="concat('      builder.setRelationDefinition(&quot;',
                     normalize-space(@relation-name), '&quot;);&#xa;')" />
    <xsl:value-of select="'      builder.setManagedObjectDefinition('" />
    <xsl:call-template name="get-definition-type" />
    <xsl:value-of select="'.getInstance());&#xa;'" />
    <xsl:if test="@source-enabled-property-name">
      <xsl:value-of
        select="concat('      builder.setSourceEnabledPropertyName(&quot;',
                       normalize-space(@source-enabled-property-name), '&quot;);&#xa;')" />
    </xsl:if>
    <xsl:if test="@target-enabled-property-name">
      <xsl:value-of
        select="concat('      builder.setTargetEnabledPropertyName(&quot;',
                       normalize-space(@target-enabled-property-name), '&quot;);&#xa;')" />
    </xsl:if>
  </xsl:template>
  <!--
    Gets the Java client configuration interface for the referenced type.
  -->
  <xsl:template name="get-client-type">
    <xsl:call-template name="get-reference-type" />
    <xsl:value-of select="'CfgClient'" />
  </xsl:template>
  <!--
    Gets the Java server configuration interface for the referenced type.
  -->
  <xsl:template name="get-server-type">
    <xsl:call-template name="get-reference-type" />
    <xsl:value-of select="'Cfg'" />
  </xsl:template>
  <!--
    Gets the Java definition configuration interface for the referenced type.
  -->
  <xsl:template name="get-definition-type">
    <xsl:call-template name="get-reference-type" />
    <xsl:value-of select="'CfgDefn'" />
  </xsl:template>
  <!--
    Gets the Java definition configuration package.
  -->
  <xsl:template name="get-definition-package">
    <xsl:choose>
      <xsl:when test="@managed-object-package">
        <xsl:value-of select="@managed-object-package" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$this-package" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--
    Gets the Java name for the referenced type.
  -->
  <xsl:template name="get-reference-type">
    <xsl:choose>
      <xsl:when test="@managed-object-name">
        <xsl:call-template name="name-to-java">
          <xsl:with-param name="value" select="@managed-object-name" />
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="name-to-java">
          <xsl:with-param name="value" select="@relation-name" />
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
