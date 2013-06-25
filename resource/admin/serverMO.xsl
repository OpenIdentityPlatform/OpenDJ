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
  !      Copyright 2007-2008 Sun Microsystems, Inc.
  ! -->
<xsl:stylesheet version="1.0" xmlns:adm="http://www.opends.org/admin"
  xmlns:admpp="http://www.opends.org/admin-preprocessor"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:import href="java-utilities.xsl" />
  <xsl:import href="preprocessor.xsl" />
  <xsl:import href="property-types.xsl" />
  <xsl:output method="text" encoding="us-ascii" />
  <!-- 
    Template for generating the interface declaration.
  -->
  <xsl:template name="generate-interface-declaration">
    <xsl:value-of select="'/**&#xa;'" />
    <xsl:call-template name="add-java-comment">
      <xsl:with-param name="indent-text" select="' *'" />
      <xsl:with-param name="content"
        select="concat('A server-side interface for querying ', $this-ufn,
                       ' settings.')" />
    </xsl:call-template>
    <xsl:value-of select="' * &lt;p&gt;&#xa;'" />
    <xsl:call-template name="add-java-comment">
      <xsl:with-param name="indent-text" select="' *'" />
      <xsl:with-param name="content" select="$this/adm:synopsis" />
    </xsl:call-template>
    <xsl:value-of select="' */&#xa;'" />
    <xsl:value-of
      select="concat('public interface ',
                                   $this-java-class ,
                                   'Cfg extends ')" />
    <xsl:choose>
      <xsl:when test="boolean($this/@extends)">
        <xsl:value-of select="concat($parent-java-class,'Cfg ')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="'Configuration '" />
      </xsl:otherwise>
    </xsl:choose>
    <xsl:text>{&#xa;</xsl:text>
  </xsl:template>
  <!-- 
    Template for generating the configuration class getter.
  -->
  <xsl:template name="generate-configuration-definition-getter">
    <xsl:value-of
      select="concat('  /**&#xa;',
                       '   * Gets the configuration class associated with this ', $this-ufn, '.&#xa;',
                       '   *&#xa;',
                       '   * @return Returns the configuration class associated with this ', $this-ufn, '.&#xa;',
                       '   */&#xa;')" />
    <xsl:value-of
      select="concat('  Class&lt;? extends ', $this-java-class,'Cfg&gt; configurationClass();&#xa;')" />
  </xsl:template>
  <!--
    Template for generating the change listener declaration.
  -->
  <xsl:template name="generate-change-listener-declaration">
    <xsl:value-of
      select="concat('  /**&#xa;',
                       '   * Register to be notified when this ', $this-ufn,' is changed.&#xa;',
                       '   *&#xa;',
                       '   * @param listener&#xa;',
                       '   *          The ', $this-ufn,' configuration change listener.&#xa;',
                       '   */&#xa;')" />
    <xsl:value-of
      select="concat('  void add', $this-short-java-class,
                       'ChangeListener(ConfigurationChangeListener&lt;',
                       $this-java-class,'Cfg&gt; listener);&#xa;')" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:value-of
      select="concat('  /**&#xa;',
                       '   * Deregister an existing ', $this-ufn,' configuration change listener.&#xa;',
                       '   *&#xa;',
                       '   * @param listener&#xa;',
                       '   *          The ', $this-ufn,' configuration change listener.&#xa;',
                       '   */&#xa;')" />
    <xsl:value-of
      select="concat('  void remove', $this-short-java-class,
                       'ChangeListener(ConfigurationChangeListener&lt;',
                       $this-java-class,'Cfg&gt; listener);&#xa;')" />
  </xsl:template>
  <!--
    Template for generating the relation getter declarations.
  -->
  <xsl:template name="generate-relation-declarations">
    <xsl:variable name="name" select="@name" />
    <xsl:variable name="ufn">
      <xsl:call-template name="name-to-ufn">
        <xsl:with-param name="value" select="$name" />
      </xsl:call-template>
    </xsl:variable>
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
          select="concat('  /**&#xa;',
                       '   * Gets the ', $ufn,'.&#xa;',
                       '   *&#xa;',
                       '   * @return Returns the ', $ufn,'.&#xa;',
                       '   * @throws ConfigException&#xa;',
                       '   *           If the ', $ufn,' could not be found or it could not&#xa;',
                       '   *           be successfully decoded.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  ', $java-class-name, 'Cfg get',
                       $java-relation-name, '() throws ConfigException;&#xa;')" />
      </xsl:when>
      <xsl:when test="adm:one-to-zero-or-one">
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Determines whether or not the ', $ufn,' exists.&#xa;',
                       '   *&#xa;',
                       '   * @return Returns &lt;true&gt; if the ', $ufn,' exists.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  boolean has',
                       $java-relation-name, '();&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Gets the ', $ufn,' if it is present.&#xa;',
                       '   *&#xa;',
                       '   * @return Returns the ', $ufn,' if it is present.&#xa;',
                       '   * @throws ConfigException&#xa;',
                       '   *           If the ', $ufn,' does not exist or it could not&#xa;',
                       '   *           be successfully decoded.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  ', $java-class-name, 'Cfg get',
                       $java-relation-name, '() throws ConfigException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Registers to be notified when the ', $ufn,' is added.&#xa;',
                       '   *&#xa;',
                       '   * @param listener&#xa;',
                       '   *          The ', $ufn,' configuration add listener.&#xa;',
                       '   * @throws ConfigException&#xa;',
                       '   *          If the add listener could not be registered.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  void add', $java-relation-name,
                       'AddListener(ConfigurationAddListener&lt;',
                       $java-class-name,'Cfg&gt; listener) throws ConfigException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Deregisters an existing ', $ufn,' configuration add listener.&#xa;',
                       '   *&#xa;',
                       '   * @param listener&#xa;',
                       '   *          The ', $ufn,' configuration add listener.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  void remove', $java-relation-name,
                       'AddListener(ConfigurationAddListener&lt;',
                       $java-class-name,'Cfg&gt; listener);&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Registers to be notified the ', $ufn,' is deleted.&#xa;',
                       '   *&#xa;',
                       '   * @param listener&#xa;',
                       '   *          The ', $ufn,' configuration delete listener.&#xa;',
                       '   * @throws ConfigException&#xa;',
                       '   *          If the delete listener could not be registered.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  void add', $java-relation-name,
                       'DeleteListener(ConfigurationDeleteListener&lt;',
                       $java-class-name,'Cfg&gt; listener) throws ConfigException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Deregisters an existing ', $ufn,' configuration delete listener.&#xa;',
                       '   *&#xa;',
                       '   * @param listener&#xa;',
                       '   *          The ', $ufn,' configuration delete listener.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  void remove', $java-relation-name,
                       'DeleteListener(ConfigurationDeleteListener&lt;',
                       $java-class-name,'Cfg&gt; listener);&#xa;')" />
      </xsl:when>
      <xsl:when test="adm:one-to-many">
        <xsl:variable name="plural-name"
          select="adm:one-to-many/@plural-name" />
        <xsl:variable name="ufpn">
          <xsl:call-template name="name-to-ufn">
            <xsl:with-param name="value" select="$plural-name" />
          </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="java-relation-plural-name">
          <xsl:call-template name="name-to-java">
            <xsl:with-param name="value" select="$plural-name" />
          </xsl:call-template>
        </xsl:variable>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Lists the ', $ufpn, '.&#xa;',
                       '   *&#xa;',
                       '   * @return Returns an array containing the names of the&#xa;',
                       '   *         ', $ufpn,'.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  String[] list', $java-relation-plural-name, '();&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Gets the named ', $ufn,'.&#xa;',
                       '   *&#xa;',
                       '   * @param name&#xa;',
                       '   *          The name of the ',$ufn,' to retrieve.&#xa;',
                       '   * @return Returns the named ', $ufn,'.&#xa;',
                       '   * @throws ConfigException&#xa;',
                       '   *           If the ', $ufn,' could not be found or it&#xa;',
                       '   *           could not be successfully decoded.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  ', $java-class-name, 'Cfg get',
                       $java-relation-name, '(String name) throws ConfigException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Registers to be notified when new ', $ufpn,' are added.&#xa;',
                       '   *&#xa;',
                       '   * @param listener&#xa;',
                       '   *          The ', $ufn,' configuration add listener.&#xa;',
                       '   * @throws ConfigException&#xa;',
                       '   *          If the add listener could not be registered.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  void add', $java-relation-name,
                       'AddListener(ConfigurationAddListener&lt;',
                       $java-class-name,'Cfg&gt; listener) throws ConfigException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Deregisters an existing ', $ufn,' configuration add listener.&#xa;',
                       '   *&#xa;',
                       '   * @param listener&#xa;',
                       '   *          The ', $ufn,' configuration add listener.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  void remove', $java-relation-name,
                       'AddListener(ConfigurationAddListener&lt;',
                       $java-class-name,'Cfg&gt; listener);&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Registers to be notified when existing ', $ufpn,' are deleted.&#xa;',
                       '   *&#xa;',
                       '   * @param listener&#xa;',
                       '   *          The ', $ufn,' configuration delete listener.&#xa;',
                       '   * @throws ConfigException&#xa;',
                       '   *          If the delete listener could not be registered.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  void add', $java-relation-name,
                       'DeleteListener(ConfigurationDeleteListener&lt;',
                       $java-class-name,'Cfg&gt; listener) throws ConfigException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Deregisters an existing ', $ufn,' configuration delete listener.&#xa;',
                       '   *&#xa;',
                       '   * @param listener&#xa;',
                       '   *          The ', $ufn,' configuration delete listener.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  void remove', $java-relation-name,
                       'DeleteListener(ConfigurationDeleteListener&lt;',
                       $java-class-name,'Cfg&gt; listener);&#xa;')" />
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
    Main document parsing template.
  -->
  <xsl:template match="/">
    <xsl:call-template name="copyright-notice" />
    <xsl:value-of
      select="concat('package ', $this-package, '.server;&#xa;')" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:call-template name="generate-import-statements">
      <xsl:with-param name="imports">
        <xsl:for-each select="$this-local-properties">
          <xsl:call-template name="get-property-java-imports" >
            <xsl:with-param name="interface" select="'server'" />
          </xsl:call-template>
        </xsl:for-each>
        <xsl:if test="$this-local-properties[@multi-valued='true']">
          <import>java.util.SortedSet</import>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="$this/@extends">
            <xsl:if test="$parent-package != $this-package">
              <xsl:element name="import">
                <xsl:value-of
                  select="concat($parent-package, '.server.', $parent-java-class, 'Cfg')" />
              </xsl:element>
            </xsl:if>
          </xsl:when>
          <xsl:otherwise>
            <import>org.opends.server.admin.Configuration</import>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:if test="not($this-is-root)">
          <import>
            org.opends.server.admin.server.ConfigurationChangeListener
          </import>
        </xsl:if>
        <xsl:if test="$this-local-relations">
          <import>org.opends.server.config.ConfigException</import>
        </xsl:if>
        <xsl:if
          test="$this-local-relations/adm:one-to-zero-or-one|$this-local-relations/adm:one-to-many">
          <import>
            org.opends.server.admin.server.ConfigurationAddListener
          </import>
          <import>
            org.opends.server.admin.server.ConfigurationDeleteListener
          </import>
        </xsl:if>
      </xsl:with-param>
    </xsl:call-template>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:call-template name="generate-interface-declaration" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:call-template name="generate-configuration-definition-getter" />
    <xsl:if test="not($this-is-root)">
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-change-listener-declaration" />
    </xsl:if>
    <xsl:for-each select="$this-local-properties">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-property-getter-declaration">
        <xsl:with-param name="interface" select="'server'" />
      </xsl:call-template>
    </xsl:for-each>
    <xsl:for-each select="$this-local-relations">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-relation-declarations" />
    </xsl:for-each>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>}&#xa;</xsl:text>
  </xsl:template>
</xsl:stylesheet>
