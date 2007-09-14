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
  <xsl:template match="adm:aggregation"
    mode="java-definition-imports">
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
    <xsl:if test="../../@multi-valued = 'true'">
      <import>java.util.TreeSet</import>
    </xsl:if>
    <import>org.opends.server.admin.ManagedObjectPath</import>
    <import>org.opends.server.admin.AggregationPropertyDefinition</import>
  </xsl:template>
  <xsl:template match="adm:aggregation" mode="java-value-type">
    <xsl:value-of select="'String'" />
  </xsl:template>
  <xsl:template match="adm:aggregation" mode="java-value-imports">
    <xsl:param name="interface" select="/.." />
    <xsl:if test="$interface = 'server'">
      <import>org.opends.server.types.DN</import>
    </xsl:if>
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
  <xsl:template match="adm:aggregation"
    mode="java-definition-post-ctor">
    <xsl:value-of select="'      INSTANCE.registerConstraint(PD_'" />
    <xsl:call-template name="name-to-java-constant">
      <xsl:with-param name="value" select="../../@name" />
    </xsl:call-template>
    <xsl:value-of select="');&#xa;'" />
  </xsl:template>
  <!--
    Generate property getter declaration(s).
  -->
  <xsl:template match="adm:aggregation"
    mode="java-property-getter-declaration">
    <xsl:param name="interface" select="/.." />
    <xsl:call-template
      name="generate-default-property-getter-declaration">
      <xsl:with-param name="interface" select="$interface" />
    </xsl:call-template>
    <xsl:if test="$interface='server'">
      <xsl:variable name="name" select="../../@name" />
      <xsl:variable name="java-property-name">
        <xsl:call-template name="name-to-java">
          <xsl:with-param name="value" select="$name" />
        </xsl:call-template>
      </xsl:variable>
      <xsl:variable name="comment">
        <xsl:if test="../../adm:synopsis">
          <xsl:value-of select="'&lt;p&gt;&#xa;'" />
          <xsl:value-of select="normalize-space(../../adm:synopsis)" />
          <xsl:value-of select="'&#xa;'" />
        </xsl:if>
        <xsl:if test="../../adm:description">
          <xsl:value-of select="'&lt;p&gt;&#xa;'" />
          <xsl:value-of select="normalize-space(../../adm:description)" />
          <xsl:value-of select="'&#xa;'" />
        </xsl:if>
      </xsl:variable>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:choose>
        <xsl:when test="string(../../@multi-valued) != 'true'">
          <xsl:call-template name="add-java-comment2">
            <xsl:with-param name="indent" select="2" />
            <xsl:with-param name="content"
              select="concat(
                       'Gets the &quot;', $name,'&quot; property as a DN.&#xa;',
                       $comment,
                       '&#xa;',
                       '@return Returns the DN value of the &quot;', $name, '&quot; property.&#xa;')" />
          </xsl:call-template>
          <xsl:value-of
            select="concat('  DN get', $java-property-name, 'DN();&#xa;')" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:call-template name="add-java-comment2">
            <xsl:with-param name="indent" select="2" />
            <xsl:with-param name="content"
              select="concat(
                       'Gets the &quot;', $name,'&quot; property as a set of DNs.&#xa;',
                       $comment,
                       '&#xa;',
                       '@return Returns the DN values of the &quot;', $name, '&quot; property.&#xa;')" />
          </xsl:call-template>
          <xsl:value-of
            select="concat('  SortedSet&lt;DN&gt; get', $java-property-name, 'DNs();&#xa;')" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>
  <!--
    Generate property getter implementation(s).
  -->
  <xsl:template match="adm:aggregation"
    mode="java-property-getter-implementation">
    <xsl:param name="interface" select="/.." />
    <xsl:call-template
      name="generate-default-property-getter-implementation">
      <xsl:with-param name="interface" select="$interface" />
    </xsl:call-template>
    <xsl:if test="$interface='server'">
      <xsl:variable name="name" select="../../@name" />
      <xsl:variable name="java-property-name">
        <xsl:call-template name="name-to-java">
          <xsl:with-param name="value" select="$name" />
        </xsl:call-template>
      </xsl:variable>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="add-java-comment2">
        <xsl:with-param name="indent" select="4" />
        <xsl:with-param name="content" select="'{@inheritDoc}&#xa;'" />
      </xsl:call-template>
      <xsl:choose>
        <xsl:when test="string(../../@multi-valued) != 'true'">
          <xsl:value-of
            select="concat('    public DN get', $java-property-name, 'DN() {&#xa;')" />
          <xsl:value-of
            select="concat('      String value = get', $java-property-name, '();&#xa;')" />
          <xsl:value-of
            select="concat('      return INSTANCE.get', $java-property-name, 'PropertyDefinition().getChildDN(value);&#xa;')" />
          <xsl:value-of select="'    }&#xa;'" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of
            select="concat('    public SortedSet&lt;DN&gt; get', $java-property-name, 'DNs() {&#xa;')" />
          <xsl:value-of
            select="concat('      SortedSet&lt;String&gt; values = get', $java-property-name, '();&#xa;')" />
          <xsl:value-of
            select="'      SortedSet&lt;DN&gt; dnValues = new TreeSet&lt;DN&gt;();&#xa;'" />
          <xsl:value-of
            select="'      for (String value : values) {&#xa;'" />
          <xsl:value-of
            select="concat('        DN dn = INSTANCE.get', $java-property-name, 'PropertyDefinition().getChildDN(value);&#xa;')" />
          <xsl:value-of select="'        dnValues.add(dn);&#xa;'" />
          <xsl:value-of select="'      }&#xa;'" />
          <xsl:value-of select="'      return dnValues;&#xa;'" />
          <xsl:value-of select="'    }&#xa;'" />
        </xsl:otherwise>
      </xsl:choose>
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
