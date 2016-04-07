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
 * Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;

import org.forgerock.util.Reject;

/**
 * Compare request implementation.
 */
final class CompareRequestImpl extends AbstractRequestImpl<CompareRequest> implements
        CompareRequest {

    private ByteString assertionValue;
    private AttributeDescription attributeDescription;
    private DN name;

    CompareRequestImpl(final CompareRequest compareRequest) {
        super(compareRequest);
        this.name = compareRequest.getName();
        this.attributeDescription = compareRequest.getAttributeDescription();
        this.assertionValue = compareRequest.getAssertionValue();
    }

    CompareRequestImpl(final DN name, final AttributeDescription attributeDescription,
            final ByteString assertionValue) {
        this.name = name;
        this.attributeDescription = attributeDescription;
        this.assertionValue = assertionValue;
    }

    @Override
    public ByteString getAssertionValue() {
        return assertionValue;
    }

    @Override
    public String getAssertionValueAsString() {
        return assertionValue.toString();
    }

    @Override
    public AttributeDescription getAttributeDescription() {
        return attributeDescription;
    }

    @Override
    public DN getName() {
        return name;
    }

    @Override
    public CompareRequest setAssertionValue(final Object value) {
        Reject.ifNull(value);
        this.assertionValue = ByteString.valueOfObject(value);
        return this;
    }

    @Override
    public CompareRequest setAttributeDescription(final AttributeDescription attributeDescription) {
        Reject.ifNull(attributeDescription);
        this.attributeDescription = attributeDescription;
        return this;
    }

    @Override
    public CompareRequest setAttributeDescription(final String attributeDescription) {
        Reject.ifNull(attributeDescription);
        this.attributeDescription = AttributeDescription.valueOf(attributeDescription);
        return this;
    }

    @Override
    public CompareRequest setName(final DN dn) {
        Reject.ifNull(dn);
        this.name = dn;
        return this;
    }

    @Override
    public CompareRequest setName(final String dn) {
        Reject.ifNull(dn);
        this.name = DN.valueOf(dn);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("CompareRequest(name=");
        builder.append(getName());
        builder.append(", attributeDescription=");
        builder.append(getAttributeDescription());
        builder.append(", assertionValue=");
        builder.append(getAssertionValueAsString());
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

    @Override
    CompareRequest getThis() {
        return this;
    }

}
