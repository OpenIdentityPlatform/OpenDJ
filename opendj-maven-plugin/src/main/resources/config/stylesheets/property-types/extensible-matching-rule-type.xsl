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

  Copyright 2009 Sun Microsystems, Inc.
  ! -->
<xsl:stylesheet version="1.0" xmlns:adm="http://opendj.forgerock.org/admin"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <!-- 
    Templates for processing extensible matching rule type properties.
  -->
  <xsl:template match="adm:extensible-matching-rule-type" mode="java-value-imports">
    <import>org.opends.server.api.ExtensibleMatchingRule</import>
  </xsl:template>
  <xsl:template match="adm:extensible-matching-rule-type" mode="java-value-type">
    <xsl:value-of select="'ExtensibleMatchingRule'" />
  </xsl:template>
  <xsl:template match="adm:extensible-matching-rule-type"
    mode="java-definition-type">
    <xsl:value-of select="'ExtensibleMatchingRuleTypePropertyDefinition'" />
  </xsl:template>
</xsl:stylesheet>
