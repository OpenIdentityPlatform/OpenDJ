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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

import static com.forgerock.opendj.util.StringPrepProfile.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

/**
 * This class implements the caseIgnoreListSubstringsMatch matching rule defined
 * in X.520 and referenced in RFC 2252.
 */
final class CaseIgnoreListSubstringMatchingRuleImpl extends AbstractSubstringMatchingRuleImpl {

    CaseIgnoreListSubstringMatchingRuleImpl() {
        super(SMR_CASE_IGNORE_LIST_NAME, EMR_CASE_IGNORE_LIST_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) {
        return SchemaUtils.normalizeStringListAttributeValue(value, TRIM, CASE_FOLD);
    }

    @Override
    ByteString normalizeSubString(final Schema schema, final ByteSequence value)
            throws DecodeException {
        // In this case, the process for normalizing a substring is the same
        // as normalizing a full value with the exception that it may
        // include an opening or trailing space.
        return SchemaUtils.normalizeStringAttributeValue(value, false, CASE_FOLD);
    }
}
