<!-- CDDL HEADER START
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
  <xsl:output method="xml" indent="yes" />
  <!--
    Global parameter: the absolute path of the base directory where
    XML managed object definitions can be found.
  -->
  <xsl:param name="base-dir" select="'.'" />
  <!-- 
    Get an absolute URI from a package, object name, and suffix.
  -->
  <xsl:template name="get-uri">
    <xsl:param name="package" select="/.." />
    <xsl:param name="name" select="/.." />
    <xsl:param name="suffix" select="'.xml'" />
    <!--
      Convert the package name to a relative path.
    -->
    <xsl:variable name="rpath" select="translate($package, '.', '/')" />
    <!-- 
      Convert the managed object name to a file name.
    -->
    <xsl:variable name="java-name">
      <xsl:call-template name="name-to-java">
        <xsl:with-param name="value" select="$name" />
      </xsl:call-template>
    </xsl:variable>
    <!--
      Get the absolute path.
    -->
    <xsl:value-of
      select="concat($base-dir, '/', $rpath, '/', $java-name, $suffix)" />
  </xsl:template>
  <!--
    Get the URI of the named package definition.
  -->
  <xsl:template name="get-package-uri">
    <xsl:param name="package" select="/.." />
    <xsl:call-template name="get-uri">
      <xsl:with-param name="package" select="$package" />
      <xsl:with-param name="name" select="'package'" />
    </xsl:call-template>
  </xsl:template>
  <!--
    Get the URI of the named managed object definition.
  -->
  <xsl:template name="get-managed-object-uri">
    <xsl:param name="package" select="/.." />
    <xsl:param name="name" select="/.." />
    <xsl:call-template name="get-uri">
      <xsl:with-param name="package" select="$package" />
      <xsl:with-param name="name"
        select="concat($name, '-configuration')" />
    </xsl:call-template>
  </xsl:template>
  <!--
    Pre-process the current managed object element.
  -->
  <xsl:template name="pre-process-managed-object">
    <xsl:if test="not(adm:root-managed-object | adm:managed-object)">
      <xsl:message terminate="yes">
        <xsl:value-of select="'No managed object definition found.'" />
      </xsl:message>
    </xsl:if>
    <xsl:apply-templates
      select="adm:root-managed-object | adm:managed-object"
      mode="pre-process" />
  </xsl:template>
  <!--
    Pre-process a managed object definition: pull in the managed object's
    inherited property definitions and relations.
  -->
  <xsl:template match="adm:managed-object" mode="pre-process">
    <xsl:if test="not(@name)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="'Managed object definition does not specify managed object name.'" />
      </xsl:message>
    </xsl:if>
    <xsl:if test="not(@package)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="'Managed object definition does not specify managed object package.'" />
      </xsl:message>
    </xsl:if>
    <xsl:variable name="parent-name" select="@extends" />
    <xsl:variable name="parent-package">
      <!--
        The parent package defaults to this managed object's package.
      -->
      <xsl:choose>
        <xsl:when test="@parent-package">
          <xsl:value-of select="@parent-package" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@package" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <!--
      Get this managed object's hierarchy if there is one.
    -->
    <xsl:variable name="_hierarchy">
      <xsl:if test="$parent-name">
        <xsl:variable name="uri">
          <xsl:call-template name="get-managed-object-uri">
            <xsl:with-param name="package" select="$parent-package" />
            <xsl:with-param name="name" select="$parent-name" />
          </xsl:call-template>
        </xsl:variable>
        <xsl:if test="not(document($uri)/adm:managed-object)">
          <xsl:message terminate="yes">
            <xsl:value-of
              select="concat('No managed object definition found in ', $uri, '.')" />
          </xsl:message>
        </xsl:if>
        <xsl:if
          test="not(document($uri)/adm:managed-object[@name=$parent-name and @package=$parent-package])">
          <xsl:message terminate="yes">
            <xsl:value-of
              select="concat('Managed object definition found in ', $uri, ' but it did not define a managed object ', $parent-name, ' in package ', $parent-package, '.')" />
          </xsl:message>
        </xsl:if>
        <xsl:apply-templates select="document($uri)/adm:managed-object"
          mode="pre-process" />
      </xsl:if>
    </xsl:variable>
    <xsl:variable name="hierarchy" select="exsl:node-set($_hierarchy)" />
    <!--
      Now pre-process this managed object.
    -->
    <xsl:copy>
      <!--
        Shallow copy this element and its attributes.
      -->
      <xsl:copy-of select="@*" />
      <!--
        Pre-process this managed object's elements.
      -->
      <xsl:apply-templates
        select="adm:TODO|adm:synopsis|adm:description"
        mode="pre-process">
        <xsl:with-param name="moname" select="@name" />
        <xsl:with-param name="mopackage" select="@package" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
      <!--
        Copy all inherited tags plus locally defined tags.
      -->
      <xsl:copy-of select="$hierarchy/adm:managed-object/adm:tag" />
      <xsl:apply-templates select="adm:tag" mode="pre-process">
        <xsl:with-param name="moname" select="@name" />
        <xsl:with-param name="mopackage" select="@package" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
      <!--
        Copy profile elements.
      -->
      <xsl:apply-templates select="adm:profile" mode="pre-process">
        <xsl:with-param name="moname" select="@name" />
        <xsl:with-param name="mopackage" select="@package" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
      <!-- 
        Add a pre-processor element defining this managed object's uppermost
        definition.
      -->
      <xsl:if test="$parent-name">
        <xsl:element name="adm:profile">
          <xsl:attribute name="name">
            <xsl:value-of select="'preprocessor'" />
          </xsl:attribute>
          <xsl:element name="admpp:parent-managed-object">
            <xsl:attribute name="name">
              <xsl:value-of select="$parent-name" />
            </xsl:attribute>
            <xsl:attribute name="package">
              <xsl:value-of select="$parent-package" />
            </xsl:attribute>
          </xsl:element>
          <xsl:copy-of
            select="$hierarchy/adm:managed-object/adm:profile[@name='preprocessor']/admpp:parent-managed-object" />
        </xsl:element>
      </xsl:if>
      <!--
        Copy all inherited relations.
      -->
      <xsl:copy-of select="$hierarchy/adm:managed-object/adm:relation" />
      <!--
        Copy all local relations.
      -->
      <xsl:apply-templates select="adm:relation" mode="pre-process">
        <xsl:with-param name="moname" select="@name" />
        <xsl:with-param name="mopackage" select="@package" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
      <!--
        Copy all inherited properties.
      -->
      <xsl:variable name="property-overrides"
        select="adm:property-override" />
      <xsl:copy-of
        select="$hierarchy/adm:managed-object/adm:property[not(@name=$property-overrides/@name)]" />
      <!--
        Copy all local properties.
      -->
      <xsl:apply-templates
        select="adm:property|adm:property-reference|adm:property-override"
        mode="pre-process">
        <xsl:with-param name="moname" select="@name" />
        <xsl:with-param name="mopackage" select="@package" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>
  <!--
    Pre-process a managed object definition: pull in the managed object's
    inherited property definitions and relations.
  -->
  <xsl:template match="adm:root-managed-object" mode="pre-process">
    <!--
      Now pre-process this root managed object.
      By definition it has no hierarchy.
    -->
    <xsl:copy>
      <!--
        Shallow copy this element and its attributes.
      -->
      <xsl:copy-of select="@*" />
      <!--
        Pre-process this managed object's elements.
      -->
      <xsl:apply-templates mode="pre-process">
        <xsl:with-param name="moname" select="'root'" />
        <xsl:with-param name="mopackage"
          select="'org.opends.server.admin.std'" />
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>
  <!--
    Pre-process a tag and validate it and by adding a "preprocessor"
    profile which contains information about where the tag was defined.
  -->
  <xsl:template match="adm:tag" mode="pre-process">
    <xsl:param name="mopackage" select="/.." />
    <xsl:param name="moname" select="/.." />
    <xsl:param name="hierarchy" />
    <!--
      Make sure that this tag is not duplicated.
    -->
    <xsl:variable name="name" select="@name" />
    <xsl:if test="../adm:tag[@name=$name][2]">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Tag ', @name, ' is already defined in this managed object')" />
      </xsl:message>
    </xsl:if>
    <!--
      Make sure that this tag does not override an existing tag.
    -->
    <xsl:if test="$hierarchy/adm:managed-object/adm:tag[@name=$name]">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Tag ', @name, ' is already defined in a parent managed object')" />
      </xsl:message>
    </xsl:if>
    <!--
      Get the referenced package.
    -->
    <xsl:variable name="uri">
      <xsl:call-template name="get-managed-object-uri">
        <xsl:with-param name="package"
          select="'org.opends.server.admin.std'" />
        <xsl:with-param name="name" select="'root'" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:if test="not(document($uri)/adm:root-managed-object)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Root managed object definition not found in ', $uri, '.')" />
      </xsl:message>
    </xsl:if>
    <xsl:if
      test="not(document($uri)/adm:root-managed-object/adm:tag-definition[@name=$name])">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Tag &quot;', $name,
                           '&quot; not defined in root managed object definition.')" />
      </xsl:message>
    </xsl:if>
    <!--
      Copy the tag.
    -->
    <xsl:element name="adm:tag">
      <xsl:copy-of select="@*" />
      <xsl:apply-templates mode="pre-process">
        <xsl:with-param name="moname" select="$moname" />
        <xsl:with-param name="mopackage" select="$mopackage" />
      </xsl:apply-templates>
    </xsl:element>
  </xsl:template>
  <!--
    Pre-process a property definition by adding a "preprocessor" profile
    which contains information about where the property was defined.
  -->
  <xsl:template match="adm:property" mode="pre-process">
    <xsl:param name="mopackage" select="/.." />
    <xsl:param name="moname" select="/.." />
    <xsl:param name="hierarchy" select="/.." />
    <!--
      Make sure that this property does not have the same name as another
      property or reference in this managed object.
    -->
    <xsl:variable name="name" select="@name" />
    <xsl:if
      test="../adm:property[@name=$name][2] |
            ../adm:property-reference[@name=$name]">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Property definition ', @name, ' is already defined in this managed object')" />
      </xsl:message>
    </xsl:if>
    <!--
      Make sure that this property does not override an existing property.
    -->
    <xsl:if
      test="$hierarchy/adm:managed-object/adm:property[@name=$name]">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Property definition ', @name, ' is already defined in a parent managed object')" />
      </xsl:message>
    </xsl:if>
    <xsl:copy>
      <!--
        Shallow copy this element and its attributes.
      -->
      <xsl:copy-of select="@*" />
      <!--
        Apply templates to subordinate elements (e.g. descriptions).
      -->
      <xsl:apply-templates mode="pre-process">
        <xsl:with-param name="mopackage" select="$mopackage" />
        <xsl:with-param name="moname" select="$moname" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
      <!--
        Now append the preprocessor profile.
      -->
      <xsl:element name="adm:profile">
        <xsl:attribute name="name">
          <xsl:value-of select="'preprocessor'" />
        </xsl:attribute>
        <xsl:element name="admpp:managed-object">
          <xsl:attribute name="name">
            <xsl:value-of select="$moname" />
          </xsl:attribute>
          <xsl:attribute name="package">
            <xsl:value-of select="$mopackage" />
          </xsl:attribute>
        </xsl:element>
      </xsl:element>
    </xsl:copy>
  </xsl:template>
  <!--
    Pre-process a property reference pulling in the referenced property
    definition and by adding a "preprocessor" profile which contains
    information about where the property was defined.
  -->
  <xsl:template match="adm:property-reference" mode="pre-process">
    <xsl:param name="mopackage" select="/.." />
    <xsl:param name="moname" select="/.." />
    <xsl:param name="hierarchy" />
    <!--
      Make sure that this property reference does not have the same name as another
      property or reference in this managed object.
    -->
    <xsl:variable name="name" select="@name" />
    <xsl:if
      test="../adm:property[@name=$name] |
            ../adm:property-reference[@name=$name][2]">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Property definition ', @name, ' is already defined in this managed object')" />
      </xsl:message>
    </xsl:if>
    <!--
      Make sure that this property does not override an existing property.
    -->
    <xsl:if
      test="$hierarchy/adm:managed-object/adm:property[@name=$name]">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Property reference ', @name, ' is already defined in a parent managed object')" />
      </xsl:message>
    </xsl:if>
    <!--
      Determine the package containing the reference property definition.
    -->
    <xsl:variable name="package">
      <xsl:choose>
        <xsl:when test="@package">
          <xsl:value-of select="@package" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$mopackage" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <!--
      Get the referenced package.
    -->
    <xsl:variable name="uri">
      <xsl:call-template name="get-package-uri">
        <xsl:with-param name="package" select="$package" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:if test="not(document($uri)/adm:package)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('No package definition found in ', $uri, '.')" />
      </xsl:message>
    </xsl:if>
    <xsl:if test="not(document($uri)/adm:package[@name=$package])">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Package definition found in ', $uri, ' but it did not define package ', $package, '.')" />
      </xsl:message>
    </xsl:if>
    <xsl:if
      test="not(document($uri)/adm:package[@name=$package]/adm:property[@name=$name])">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Referenced property definition &quot;', $name,
                           '&quot; not found in package definition &quot;', $package,
                           '&quot;.')" />
      </xsl:message>
    </xsl:if>
    <!--
      Copy the referenced property definition taking care to override
      the default behavior and admin action if required.
    -->
    <xsl:variable name="property"
      select="document($uri)/adm:package[@name=$package]/adm:property[@name=$name]" />
    <xsl:element name="adm:property">
      <xsl:copy-of select="$property/@*" />
      <xsl:apply-templates
        select="$property/adm:TODO | $property/adm:synopsis | $property/adm:description"
        mode="pre-process">
        <xsl:with-param name="mopackage" select="$mopackage" />
        <xsl:with-param name="moname" select="$moname" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
      <xsl:choose>
        <xsl:when test="adm:requires-admin-action">
          <xsl:apply-templates select="adm:requires-admin-action"
            mode="pre-process">
            <xsl:with-param name="mopackage" select="$mopackage" />
            <xsl:with-param name="moname" select="$moname" />
            <xsl:with-param name="hierarchy" select="$hierarchy" />
          </xsl:apply-templates>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates
            select="$property/adm:requires-admin-action"
            mode="pre-process">
            <xsl:with-param name="mopackage" select="$mopackage" />
            <xsl:with-param name="moname" select="$moname" />
            <xsl:with-param name="hierarchy" select="$hierarchy" />
          </xsl:apply-templates>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:choose>
        <xsl:when test="adm:default-behavior">
          <xsl:apply-templates select="adm:default-behavior"
            mode="pre-process">
            <xsl:with-param name="mopackage" select="$mopackage" />
            <xsl:with-param name="moname" select="$moname" />
            <xsl:with-param name="hierarchy" select="$hierarchy" />
          </xsl:apply-templates>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="$property/adm:default-behavior"
            mode="pre-process">
            <xsl:with-param name="mopackage" select="$mopackage" />
            <xsl:with-param name="moname" select="$moname" />
            <xsl:with-param name="hierarchy" select="$hierarchy" />
          </xsl:apply-templates>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:apply-templates
        select="$property/adm:syntax | $property/adm:profile"
        mode="pre-process">
        <xsl:with-param name="mopackage" select="$mopackage" />
        <xsl:with-param name="moname" select="$moname" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
      <!--
        Now append the preprocessor profile.
      -->
      <xsl:element name="adm:profile">
        <xsl:attribute name="name">
          <xsl:value-of select="'preprocessor'" />
        </xsl:attribute>
        <xsl:element name="admpp:managed-object">
          <xsl:attribute name="name">
            <xsl:value-of select="$moname" />
          </xsl:attribute>
          <xsl:attribute name="package">
            <xsl:value-of select="$mopackage" />
          </xsl:attribute>
        </xsl:element>
        <xsl:element name="admpp:package">
          <xsl:attribute name="name">
            <xsl:value-of select="$package" />
          </xsl:attribute>
        </xsl:element>
      </xsl:element>
    </xsl:element>
  </xsl:template>
  <!--
    Pre-process a property override pulling in the inherited property
    definition and by adding a "preprocessor" profile which contains
    information about where the property was redefined.
  -->
  <xsl:template match="adm:property-override" mode="pre-process">
    <xsl:param name="mopackage" select="/.." />
    <xsl:param name="moname" select="/.." />
    <xsl:param name="hierarchy" />
    <!--
      Make sure that this property override does not have the same name as another
      property override in this managed object.
    -->
    <xsl:variable name="name" select="@name" />
    <xsl:if test="../adm:property-override[@name=$name][2]">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Property override ', @name, ' is already overridden in this managed object')" />
      </xsl:message>
    </xsl:if>
    <!--
      Make sure that this property overrides an existing property.
    -->
    <xsl:if
      test="not($hierarchy/adm:managed-object/adm:property[@name=$name])">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Cannot find inherited property ', @name, ' for property override')" />
      </xsl:message>
    </xsl:if>
    <!--
      Copy the inherited property definition taking care to override
      the default behavior and admin action if required.
    -->
    <xsl:variable name="property"
      select="$hierarchy/adm:managed-object/adm:property[@name=$name]" />
    <xsl:element name="adm:property">
      <xsl:copy-of select="$property/@*" />
      <xsl:apply-templates
        select="$property/adm:TODO | $property/adm:synopsis | $property/adm:description"
        mode="pre-process">
        <xsl:with-param name="mopackage" select="$mopackage" />
        <xsl:with-param name="moname" select="$moname" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
      <xsl:choose>
        <xsl:when test="adm:requires-admin-action">
          <xsl:apply-templates select="adm:requires-admin-action"
            mode="pre-process">
            <xsl:with-param name="mopackage" select="$mopackage" />
            <xsl:with-param name="moname" select="$moname" />
            <xsl:with-param name="hierarchy" select="$hierarchy" />
          </xsl:apply-templates>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates
            select="$property/adm:requires-admin-action"
            mode="pre-process">
            <xsl:with-param name="mopackage" select="$mopackage" />
            <xsl:with-param name="moname" select="$moname" />
            <xsl:with-param name="hierarchy" select="$hierarchy" />
          </xsl:apply-templates>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:choose>
        <xsl:when test="adm:default-behavior">
          <xsl:apply-templates select="adm:default-behavior"
            mode="pre-process">
            <xsl:with-param name="mopackage" select="$mopackage" />
            <xsl:with-param name="moname" select="$moname" />
            <xsl:with-param name="hierarchy" select="$hierarchy" />
          </xsl:apply-templates>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="$property/adm:default-behavior"
            mode="pre-process">
            <xsl:with-param name="mopackage" select="$mopackage" />
            <xsl:with-param name="moname" select="$moname" />
            <xsl:with-param name="hierarchy" select="$hierarchy" />
          </xsl:apply-templates>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:apply-templates
        select="$property/adm:syntax | $property/adm:profile[@name!='preprocessor']"
        mode="pre-process">
        <xsl:with-param name="mopackage" select="$mopackage" />
        <xsl:with-param name="moname" select="$moname" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
      <!--
        Now append the preprocessor profile.
      -->
      <xsl:element name="adm:profile">
        <xsl:attribute name="name">
          <xsl:value-of select="'preprocessor'" />
        </xsl:attribute>
        <xsl:element name="admpp:managed-object">
          <xsl:attribute name="name">
            <xsl:value-of select="$moname" />
          </xsl:attribute>
          <xsl:attribute name="package">
            <xsl:value-of select="$mopackage" />
          </xsl:attribute>
        </xsl:element>
      </xsl:element>
    </xsl:element>
  </xsl:template>
  <!--
    Pre-process a relation, merging information from the referenced
    managed object where required, and by adding a "preprocessor" profile
    which contains information about where the relation was defined.
  -->
  <xsl:template match="adm:relation" mode="pre-process">
    <xsl:param name="mopackage" select="/.." />
    <xsl:param name="moname" select="/.." />
    <xsl:param name="hierarchy" select="/.." />
    <!--
      Determine the name of the relation.
    -->
    <xsl:variable name="name" select="@name" />
    <!--
      Make sure that this relation does not override an existing relation.
    -->
    <xsl:if
      test="$hierarchy/adm:managed-object/adm:relation[@name=$name]">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Relation ', $name, ' is already defined in a parent managed object.')" />
      </xsl:message>
    </xsl:if>
    <!--
      Make sure that this relation is not already defined in this managed object.
    -->
    <xsl:if test="../adm:relation[@name=$name][2]">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Relation ', $name, ' is already defined in this managed object.')" />
      </xsl:message>
    </xsl:if>
    <!-- 
      Now get the referenced managed object.
    -->
    <xsl:variable name="mname">
      <xsl:choose>
        <xsl:when test="not(@managed-object-name)">
          <xsl:value-of select="$name" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@managed-object-name" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="mpackage">
      <xsl:choose>
        <xsl:when test="not(@managed-object-package)">
          <xsl:value-of select="$mopackage" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@managed-object-package" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="uri">
      <xsl:call-template name="get-managed-object-uri">
        <xsl:with-param name="name" select="$mname" />
        <xsl:with-param name="package" select="$mpackage" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="managed-object"
      select="document($uri)/adm:managed-object[@name=$mname]" />
    <xsl:if test="not($managed-object)">
      <xsl:message terminate="yes">
        <xsl:value-of
          select="concat('Managed object definition &quot;', $mname, '&quot; not found in ', $uri, '.')" />
      </xsl:message>
    </xsl:if>
    <!--
      Now merge the relation.
    -->
    <xsl:copy>
      <xsl:copy-of select="@*" />
      <!--
        Add missing attribute managed-object-name if it is not provided.
      -->
      <xsl:if test="not(@managed-object-name)">
        <xsl:attribute name="managed-object-name">
          <xsl:value-of select="$mname" />
        </xsl:attribute>
      </xsl:if>
      <!--
        Add missing attribute managed-object-package if it is not provided.
      -->
      <xsl:if test="not(@managed-object-package)">
        <xsl:attribute name="managed-object-package">
          <xsl:value-of select="$mpackage" />
        </xsl:attribute>
      </xsl:if>
      <!-- 
        Copy TODO element.
      -->
      <xsl:copy-of select="adm:TODO" />
      <!-- 
        Copy synopsis element from referenced managed object if it is undefined.
      -->
      <xsl:choose>
        <xsl:when test="adm:synopsis">
          <xsl:apply-templates select="adm:synopsis"
            mode="merge-relation">
            <xsl:with-param name="managed-object"
              select="$managed-object" />
          </xsl:apply-templates>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="$managed-object/adm:synopsis"
            mode="merge-relation">
            <xsl:with-param name="managed-object"
              select="$managed-object" />
          </xsl:apply-templates>
        </xsl:otherwise>
      </xsl:choose>
      <!-- 
        Copy description element from referenced managed object if it is undefined.
      -->
      <xsl:choose>
        <xsl:when test="adm:description">
          <xsl:apply-templates select="adm:description"
            mode="merge-relation">
            <xsl:with-param name="managed-object"
              select="$managed-object" />
          </xsl:apply-templates>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="$managed-object/adm:description"
            mode="merge-relation">
            <xsl:with-param name="managed-object"
              select="$managed-object" />
          </xsl:apply-templates>
        </xsl:otherwise>
      </xsl:choose>
      <!--
        Merge remaining elements.
      -->
      <xsl:apply-templates
        select="*[not(self::adm:TODO|self::adm:synopsis|self::adm:description)]"
        mode="merge-relation">
        <xsl:with-param name="managed-object" select="$managed-object" />
      </xsl:apply-templates>
      <!--
        Now append the preprocessor profile.
      -->
      <xsl:element name="adm:profile">
        <xsl:attribute name="name">
          <xsl:value-of select="'preprocessor'" />
        </xsl:attribute>
        <xsl:element name="admpp:managed-object">
          <xsl:attribute name="name">
            <xsl:value-of select="$moname" />
          </xsl:attribute>
          <xsl:attribute name="package">
            <xsl:value-of select="$mopackage" />
          </xsl:attribute>
        </xsl:element>
      </xsl:element>
    </xsl:copy>
  </xsl:template>
  <!--
    Default template for merging relations.
  -->
  <xsl:template match="*|comment()" mode="merge-relation">
    <xsl:param name="managed-object" select="/.." />
    <xsl:copy>
      <xsl:copy-of select="@*" />
      <xsl:apply-templates mode="merge-relation">
        <xsl:with-param name="managed-object" select="$managed-object" />
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>
  <!--
    Merge a one-to-many relation.
  -->
  <xsl:template match="adm:one-to-many" mode="merge-relation">
    <xsl:param name="managed-object" select="/.." />
    <!--
      Make sure that if this relation uses a naming property that the
      naming property exists, is single-valued, mandatory, and read-only.
    -->
    <xsl:if test="@naming-property">
      <xsl:variable name="naming-property-name"
        select="@naming-property" />

      <!--
        FIXME: this does not cope with the situation where the property
        is inherited, referenced, or overridden.
      -->
      <xsl:variable name="naming-property"
        select="$managed-object/adm:property[@name=$naming-property-name]" />
      <xsl:if test="not($naming-property)">
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Relation ', ../@name,
                           ' references an unknown naming property ',
                           $naming-property-name, ' in ',
                           $managed-object/@name, '.')" />
        </xsl:message>
      </xsl:if>
      <xsl:if test="not($naming-property/@read-only='true')">
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Relation ', ../@name,
                           ' references the naming property ',
                           $naming-property-name, ' in ',
                           $managed-object/@name, ' which is not read-only. ',
                           'Naming properties must be read-only.')" />
        </xsl:message>
      </xsl:if>
      <xsl:if test="not($naming-property/@mandatory='true')">
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Relation ', ../@name,
                           ' references the naming property ',
                           $naming-property-name, ' in ',
                           $managed-object/@name, ' which is not mandatory. ',
                           'Naming properties must be mandatory.')" />
        </xsl:message>
      </xsl:if>
      <xsl:if test="$naming-property/@multi-valued='true'">
        <xsl:message terminate="yes">
          <xsl:value-of
            select="concat('Relation ', ../@name,
                           ' references the naming property ',
                           $naming-property-name, ' in ',
                           $managed-object/@name, ' which is multi-valued. ',
                           'Naming properties must be single-valued.')" />
        </xsl:message>
      </xsl:if>
    </xsl:if>
    <xsl:copy>
      <xsl:copy-of select="@*" />
      <!--
        Add missing plural name attribute if not present.
      -->
      <xsl:if test="not(@plural-name)">
        <xsl:attribute name="plural-name">
          <xsl:value-of select="$managed-object/@plural-name" />
        </xsl:attribute>
      </xsl:if>
      <xsl:apply-templates mode="merge-relation">
        <xsl:with-param name="managed-object" select="$managed-object" />
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>
  <!--
    Process a rich-description element in a relation.
  -->
  <xsl:template match="adm:synopsis|adm:description"
    mode="merge-relation">
    <xsl:param name="managed-object" select="/.." />
    <xsl:copy>
      <!--
        Shallow copy.
      -->
      <xsl:copy-of select="@*" />
      <xsl:apply-templates mode="rich-description">
        <xsl:with-param name="ufn">
          <xsl:call-template name="name-to-ufn">
            <xsl:with-param name="value" select="$managed-object/@name" />
          </xsl:call-template>
        </xsl:with-param>
        <xsl:with-param name="ufpn">
          <xsl:call-template name="name-to-ufn">
            <xsl:with-param name="value"
              select="$managed-object/@plural-name" />
          </xsl:call-template>
        </xsl:with-param>
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>
  <!--
    Process a rich-description element.
  -->
  <xsl:template
    match="adm:synopsis|adm:description|adm:unit-description"
    mode="pre-process">
    <xsl:copy>
      <!--
        Shallow copy.
      -->
      <xsl:copy-of select="@*" />
      <xsl:apply-templates mode="rich-description">
        <xsl:with-param name="ufn" select="$this-ufn" />
        <xsl:with-param name="ufpn" select="$this-ufpn" />
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>
  <!--
    Process a relative inherited default behavior
  -->
  <xsl:template match="adm:relative" mode="pre-process">
    <xsl:param name="mopackage" select="/.." />
    <xsl:param name="moname" select="/.." />
    <xsl:param name="hierarchy" select="/.." />
    <xsl:copy>
      <!--
        Shallow copy.
      -->
      <xsl:copy-of select="@*" />
      <!--
        Add missing attribute managed-object-package if it is not provided.
      -->
      <xsl:if test="not(@managed-object-package)">
        <xsl:attribute name="managed-object-package">
          <xsl:value-of select="$mopackage" />
        </xsl:attribute>
      </xsl:if>
      <!--
        Apply templates to subordinate elements.
      -->
      <xsl:apply-templates mode="pre-process">
        <xsl:with-param name="mopackage" select="$mopackage" />
        <xsl:with-param name="moname" select="$moname" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>
  <!--
    Process a user-friendly-name element.
  -->
  <xsl:template match="adm:user-friendly-name"
    mode="rich-description">
    <xsl:param name="ufn" select="/.." />
    <xsl:value-of select="$ufn" />
  </xsl:template>
  <!--
    Process a user-friendly-plural-name element.
  -->
  <xsl:template match="adm:user-friendly-plural-name"
    mode="rich-description">
    <xsl:param name="ufpn" select="/.." />
    <xsl:value-of select="$ufpn" />
  </xsl:template>
  <!--
    Process a product-name element.
  -->
  <xsl:template match="adm:product-name" mode="rich-description">
    <xsl:value-of select="$product-name" />
  </xsl:template>
  <!--
    Default template for rich descriptions.
  -->
  <xsl:template match="*|comment()" mode="rich-description">
    <xsl:param name="ufn" select="/.." />
    <xsl:param name="ufpn" select="/.." />
    <xsl:copy>
      <xsl:copy-of select="@*" />
      <xsl:apply-templates mode="rich-description">
        <xsl:with-param name="ufn" select="$ufn" />
        <xsl:with-param name="ufpn" select="$ufpn" />
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>
  <!--
    Default template for pre-processing.
  -->
  <xsl:template match="*|comment()" mode="pre-process">
    <xsl:param name="mopackage" select="/.." />
    <xsl:param name="moname" select="/.." />
    <xsl:param name="hierarchy" />
    <xsl:copy>
      <xsl:copy-of select="@*" />
      <xsl:apply-templates mode="pre-process">
        <xsl:with-param name="mopackage" select="$mopackage" />
        <xsl:with-param name="moname" select="$moname" />
        <xsl:with-param name="hierarchy" select="$hierarchy" />
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>
  <!--
    Useful variables relating to the current managed object.
  -->
  <!--
    Product name.
    
    FIXME: should get this from the root configuration but for some
    reason we get a circular dependency error when constructing
    the URI in JDK1.6.
  -->
  <xsl:variable name="product-name" select="'OpenDS Directory Server'" />
  <xsl:variable name="this-name">
    <xsl:choose>
      <xsl:when test="/adm:managed-object">
        <xsl:value-of select="/adm:managed-object/@name" />
      </xsl:when>
      <xsl:otherwise>
        <!--
          Must be the root configuration.  
        -->
        <xsl:value-of select="'root'" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="this-plural-name">
    <xsl:choose>
      <xsl:when test="/adm:managed-object">
        <xsl:value-of select="/adm:managed-object/@plural-name" />
      </xsl:when>
      <xsl:otherwise>
        <!--
          Must be the root configuration - the plural form should never
          be required as this is a singleton. We'll define it for
          consistency. 
        -->
        <xsl:value-of select="'roots'" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="this-ufn">
    <xsl:choose>
      <xsl:when test="/adm:managed-object/adm:user-friendly-name">
        <xsl:value-of
          select="normalize-space(/adm:managed-object/adm:user-friendly-name)" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="name-to-ufn">
          <xsl:with-param name="value" select="$this-name" />
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="this-ufpn">
    <xsl:choose>
      <xsl:when
        test="/adm:managed-object/adm:user-friendly-plural-name">
        <xsl:value-of
          select="normalize-space(/adm:managed-object/adm:user-friendly-plural-name)" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="name-to-ufn">
          <xsl:with-param name="value" select="$this-plural-name" />
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="_this">
    <xsl:call-template name="pre-process-managed-object" />
  </xsl:variable>
  <xsl:variable name="_this_tmp" select="exsl:node-set($_this)" />
  <xsl:variable name="this"
    select="$_this_tmp/adm:managed-object | $_this_tmp/adm:root-managed-object" />
  <xsl:variable name="this-is-abstract"
    select="boolean(string($this/@abstract) = 'true')" />
  <xsl:variable name="this-is-root"
    select="not(local-name($this) = 'managed-object')" />
  <xsl:variable name="this-package">
    <xsl:choose>
      <xsl:when test="not($this-is-root)">
        <xsl:value-of select="$this/@package" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="'org.opends.server.admin.std'" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="this-java-class">
    <xsl:call-template name="name-to-java">
      <xsl:with-param name="value" select="$this-name" />
    </xsl:call-template>
  </xsl:variable>
  <xsl:variable name="_top-name"
    select="$this/adm:profile[@name='preprocessor']/admpp:parent-managed-object[last()]/@name" />
  <xsl:variable name="_top-length" select="string-length($_top-name)" />
  <xsl:variable name="_this-length" select="string-length($this-name)" />
  <xsl:variable name="_diff" select="$_this-length - $_top-length" />
  <xsl:variable name="_start"
    select="substring($this-name, 1, $_diff - 1)" />
  <xsl:variable name="_middle"
    select="substring($this-name, $_diff, 1)" />
  <xsl:variable name="_end"
    select="substring($this-name, $_diff + 1, $_top-length)" />
  <xsl:variable name="this-short-name">
    <xsl:choose>
      <xsl:when test="$this-is-root">
        <xsl:value-of select="''" />
      </xsl:when>
      <xsl:when test="not($_top-name)">
        <xsl:value-of select="''" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:if test="$_middle != '-' or $_end != $_top-name">
          <xsl:message terminate="yes">
            <xsl:value-of
              select="concat('The managed object ', $this-name, ' should end with ', $_top-name)" />
          </xsl:message>
        </xsl:if>
        <xsl:value-of select="$_start" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="this-short-java-class">
    <xsl:call-template name="name-to-java">
      <xsl:with-param name="value" select="$this-short-name" />
    </xsl:call-template>
  </xsl:variable>
  <!-- 
    Useful variables relating to the parent managed object.
  -->
  <xsl:variable name="parent-name" select="$this/@extends" />
  <xsl:variable name="parent-package">
    <xsl:choose>
      <xsl:when test="$this/@parent-package">
        <xsl:value-of select="$this/@parent-package" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$this-package" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="parent-java-class">
    <xsl:call-template name="name-to-java">
      <xsl:with-param name="value" select="$parent-name" />
    </xsl:call-template>
  </xsl:variable>
  <!-- 
    Useful variables relating to managed object's relations.
  -->
  <xsl:variable name="this-local-relations"
    select="$this/adm:relation[adm:profile[@name='preprocessor']/admpp:managed-object[@name=$this-name and @package=$this-package]]" />
  <xsl:variable name="this-inherited-relations"
    select="$this/adm:relation[adm:profile[@name='preprocessor']/admpp:managed-object[not(@name=$this-name and @package=$this-package)]]" />
  <xsl:variable name="this-all-relations" select="$this/adm:relation" />
  <!-- 
    Useful variables relating to managed object's properties.
  -->
  <xsl:variable name="this-local-properties"
    select="$this/adm:property[adm:profile[@name='preprocessor']/admpp:managed-object[@name=$this-name and @package=$this-package]]" />
  <xsl:variable name="this-inherited-properties"
    select="$this/adm:property[adm:profile[@name='preprocessor']/admpp:managed-object[not(@name=$this-name and @package=$this-package)]]" />
  <xsl:variable name="this-all-properties" select="$this/adm:property" />
  <!--
    Default rule for testing.
  -->
  <xsl:template match="/">
    <xsl:copy-of select="$this" />
  </xsl:template>
</xsl:stylesheet>
