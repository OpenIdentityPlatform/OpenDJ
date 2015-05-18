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
 *      Portions copyright 2013-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.IntegerOrderingMatchingRuleImpl.normalizeValueAndEncode;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class defines the integerMatch matching rule defined in X.520 and
 * referenced in RFC 2252. The implementation of this matching rule is
 * intentionally aligned with the ordering matching rule so that they could
 * potentially share the same index.
 */
final class IntegerEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    IntegerEqualityMatchingRuleImpl() {
        super(EMR_INTEGER_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return normalizeValueAndEncode(value);
    }
}
