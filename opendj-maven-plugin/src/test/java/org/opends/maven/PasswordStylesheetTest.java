/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.maven;

import static org.testng.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.testng.annotations.Test;
import org.forgerock.testng.ForgeRockTestCase;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit" })
public class PasswordStylesheetTest extends ForgeRockTestCase {
    @Test
    public void overrideIsAcceptedByDescriptorSchema() throws Exception {
        URL descriptor = getClass().getResource("/config/xml/org/forgerock/opendj/server/config/"
                + "LDAPPassThroughAuthenticationPolicyConfiguration.xml");
        String xml = new String(descriptor.openStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("<adm:password />", "<adm:password encryption-scheme=\"3DES\" />");
        URL schema = getClass().getResource("/config/stylesheets/admin.xsd");
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schema).newValidator()
                .validate(new StreamSource(new StringReader(xml)));
    }

    @Test
    public void overrideIsEmittedIntoPropertyBuilder() throws Exception {
        URL stylesheet = getClass().getResource("/config/stylesheets/property-types/password.xsl");
        String wrapper = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                + "xmlns:adm=\"http://opendj.forgerock.org/admin\">"
                + "<xsl:import href=\"" + stylesheet + "\"/>"
                + "<xsl:output method=\"text\"/><xsl:template match=\"/\">"
                + "<xsl:apply-templates select=\"adm:password\" mode=\"java-definition-ctor\"/>"
                + "</xsl:template></xsl:stylesheet>";
        String xml = "<adm:password xmlns:adm=\"http://opendj.forgerock.org/admin\" encryption-scheme=\"3DES\"/>";
        StringWriter out = new StringWriter();
        TransformerFactory.newInstance().newTransformer(new StreamSource(new StringReader(wrapper)))
                .transform(new StreamSource(new StringReader(xml)), new StreamResult(out));
        assertTrue(out.toString().contains("builder.setEncryptionScheme(\"3DES\")"));
    }
}
