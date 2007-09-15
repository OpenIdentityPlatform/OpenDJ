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
    
    
    
    WARNING: when new property types are defined, they must be
    included here.
    
    These stylesheets are included and NOT imported so that they
    have the same import precedence as the default rules.
    
    
    
  -->
  <xsl:include href="property-types/aggregation.xsl" />
  <xsl:include href="property-types/attribute-type.xsl" />
  <xsl:include href="property-types/boolean.xsl" />
  <xsl:include href="property-types/dn.xsl" />
  <xsl:include href="property-types/duration.xsl" />
  <xsl:include href="property-types/enumeration.xsl" />
  <xsl:include href="property-types/integer.xsl" />
  <xsl:include href="property-types/ip-address-mask.xsl" />
  <xsl:include href="property-types/ip-address.xsl" />
  <xsl:include href="property-types/java-class.xsl" />
  <xsl:include href="property-types/aci.xsl" />
  <xsl:include href="property-types/oid.xsl" />
  <xsl:include href="property-types/password.xsl" />
  <xsl:include href="property-types/size.xsl" />
  <xsl:include href="property-types/string.xsl" />
  <!--
    
    
    
    Default rules applicable to each property type.
    
    Property type stylesheets should override these where necessary.
    
    
    
  -->
  <!--
    Get the Java object-based type associated with a property syntax.
    
    By default property values are represented using strings.
  -->
  <xsl:template match="*" mode="java-value-type">
    <xsl:value-of select="'String'" />
  </xsl:template>
  <!--
    Get the Java primitive type, if applicable, associated with a
    property syntax.
    
    By default property values are represented using the type defined by
    java-value-type.
  -->
  <xsl:template match="*" mode="java-value-primitive-type">
    <xsl:apply-templates select="." mode="java-value-type" />
  </xsl:template>
  <!--
    Generate import elements represesenting the import statements
    required by values of the property.
    
    By default property values are represented using strings which
    don't require an import statement - so do nothing.
  -->
  <xsl:template match="*" mode="java-value-imports">
    <xsl:param name="interface" select="/.." />
  </xsl:template>
  <!--
    Generate the Java definition type used to define the property.
    
    By default properties are defined using string property
    definitions.
  -->
  <xsl:template match="*" mode="java-definition-type">
    <xsl:value-of select="'StringPropertyDefinition'" />
  </xsl:template>
  <!--
    Generate import elements represesenting the import statements
    required by the property's definition and its values.
    
    By default assume that the definition type is in
    org.opends.server.admin and is derived directly from the
    java-definition-type (might not be the case for parameterized
    types. In addition pull in the value imports.
  -->
  <xsl:template match="*" mode="java-definition-imports">
    <xsl:element name="import">
      <xsl:value-of select="'org.opends.server.admin.'" />
      <xsl:apply-templates select="." mode="java-definition-type" />
    </xsl:element>
    <xsl:apply-templates select="." mode="java-value-imports">
      <xsl:with-param name="interface" select="'server'" />
    </xsl:apply-templates>
  </xsl:template>
  <!--
    If the property definition is generic, get the generic type. Otherwise,
    do nothing.
    
    Default: do nothing.
  -->
  <xsl:template match="*" mode="java-definition-generic-type" />
  <!--
    Generate property definition specific constructor setters.
    
    By default, do nothing.
  -->
  <xsl:template match="*" mode="java-definition-ctor" />
  <!--
    Generate property definition specific post-construction code.
    
    By default, do nothing.
  -->
  <xsl:template match="*" mode="java-definition-post-ctor" />
  <!--
    Generate property getter declaration(s).
    
    By default, generate a single getter with minimal documentation.
  -->
  <xsl:template match="*" mode="java-property-getter-declaration">
    <xsl:param name="interface" select="/.." />
    <xsl:call-template
      name="generate-default-property-getter-declaration">
      <xsl:with-param name="interface" select="$interface" />
    </xsl:call-template>
  </xsl:template>
  <!--
    Generate property getter implementation(s).
    
    By default, generate a single getter.
  -->
  <xsl:template match="*" mode="java-property-getter-implementation">
    <xsl:param name="interface" select="/.." />
    <xsl:call-template
      name="generate-default-property-getter-implementation">
      <xsl:with-param name="interface" select="$interface" />
    </xsl:call-template>
  </xsl:template>
  <!--
    Generate property setter declaration(s).
    
    By default, generate a single setter with minimal documentation.
  -->
  <xsl:template match="*" mode="java-property-setter-declaration">
    <xsl:call-template
      name="generate-default-property-setter-declaration" />
  </xsl:template>
  <!--
    Generate property setter implementation(s).
    
    By default, generate a single setter.
  -->
  <xsl:template match="*" mode="java-property-setter-implementation">
    <xsl:call-template
      name="generate-default-property-setter-implementation" />
  </xsl:template>
  <!--
    
    
    Wrapper templates which can be called directly instead of
    requiring the more indirect and less readable apply-templates
    mechanism.
    
    
  -->
  <!-- 
    Get the Java imports required for a property's values.
  -->
  <xsl:template name="get-property-java-imports">
    <xsl:param name="interface" select="/.." />
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-value-imports">
      <xsl:with-param name="interface" select="$interface" />
    </xsl:apply-templates>
  </xsl:template>
  <!-- 
    Get the Java imports required for a property's definition.
  -->
  <xsl:template name="get-property-definition-java-imports">
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-definition-imports" />
  </xsl:template>
  <!-- 
    Get the Java object-based type associated with a property syntax.
  -->
  <xsl:template name="get-property-java-type">
    <xsl:apply-templates select="adm:syntax/*" mode="java-value-type" />
  </xsl:template>
  <!-- 
    Get the Java primitive type, if applicable, associated with a
    property syntax.
  -->
  <xsl:template name="get-property-java-primitive-type">
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-value-primitive-type" />
  </xsl:template>
  <!-- 
    Get the property definition type associated with a
    property syntax.
  -->
  <xsl:template name="get-property-definition-type">
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-definition-type" />
  </xsl:template>
  <!-- 
    If the property definition is generic, get the generic type. Otherwise,
    do nothing.
  -->
  <xsl:template name="get-property-definition-generic-type">
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-definition-generic-type" />
  </xsl:template>
  <!-- 
    Generate property definition specific constructor setters.
  -->
  <xsl:template name="get-property-definition-ctor">
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-definition-ctor" />
  </xsl:template>
  <!-- 
    Generate property definition specific post-construction code.
  -->
  <xsl:template name="get-property-definition-post-ctor">
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-definition-post-ctor" />
  </xsl:template>
  <!-- 
    Generate the property getter declarations.
  -->
  <xsl:template name="generate-property-getter-declaration">
    <xsl:param name="interface" select="/.." />
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-property-getter-declaration">
      <xsl:with-param name="interface" select="$interface" />
    </xsl:apply-templates>
  </xsl:template>
  <!-- 
    Generate the property getter implementations.
  -->
  <xsl:template name="generate-property-getter-implementation">
    <xsl:param name="interface" select="/.." />
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-property-getter-implementation">
      <xsl:with-param name="interface" select="$interface" />
    </xsl:apply-templates>
  </xsl:template>
  <!-- 
    Generate the property setter declarations.
  -->
  <xsl:template name="generate-property-setter-declaration">
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-property-setter-declaration" />
  </xsl:template>
  <!-- 
    Generate the property setter implementations.
  -->
  <xsl:template name="generate-property-setter-implementation">
    <xsl:apply-templates select="adm:syntax/*"
      mode="java-property-setter-implementation" />
  </xsl:template>
  <!--
    Generate the default property getter declarations.
  -->
  <xsl:template name="generate-default-property-getter-declaration">
    <xsl:param name="interface" select="/.." />
    <xsl:apply-templates select="../.."
      mode="generate-default-property-getter-declaration-aux">
      <xsl:with-param name="interface" select="$interface" />
    </xsl:apply-templates>
  </xsl:template>
  <xsl:template match="adm:property"
    mode="generate-default-property-getter-declaration-aux">
    <xsl:param name="interface" select="/.." />
    <xsl:variable name="name" select="@name" />
    <xsl:variable name="java-property-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="$name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:value-of
      select="concat('  /**&#xa;',
                     '   * Gets the &quot;', $name,'&quot; property.&#xa;')" />
    <xsl:if test="adm:synopsis">
      <xsl:value-of select="'   * &lt;p&gt;&#xa;'" />
      <xsl:call-template name="add-java-comment">
        <xsl:with-param name="indent-text" select="'   *'" />
        <xsl:with-param name="content" select="adm:synopsis" />
      </xsl:call-template>
    </xsl:if>
    <xsl:if test="adm:description">
      <xsl:value-of select="'   * &lt;p&gt;&#xa;'" />
      <xsl:call-template name="add-java-comment">
        <xsl:with-param name="indent-text" select="'   *'" />
        <xsl:with-param name="content" select="adm:description" />
      </xsl:call-template>
    </xsl:if>
    <xsl:choose>
      <xsl:when test="string(@multi-valued) != 'true'">
        <xsl:value-of
          select="concat('   *&#xa;',
                     '   * @return Returns the value of the &quot;', $name,'&quot; property.&#xa;',
                     '   */&#xa;')" />
        <xsl:value-of select="'  '" />
        <xsl:choose>
          <xsl:when test="adm:default-behavior/adm:defined">
            <!-- 
              The method is guaranteed to return a value since there is a
              well-defined default value.
            -->
            <xsl:call-template name="get-property-java-primitive-type" />
          </xsl:when>
          <xsl:when
            test="$interface = 'server' and @mandatory = 'true'">
            <!-- 
              The method is guaranteed to return a value in the server interface, but
              not necessarily in the client, since the mandatory property might not
              have been created yet.
            -->
            <xsl:call-template name="get-property-java-primitive-type" />
          </xsl:when>
          <xsl:otherwise>
            <xsl:call-template name="get-property-java-type" />
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>
      <xsl:otherwise>
        <xsl:choose>
          <xsl:when test="$interface='server'">
            <xsl:value-of
              select="concat('   *&#xa;',
                     '   * @return Returns an unmodifiable set containing the values of the &quot;', $name,'&quot; property.&#xa;',
                     '   */&#xa;')" />
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of
              select="concat('   *&#xa;',
                     '   * @return Returns the values of the &quot;', $name,'&quot; property.&#xa;',
                     '   */&#xa;')" />
          </xsl:otherwise>
        </xsl:choose>
        <xsl:value-of select="'  SortedSet&lt;'" />
        <xsl:call-template name="get-property-java-type" />
        <xsl:value-of select="'&gt;'" />
      </xsl:otherwise>
    </xsl:choose>
    <xsl:choose>
      <xsl:when test="adm:syntax/adm:boolean">
        <xsl:value-of select="' is'" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="' get'" />
      </xsl:otherwise>
    </xsl:choose>
    <xsl:value-of
      select="concat($java-property-name,
                                 '();&#xa;')" />
  </xsl:template>
  <!--
    Generate the default property getter implementation.
  -->
  <xsl:template
    name="generate-default-property-getter-implementation">
    <xsl:param name="interface" select="/.." />
    <xsl:apply-templates select="../.."
      mode="generate-default-property-getter-implementation-aux">
      <xsl:with-param name="interface" select="$interface" />
    </xsl:apply-templates>
  </xsl:template>
  <xsl:template match="adm:property"
    mode="generate-default-property-getter-implementation-aux">
    <xsl:param name="interface" select="/.." />
    <xsl:variable name="java-prop-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="@name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:value-of
      select="concat('    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;',
                     '    public ')" />
    <xsl:choose>
      <xsl:when test="string(@multi-valued) != 'true'">
        <xsl:choose>
          <xsl:when test="adm:default-behavior/adm:defined">
            <!--
              The method is guaranteed to return a value since there is a
              well-defined default value.
            -->
            <xsl:call-template name="get-property-java-primitive-type" />
          </xsl:when>
          <xsl:when
            test="$interface = 'server' and @mandatory = 'true'">
            <!--
              The method is guaranteed to return a value in the server interface, but
              not necessarily in the client, since the mandatory property might not
              have been created yet.
            -->
            <xsl:call-template name="get-property-java-primitive-type" />
          </xsl:when>
          <xsl:otherwise>
            <xsl:call-template name="get-property-java-type" />
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="'SortedSet&lt;'" />
        <xsl:call-template name="get-property-java-type" />
        <xsl:value-of select="'&gt;'" />
      </xsl:otherwise>
    </xsl:choose>
    <xsl:choose>
      <xsl:when test="adm:syntax/adm:boolean">
        <xsl:value-of select="' is'" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="' get'" />
      </xsl:otherwise>
    </xsl:choose>
    <xsl:choose>
      <xsl:when test="$interface='server'">
        <xsl:value-of
          select="concat($java-prop-name, '() {&#xa;',
                                     '      return p', $java-prop-name , ';&#xa;' ,
                                     '    }&#xa;')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:choose>
          <xsl:when test="string(@multi-valued) != 'true'">
            <xsl:value-of
              select="concat($java-prop-name, '() {&#xa;',
                                     '      return impl.getPropertyValue',
                                     '(INSTANCE.get', $java-prop-name ,
                                     'PropertyDefinition());&#xa;' ,
                                     '    }&#xa;')" />
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of
              select="concat($java-prop-name, '() {&#xa;',
                                     '      return impl.getPropertyValues',
                                     '(INSTANCE.get', $java-prop-name ,
                                     'PropertyDefinition());&#xa;' ,
                                     '    }&#xa;')" />
          </xsl:otherwise>
        </xsl:choose>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--
    Generate the default property setter declarations.
  -->
  <xsl:template name="generate-default-property-setter-declaration">
    <xsl:apply-templates select="../.."
      mode="generate-default-property-setter-declaration-aux" />
  </xsl:template>
  <xsl:template match="adm:property"
    mode="generate-default-property-setter-declaration-aux">
    <xsl:if test="not(@monitoring='true')">
      <xsl:variable name="name" select="@name" />
      <xsl:variable name="java-property-name">
        <xsl:call-template name="name-to-java">
          <xsl:with-param name="value" select="$name" />
        </xsl:call-template>
      </xsl:variable>
      <xsl:value-of
        select="concat('  /**&#xa;',
                     '   * Sets the &quot;', $name, '&quot; property.&#xa;')" />
      <xsl:if test="adm:synopsis">
        <xsl:value-of select="'   * &lt;p&gt;&#xa;'" />
        <xsl:call-template name="add-java-comment">
          <xsl:with-param name="indent-text" select="'   *'" />
          <xsl:with-param name="content" select="adm:synopsis" />
        </xsl:call-template>
      </xsl:if>
      <xsl:if test="adm:description">
        <xsl:value-of select="'   * &lt;p&gt;&#xa;'" />
        <xsl:call-template name="add-java-comment">
          <xsl:with-param name="indent-text" select="'   *'" />
          <xsl:with-param name="content" select="adm:description" />
        </xsl:call-template>
      </xsl:if>
      <xsl:if test="@read-only='true'">
        <xsl:value-of select="'   * &lt;p&gt;&#xa;'" />
        <xsl:value-of
          select="concat(
                     '   * This property is read-only and can only be modified during&#xa;',
                     '   * creation of a ', $this-ufn, '.&#xa;')" />
      </xsl:if>
      <xsl:choose>
        <xsl:when test="not(@multi-valued='true')">
          <xsl:value-of
            select="concat('   *&#xa;',
                     '   * @param value The value of the &quot;', $name, '&quot; property.&#xa;',
                     '   * @throws IllegalPropertyValueException&#xa;',
                     '   *           If the new value is invalid.&#xa;')" />
          <xsl:if test="@read-only='true'">
            <xsl:value-of
              select="concat(
                     '   * @throws PropertyIsReadOnlyException&#xa;',
                     '   *           If this ', $this-ufn, ' is not being initialized.&#xa;')" />
          </xsl:if>
          <xsl:value-of
            select="concat(
                     '   */&#xa;',
                     '  void set', $java-property-name, '(')" />
          <xsl:choose>
            <xsl:when test="@mandatory = 'true'">
              <xsl:call-template
                name="get-property-java-primitive-type" />
            </xsl:when>
            <xsl:otherwise>
              <xsl:call-template name="get-property-java-type" />
            </xsl:otherwise>
          </xsl:choose>
          <xsl:value-of select="' value'" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of
            select="concat('   *&#xa;',
                     '   * @param values The values of the &quot;', $name, '&quot; property.&#xa;',
                     '   * @throws IllegalPropertyValueException&#xa;',
                     '   *           If one or more of the new values are invalid.&#xa;')" />
          <xsl:if test="@read-only='true'">
            <xsl:value-of
              select="concat(
                     '   * @throws PropertyIsReadOnlyException&#xa;',
                     '   *           If this ', $this-ufn, ' is not being initialized.&#xa;')" />
          </xsl:if>
          <xsl:value-of
            select="concat(
                     '   */&#xa;',
                     '  void set', $java-property-name, '(Collection&lt;')" />
          <xsl:call-template name="get-property-java-type" />
          <xsl:value-of select="'&gt; values'" />
        </xsl:otherwise>
      </xsl:choose>
      <xsl:value-of select="') throws IllegalPropertyValueException'" />
      <xsl:if test="@read-only='true'">
        <xsl:value-of select="', PropertyIsReadOnlyException'" />
      </xsl:if>
      <xsl:value-of select="';&#xa;'" />
    </xsl:if>
  </xsl:template>
  <!--
    Generate the default property setter implementation.
  -->
  <xsl:template
    name="generate-default-property-setter-implementation">
    <xsl:apply-templates select="../.."
      mode="generate-default-property-setter-implementation-aux" />
  </xsl:template>
  <xsl:template match="adm:property"
    mode="generate-default-property-setter-implementation-aux">
    <xsl:if test="not(@monitoring='true')">
      <xsl:variable name="java-prop-name">
        <xsl:call-template name="name-to-java">
          <xsl:with-param name="value" select="@name" />
        </xsl:call-template>
      </xsl:variable>
      <xsl:value-of
        select="concat('    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;',
                     '    public void set',
                     $java-prop-name ,
                     '(')" />
      <xsl:choose>
        <xsl:when test="not(@multi-valued='true')">
          <xsl:choose>
            <xsl:when test="@mandatory='true'">
              <xsl:call-template
                name="get-property-java-primitive-type" />
            </xsl:when>
            <xsl:otherwise>
              <xsl:call-template name="get-property-java-type" />
            </xsl:otherwise>
          </xsl:choose>
          <xsl:value-of select="' value)'" />
          <xsl:if test="@read-only='true'">
            <xsl:value-of
              select="' throws PropertyIsReadOnlyException'" />
          </xsl:if>
          <xsl:value-of
            select="concat(' {&#xa;' ,
                     '      impl.setPropertyValue(INSTANCE.get',
                     $java-prop-name ,
                     'PropertyDefinition(), value);&#xa;',
                     '    }&#xa;')" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="'Collection&lt;'" />
          <xsl:call-template name="get-property-java-type" />
          <xsl:value-of
            select="concat('&gt; values) {&#xa;' ,
                     '      impl.setPropertyValues(INSTANCE.get',
                     $java-prop-name ,
                     'PropertyDefinition(), values);&#xa;',
                     '    }&#xa;')" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
