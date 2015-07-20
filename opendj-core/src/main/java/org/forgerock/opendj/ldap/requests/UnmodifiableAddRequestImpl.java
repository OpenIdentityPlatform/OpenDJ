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
 *      Portions copyright 2012-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.requests;

import java.util.Collection;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.AttributeParser;
import org.forgerock.opendj.ldap.Attributes;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;

import org.forgerock.opendj.ldif.ChangeRecordVisitor;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import com.forgerock.opendj.util.Iterables;

/**
 * Unmodifiable add request implementation.
 */
final class UnmodifiableAddRequestImpl extends AbstractUnmodifiableRequest<AddRequest> implements
        AddRequest {
    private static final Function<Attribute, Attribute, NeverThrowsException> UNMODIFIABLE_ATTRIBUTE_FUNCTION =
            new Function<Attribute, Attribute, NeverThrowsException>() {
                @Override
                public Attribute apply(final Attribute value) {
                    return Attributes.unmodifiableAttribute(value);
                }
            };

    UnmodifiableAddRequestImpl(final AddRequest impl) {
        super(impl);
    }

    @Override
    public <R, P> R accept(final ChangeRecordVisitor<R, P> v, final P p) {
        return v.visitChangeRecord(p, this);
    }

    @Override
    public boolean addAttribute(final Attribute attribute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAttribute(final Attribute attribute,
            final Collection<? super ByteString> duplicateValues) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AddRequest addAttribute(final String attributeDescription, final Object... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AddRequest clearAttributes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAttribute(final Attribute attribute,
            final Collection<? super ByteString> missingValues) {
        return impl.containsAttribute(attribute, missingValues);
    }

    @Override
    public boolean containsAttribute(final String attributeDescription, final Object... values) {
        return impl.containsAttribute(attributeDescription, values);
    }

    @Override
    public Iterable<Attribute> getAllAttributes() {
        return Iterables.unmodifiableIterable(Iterables.transformedIterable(
                impl.getAllAttributes(), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }

    @Override
    public Iterable<Attribute> getAllAttributes(final AttributeDescription attributeDescription) {
        return Iterables.unmodifiableIterable(Iterables.transformedIterable(impl
                .getAllAttributes(attributeDescription), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }

    @Override
    public Iterable<Attribute> getAllAttributes(final String attributeDescription) {
        return Iterables.unmodifiableIterable(Iterables.transformedIterable(impl
                .getAllAttributes(attributeDescription), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }

    @Override
    public Attribute getAttribute(final AttributeDescription attributeDescription) {
        final Attribute attribute = impl.getAttribute(attributeDescription);
        if (attribute != null) {
            return Attributes.unmodifiableAttribute(attribute);
        } else {
            return null;
        }
    }

    @Override
    public Attribute getAttribute(final String attributeDescription) {
        final Attribute attribute = impl.getAttribute(attributeDescription);
        if (attribute != null) {
            return Attributes.unmodifiableAttribute(attribute);
        } else {
            return null;
        }
    }

    @Override
    public int getAttributeCount() {
        return impl.getAttributeCount();
    }

    @Override
    public DN getName() {
        return impl.getName();
    }

    @Override
    public AttributeParser parseAttribute(final AttributeDescription attributeDescription) {
        return impl.parseAttribute(attributeDescription);
    }

    @Override
    public AttributeParser parseAttribute(final String attributeDescription) {
        return impl.parseAttribute(attributeDescription);
    }

    @Override
    public boolean removeAttribute(final Attribute attribute,
            final Collection<? super ByteString> missingValues) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAttribute(final AttributeDescription attributeDescription) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AddRequest removeAttribute(final String attributeDescription, final Object... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replaceAttribute(final Attribute attribute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AddRequest replaceAttribute(final String attributeDescription, final Object... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AddRequest setName(final DN dn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AddRequest setName(final String dn) {
        throw new UnsupportedOperationException();
    }
}
