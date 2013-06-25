<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<!--
 ! CDDL HEADER START
 !
 ! The contents of this file are subject to the terms of the
 ! Common Development and Distribution License, Version 1.0 only
 ! (the "License").  You may not use this file except in compliance
 ! with the License.
 ! 
 ! You can obtain a copy of the license at
 ! trunk/opends/resource/legal-notices/CDDLv1_0.txt
 ! or http://forgerock.org/license/CDDLv1.0.html.
 ! See the License for the specific language governing permissions
 ! and limitations under the License.
 ! 
 ! When distributing Covered Code, include this CDDL HEADER in each
 ! file and include the License file at
 ! trunk/opends/resource/legal-notices/CDDLv1_0.txt.  If applicable,
 ! add the following below this CDDL HEADER, with the fields enclosed
 ! by brackets "[]" replaced with your own identifying information:
 !      Portions Copyright [yyyy] [name of copyright owner]
 !
 ! CDDL HEADER END
 !
 !      Copyright 2013 ForgeRock AS.
 ! -->
    <xsl:output method="xml" indent="yes"/>

    <xsl:param name="filetomerge"/>

    <xsl:template match="/">
        <xsl:element name="qa">
            <xsl:element name="functional-tests">
                <xsl:copy-of select="/qa/functional-tests/identification"/>
                <xsl:element name="results">
                    <xsl:copy-of select="/qa/functional-tests/results/*"/>
                    <xsl:copy-of select="document($filetomerge)/qa/functional-tests/results/*"/>
                </xsl:element>
            </xsl:element>
        </xsl:element>

    </xsl:template>
</xsl:stylesheet>

