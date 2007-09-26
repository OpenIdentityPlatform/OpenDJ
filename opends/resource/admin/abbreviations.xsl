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
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <!--
    This XSLT file contains a list of acronyms and abbreviations which should
    be converted to upper-case when used in applications (e.g. as Java names).
  -->
  <!--
    Determines whether or not the provided word is a known abbreviation or
    acronym.

    @param value The word.

    @return Returns the string "true" if the word is an abbreviation.
  -->
  <xsl:template name="is-abbreviation">
    <xsl:param name="value" select="/.." />
    <xsl:value-of
      select="$value = 'aci' or $value = 'ip' or $value = 'ssl'
              or $value = 'dn' or $value = 'rdn' or $value = 'jmx'
              or $value = 'smtp' or $value = 'http'  or $value = 'https'
              or $value = 'ldap' or $value = 'ldaps' or $value = 'ldif'
              or $value = 'jdbc' or $value = 'tcp' or $value = 'tls'
              or $value = 'pkcs11' or $value = 'sasl' or $value = 'gssapi'
              or $value = 'md5' or $value = 'je' or $value = 'dse'
              or $value = 'fifo' or $value = 'vlv' or $value = 'uuid'
              or $value = 'md5' or $value = 'sha1' or $value = 'sha256'
              or $value = 'sha384' or $value = 'sha512' or $value = 'tls'
              or $value = 'des' or $value = 'aes' or $value = 'rc4'
              or $value = 'db'
             "/>
  </xsl:template>
</xsl:stylesheet>
