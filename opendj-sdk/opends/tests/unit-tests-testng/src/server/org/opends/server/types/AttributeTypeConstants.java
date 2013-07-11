/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.types;

import java.util.Collections;

import org.opends.server.schema.OIDSyntax;

/**
 * Holds in-memory constants representing the AttributeTypes. This allows to not
 * start the whole server for simple unit tests.
 */
@SuppressWarnings("javadoc")
public interface AttributeTypeConstants
{

  AttributeType OBJECT_CLASS = new AttributeType(
      "( 2.5.4.0 NAME 'objectClass' EQUALITY objectIdentifierMatch "
          + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.38 X-ORIGIN 'RFC 2256' )",
      "objectClass", Collections.singletonList("objectClass"), "2.5.4.0", null,
      null, new OIDSyntax(), AttributeUsage.USER_APPLICATIONS, false, false,
      false, false);

  AttributeType[] ALL = { OBJECT_CLASS, };

}
