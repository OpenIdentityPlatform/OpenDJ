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

  Copyright 2008-2009 Sun Microsystems, Inc.
  Portions Copyright 2014-2016 ForgeRock AS.
  ! -->
<xsl:stylesheet version="1.0" xmlns:adm="http://opendj.forgerock.org/admin"
  xmlns:admpp="http://opendj.forgerock.org/admin-preprocessor"
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
        <xsl:call-template name="add-java-comment2">
          <xsl:with-param name="indent" select="2" />
          <xsl:with-param name="content"
            select="concat(
                       'Gets the ', $ufn,'.&#xa;',
                       '&#xa;',
                       '@return Returns the ', $ufn,'.&#xa;',
                       '@throws DefinitionDecodingException&#xa;',
                       '          If the ', $ufn, ' was found but its type could not be determined.&#xa;',
                       '@throws ManagedObjectDecodingException&#xa;',
                       '          If the ', $ufn, ' was found but one or more of its properties could not be decoded.&#xa;',
                       '@throws ManagedObjectNotFoundException&#xa;',
                       '          If the ', $ufn, ' could not be found on the server.&#xa;',
                       '@throws ConcurrentModificationException&#xa;',
                       '          If this ', $this-ufn, ' has been removed from the server by another client.&#xa;',
                       '@throws LdapException&#xa;',
                       '          If any other error occurs.')" />
        </xsl:call-template>
        <xsl:value-of
          select="concat('  ', $java-class-name, 'CfgClient get', $java-relation-name, '()&#xa;',
                       '      throws DefinitionDecodingException, ManagedObjectDecodingException,&#xa;',
                       '      ManagedObjectNotFoundException, ConcurrentModificationException,&#xa;',
                       '      LdapException;&#xa;')" />
      </xsl:when>
      <xsl:when test="adm:one-to-zero-or-one">
        <xsl:call-template name="add-java-comment2">
          <xsl:with-param name="indent" select="2" />
          <xsl:with-param name="content"
            select="concat('Determines whether the ', $ufn,' exists.&#xa;',
                       '&#xa;',
                       '@return Returns &lt;true&gt; if the ', $ufn,' exists.&#xa;',
                       '@throws ConcurrentModificationException&#xa;',
                       '          If this ', $this-ufn, ' has been removed from the server by another client.&#xa;',
                       '@throws LdapException&#xa;',
                       '          If any other error occurs.')" />
        </xsl:call-template>
        <xsl:value-of
          select="concat('  boolean has',
                       $java-relation-name, '() throws ConcurrentModificationException,&#xa;',
                       '      LdapException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:call-template name="add-java-comment2">
          <xsl:with-param name="indent" select="2" />
          <xsl:with-param name="content"
            select="concat(
                       'Gets the ', $ufn,' if it is present.&#xa;',
                       '&#xa;',
                       '@return Returns the ', $ufn, ' if it is present.&#xa;',
                       '@throws DefinitionDecodingException&#xa;',
                       '          If the ', $ufn, ' was found but its type could not be determined.&#xa;',
                       '@throws ManagedObjectDecodingException&#xa;',
                       '          If the ', $ufn, ' was found but one or more of its properties could not be decoded.&#xa;',
                       '@throws ManagedObjectNotFoundException&#xa;',
                       '          If the ', $ufn, ' is not present.&#xa;',
                       '@throws ConcurrentModificationException&#xa;',
                       '          If this ', $this-ufn, ' has been removed from the server by another client.&#xa;',
                       '@throws LdapException&#xa;',
                       '          If any other error occurs.')" />
        </xsl:call-template>
        <xsl:value-of
          select="concat('  ', $java-class-name, 'CfgClient get', $java-relation-name, '()&#xa;',
                       '      throws DefinitionDecodingException, ManagedObjectDecodingException,&#xa;',
                       '      ManagedObjectNotFoundException, ConcurrentModificationException,&#xa;',
                       '      LdapException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:call-template name="add-java-comment2">
          <xsl:with-param name="indent" select="2" />
          <xsl:with-param name="content"
            select="concat(
                       'Creates a new ', $ufn,'. The new ', $ufn,' will initially ',
                       'not contain any property values (including mandatory ',
                       'properties). Once the ', $ufn,' has been configured it ',
                       'can be added to the server using the {@link #commit()} ',
                       'method.&#xa;',
                       '&#xa;',
                       '@param &lt;C&gt;&#xa;',
                       '         The type of the ', $ufn,' being created.&#xa;',
                       '@param d&#xa;',
                       '         The definition of the ', $ufn,' to be created.&#xa;',
                       '@param exceptions&#xa;',
                       '         An optional collection in which to place any ',
                       '{@link PropertyException}s that occurred whilst ',
                       'attempting to determine the default values of the ', $ufn,
                       '. This argument can be &lt;code&gt;null&lt;code&gt;.&#xa;',
                       '@return Returns a new ', $ufn,' configuration instance.&#xa;')" />
        </xsl:call-template>
        <xsl:value-of
          select="concat('  &lt;C extends ', $java-class-name,'CfgClient&gt; C create', $java-relation-name, '(&#xa;',
                           '      ManagedObjectDefinition&lt;C, ? extends ', $java-class-name,'Cfg&gt; d, Collection&lt;PropertyException&gt; exceptions);&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:call-template name="add-java-comment2">
          <xsl:with-param name="indent" select="2" />
          <xsl:with-param name="content"
            select="concat(
                       'Removes the ', $ufn,' if it exists.&#xa;',
                       '&#xa;',
                       '@throws ManagedObjectNotFoundException&#xa;',
                       '          If the ', $ufn, ' does not exist.&#xa;',
                       '@throws OperationRejectedException&#xa;',
                       '          If the server refuses to remove the ', $ufn, ' due to some server-side constraint which cannot be satisfied (for example, if it is referenced by another managed object).&#xa;',
                       '@throws ConcurrentModificationException&#xa;',
                       '          If this ', $this-ufn, ' has been removed from the server by another client.&#xa;',
                       '@throws LdapException&#xa;',
                       '          If any other error occurs.')" />
        </xsl:call-template>
        <xsl:value-of
          select="concat('  void remove', $java-relation-name, '()&#xa;',
                         '      throws ManagedObjectNotFoundException, OperationRejectedException,&#xa;',
                         '      ConcurrentModificationException, LdapException;&#xa;')" />
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
        <xsl:call-template name="add-java-comment2">
          <xsl:with-param name="indent" select="2" />
          <xsl:with-param name="content"
            select="concat('Lists the ', $ufpn,'.&#xa;',
                       '&#xa;',
                       '@return Returns an array containing the names of the ', $ufpn,'.&#xa;',
                       '@throws ConcurrentModificationException&#xa;',
                       '          If this ', $this-ufn, ' has been removed from the server by another client.&#xa;',
                       '@throws LdapException&#xa;',
                       '          If any other error occurs.')" />
        </xsl:call-template>
        <xsl:value-of
          select="concat('  String[] list',
                       $java-relation-plural-name, '() throws ConcurrentModificationException,&#xa;',
                       '      LdapException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:call-template name="add-java-comment2">
          <xsl:with-param name="indent" select="2" />
          <xsl:with-param name="content"
            select="concat(
                       'Gets the named ', $ufn, '.&#xa;',
                       '&#xa;',
                       '@param name&#xa;',
                       '          The name of the ', $ufn,' to retrieve.&#xa;',
                       '@return Returns the named ', $ufn, '.&#xa;',
                       '@throws DefinitionDecodingException&#xa;',
                       '          If the named ', $ufn, ' was found but its type could not be determined.&#xa;',
                       '@throws ManagedObjectDecodingException&#xa;',
                       '          If the named ', $ufn, ' was found but one or more of its properties could not be decoded.&#xa;',
                       '@throws ManagedObjectNotFoundException&#xa;',
                       '          If the named ', $ufn, ' was not found on the server.&#xa;',
                       '@throws ConcurrentModificationException&#xa;',
                       '          If this ', $this-ufn, ' has been removed from the server by another client.&#xa;',
                       '@throws LdapException&#xa;',
                       '          If any other error occurs.')" />
        </xsl:call-template>
        <xsl:value-of
          select="concat('  ', $java-class-name, 'CfgClient get', $java-relation-name, '(String name)&#xa;',
                       '      throws DefinitionDecodingException, ManagedObjectDecodingException,&#xa;',
                       '      ManagedObjectNotFoundException, ConcurrentModificationException,&#xa;',
                       '      LdapException;&#xa;')" />
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:choose>
          <xsl:when test="string(adm:one-to-many/@unique) != 'true'">
            <xsl:call-template name="add-java-comment2">
              <xsl:with-param name="indent" select="2" />
              <xsl:with-param name="content"
                select="concat(
                           'Creates a new ', $ufn,'. The new ', $ufn,' will initially ',
                           'not contain any property values (including mandatory ',
                           'properties). Once the ', $ufn,' has been configured it ',
                           'can be added to the server using the {@link #commit()} ',
                           'method.&#xa;',
                           '&#xa;',
                           '@param &lt;C&gt;&#xa;',
                           '         The type of the ', $ufn,' being created.&#xa;',
                           '@param d&#xa;',
                           '         The definition of the ', $ufn,' to be created.&#xa;',
                           '@param name&#xa;',
                           '         The name of the new ', $ufn,'.&#xa;',
                           '@param exceptions&#xa;',
                           '         An optional collection in which to place any ',
                           '{@link PropertyException}s that occurred whilst ',
                           'attempting to determine the default values of the ', $ufn,
                           '. This argument can be &lt;code&gt;null&lt;code&gt;.&#xa;',
                           '@return Returns a new ', $ufn,' configuration instance.&#xa;',
                           '@throws IllegalManagedObjectNameException&#xa;',
                           '         If the name of the new ', $ufn,' is invalid.&#xa;')" />
            </xsl:call-template>
            <xsl:value-of
              select="concat('  &lt;C extends ', $java-class-name,'CfgClient&gt; C create', $java-relation-name, '(&#xa;',
                               '      ManagedObjectDefinition&lt;C, ? extends ', $java-class-name,'Cfg&gt; d, String name, Collection&lt;PropertyException&gt; exceptions) throws IllegalManagedObjectNameException;&#xa;')" />
          </xsl:when>
          <xsl:when test="string(adm:one-to-many/@unique) = 'true'">
            <xsl:call-template name="add-java-comment2">
              <xsl:with-param name="indent" select="2" />
              <xsl:with-param name="content"
                select="concat(
                           'Creates a new ', $ufn,'. The new ', $ufn,' will initially ',
                           'not contain any property values (including mandatory ',
                           'properties). Once the ', $ufn,' has been configured it ',
                           'can be added to the server using the {@link #commit()} ',
                           'method.&#xa;',
                           '&#xa;',
                           '@param &lt;C&gt;&#xa;',
                           '         The type of the ', $ufn,' being created.&#xa;',
                           '@param d&#xa;',
                           '         The definition of the ', $ufn,' to be created.&#xa;',
                           '@param exceptions&#xa;',
                           '         An optional collection in which to place any ',
                           '{@link PropertyException}s that occurred whilst ',
                           'attempting to determine the default values of the ', $ufn,
                           '. This argument can be &lt;code&gt;null&lt;code&gt;.&#xa;',
                           '@return Returns a new ', $ufn,' configuration instance.&#xa;')" />
            </xsl:call-template>
            <xsl:value-of
              select="concat('  &lt;C extends ', $java-class-name,'CfgClient&gt; C create', $java-relation-name, '(&#xa;',
                               '      ManagedObjectDefinition&lt;C, ? extends ', $java-class-name,'Cfg&gt; d, Collection&lt;PropertyException&gt; exceptions);&#xa;')" />
          </xsl:when>
        </xsl:choose>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:text>&#xa;</xsl:text>
        <xsl:call-template name="add-java-comment2">
          <xsl:with-param name="indent" select="2" />
          <xsl:with-param name="content"
            select="concat(
                       'Removes the named ', $ufn,'.&#xa;',
                       '&#xa;',
                       '@param name&#xa;',
                       '         The name of the ', $ufn,' to remove.&#xa;',
                       '@throws ManagedObjectNotFoundException&#xa;',
                       '          If the ', $ufn, ' does not exist.&#xa;',
                       '@throws OperationRejectedException&#xa;',
                       '          If the server refuses to remove the ', $ufn, ' due to some server-side constraint which cannot be satisfied (for example, if it is referenced by another managed object).&#xa;',
                       '@throws ConcurrentModificationException&#xa;',
                       '          If this ', $this-ufn, ' has been removed from the server by another client.&#xa;',
                       '@throws LdapException&#xa;',
                       '          If any other error occurs.')" />
        </xsl:call-template>
        <xsl:value-of
          select="concat('  void remove', $java-relation-name, '(String name)&#xa;',
                         '      throws ManagedObjectNotFoundException, OperationRejectedException,&#xa;',
                         '      ConcurrentModificationException, LdapException;&#xa;')" />
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
          <xsl:call-template name="get-property-java-imports">
            <xsl:with-param name="interface" select="'client'" />
          </xsl:call-template>
        </xsl:for-each>
        <xsl:if test="$this-local-properties[@multi-valued='true']">
          <import>java.util.Collection</import>
          <import>java.util.SortedSet</import>
        </xsl:if>
        <xsl:if
          test="$this-local-properties[not(@monitoring='true')]">
          <import>
            org.forgerock.opendj.config.PropertyException
          </import>
        </xsl:if>
        <xsl:if test="$this-local-properties[@read-only='true']">
          <import>
            org.forgerock.opendj.config.PropertyException
          </import>
        </xsl:if>
        <xsl:if test="$this-local-relations">
          <import>
            org.forgerock.opendj.config.DefinitionDecodingException
          </import>
          <import>
            org.forgerock.opendj.config.ManagedObjectNotFoundException
          </import>
          <import>
            org.forgerock.opendj.config.client.ManagedObjectDecodingException
          </import>
          <import>
            org.forgerock.opendj.config.client.ConcurrentModificationException
          </import>
          <import>
            org.forgerock.opendj.ldap.LdapException
          </import>
        </xsl:if>
        <xsl:for-each
          select="$this-local-relations[adm:one-to-zero-or-one]|$this-local-relations[adm:one-to-many]">
          <xsl:variable name="java-class-name">
            <xsl:call-template name="name-to-java">
              <xsl:with-param name="value"
                select="@managed-object-name" />
            </xsl:call-template>
          </xsl:variable>
          <xsl:element name="import">
            <xsl:value-of
              select="concat(@managed-object-package, '.server.', $java-class-name, 'Cfg')" />
          </xsl:element>
        </xsl:for-each>
        <xsl:if
          test="$this-local-relations/adm:one-to-zero-or-one|$this-local-relations/adm:one-to-many">
          <import>java.util.Collection</import>
          <import>
            org.forgerock.opendj.config.PropertyException
          </import>
          <import>
            org.forgerock.opendj.config.client.OperationRejectedException
          </import>
        </xsl:if>
        <xsl:if test="$this-local-relations/adm:one-to-many[not(@unique = 'true')]">
          <import>
            org.forgerock.opendj.config.client.IllegalManagedObjectNameException
          </import>
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
            <import>org.forgerock.opendj.config.ConfigurationClient</import>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:element name="import">
          <xsl:value-of
            select="concat($this-package, '.server.', $this-java-class, 'Cfg')" />
        </xsl:element>
        <import>org.forgerock.opendj.config.ManagedObjectDefinition</import>
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
