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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
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
import org.opends.server.types.DN;

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