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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.Collection;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.AttributeParser;
import org.forgerock.opendj.ldap.Attributes;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

import com.forgerock.opendj.util.Iterables;

/**
 * Unmodifiable add request implementation.
 */
final class UnmodifiableAddRequestImpl extends AbstractUnmodifiableRequest<AddRequest> implements
        AddRequest {
    private static final Function<Attribute, Attribute, Void> UNMODIFIABLE_ATTRIBUTE_FUNCTION =
            new Function<Attribute, Attribute, Void>() {

                public Attribute apply(final Attribute value, final Void p) {
                    return Attributes.unmodifiableAttribute(value);
                }

            };

    UnmodifiableAddRequestImpl(AddRequest impl) {
        super(impl);
    }

    public <R, P> R accept(ChangeRecordVisitor<R, P> v, P p) {
        return v.visitChangeRecord(p, this);
    }

    public boolean addAttribute(Attribute attribute) {
        throw new UnsupportedOperationException();
    }

    public boolean addAttribute(Attribute attribute, Collection<? super ByteString> duplicateValues) {
        throw new UnsupportedOperationException();
    }

    public AddRequest addAttribute(String attributeDescription, Object... values) {
        throw new UnsupportedOperationException();
    }

    public AddRequest clearAttributes() {
        throw new UnsupportedOperationException();
    }

    public boolean containsAttribute(Attribute attribute, Collection<? super ByteString> missingValues) {
        return impl.containsAttribute(attribute, missingValues);
    }

    public boolean containsAttribute(String attributeDescription, Object... values) {
        return impl.containsAttribute(attributeDescription, values);
    }

    public Iterable<Attribute> getAllAttributes() {
        return Iterables.unmodifiableIterable(Iterables.transformedIterable(
                impl.getAllAttributes(), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }

    public Iterable<Attribute> getAllAttributes(AttributeDescription attributeDescription) {
        return Iterables.unmodifiableIterable(Iterables.transformedIterable(impl
                .getAllAttributes(attributeDescription), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }

    public Iterable<Attribute> getAllAttributes(String attributeDescription) {
        return Iterables.unmodifiableIterable(Iterables.transformedIterable(impl
                .getAllAttributes(attributeDescription), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }

    public Attribute getAttribute(AttributeDescription attributeDescription) {
        final Attribute attribute = impl.getAttribute(attributeDescription);
        if (attribute != null) {
            return Attributes.unmodifiableAttribute(attribute);
        } else {
            return null;
        }
    }

    public Attribute getAttribute(String attributeDescription) {
        final Attribute attribute = impl.getAttribute(attributeDescription);
        if (attribute != null) {
            return Attributes.unmodifiableAttribute(attribute);
        } else {
            return null;
        }
    }

    public int getAttributeCount() {
        return impl.getAttributeCount();
    }

    public DN getName() {
        return impl.getName();
    }

    public AttributeParser parseAttribute(AttributeDescription attributeDescription) {
        return impl.parseAttribute(attributeDescription);
    }

    public AttributeParser parseAttribute(String attributeDescription) {
        return impl.parseAttribute(attributeDescription);
    }

    public boolean removeAttribute(Attribute attribute, Collection<? super ByteString> missingValues) {
        throw new UnsupportedOperationException();
    }

    public boolean removeAttribute(AttributeDescription attributeDescription) {
        throw new UnsupportedOperationException();
    }

    public AddRequest removeAttribute(String attributeDescription, Object... values) {
        throw new UnsupportedOperationException();
    }

    public boolean replaceAttribute(Attribute attribute) {
        throw new UnsupportedOperationException();
    }

    public AddRequest replaceAttribute(String attributeDescription, Object... values) {
        throw new UnsupportedOperationException();
    }

    public AddRequest setName(DN dn) {
        throw new UnsupportedOperationException();
    }

    public AddRequest setName(String dn) {
        throw new UnsupportedOperationException();
    }
}
