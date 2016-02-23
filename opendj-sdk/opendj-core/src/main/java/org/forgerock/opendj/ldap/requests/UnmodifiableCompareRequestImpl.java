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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;

/**
 * Unmodifiable compare request implementation.
 */
final class UnmodifiableCompareRequestImpl extends AbstractUnmodifiableRequest<CompareRequest>
        implements CompareRequest {
    UnmodifiableCompareRequestImpl(final CompareRequest impl) {
        super(impl);
    }

    @Override
    public ByteString getAssertionValue() {
        return impl.getAssertionValue();
    }

    @Override
    public String getAssertionValueAsString() {
        return impl.getAssertionValueAsString();
    }

    @Override
    public AttributeDescription getAttributeDescription() {
        return impl.getAttributeDescription();
    }

    @Override
    public DN getName() {
        return impl.getName();
    }

    @Override
    public CompareRequest setAssertionValue(final Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompareRequest setAttributeDescription(final AttributeDescription attributeDescription) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompareRequest setAttributeDescription(final String attributeDescription) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompareRequest setName(final DN dn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompareRequest setName(final String dn) {
        throw new UnsupportedOperationException();
    }
}
