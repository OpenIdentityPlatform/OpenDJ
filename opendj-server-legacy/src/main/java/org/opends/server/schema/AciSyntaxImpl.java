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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.opends.server.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SyntaxImpl;
import org.opends.server.authorization.dseecompat.Aci;
import org.opends.server.authorization.dseecompat.AciException;
import org.forgerock.opendj.ldap.DN;

/**
 * Implementation of Access control information (aci) attribute syntax.
 */
final class AciSyntaxImpl implements SyntaxImpl {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    AciSyntaxImpl() {
      // // nothing to do
    }

    @Override
    public String getApproximateMatchingRule() {
        return null;
    }

    @Override
    public String getEqualityMatchingRule() {
        return EMR_CASE_IGNORE_IA5_OID;
    }

    @Override
    public String getOrderingMatchingRule() {
        return null;
    }

    @Override
    public String getSubstringMatchingRule() {
        return SMR_CASE_IGNORE_IA5_OID;
    }

    @Override
    public String getName() {
        return SYNTAX_ACI_NAME;
    }

    @Override
    public boolean isHumanReadable() {
        return true;
    }

    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
      try
      {
        Aci.decode(value, DN.rootDN());
        return true;
      }
      catch (AciException e)
      {
        logger.traceException(e);
        logger.warn(e.getMessageObject());
        invalidReason.append(e.getMessageObject());
        return false;
      }
    }

    @Override
    public boolean isBEREncodingRequired()
    {
      return false;
    }
}
