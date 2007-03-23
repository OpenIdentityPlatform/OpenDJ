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
        select="concat('A client-side interface for reading and modifying ',
                       $this-ufn, ' settings.')" />
    </xsl:call-template>
    <xsl:value-of select="' * &lt;p&gt;&#xa;'" />
    <xsl:call-template name="add-java-comment">
      <xsl:with-param name="indent-text" select="' *'" />
      <xsl:with-param name="content" select="$this/adm:synopsis" />
    </xsl:call-template>
    <xsl:value-of select="' */&#xa;'" />
    <xsl:value-of
      select="concat('public interface ',
                     $this-java-class,
                     'CfgClient extends ')" />
    <xsl:choose>
      <xsl:when test="boolean($this/@extends)">
        <xsl:value-of select="concat($parent-java-class,'CfgClient ')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="'ConfigurationClient '" />
      </xsl:otherwise>
    </xsl:choose>
    <xsl:text>{&#xa;</xsl:text>
  </xsl:template>
  <!-- 
    Template for generating the configuration definition getter.
  -->
  <xsl:template name="generate-configuration-definition-getter">
    <xsl:value-of
      select="concat('  /**&#xa;',
                       '   * Get the configuration definition associated with this ', $this-ufn, '.&#xa;',
                       '   *&#xa;',
                       '   * @return Returns the configuration definition associated with this ', $this-ufn, '.&#xa;',
                       '   */&#xa;')" />
    <xsl:value-of
      select="concat('  ManagedObjectDefinition&lt;? extends ', $this-java-class,'CfgClient, ? extends ', $this-java-class,'Cfg&gt; definition();&#xa;')" />
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
                       '   * @throws OperationsException&#xa;',
                       '   *           If the ', $ufn,' could not be read due to some&#xa;',
                       '   *           underlying communication problem.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  ', $java-class-name, 'CfgClient get',
                       $java-relation-name, '() throws OperationsException;&#xa;')" />
      </xsl:when>
      <xsl:when test="adm:one-to-zero-or-one">
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Determines whether or not the ', $ufn,' exists.&#xa;',
                       '   *&#xa;',
                       '   * @return Returns &lt;true&gt; if the ', $ufn,' exists.&#xa;',
                       '   * @throws OperationsException&#xa;',
                       '   *           If the determination could not be made&#xa;',
                       '   *           due to some underlying communication problem.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  boolean has',
                       $java-relation-name, '() throws OperationsException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Gets the ', $ufn,' if it is present.&#xa;',
                       '   *&#xa;',
                       '   * @return Returns the ', $ufn,' if it is present.&#xa;',
                       '   * @throws OperationsException&#xa;',
                       '   *           If the ', $ufn,' does not exist or could not be read&#xa;',
                       '   *           due to some underlying communication problem.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  ', $java-class-name, 'CfgClient get',
                       $java-relation-name, '() throws OperationsException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Creates the ', $ufn,' if it does not exist yet.&#xa;',
                       '   *&#xa;',
                       '   * @param &lt;C&gt;&#xa;',
                       '   *          The type of the ', $ufn,' being added.&#xa;',
                       '   * @param d&#xa;',
                       '   *          The definition of the ', $ufn,' to be created.&#xa;',
                       '   * @param p&#xa;',
                       '   *          A property provider which can be used to initialize&#xa;',
                       '   *          the property values of the new ', $ufn,'.&#xa;',
                       '   * @return Returns the ', $ufn,' instance representing the&#xa;',
                       '   *         ', $ufn,' that was created.&#xa;',
                       '   * @throws OperationsException&#xa;',
                       '   *           If the ', $ufn,' already exists or could not be created&#xa;',
                       '   *           due to some underlying communication problem.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  &lt;C extends ', $java-class-name,'CfgClient&gt; C create', $java-relation-name, '(&#xa;',
                           '      ManagedObjectDefinition&lt;C, ?&gt; d, PropertyProvider p) throws OperationsException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Removes the ', $ufn,' if it exists.&#xa;',
                       '   *&#xa;',
                       '   * @throws OperationsException&#xa;',
                       '   *           If the ', $ufn,' does not exist or could not be removed&#xa;',
                       '   *           due to some underlying communication problem.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  void remove',
                       $java-relation-name, '() throws OperationsException;&#xa;')" />
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
                       '   * Lists the ', $ufpn,'.&#xa;',
                       '   *&#xa;',
                       '   * @return Returns an array containing the names of the&#xa;',
                       '   *         ', $ufpn,'.&#xa;',
                       '   * @throws OperationsException&#xa;',
                       '   *           If the ', $ufpn,' could not be listed due to some&#xa;',
                       '   *           underlying communication problem.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  String[] list',
                       $java-relation-plural-name, '() throws OperationsException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Gets the named ', $ufn,'.&#xa;',
                       '   *&#xa;',
                       '   * @param name&#xa;',
                       '   *          The name of the ', $ufn,' to retrieve.&#xa;',
                       '   * @return Returns the named ', $ufn,'.&#xa;',
                       '   * @throws OperationsException&#xa;',
                       '   *           If the ', $ufn,' does not exist or could not be read&#xa;',
                       '   *           due to some underlying communication problem.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  ', $java-class-name, 'CfgClient get',
                       $java-relation-name, '(String name) throws OperationsException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Creates a new ', $ufn,'.&#xa;',
                       '   *&#xa;',
                       '   * @param &lt;C&gt;&#xa;',
                       '   *          The type of the ', $ufn,' being added.&#xa;',
                       '   * @param d&#xa;',
                       '   *          The definition of the ', $ufn,' to be created.&#xa;',
                       '   * @param name&#xa;',
                       '   *          The name of the new ', $ufn,'.&#xa;',
                       '   * @param p&#xa;',
                       '   *          A property provider which can be used to initialize&#xa;',
                       '   *          the property values of the new ', $ufn,'.&#xa;',
                       '   * @return Returns a new ', $ufn,' instance representing the&#xa;',
                       '   *         ', $ufn,' that was created.&#xa;',
                       '   * @throws OperationsException&#xa;',
                       '   *           If the ', $ufn,' already exists or could not be created&#xa;',
                       '   *           due to some underlying communication problem.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  &lt;C extends ', $java-class-name,'CfgClient&gt; C create', $java-relation-name, '(&#xa;',
                           '      ManagedObjectDefinition&lt;C, ?&gt; d, String name, PropertyProvider p) throws OperationsException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:value-of
          select="concat('  /**&#xa;',
                       '   * Removes the named ', $ufn,'.&#xa;',
                       '   *&#xa;',
                       '   * @param name&#xa;',
                       '   *          The name of the ', $ufn,' to remove.&#xa;',
                       '   * @throws OperationsException&#xa;',
                       '   *           If the ', $ufn,' does not exist or could not be removed&#xa;',
                       '   *           due to some underlying communication problem.&#xa;',
                       '   */&#xa;')" />
        <xsl:value-of
          select="concat('  void remove',
                       $java-relation-name, '(String name) throws OperationsException;&#xa;')" />
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
      select="concat('package ', $this-package, '.client;&#xa;')" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:call-template name="generate-import-statements">
      <xsl:with-param name="imports">
        <xsl:for-each select="$this-local-properties">
          <xsl:call-template name="get-property-java-imports" />
        </xsl:for-each>
        <xsl:if test="$this-local-properties[@multi-valued='true']">
          <import>java.util.Collection</import>
          <import>java.util.SortedSet</import>
        </xsl:if>
        <xsl:if test="$this-local-properties[not(@read-only='true')]">
          <import>
            org.opends.server.admin.IllegalPropertyValueException
          </import>
        </xsl:if>
        <xsl:if test="$this-local-relations">
          <import>org.opends.server.admin.OperationsException</import>
        </xsl:if>
        <xsl:if
          test="$this-local-relations/adm:one-to-zero-or-one|$this-local-relations/adm:one-to-many">
          <import>org.opends.server.admin.PropertyProvider</import>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="$this/@extends">
            <xsl:if test="$parent-package != $this-package">
              <xsl:element name="import">
                <xsl:value-of
                  select="concat($parent-package, '.client.', $parent-java-class, 'CfgClient')" />
              </xsl:element>
            </xsl:if>
          </xsl:when>
          <xsl:otherwise>
            <import>org.opends.server.admin.ConfigurationClient</import>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:element name="import">
          <xsl:value-of
            select="concat($this-package, '.server.', $this-java-class, 'Cfg')" />
        </xsl:element>
        <import>org.opends.server.admin.ManagedObjectDefinition</import>
      </xsl:with-param>
    </xsl:call-template>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:text>&#xa;</xsl:text>
    <xsl:call-template name="generate-interface-declaration" />
    <xsl:text>&#xa;</xsl:text>
    <xsl:call-template name="generate-configuration-definition-getter" />
    <xsl:for-each select="$this-local-properties">
      <xsl:sort select="@name" />
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-property-getter-declaration">
        <xsl:with-param name="interface" select="'client'" />
      </xsl:call-template>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:text>&#xa;</xsl:text>
      <xsl:call-template name="generate-property-setter-declaration" />
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
