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
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:exsl="http://exslt.org/common">
  <!--
    This XSLT file contains utility templates which can be used for any
    generating Java code.
  -->
  <xsl:import href="text-utilities.xsl" />
  <xsl:output method="text" encoding="us-ascii" />
  <!--
    Add a copyright notice to the top of a Java source file.
    
    TODO: it would be nice to generate the copyright year automatically.
  -->
  <xsl:template name="copyright-notice">
    <xsl:value-of
      select="concat('/*&#xa;',
                     ' * CDDL HEADER START&#xa;',
                     ' *&#xa;',
                     ' * The contents of this file are subject to the terms of the&#xa;',
                     ' * Common Development and Distribution License, Version 1.0 only&#xa;',
                     ' * (the &quot;License&quot;).  You may not use this file except in compliance&#xa;',
                     ' * with the License.&#xa;',
                     ' *&#xa;',
                     ' * You can obtain a copy of the license at&#xa;',
                     ' * trunk/opends/resource/legal-notices/OpenDS.LICENSE&#xa;',
                     ' * or https://OpenDS.dev.java.net/OpenDS.LICENSE.&#xa;',
                     ' * See the License for the specific language governing permissions&#xa;',
                     ' * and limitations under the License.&#xa;',
                     ' *&#xa;',
                     ' * When distributing Covered Code, include this CDDL HEADER in each&#xa;',
                     ' * file and include the License file at&#xa;',
                     ' * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,&#xa;',
                     ' * add the following below this CDDL HEADER, with the fields enclosed&#xa;',
                     ' * by brackets &quot;[]&quot; replaced with your own identifying information:&#xa;',
                     ' *      Portions Copyright [yyyy] [name of copyright owner]&#xa;',
                     ' *&#xa;',
                     ' * CDDL HEADER END&#xa;',
                     ' *&#xa;',
                     ' *&#xa;',
                     ' *      Copyright 2008 Sun Microsystems, Inc.&#xa;',
                     ' */&#xa;')" />
  </xsl:template>
  <!--
    Convert an entity or property ID to a Java mixed-cased name.
    For example, the string "my-string-value" will be converted to
    the string "myStringValue".
    
    @param value
    The ID string to be converted to a Java name.
  -->
  <xsl:template name="name-to-java">
    <xsl:param name="value" />
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
          <xsl:call-template name="name-to-java">
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
  <!--
    Convert an entity or property ID to a Java constant name.
    For example, the string "my-string-value" will be converted to
    the string "MY_STRING_VALUE".
    
    @param value
    The ID string to be converted to a Java constant.
  -->
  <xsl:template name="name-to-java-constant">
    <xsl:param name="value" />
    <xsl:value-of
      select="translate($value,
                        'abcdefghijklmnopqrstuvwxyz-',
                        'ABCDEFGHIJKLMNOPQRSTUVWXYZ_')" />
  </xsl:template>
  <!--
    Add a Java comment. This template handles two levels of
    indentation: the indentation string for the first line, and a
    second indentation string used for subsequent lines. The template
    will output the content wrapping at the nearest word boundary to
    column 70.
    
    @param indent-text
    The indentation text used for the first line.
    
    @param indent-text2
    The indentation text used for all lines except
    the first - defaults to the value of indent-text.
    
    @param content
    The content to be output in the comment.
  -->
  <xsl:template name="add-java-comment">
    <xsl:param name="indent-text" />
    <xsl:param name="indent-text2" select="$indent-text" />
    <xsl:param name="content" />
    <xsl:call-template name="format-text">
      <xsl:with-param name="indent-text" select="$indent-text" />
      <xsl:with-param name="indent-text2" select="$indent-text2" />
      <xsl:with-param name="wrap-column" select="'70'" />
      <xsl:with-param name="content" select="$content" />
    </xsl:call-template>
  </xsl:template>
  <!--
    Add a Java comment at the specified indentation.
    
    This template handles embedded newline characters
    and will also indent individual lines according to
    the number of leading spaces they contain.
    
    @param indent
    The indentation column for the comment.
    
    @param content
    The content to be output in the comment.
  -->
  <xsl:template name="add-java-comment2">
    <xsl:param name="indent" select="/.." />
    <xsl:param name="content" select="/.." />
    <!--  Compute the indentation string. -->
    <xsl:variable name="indent-text">
      <xsl:call-template name="add-indent">
        <xsl:with-param name="indent" select="$indent + 1" />
      </xsl:call-template>
      <xsl:value-of select="'*'" />
    </xsl:variable>
    <!--  Output the comment header. -->
    <xsl:call-template name="add-indent">
      <xsl:with-param name="indent" select="$indent" />
    </xsl:call-template>
    <xsl:value-of select="'/**&#xa;'" />
    <!--  Output the comment contents. -->
    <xsl:call-template name="add-java-comment-line">
      <xsl:with-param name="indent-text" select="$indent-text" />
      <xsl:with-param name="content" select="$content" />
    </xsl:call-template>
    <!--  Output the header trailer. -->
    <xsl:value-of select="concat($indent-text, '/&#xa;')" />
  </xsl:template>
  <!-- Creates a padding string of the required length. -->
  <xsl:template name="add-indent">
    <xsl:param name="indent" select="/.." />
    <xsl:if test="$indent > 0">
      <xsl:value-of select="' '" />
      <xsl:call-template name="add-indent">
        <xsl:with-param name="indent" select="$indent - 1" />
      </xsl:call-template>
    </xsl:if>
  </xsl:template>
  <xsl:template name="add-java-comment-line">
    <xsl:param name="indent-text" select="/.." />
    <xsl:param name="content" select="/.." />
    <!--  Get the next line. -->
    <xsl:variable name="head"
      select="substring-before($content, '&#xa;')" />
    <xsl:variable name="tail"
      select="substring-after($content, '&#xa;')" />
    <!--
      Case #1 - content is empty
      Case #2 - no newline
      Case #3 - contains a new line
      Case #3.1 - begins with newline
      Case #3.2 - ends with newline
    -->
    <xsl:choose>
      <xsl:when test="string-length($content) = 0">
        <!-- Do nothing. -->
      </xsl:when>
      <xsl:when test="not(contains($content, '&#xa;'))">
        <!-- Single line of text. -->
        <xsl:call-template name="java-format-line">
          <xsl:with-param name="indent-text" select="$indent-text" />
          <xsl:with-param name="line" select="$content" />
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <!-- Output the first line and repeat for remaining lines. -->
        <xsl:call-template name="java-format-line">
          <xsl:with-param name="indent-text" select="$indent-text" />
          <xsl:with-param name="line" select="$head" />
        </xsl:call-template>
        <xsl:call-template name="add-java-comment-line">
          <xsl:with-param name="indent-text" select="$indent-text" />
          <xsl:with-param name="content" select="$tail" />
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Formats a line of comment text. -->
  <xsl:template name="java-format-line">
    <xsl:param name="indent-text" select="/.." />
    <xsl:param name="line" select="/.." />
    <!--  First count the number of leading spaces to determine the indent. -->
    <xsl:variable name="leading-spaces">
      <xsl:call-template name="java-format-line-help">
        <xsl:with-param name="line" select="$line" />
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="content"
      select="substring($line, $leading-spaces + 1)" />
    <xsl:variable name="padding1">
      <xsl:value-of select="$indent-text" />
      <xsl:call-template name="add-indent">
        <xsl:with-param name="indent" select="$leading-spaces" />
      </xsl:call-template>
    </xsl:variable>
    <!-- We need to use indent2 for certain javadoc keywords. -->
    <xsl:variable name="padding2">
      <xsl:choose>
        <xsl:when test="starts-with($content, '@return')">
          <xsl:value-of select="concat($padding1, '        ')" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$padding1" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <!-- Now output the line, wrapping as necessary. -->
    <xsl:call-template name="format-text">
      <xsl:with-param name="indent-text" select="$padding1" />
      <xsl:with-param name="indent-text2" select="$padding2" />
      <xsl:with-param name="wrap-column" select="'70'" />
      <xsl:with-param name="content" select="$content" />
    </xsl:call-template>
  </xsl:template>
  <!--  Determines the number of leading spaces in the provided string. -->
  <xsl:template name="java-format-line-help">
    <xsl:param name="line" select="/.." />
    <xsl:param name="count" select="0" />
    <xsl:choose>
      <xsl:when test="starts-with($line, ' ')">
        <xsl:call-template name="java-format-line-help">
          <xsl:with-param name="line"
            select="substring-after($line, ' ')" />
          <xsl:with-param name="count" select="$count + 1" />
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$count" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--
    Utility template for removing duplicate values from a node-set.
    
    This template is based on the version published on the XSLT site.
    It is not capable of normalizing nodes - so they must be
    pre-normalized before this template is called.
    
    @param nodes A node-set containing the duplicate nodes.
  -->
  <xsl:template name="set-distinct">
    <xsl:param name="nodes" select="/.." />
    <xsl:call-template name="_set-distinct">
      <xsl:with-param name="nodes" select="$nodes" />
    </xsl:call-template>
  </xsl:template>
  <!-- set-distinct helper template -->
  <xsl:template name="_set-distinct">
    <xsl:param name="nodes" select="/.." />
    <xsl:param name="distinct" select="/.." />
    <xsl:choose>
      <xsl:when test="$nodes">
        <xsl:variable name="value" select="$nodes[1]" />
        <xsl:choose>
          <xsl:when test="$distinct[. = $value]">
            <xsl:call-template name="_set-distinct">
              <xsl:with-param name="distinct" select="$distinct" />
              <xsl:with-param name="nodes"
                select="$nodes[position() > 1]" />
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <xsl:call-template name="_set-distinct">
              <xsl:with-param name="distinct"
                select="$distinct | $nodes[1]" />
              <xsl:with-param name="nodes"
                select="$nodes[position() > 1]" />
            </xsl:call-template>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="$distinct" mode="set-distinct" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- set-distinct helper template -->
  <xsl:template match="node()|@*" mode="set-distinct">
    <xsl:copy-of select="." />
  </xsl:template>
  <!-- 
    Generate a set of import statements.
    
    This template takes a result tree fragment as a parameter
    containing elements of the form:
    
    <import>java.net.InetAddress</import>
    <import>...</import>
    
    This template will normalize each element and remove duplicates
    before generating the output.
    
    @param imports The result tree fragment containing the import elements.
  -->
  <xsl:template name="generate-import-statements">
    <xsl:param name="imports" select="/.." />
    <!-- 
      Normalize the import elements since the set-distinct
      template cannot handle additional whitespace
    -->
    <xsl:variable name="normalized-imports">
      <xsl:for-each select="exsl:node-set($imports)/import">
        <xsl:element name="import">
          <xsl:value-of select="normalize-space()" />
        </xsl:element>
      </xsl:for-each>
    </xsl:variable>
    <!--
      Now remove the duplicates
    -->
    <xsl:variable name="unique">
      <xsl:call-template name="set-distinct">
        <xsl:with-param name="nodes"
          select="exsl:node-set($normalized-imports)/import" />
      </xsl:call-template>
    </xsl:variable>
    <!--
      Now output the import statements
    -->
    <xsl:for-each select="exsl:node-set($unique)/import">
      <xsl:sort select="normalize-space()" />
      <xsl:value-of
        select="concat('import ', normalize-space(),';&#xa;')" />
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
