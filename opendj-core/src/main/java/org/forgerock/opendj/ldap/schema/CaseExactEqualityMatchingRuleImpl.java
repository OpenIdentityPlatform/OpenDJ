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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

import static com.forgerock.opendj.util.StringPrepProfile.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

/**
 * This class defines the caseExactMatch matching rule defined in X.520 and
 * referenced in RFC 4519.
 */
final class CaseExactEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    CaseExactEqualityMatchingRuleImpl() {
        super(EMR_CASE_EXACT_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) {
        return SchemaUtils.normalizeStringAttributeValue(value, TRIM, NO_CASE_FOLD);
    }

    @Override
    public String keyToHumanReadableString(ByteSequence key) {
        return key.toString();
    }
}
