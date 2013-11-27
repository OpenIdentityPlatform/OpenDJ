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
  !      Copyright 2008 Sun Microsystems, Inc.
  ! -->
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <!--
    This XSLT file contains generic templates which can be used for any
    application.
  -->
  <xsl:import href="abbreviations.xsl" />
  <xsl:output method="text" encoding="us-ascii" />
  <!--
    Format a block of text. This template handles two levels of
    indentation: the indentation string for the first line, and a
    second indentation string used for subsequent lines. The template
    will output the content wrapping at the nearest word boundary to
    the specified column.
    
    @param indent-text
    The indentation text used for the first line.
    
    @param indent-text2
    The indentation text used for all lines except
    the first - defaults to the value of indent-text.
    
    @param content
    The text to be formatted.
    
    @param wrap-column
    The text column before which text should be word
    wrapped.
  -->
  <xsl:template name="format-text">
    <xsl:param name="indent-text" />
    <xsl:param name="indent-text2" select="$indent-text" />
    <xsl:param name="wrap-column" />
    <xsl:param name="content" />
    <xsl:value-of select="$indent-text" />
    <xsl:call-template name="format-text-help">
      <xsl:with-param name="indent-text" select="$indent-text2" />
      <xsl:with-param name="wrap-column" select="$wrap-column" />
      <xsl:with-param name="content" select="normalize-space($content)" />
      <xsl:with-param name="column"
        select="string-length($indent-text) + 1" />
    </xsl:call-template>
    <xsl:text>&#xa;</xsl:text>
  </xsl:template>
  <!--
    PRIVATE implementation template for format-text.
  -->
  <xsl:template name="format-text-help">
    <xsl:param name="indent-text" />
    <xsl:param name="wrap-column" />
    <xsl:param name="content" />
    <xsl:param name="column" />
    <xsl:variable name="head" select="substring-before($content, ' ')" />
    <xsl:variable name="tail" select="substring-after($content, ' ')" />
    <xsl:if test="string-length($content)">
      <xsl:choose>
        <xsl:when test="string-length($head) = 0">
          <xsl:if
            test="(string-length($content) + $column) > $wrap-column">
            <xsl:text>&#xa;</xsl:text>
            <xsl:value-of select="$indent-text" />
          </xsl:if>
          <xsl:value-of select="' '" />
          <xsl:value-of select="$content" />
        </xsl:when>
        <xsl:when
          test="(string-length($head) + $column) > $wrap-column">
          <xsl:text>&#xa;</xsl:text>
          <xsl:value-of select="$indent-text" />
          <xsl:value-of select="' '" />
          <xsl:value-of select="$head" />
          <xsl:call-template name="format-text-help">
            <xsl:with-param name="indent-text" select="$indent-text" />
            <xsl:with-param name="wrap-column" select="$wrap-column" />
            <xsl:with-param name="content" select="$tail" />
            <xsl:with-param name="column"
              select="string-length($indent-text) + string-length($head) + 1" />
          </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="concat(' ', $head)" />
          <xsl:call-template name="format-text-help">
            <xsl:with-param name="indent-text" select="$indent-text" />
            <xsl:with-param name="wrap-column" select="$wrap-column" />
            <xsl:with-param name="content" select="$tail" />
            <xsl:with-param name="column"
              select="$column + string-length($head) + 1" />
          </xsl:call-template>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>
  <!--
    Convert a string to title-case or, if the string is a known
    abbreviation, convert it to upper-case. For example, the string
    "hello" will be converted to the string "Hello", but the string
    "ldap" will be converted to "LDAP".
    
    @param value
    The string to be converted to title-case.
  -->
  <xsl:template name="to-title-case">
    <xsl:param name="value" />
    <xsl:variable name="is-abbreviation">
      <xsl:call-template name="is-abbreviation">
        <xsl:with-param name="value" select="$value" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:choose>
      <!-- Convert common abbreviations to uppercase -->
      <xsl:when test="$is-abbreviation = 'true'">
        <xsl:value-of
          select="translate($value,
                            'abcdefghijklmnopqrstuvwxyz',
                            'ABCDEFGHIJKLMNOPQRSTUVWXYZ')" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:variable name="first" select="substring($value, 1, 1)" />
        <xsl:variable name="remainder" select="substring($value, 2)" />
        <xsl:variable name="first-upper"
          select="translate($first,
                            'abcdefghijklmnopqrstuvwxyz',
                            'ABCDEFGHIJKLMNOPQRSTUVWXYZ')" />
        <xsl:value-of select="concat($first-upper, $remainder)" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--
    Convert an entity or property ID to a user friendly mixed-cased
    name. For example, the string "my-string-value" will be converted to
    the string "My String Value".
    
    @param value
    The ID string to be converted to a Java name.
  -->
  <xsl:template name="name-to-ufn">
    <xsl:param name="value" select="/.." />
    <xsl:if test="string-length($value)">
      <xsl:choose>
        <xsl:when test="contains($value, '-')">
          <xsl:variable name="head"
            select="substring-before($value, '-')" />
          <xsl:variable name="tail"
            select="substring-after($value, '-')" />
          <xsl:call-template name="to-title-case">
            <xsl:with-param name="value" select="$head" />
          </xsl:call-template>
          <xsl:value-of select="' '" />
          <xsl:call-template name="name-to-ufn">
            <xsl:with-param name="value" select="$tail" />
          </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
          <xsl:call-template name="to-title-case">
            <xsl:with-param name="value" select="$value" />
          </xsl:call-template>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
