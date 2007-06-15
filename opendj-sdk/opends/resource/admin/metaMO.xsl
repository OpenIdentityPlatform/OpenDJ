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
  xmlns:admpp="http://www.opends.org/admin-preprocessor"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:exsl="http://exslt.org/common">
  <xsl:import href="java-utilities.xsl" />
  <xsl:import href="preprocessor.xsl" />
  <xsl:import href="property-types.xsl" />
  <xsl:output method="text" encoding="us-ascii" />
  <!--
    Template for generating the class declaration.
  -->
  <xsl:template name="generate-meta-class-declaration">
    <xsl:value-of select="'/**&#xa;'" />
    <xsl:call-template name="add-java-comment">
      <xsl:with-param name="indent-text" select="' *'" />
      <xsl:with-param name="content"
        select="concat('An interface for querying the ', $this-ufn,
                       ' managed object definition meta information.')" />
    </xsl:call-template>
    <xsl:value-of select="' * &lt;p&gt;&#xa;'" />
    <xsl:call-template name="add-java-comment">
      <xsl:with-param name="indent-text" select="' *'" />
      <xsl:with-param name="content" select="$this/adm:synopsis" />
    </xsl:call-template>
    <xsl:value-of select="' */&#xa;'" />
    <xsl:value-of
      select="concat('public final class ',
                     $this-java-class,
                     'CfgDefn extends ')" />
    <xsl:choose>
      <xsl:when test="$this-is-abstract">
        <xsl:value-of
          select="concat('AbstractManagedObjectDefinition&lt;',
                         $this-java-class, 'CfgClient, ',
                         $this-java-class, 'Cfg&gt; {&#xa;')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of
          select="concat('ManagedObjectDefinition&lt;',
                         $this-java-class, 'CfgClient, ',
                         $this-java-class, 'Cfg&gt; {&#xa;')" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--
    Template for generating the meta class body.
  -->
  <xsl:template name="generate-meta-class-body">
    <!--
      Singleton configuration definition instance.
    -->
    <xsl:value-of
      select="concat('  // The singleton configuration definition instance.&#xa;',
                     '  private static final ',
                     $this-java-class ,
                     'CfgDefn INSTANCE = new ', $this-java-class, 'CfgDefn();&#xa;')" />
    <!--
      Generate enumerations defined by this managed object..
    -->
    <xsl:for-each
      select="$this-local-properties[adm:syntax/adm:enumeration]">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-enumeration" />
    </xsl:for-each>
    <!--
      Define application tags if this is the root configuration.
    -->
    <xsl:if test="$this-is-root">
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:value-of select="'  // Define managed object tags.&#xa;'" />
      <xsl:value-of select="'  static {&#xa;'" />
      <xsl:for-each select="$this/adm:tag-definition">
        <xsl:sort select="@name" />
        <xsl:value-of
          select="concat('    Tag.define(&quot;', @name, '&quot;);&#xa;')" />
      </xsl:for-each>
      <xsl:value-of select="'  }&#xa;'" />
    </xsl:if>
    <!--
      Generate declarations for properties defined or
      overridden by this managed object.
    -->
    <xsl:for-each select="$this-local-properties">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-property-declaration" />
    </xsl:for-each>
    <!--
      Generate declarations for relations.
    -->
    <xsl:for-each select="$this-local-relations">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-relation-declaration" />
    </xsl:for-each>
    <!--
      Generate constructors for properties defined or
      overridden by this managed object.
    -->
    <xsl:for-each select="$this-local-properties">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-property-constructor" />
    </xsl:for-each>
    <!--
      Generate constructors for relations.
    -->
    <xsl:for-each select="$this-local-relations">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-relation-constructor" />
    </xsl:for-each>
    <!--
      Register any tags associated with this managed object definition.
    -->
    <xsl:if test="$this/adm:tag">
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:value-of
        select="'  // Register the tags associated with this managed object definition.&#xa;'" />
      <xsl:value-of select="'  static {&#xa;'" />
      <xsl:for-each select="$this/adm:tag">
        <xsl:sort select="@name" />
        <xsl:value-of
          select="concat('    INSTANCE.registerTag(Tag.valueOf(&quot;', @name, '&quot;));&#xa;')" />
      </xsl:for-each>
      <xsl:value-of select="'  }&#xa;'" />
    </xsl:if>
    <!--
      Configuration definition singleton getter.
    -->
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of select="'  /**&#xa;'" />
    <xsl:call-template name="add-java-comment">
      <xsl:with-param name="indent-text" select="'   *'" />
      <xsl:with-param name="content"
        select="concat('Get the ', $this-ufn,' configuration definition singleton.')" />
    </xsl:call-template>
    <xsl:value-of select="'   *&#xa;'" />
    <xsl:call-template name="add-java-comment">
      <xsl:with-param name="indent-text" select="'   *'" />
      <xsl:with-param name="indent-text2" select="'   *        '" />
      <xsl:with-param name="content"
        select="concat('@return Returns the ', $this-ufn,
                     ' configuration definition singleton.')" />
    </xsl:call-template>
    <xsl:value-of select="'   */&#xa;'" />
    <xsl:value-of
      select="concat('  public static ',
                     $this-java-class ,
                     'CfgDefn getInstance() {&#xa;',
                     '    return INSTANCE;&#xa;',
                     '  }&#xa;')" />
    <!--
      Private constructor.
    -->
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('  /**&#xa;',
                     '   * Private constructor.&#xa;',
                     '   */&#xa;',
                     '  private ',
                     $this-java-class ,
                     'CfgDefn() {&#xa;')" />
    <xsl:choose>
      <xsl:when test="boolean($this/@extends)">
        <xsl:value-of
          select="concat('    super(&quot;',
                         $this/@name,
                         '&quot;, ',
                         $parent-java-class,
                         'CfgDefn.getInstance());&#xa;')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of
          select="concat('    super(&quot;',
                         $this/@name,
                         '&quot;, null);&#xa;')" />
      </xsl:otherwise>
    </xsl:choose>
    <xsl:value-of select="concat('  }&#xa;')" />
    <!--
      Create configuration view factory methods for non-abstract definitions
    -->
    <xsl:if test="not($this-is-abstract)">
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <!--
        Generate configuration client factory method.
      -->
      <xsl:value-of
        select="concat('  /**&#xa;',
                       '   * {@inheritDoc}&#xa;',
                       '   */&#xa;',
                       '  public ',
                       $this-java-class,
                       'CfgClient createClientConfiguration(&#xa;',
                       '      ManagedObject&lt;? extends ',
                       $this-java-class,
                       'CfgClient&gt; impl) {&#xa;',
                       '    return new ',
                       $this-java-class ,
                       'CfgClientImpl(impl);&#xa;',
                       '  }&#xa;')" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <!--
        Generate configuration server factory method.
      -->
      <xsl:value-of
        select="concat('  /**&#xa;',
                       '   * {@inheritDoc}&#xa;',
                       '   */&#xa;',
                       '  public ',
                       $this-java-class,
                       'Cfg createServerConfiguration(&#xa;',
                       '      ServerManagedObject&lt;? extends ',$this-java-class,'Cfg&gt; impl) {&#xa;',
                       '    return new ',
                       $this-java-class ,
                       'CfgServerImpl(impl);&#xa;',
                       '  }&#xa;')" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <!--
        Generate configuration server class getter..
      -->
      <xsl:value-of
        select="concat('  /**&#xa;',
                       '   * {@inheritDoc}&#xa;',
                       '   */&#xa;',
                       '  public Class&lt;',
                       $this-java-class,
                       'Cfg&gt; getServerConfigurationClass() {&#xa;',
                       '    return ',
                       $this-java-class ,
                       'Cfg.class;&#xa;',
                       '  }&#xa;')" />
    </xsl:if>
    <!--
      Generate property definition getters for all properties.
    -->
    <xsl:for-each select="$this-all-properties">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-property-definition-getter" />
    </xsl:for-each>
    <!--
      Generate relation definition getters for all relations.
    -->
    <xsl:for-each select="$this-all-relations">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-relation-definition-getter" />
    </xsl:for-each>
    <!--
      Managed object class implementations.
    -->
    <xsl:if test="not($this-is-abstract)">
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-client-impl-class" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-server-impl-class" />
    </xsl:if>
  </xsl:template>
  <!--
    Generate managed object client class implementation.
  -->
  <xsl:template name="generate-client-impl-class">
    <!--
      Declaration.
    -->
    <xsl:value-of
      select="concat('  /**&#xa;',
                     '   * Managed object client implementation.&#xa;',
                     '   */&#xa;',
                     '  private static class ',
                     $this-java-class ,
                     'CfgClientImpl implements&#xa;    ',
                     $this-java-class ,
                     'CfgClient {&#xa;')" />
    <xsl:text>&#xa;</xsl:text>
    <!--
      Private instance.
    -->
    <xsl:value-of
      select="concat('    // Private implementation.&#xa;',
                     '    private ManagedObject&lt;? extends ',
                     $this-java-class,
                     'CfgClient&gt; impl;&#xa;')" />
    <!--
      Private constructor.
    -->
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('    // Private constructor.&#xa;',
                     '    private ',
                     $this-java-class,
                     'CfgClientImpl(&#xa;',
                     '        ManagedObject&lt;? extends ',
                     $this-java-class,
                     'CfgClient&gt; impl) {&#xa;',
                     '      this.impl = impl;&#xa;',
                     '    }&#xa;')" />
    <!--
      Getters/Setters for all properties.
    -->
    <xsl:for-each select="$this-all-properties">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-property-getter">
        <xsl:with-param name="interface" select="'client'" />
      </xsl:call-template>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-property-setter" />
    </xsl:for-each>
    <!--
      Relation methods.
    -->
    <xsl:for-each select="$this-all-relations">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-client-relation-methods" />
    </xsl:for-each>
    <!--
      Managed object definition getter.
    -->
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;',
                     '    public ManagedObjectDefinition&lt;? extends ', $this-java-class,'CfgClient, ? extends ', $this-java-class,'Cfg&gt; definition() {&#xa;',
                     '      return INSTANCE;&#xa;',
                     '    }&#xa;')" />
    <!--
      Property provider view.
    -->
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;',
                     '    public PropertyProvider properties() {&#xa;',
                     '      return impl;&#xa;',
                     '    }&#xa;')" />
    <!--
      Commit method.
    -->
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;',
                     '    public void commit() throws ManagedObjectAlreadyExistsException,&#xa;',
                     '        MissingMandatoryPropertiesException, ConcurrentModificationException,&#xa;',
                     '        OperationRejectedException, AuthorizationException,&#xa;',
                     '        CommunicationException {&#xa;',
                     '      impl.commit();&#xa;',
                     '    }&#xa;')" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of select="'  }&#xa;'" />
  </xsl:template>
  <!--
    Generate managed object server class implementation.
  -->
  <xsl:template name="generate-server-impl-class">
    <!--
      Declaration.
    -->
    <xsl:value-of
      select="concat('  /**&#xa;',
                     '   * Managed object server implementation.&#xa;',
                     '   */&#xa;',
                     '  private static class ',
                     $this-java-class ,
                     'CfgServerImpl implements&#xa;    ',
                     $this-java-class ,
                     'Cfg {&#xa;')" />
    <xsl:text>&#xa;</xsl:text>
    <!--
      Private instance.
    -->
    <xsl:value-of
      select="concat('    // Private implementation.&#xa;',
                     '    private ServerManagedObject&lt;? extends ', $this-java-class, 'Cfg&gt; impl;&#xa;')" />
    <!--
      Private constructor.
    -->
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('    // Private constructor.&#xa;',
                     '    private ',
                     $this-java-class,
                     'CfgServerImpl(ServerManagedObject&lt;? extends ', $this-java-class, 'Cfg&gt; impl) {&#xa;',
                     '      this.impl = impl;&#xa;',
                     '    }&#xa;')" />
    <!--
      Generate all the change listener methods - one for each managed
      object in the hierarchy.
    -->
    <xsl:if test="not($this-is-root)">
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-change-listener" />
    </xsl:if>
    <!--
      Getters/Setters for all properties.
    -->
    <xsl:for-each select="$this-all-properties">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-property-getter">
        <xsl:with-param name="interface" select="'server'" />
      </xsl:call-template>
    </xsl:for-each>
    <!--
      Relation methods.
    -->
    <xsl:for-each select="$this-all-relations">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-server-relation-methods" />
    </xsl:for-each>
    <!--
      Configuration definition getter.
    -->
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;',
                     '    public ManagedObjectDefinition&lt;? extends ', $this-java-class,'CfgClient, ? extends ', $this-java-class,'Cfg&gt; definition() {&#xa;',
                     '      return INSTANCE;&#xa;',
                     '    }&#xa;')" />
    <!--
      Property provider view.
    -->
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;',
                     '    public PropertyProvider properties() {&#xa;',
                     '      return impl;&#xa;',
                     '    }&#xa;')" />
    <!--
      Configuration entry DN getter.
    -->
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;',
                     '    public DN dn() {&#xa;',
                     '      return impl.getDN();&#xa;',
                     '    }&#xa;')" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of select="'  }&#xa;'" />
  </xsl:template>
  <!--
    Generate a property definition constructor.
  -->
  <xsl:template name="generate-property-declaration">
    <xsl:variable name="java-prop-name">
      <xsl:call-template name="name-to-java-constant">
        <xsl:with-param name="value" select="@name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="type">
      <xsl:call-template name="get-property-definition-type" />
    </xsl:variable>
    <xsl:variable name="generic-type">
      <xsl:call-template name="get-property-definition-generic-type" />
    </xsl:variable>
    <xsl:variable name="pdtype">
      <xsl:choose>
        <xsl:when test="string-length($generic-type) != 0">
          <xsl:value-of
            select="concat($type, '&lt;', $generic-type, '&gt;')" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$type" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:value-of
      select="concat('  // The &quot;',
                     @name,
                     '&quot; property definition.&#xa;',
                     '  private static final ', $pdtype, ' PD_', $java-prop-name, ';&#xa;')" />
  </xsl:template>
  <!--
    Generate a property definition constructor.
  -->
  <xsl:template name="generate-property-constructor">
    <xsl:variable name="java-prop-name">
      <xsl:call-template name="name-to-java-constant">
        <xsl:with-param name="value" select="@name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="type">
      <xsl:call-template name="get-property-definition-type" />
    </xsl:variable>
    <xsl:variable name="generic-type">
      <xsl:call-template name="get-property-definition-generic-type" />
    </xsl:variable>
    <xsl:variable name="value-type">
      <xsl:call-template name="get-property-java-type" />
    </xsl:variable>
    <xsl:value-of
      select="concat('  // Build the &quot;', @name, '&quot; property definition.&#xa;',
                                 '  static {&#xa;')" />
    <xsl:choose>
      <xsl:when test="string-length($generic-type) != 0">
        <xsl:value-of
          select="concat('      ', $type, '.Builder&lt;', $generic-type, '&gt; builder = ', $type, '.createBuilder(INSTANCE, &quot;',@name, '&quot;);&#xa;')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of
          select="concat('      ', $type, '.Builder builder = ', $type, '.createBuilder(INSTANCE, &quot;',@name, '&quot;);&#xa;')" />
      </xsl:otherwise>
    </xsl:choose>
    <xsl:if test="@multi-valued='true'">
      <xsl:value-of
        select="'      builder.setOption(PropertyOption.MULTI_VALUED);&#xa;'" />
    </xsl:if>
    <xsl:if test="@read-only='true'">
      <xsl:value-of
        select="'      builder.setOption(PropertyOption.READ_ONLY);&#xa;'" />
    </xsl:if>
    <xsl:if test="@monitoring='true'">
      <xsl:value-of
        select="'      builder.setOption(PropertyOption.MONITORING);&#xa;'" />
    </xsl:if>
    <xsl:if test="@mandatory='true'">
      <xsl:value-of
        select="'      builder.setOption(PropertyOption.MANDATORY);&#xa;'" />
    </xsl:if>
    <xsl:if test="@hidden='true'">
      <xsl:value-of
        select="'      builder.setOption(PropertyOption.HIDDEN);&#xa;'" />
    </xsl:if>
    <xsl:variable name="action-type">
      <xsl:choose>
        <xsl:when test="adm:requires-admin-action">
          <xsl:call-template name="name-to-java-constant">
            <xsl:with-param name="value"
              select="local-name(adm:requires-admin-action/*)" />
          </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="'NONE'" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:value-of
      select="concat('      builder.setAdministratorAction(new AdministratorAction(AdministratorAction.Type.', $action-type, ', INSTANCE, &quot;', @name, '&quot;));&#xa;')" />
    <xsl:if
      test="not(@mandatory='true') and not(adm:default-behavior)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('No default behavior defined for non-mandatory property &quot;', @name,
                         '&quot;.')" />
      </xsl:message>
    </xsl:if>
    <xsl:choose>
      <xsl:when
        test="not(adm:default-behavior) or adm:default-behavior/adm:undefined">
        <xsl:value-of
          select="concat('      builder.setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider&lt;', $value-type,'&gt;());&#xa;')" />
      </xsl:when>
      <xsl:when test="adm:default-behavior/adm:alias">
        <xsl:value-of
          select="concat('      builder.setDefaultBehaviorProvider(new AliasDefaultBehaviorProvider&lt;', $value-type,'&gt;(INSTANCE, &quot;', @name, '&quot;));&#xa;')" />
      </xsl:when>
      <xsl:when test="adm:default-behavior/adm:defined">
        <xsl:value-of
          select="concat('      DefaultBehaviorProvider&lt;', $value-type,'&gt; provider = ',
                             'new DefinedDefaultBehaviorProvider&lt;', $value-type,'&gt;(')" />
        <xsl:for-each
          select="adm:default-behavior/adm:defined/adm:value">
          <xsl:value-of
            select="concat('&quot;', normalize-space(), '&quot;')" />
          <xsl:if test="position() != last()">
            <xsl:value-of select="', '" />
          </xsl:if>
        </xsl:for-each>
        <xsl:value-of select="');&#xa;'" />
        <xsl:value-of
          select="'      builder.setDefaultBehaviorProvider(provider);&#xa;'" />
      </xsl:when>
      <xsl:when
        test="adm:default-behavior/adm:inherited/adm:relative">
        <xsl:value-of
          select="concat('      DefaultBehaviorProvider&lt;', $value-type,'&gt; provider = ',
                             'new RelativeInheritedDefaultBehaviorProvider&lt;', $value-type,'&gt;(')" />
        <xsl:variable name="managed-object-name">
          <xsl:call-template name="name-to-java">
            <xsl:with-param name="value"
              select="adm:default-behavior/adm:inherited/adm:relative/@managed-object-name" />
          </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="property-name"
          select="adm:default-behavior/adm:inherited/adm:relative/@property-name" />
        <xsl:variable name="offset"
          select="adm:default-behavior/adm:inherited/adm:relative/@offset" />
        <xsl:value-of
          select="concat($managed-object-name, 'CfgDefn.getInstance(), &quot;', $property-name, '&quot;, ', $offset, ');&#xa;')" />
        <xsl:value-of
          select="'      builder.setDefaultBehaviorProvider(provider);&#xa;'" />
      </xsl:when>
      <xsl:when
        test="adm:default-behavior/adm:inherited/adm:absolute">
        <xsl:value-of
          select="concat('      DefaultBehaviorProvider&lt;', $value-type,'&gt; provider = ',
                             'new AbsoluteInheritedDefaultBehaviorProvider&lt;', $value-type,'&gt;(')" />
        <xsl:variable name="property-name"
          select="adm:default-behavior/adm:inherited/adm:absolute/@property-name" />
        <xsl:variable name="path"
          select="adm:default-behavior/adm:inherited/adm:absolute/@path" />
        <xsl:value-of
          select="concat('ManagedObjectPath.valueOf(&quot;', $path, '&quot;), &quot;', $property-name, '&quot;);&#xa;')" />
        <xsl:value-of
          select="'      builder.setDefaultBehaviorProvider(provider);&#xa;'" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Unrecognized default behavior type for property &quot;', @name,
                         '&quot;.')" />
        </xsl:message>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:call-template name="get-property-definition-ctor" />
    <xsl:value-of
      select="concat('      PD_', $java-prop-name, ' = builder.getInstance();&#xa;')" />
    <xsl:value-of
      select="concat('      INSTANCE.registerPropertyDefinition(PD_', $java-prop-name, ');&#xa;')" />
    <xsl:value-of select="'  }&#xa;'" />
  </xsl:template>
  <!--
    Generate a relation definition declaration.
  -->
  <xsl:template name="generate-relation-declaration">
    <xsl:variable name="relation-name">
      <xsl:choose>
        <xsl:when test="adm:one-to-many">
          <xsl:value-of select="adm:one-to-many/@plural-name" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@name" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="java-relation-name">
      <xsl:call-template name="name-to-java-constant">
        <xsl:with-param name="value" select="$relation-name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="java-managed-object-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="@managed-object-name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:value-of
      select="concat('  // The &quot;',
                     $relation-name,
                     '&quot; relation definition.&#xa;',
                     '  private static final ')" />
    <xsl:choose>
      <xsl:when test="adm:one-to-one">
        <xsl:text>SingletonRelationDefinition&lt;</xsl:text>
      </xsl:when>
      <xsl:when test="adm:one-to-zero-or-one">
        <xsl:text>OptionalRelationDefinition&lt;</xsl:text>
      </xsl:when>
      <xsl:when test="adm:one-to-many">
        <xsl:text>InstantiableRelationDefinition&lt;</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Unknown relation type &quot;', local-name(*), '&quot; in relation &quot;', @name, '&quot;.')" />
        </xsl:message>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:value-of
      select="concat($java-managed-object-name, 'CfgClient, ', $java-managed-object-name, 'Cfg&gt; RD_', $java-relation-name, ';&#xa;')" />
  </xsl:template>
  <!--
    Generate a relation definition constructor.
  -->
  <xsl:template name="generate-relation-constructor">
    <xsl:variable name="relation-name">
      <xsl:choose>
        <xsl:when test="adm:one-to-many">
          <xsl:value-of select="adm:one-to-many/@plural-name" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@name" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="java-relation-name">
      <xsl:call-template name="name-to-java-constant">
        <xsl:with-param name="value" select="$relation-name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="java-managed-object-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="@managed-object-name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:value-of
      select="concat('  // Build the &quot;', $relation-name, '&quot; relation definition.&#xa;',
                     '  static {&#xa;',
                     '    RD_', $java-relation-name, ' = new ')" />
    <xsl:choose>
      <xsl:when test="adm:one-to-one">
        <xsl:text>SingletonRelationDefinition&lt;</xsl:text>
      </xsl:when>
      <xsl:when test="adm:one-to-zero-or-one">
        <xsl:text>OptionalRelationDefinition&lt;</xsl:text>
      </xsl:when>
      <xsl:when test="adm:one-to-many">
        <xsl:text>InstantiableRelationDefinition&lt;</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Unknown relation type &quot;', local-name(*), '&quot; in relation &quot;', @name, '&quot;.')" />
        </xsl:message>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:value-of
      select="concat($java-managed-object-name, 'CfgClient, ', $java-managed-object-name, 'Cfg&gt;(&#xa;',
                     '        INSTANCE, &quot;', @name, '&quot;, ')" />
    <xsl:if test="adm:one-to-many">
      <xsl:value-of
        select="concat('&quot;', adm:one-to-many/@plural-name, '&quot;, ')" />
    </xsl:if>
    <xsl:value-of
      select="concat($java-managed-object-name, 'CfgDefn.getInstance());&#xa;')" />
    <xsl:value-of
      select="concat('    INSTANCE.registerRelationDefinition(RD_', $java-relation-name,');&#xa;')" />
    <xsl:value-of select="'  }&#xa;'" />
  </xsl:template>
  <!--
    Generate a property definition getter for a locally defined
    or overriden property.
  -->
  <xsl:template name="generate-property-definition-getter">
    <xsl:variable name="java-prop-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="@name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="java-prop-name-constant">
      <xsl:call-template name="name-to-java-constant">
        <xsl:with-param name="value" select="@name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="type">
      <xsl:call-template name="get-property-definition-type" />
    </xsl:variable>
    <xsl:variable name="generic-type">
      <xsl:call-template name="get-property-definition-generic-type" />
    </xsl:variable>
    <xsl:variable name="pdtype">
      <xsl:choose>
        <xsl:when test="string-length($generic-type) != 0">
          <xsl:value-of
            select="concat($type, '&lt;', $generic-type, '&gt;')" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$type" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:value-of
      select="concat('  /**&#xa;',
                     '   * Get the &quot;',
                     @name,
                     '&quot; property definition.&#xa;')" />
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
      <xsl:when
        test="adm:profile[@name='preprocessor']/admpp:managed-object[@name=$this-name and @package=$this-package]">
        <xsl:value-of
          select="concat('   *&#xa;',
                     '   * @return Returns the &quot;',
                     @name,
                     '&quot; property definition.&#xa;',
                     '   */&#xa;',
                     '  public ',
                     $pdtype,
                     ' get',
                     $java-prop-name,
                     'PropertyDefinition() {&#xa;' ,
                     '    return PD_',
                     $java-prop-name-constant ,
                     ';&#xa;' ,
                     '  }&#xa;')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of
          select="concat('   *&#xa;',
                     '   * @return Returns the &quot;',
                     @name,
                     '&quot; property definition.&#xa;',
                     '   */&#xa;',
                     '  public ',
                     $pdtype,
                     ' get',
                     $java-prop-name,
                     'PropertyDefinition() {&#xa;' ,
                     '    return ',
                     $parent-java-class, 'CfgDefn.getInstance().get',
                     $java-prop-name,
                     'PropertyDefinition();&#xa;',
                     '  }&#xa;')" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--
    Generate a relation definition getter.
  -->
  <xsl:template name="generate-relation-definition-getter">
    <xsl:variable name="relation-name">
      <xsl:choose>
        <xsl:when test="adm:one-to-many">
          <xsl:value-of select="adm:one-to-many/@plural-name" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@name" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="java-relation-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="$relation-name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="java-relation-name-constant">
      <xsl:call-template name="name-to-java-constant">
        <xsl:with-param name="value" select="$relation-name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:value-of
      select="concat('  /**&#xa;',
                     '   * Get the &quot;',
                     $relation-name,
                     '&quot; relation definition.&#xa;',
                     '   *&#xa;',
                     '   * @return Returns the &quot;',
                     $relation-name,
                     '&quot; relation definition.&#xa;',
                     '   */&#xa;',
                     '  public ')" />
    <xsl:choose>
      <xsl:when test="adm:one-to-one">
        <xsl:text>SingletonRelationDefinition&lt;</xsl:text>
      </xsl:when>
      <xsl:when test="adm:one-to-zero-or-one">
        <xsl:text>OptionalRelationDefinition&lt;</xsl:text>
      </xsl:when>
      <xsl:when test="adm:one-to-many">
        <xsl:text>InstantiableRelationDefinition&lt;</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Unknown relation type &quot;', local-name(*), '&quot; in relation &quot;', @name, '&quot;.')" />
        </xsl:message>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:variable name="java-managed-object-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="@managed-object-name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:choose>
      <xsl:when
        test="adm:profile[@name='preprocessor']/admpp:managed-object[@name=$this-name and @package=$this-package]">
        <xsl:value-of
          select="concat($java-managed-object-name, 'CfgClient,',
                     $java-managed-object-name, 'Cfg&gt; get',
                     $java-relation-name,
                     'RelationDefinition() {&#xa;' ,
                     '    return RD_',
                     $java-relation-name-constant,
                     ';&#xa;' ,
                     '  }&#xa;')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of
          select="concat($java-managed-object-name, 'CfgClient,',
                     $java-managed-object-name, 'Cfg&gt; get',
                     $java-relation-name,
                     'RelationDefinition() {&#xa;' ,
                     '    return ',
                     $parent-java-class, 'CfgDefn.getInstance().get',
                     $java-relation-name,
                     'RelationDefinition();&#xa;',
                     '  }&#xa;')" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--
    Generate a property value getter.
  -->
  <xsl:template name="generate-property-getter">
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
  </xsl:template>
  <!--
    Generate a property value setter.
  -->
  <xsl:template name="generate-property-setter">
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
  <!--
    Generate client relation methods.
  -->
  <xsl:template name="generate-client-relation-methods">
    <xsl:variable name="name" select="@name" />
    <xsl:variable name="java-relation-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="$name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="java-class-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="@managed-object-name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="adm:one-to-one">
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public ', $java-class-name, 'CfgClient get', $java-relation-name, '()&#xa;',
                         '        throws DefinitionDecodingException, ManagedObjectDecodingException,&#xa;',
                         '        ManagedObjectNotFoundException, ConcurrentModificationException,&#xa;',
                         '        AuthorizationException, CommunicationException {&#xa;',
                         '      return impl.getChild(INSTANCE.get', $java-relation-name,'RelationDefinition()).getConfiguration();&#xa;',
                         '    }&#xa;')" />
      </xsl:when>
      <xsl:when test="adm:one-to-zero-or-one">
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public boolean has', $java-relation-name, '() throws ConcurrentModificationException,&#xa;',
                         '        AuthorizationException, CommunicationException {&#xa;',
                         '      return impl.hasChild(INSTANCE.get', $java-relation-name,'RelationDefinition());&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public ', $java-class-name, 'CfgClient get', $java-relation-name, '()&#xa;',
                         '        throws DefinitionDecodingException, ManagedObjectDecodingException,&#xa;',
                         '        ManagedObjectNotFoundException, ConcurrentModificationException,&#xa;',
                         '        AuthorizationException, CommunicationException {&#xa;',
                         '      return impl.getChild(INSTANCE.get', $java-relation-name,'RelationDefinition()).getConfiguration();&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public &lt;M extends ', $java-class-name, 'CfgClient&gt; M create', $java-relation-name, '(&#xa;',
                         '        ManagedObjectDefinition&lt;M, ?&gt; d, Collection&lt;DefaultBehaviorException&gt; exceptions) {&#xa;',
                         '      return impl.createChild(INSTANCE.get', $java-relation-name,'RelationDefinition(), d, exceptions).getConfiguration();&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public void remove', $java-relation-name, '()&#xa;',
                         '        throws ManagedObjectNotFoundException, ConcurrentModificationException,&#xa;',
                         '        OperationRejectedException, AuthorizationException, CommunicationException {&#xa;',
                         '      impl.removeChild(INSTANCE.get', $java-relation-name,'RelationDefinition());&#xa;',
                         '    }&#xa;')" />
      </xsl:when>
      <xsl:when test="adm:one-to-many">
        <xsl:variable name="plural-name"
          select="adm:one-to-many/@plural-name" />
        <xsl:variable name="java-relation-plural-name">
          <xsl:call-template name="name-to-java">
            <xsl:with-param name="value" select="$plural-name" />
          </xsl:call-template>
        </xsl:variable>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public String[] list', $java-relation-plural-name, '() throws ConcurrentModificationException,&#xa;',
                         '        AuthorizationException, CommunicationException {&#xa;',
                         '      return impl.listChildren(INSTANCE.get', $java-relation-plural-name,'RelationDefinition());&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public ', $java-class-name, 'CfgClient get', $java-relation-name, '(String name)&#xa;',
                         '        throws DefinitionDecodingException, ManagedObjectDecodingException,&#xa;',
                         '        ManagedObjectNotFoundException, ConcurrentModificationException,&#xa;',
                         '        AuthorizationException, CommunicationException {&#xa;',
                         '      return impl.getChild(INSTANCE.get', $java-relation-plural-name,'RelationDefinition(), name).getConfiguration();&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public &lt;M extends ', $java-class-name, 'CfgClient&gt; M create', $java-relation-name, '(&#xa;',
                         '        ManagedObjectDefinition&lt;M, ?&gt; d, String name, Collection&lt;DefaultBehaviorException&gt; exceptions) {&#xa;',
                         '      return impl.createChild(INSTANCE.get', $java-relation-plural-name,'RelationDefinition(), d, name, exceptions).getConfiguration();&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public void remove', $java-relation-name, '(String name)&#xa;',
                         '        throws ManagedObjectNotFoundException, ConcurrentModificationException,&#xa;',
                         '        OperationRejectedException, AuthorizationException, CommunicationException {&#xa;',
                         '      impl.removeChild(INSTANCE.get', $java-relation-plural-name,'RelationDefinition(), name);&#xa;',
                         '    }&#xa;')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Unknown relation type &quot;', local-name(*), '&quot; in relation &quot;', $name, '&quot;.')" />
        </xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--
    Generate server relation methods.
  -->
  <xsl:template name="generate-server-relation-methods">
    <xsl:variable name="name" select="@name" />
    <xsl:variable name="java-relation-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="$name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="java-class-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="@managed-object-name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="adm:one-to-one">
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public ', $java-class-name, 'Cfg get',
                         $java-relation-name, '() throws ConfigException {&#xa;',
                         '      return impl.getChild(INSTANCE.get', $java-relation-name, 'RelationDefinition()).getConfiguration();&#xa;',
                         '    }&#xa;')" />
      </xsl:when>
      <xsl:when test="adm:one-to-zero-or-one">
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public boolean has',
                         $java-relation-name, '() {&#xa;',
                         '      return impl.hasChild(INSTANCE.get', $java-relation-name, 'RelationDefinition());&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public ', $java-class-name, 'Cfg get',
                         $java-relation-name, '() throws ConfigException {&#xa;',
                         '      return impl.getChild(INSTANCE.get', $java-relation-name, 'RelationDefinition()).getConfiguration();&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public void add', $java-relation-name, 'AddListener(&#xa;',
                         '        ConfigurationAddListener&lt;', $java-class-name,'Cfg&gt; listener) throws ConfigException {&#xa;',
                         '      impl.registerAddListener(INSTANCE.get', $java-relation-name, 'RelationDefinition(), listener);&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public void remove', $java-relation-name, 'AddListener(&#xa;',
                         '        ConfigurationAddListener&lt;', $java-class-name,'Cfg&gt; listener) {&#xa;',
                         '      impl.deregisterAddListener(INSTANCE.get', $java-relation-name, 'RelationDefinition(), listener);&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public void add', $java-relation-name, 'DeleteListener(&#xa;',
                         '        ConfigurationDeleteListener&lt;', $java-class-name,'Cfg&gt; listener) throws ConfigException {&#xa;',
                         '      impl.registerDeleteListener(INSTANCE.get', $java-relation-name, 'RelationDefinition(), listener);&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public void remove', $java-relation-name, 'DeleteListener(&#xa;',
                         '        ConfigurationDeleteListener&lt;', $java-class-name,'Cfg&gt; listener) {&#xa;',
                         '      impl.deregisterDeleteListener(INSTANCE.get', $java-relation-name, 'RelationDefinition(), listener);&#xa;',
                         '    }&#xa;')" />
      </xsl:when>
      <xsl:when test="adm:one-to-many">
        <xsl:variable name="plural-name"
          select="adm:one-to-many/@plural-name" />
        <xsl:variable name="java-relation-plural-name">
          <xsl:call-template name="name-to-java">
            <xsl:with-param name="value" select="$plural-name" />
          </xsl:call-template>
        </xsl:variable>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public String[] list',
                         $java-relation-plural-name, '() {&#xa;',
                         '      return impl.listChildren(INSTANCE.get', $java-relation-plural-name,'RelationDefinition());&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public ', $java-class-name, 'Cfg get',
                         $java-relation-name, '(String name) throws ConfigException {&#xa;',
                         '      return impl.getChild(INSTANCE.get', $java-relation-plural-name, 'RelationDefinition(), name).getConfiguration();&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public void add', $java-relation-name, 'AddListener(&#xa;',
                         '        ConfigurationAddListener&lt;', $java-class-name,'Cfg&gt; listener) throws ConfigException {&#xa;',
                         '      impl.registerAddListener(INSTANCE.get', $java-relation-plural-name, 'RelationDefinition(), listener);&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public void remove', $java-relation-name, 'AddListener(&#xa;',
                         '        ConfigurationAddListener&lt;', $java-class-name,'Cfg&gt; listener) {&#xa;',
                         '      impl.deregisterAddListener(INSTANCE.get', $java-relation-plural-name, 'RelationDefinition(), listener);&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public void add', $java-relation-name, 'DeleteListener(&#xa;',
                         '        ConfigurationDeleteListener&lt;', $java-class-name,'Cfg&gt; listener) throws ConfigException {&#xa;',
                         '      impl.registerDeleteListener(INSTANCE.get', $java-relation-plural-name, 'RelationDefinition(), listener);&#xa;',
                         '    }&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('    /**&#xa;',
                         '     * {@inheritDoc}&#xa;',
                         '     */&#xa;',
                         '    public void remove', $java-relation-name, 'DeleteListener(&#xa;',
                         '        ConfigurationDeleteListener&lt;', $java-class-name,'Cfg&gt; listener) {&#xa;',
                         '      impl.deregisterDeleteListener(INSTANCE.get', $java-relation-plural-name, 'RelationDefinition(), listener);&#xa;',
                         '    }&#xa;')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Unknown relation type &quot;', local-name(*), '&quot; in relation &quot;', $name, '&quot;.')" />
        </xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--
    Generate change listener registration methods.
  -->
  <xsl:template name="generate-change-listener">
    <!--
      Process this managed object.
    -->
    <xsl:variable name="top-name"
      select="$this/adm:profile[@name='preprocessor']/admpp:parent-managed-object[last()]/@name" />
    <xsl:call-template name="generate-change-listener-help">
      <xsl:with-param name="top-name" select="$top-name" />
      <xsl:with-param name="name" select="$this-name" />
    </xsl:call-template>
    <!--
      Process parent hierarchy.
    -->
    <xsl:for-each
      select="$this/adm:profile[@name='preprocessor']/admpp:parent-managed-object">
      <xsl:call-template name="generate-change-listener-help">
        <xsl:with-param name="top-name" select="$top-name" />
        <xsl:with-param name="name" select="@name" />
      </xsl:call-template>
    </xsl:for-each>
  </xsl:template>
  <!--
    Generate a single set of change listener registration methods.
  -->
  <xsl:template name="generate-change-listener-help">
    <xsl:param name="top-name" select="/.." />
    <xsl:param name="name" select="/.." />
    <xsl:variable name="short-name">
      <xsl:choose>
        <xsl:when test="not($top-name) or $top-name = $name">
          <xsl:value-of select="''" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:variable name="top-length"
            select="string-length($top-name)" />
          <xsl:variable name="length" select="string-length($name)" />
          <xsl:variable name="diff" select="$length - $top-length" />
          <xsl:value-of select="substring($name, 1, $diff - 1)" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="java-class">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="$name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="short-java-class">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="$short-name" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:value-of
      select="concat('    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;')" />
    <xsl:value-of
      select="concat('    public void add', $short-java-class, 'ChangeListener(&#xa;',
                     '        ConfigurationChangeListener&lt;',$java-class,'Cfg&gt; listener) {&#xa;',
                     '      impl.registerChangeListener(listener);&#xa;',
                     '    }&#xa;')" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;')" />
    <xsl:value-of
      select="concat('    public void remove', $short-java-class, 'ChangeListener(&#xa;',
                     '        ConfigurationChangeListener&lt;',$java-class,'Cfg&gt; listener) {&#xa;',
                     '      impl.deregisterChangeListener(listener);&#xa;',
                     '    }&#xa;')" />
  </xsl:template>
  <!--
    Generate import statements for change-listener
  -->
  <xsl:template name="generate-change-listener-import-statements">
    <!--
      Process this managed object.
    -->
    <xsl:element name="import">
      <xsl:value-of
        select="concat($this-package, '.server.', $this-java-class, 'Cfg')" />
    </xsl:element>
    <!--
      Process parent hierarchy.
    -->
    <xsl:for-each
      select="$this/adm:profile[@name='preprocessor']/admpp:parent-managed-object">
      <xsl:variable name="java-class">
        <xsl:call-template name="name-to-java">
          <xsl:with-param name="value" select="@name" />
        </xsl:call-template>
      </xsl:variable>
      <xsl:element name="import">
        <xsl:value-of
          select="concat(@package, '.server.', $java-class, 'Cfg')" />
      </xsl:element>
    </xsl:for-each>
  </xsl:template>
  <!--
    Generate an enumeration for a locally defined enumerated property.
  -->
  <xsl:template name="generate-enumeration">
    <xsl:value-of
      select="concat('  /**&#xa;',
                     '   * Defines the set of permissable values for the &quot;', @name, '&quot; property.&#xa;')" />
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
    <xsl:value-of
      select="concat('   */&#xa;',
                     '  public static enum ')" />
    <xsl:call-template name="name-to-java">
      <xsl:with-param name="value" select="@name" />
    </xsl:call-template>
    <xsl:value-of select="' {&#xa;'" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:for-each select="adm:syntax/adm:enumeration/adm:value">
      <xsl:sort select="@name" />
      <xsl:value-of select="'    /**&#xa;'" />
      <xsl:call-template name="add-java-comment">
        <xsl:with-param name="indent-text" select="'     *'" />
        <xsl:with-param name="content" select="adm:synopsis" />
      </xsl:call-template>
      <xsl:value-of select="'     */&#xa;'" />
      <xsl:value-of select="'    '" />
      <xsl:call-template name="name-to-java-constant">
        <xsl:with-param name="value" select="@name" />
      </xsl:call-template>
      <xsl:value-of select="concat('(&quot;', @name, '&quot;)')" />
      <xsl:choose>
        <xsl:when test="position() != last()">
          <xsl:value-of select="',&#xa;'" />
          <xsl:text>&#xa;</xsl:text>
          <xsl:text>&#xa;</xsl:text>
          <xsl:text>&#xa;</xsl:text>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="';&#xa;'" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="'    // String representation of the value.&#xa;'" />
    <xsl:value-of select="'    private final String name;&#xa;'" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of select="'    // Private constructor.&#xa;'" />
    <xsl:value-of select="'    private '" />
    <xsl:call-template name="name-to-java">
      <xsl:with-param name="value" select="@name" />
    </xsl:call-template>
    <xsl:value-of
      select="concat('(String name) { this.name = name; }&#xa;',
                     '&#xa;',
                     '&#xa;',
                     '&#xa;',
                     '    /**&#xa;',
                     '     * {@inheritDoc}&#xa;',
                     '     */&#xa;',
                     '    public String toString() { return name; }&#xa;')" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of select="'  }&#xa;'" />
  </xsl:template>
  <!--
    Main document parsing template.
  -->
  <xsl:template match="/">
    <xsl:call-template name="copyright-notice" />
    <xsl:value-of
      select="concat('package ', $this-package, '.meta;&#xa;')" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:call-template name="generate-import-statements">
      <xsl:with-param name="imports">
        <xsl:if
          test="$this-local-properties[@multi-valued='true' or
                                       @read-only='true' or
                                       @monitoring='true' or
                                       @hidden='true' or
                                       @mandatory='true']">
          <import>org.opends.server.admin.PropertyOption</import>
        </xsl:if>
        <xsl:if test="$this-local-properties">
          <import>org.opends.server.admin.AdministratorAction</import>
        </xsl:if>
        <xsl:if test="$this/adm:tag-definition or $this/adm:tag">
          <import>org.opends.server.admin.Tag</import>
        </xsl:if>
        <xsl:if
          test="$this-local-properties[adm:default-behavior/adm:undefined or not(adm:default-behavior)]">
          <import>
            org.opends.server.admin.UndefinedDefaultBehaviorProvider
          </import>
        </xsl:if>
        <xsl:if
          test="$this-local-properties/adm:default-behavior/adm:alias">
          <import>
            org.opends.server.admin.AliasDefaultBehaviorProvider
          </import>
        </xsl:if>
        <xsl:if
          test="$this-local-properties/adm:default-behavior/adm:inherited/adm:absolute">
          <import>
            org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider
          </import>
          <import>org.opends.server.admin.ManagedObjectPath</import>
        </xsl:if>
        <xsl:if
          test="$this-local-properties/adm:default-behavior/adm:inherited/adm:relative">
          <import>
            org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider
          </import>
          <xsl:for-each
            select="$this-local-properties/adm:default-behavior/adm:inherited/adm:relative">
            <xsl:if test="@managed-object-package != $this-package">
              <xsl:variable name="java-class-name">
                <xsl:call-template name="name-to-java">
                  <xsl:with-param name="value"
                    select="@managed-object-name" />
                </xsl:call-template>
              </xsl:variable>
              <xsl:element name="import">
                <xsl:value-of
                  select="concat(@managed-object-package, '.meta.', $java-class-name, 'CfgDefn')" />
              </xsl:element>
            </xsl:if>
          </xsl:for-each>
        </xsl:if>
        <xsl:if
          test="$this-local-properties/adm:default-behavior/adm:defined">
          <import>
            org.opends.server.admin.DefinedDefaultBehaviorProvider
          </import>
          <import>
            org.opends.server.admin.DefaultBehaviorProvider
          </import>
        </xsl:if>
        <xsl:element name="import">
          <xsl:value-of
            select="concat($this-package, '.client.', $this-java-class, 'CfgClient')" />
        </xsl:element>
        <xsl:element name="import">
          <xsl:value-of
            select="concat($this-package, '.server.', $this-java-class, 'Cfg')" />
        </xsl:element>
        <xsl:for-each select="$this-inherited-properties">
          <xsl:call-template name="get-property-java-imports" />
        </xsl:for-each>
        <xsl:for-each select="$this-all-properties">
          <xsl:call-template
            name="get-property-definition-java-imports" />
        </xsl:for-each>
        <xsl:for-each select="$this-all-relations">
          <xsl:variable name="java-class-name">
            <xsl:call-template name="name-to-java">
              <xsl:with-param name="value"
                select="@managed-object-name" />
            </xsl:call-template>
          </xsl:variable>
          <xsl:element name="import">
            <xsl:value-of
              select="concat(@managed-object-package, '.client.', $java-class-name, 'CfgClient')" />
          </xsl:element>
          <xsl:element name="import">
            <xsl:value-of
              select="concat(@managed-object-package, '.server.', $java-class-name, 'Cfg')" />
          </xsl:element>
        </xsl:for-each>
        <xsl:if test="$this-all-relations/adm:one-to-many">
          <import>
            org.opends.server.admin.InstantiableRelationDefinition
          </import>
        </xsl:if>
        <xsl:if test="$this-all-relations/adm:one-to-zero-or-one">
          <import>
            org.opends.server.admin.OptionalRelationDefinition
          </import>
        </xsl:if>
        <xsl:if test="$this-all-relations/adm:one-to-one">
          <import>
            org.opends.server.admin.SingletonRelationDefinition
          </import>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="$this-is-abstract">
            <import>
              org.opends.server.admin.AbstractManagedObjectDefinition
            </import>
          </xsl:when>
          <xsl:otherwise>
            <import>
              org.opends.server.admin.ManagedObjectDefinition
            </import>
            <import>org.opends.server.admin.PropertyProvider</import>
            <import>
              org.opends.server.admin.client.MissingMandatoryPropertiesException
            </import>
            <import>
              org.opends.server.admin.ManagedObjectAlreadyExistsException
            </import>
            <import>
              org.opends.server.admin.client.AuthorizationException
            </import>
            <import>
              org.opends.server.admin.client.CommunicationException
            </import>
            <import>
              org.opends.server.admin.client.ConcurrentModificationException
            </import>
            <import>
              org.opends.server.admin.client.OperationRejectedException
            </import>
            <import>
              org.opends.server.admin.client.ManagedObject
            </import>
            <import>
              org.opends.server.admin.server.ServerManagedObject
            </import>
            <xsl:if test="not($this-is-root)">
              <import>
                org.opends.server.admin.server.ConfigurationChangeListener
              </import>
              <xsl:call-template
                name="generate-change-listener-import-statements" />
            </xsl:if>
            <import>org.opends.server.types.DN</import>
            <xsl:if test="$this-all-relations">
              <import>
                org.opends.server.admin.DefinitionDecodingException
              </import>
              <import>
                org.opends.server.admin.ManagedObjectNotFoundException
              </import>
              <import>
                org.opends.server.admin.client.ManagedObjectDecodingException
              </import>
            </xsl:if>
            <xsl:if test="$this-all-relations/adm:one-to-many">
              <import>java.util.Collection</import>
              <import>
                org.opends.server.admin.DefaultBehaviorException
              </import>
              <import>
                org.opends.server.admin.server.ConfigurationAddListener
              </import>
              <import>
                org.opends.server.admin.server.ConfigurationDeleteListener
              </import>
              <import>org.opends.server.config.ConfigException</import>
            </xsl:if>
            <xsl:if test="$this-all-relations/adm:one-to-zero-or-one">
              <import>java.util.Collection</import>
              <import>
                org.opends.server.admin.DefaultBehaviorException
              </import>
              <import>
                org.opends.server.admin.server.ConfigurationAddListener
              </import>
              <import>
                org.opends.server.admin.server.ConfigurationDeleteListener
              </import>
              <import>org.opends.server.config.ConfigException</import>
            </xsl:if>
            <xsl:if test="$this-all-relations/adm:one-to-one">
              <import>org.opends.server.config.ConfigException</import>
            </xsl:if>
            <xsl:if test="$this-all-properties[@multi-valued='true']">
              <import>java.util.SortedSet</import>
              <import>java.util.Collection</import>
            </xsl:if>
            <xsl:if test="$this-all-properties[@read-only='true']">
              <import>
                org.opends.server.admin.PropertyIsReadOnlyException
              </import>
            </xsl:if>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:if test="$this/@extends">
          <xsl:if test="$parent-package != $this-package">
            <xsl:element name="import">
              <xsl:value-of
                select="concat($parent-package, '.meta.', $parent-java-class, 'CfgDefn')" />
            </xsl:element>
          </xsl:if>
        </xsl:if>
      </xsl:with-param>
    </xsl:call-template>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:call-template name="generate-meta-class-declaration" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:call-template name="generate-meta-class-body" />
    <xsl:text>}&#xa;</xsl:text>
  </xsl:template>
</xsl:stylesheet>
