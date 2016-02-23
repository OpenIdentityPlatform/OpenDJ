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
 * Portions copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class defines the uuidOrderingMatch matching rule defined in RFC 4530.
 * This will be the default ordering matching rule for the UUID syntax.
 */
final class UUIDOrderingMatchingRuleImpl extends AbstractOrderingMatchingRuleImpl {
    UUIDOrderingMatchingRuleImpl() {
        // Don't re-use equality matching rule index because it is hash based.
        super(OMR_UUID_NAME);
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) throws DecodeException {
        return UUIDEqualityMatchingRuleImpl.normalize(value).toByteString();
    }

    @Override
    public String keyToHumanReadableString(ByteSequence key) {
        return key.toString();
    }
}
