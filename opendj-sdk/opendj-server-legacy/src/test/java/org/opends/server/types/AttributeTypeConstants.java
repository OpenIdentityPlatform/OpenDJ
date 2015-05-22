/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.Arrays;

import org.forgerock.opendj.ldap.schema.AttributeUsage;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.schema.OIDSyntax;

/**
 * Holds in-memory constants representing the AttributeTypes. This allows to not
 * start the whole server for simple unit tests.
 */
@SuppressWarnings("javadoc")
public interface AttributeTypeConstants
{

  Syntax OID_SYNTAX = new OIDSyntax().getSDKSyntax(CoreSchema.getInstance());

  AttributeType OBJECT_CLASS = new AttributeType(
      "( 2.5.4.0 NAME 'objectClass' EQUALITY objectIdentifierMatch "
          + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.38 X-ORIGIN 'RFC 2256' )",
      "objectClass", Arrays.asList("objectClass"), "2.5.4.0",
      null, null, OID_SYNTAX, AttributeUsage.USER_APPLICATIONS,
      false, false, false, false);

  AttributeType COMMON_NAME = new AttributeType(
      "( 2.5.4.3 NAME ( 'cn' 'commonName' ) SUP name X-ORIGIN 'RFC 4519' )",
      "commonName", Arrays.asList("cn", "commonName"), "2.5.4.3",
      null, null, OID_SYNTAX, AttributeUsage.USER_APPLICATIONS,
      false, false, false, false);

  AttributeType ORGANIZATION_NAME = new AttributeType(
      "( 2.5.4.10 NAME ( 'o' 'organizationName' ) SUP name X-ORIGIN 'RFC 4519' )",
      "organizationName", Arrays.asList("o", "organizationName"), "2.5.4.10",
      null, null, OID_SYNTAX, AttributeUsage.USER_APPLICATIONS,
      false, false, false, false);

  AttributeType ORGANIZATIONAL_UNIT_NAME = new AttributeType(
      "( 2.5.4.11 NAME ( 'ou' 'organizationalUnitName' ) SUP name X-ORIGIN 'RFC 4519' )",
      "organizationalUnitName", Arrays.asList("ou", "organizationalUnitName"), "2.5.4.11",
      null, null, OID_SYNTAX, AttributeUsage.USER_APPLICATIONS,
      false, false, false, false);

  AttributeType DOMAIN_COMPONENT = new AttributeType(
      "( 0.9.2342.19200300.100.1.25 NAME ( 'dc' 'domainComponent' ) "
          + "EQUALITY caseIgnoreIA5Match SUBSTR caseIgnoreIA5SubstringsMatch"
          + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE X-ORIGIN 'RFC 4519' )",
      "domainComponent", Arrays.asList("dc", "domainComponent"), "0.9.2342.19200300.100.1.25",
      null, null, OID_SYNTAX, AttributeUsage.USER_APPLICATIONS,
      false, false, false, false);

  AttributeType[] ALL = { OBJECT_CLASS, COMMON_NAME, ORGANIZATION_NAME,
    ORGANIZATIONAL_UNIT_NAME, DOMAIN_COMPONENT, };

}
