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
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.Collection;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.AttributeParser;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

/**
 * Add request implementation.
 */
final class AddRequestImpl extends AbstractRequestImpl<AddRequest> implements AddRequest {
    private final Entry entry;

    AddRequestImpl(final AddRequest addRequest) {
        super(addRequest);
        this.entry = LinkedHashMapEntry.deepCopyOfEntry(addRequest);
    }

    AddRequestImpl(final Entry entry) {
        this.entry = entry;
    }

    @Override
    public <R, P> R accept(final ChangeRecordVisitor<R, P> v, final P p) {
        return v.visitChangeRecord(p, this);
    }

    @Override
    public boolean addAttribute(final Attribute attribute) {
        return entry.addAttribute(attribute);
    }

    @Override
    public boolean addAttribute(final Attribute attribute,
            final Collection<? super ByteString> duplicateValues) {
        return entry.addAttribute(attribute, duplicateValues);
    }

    @Override
    public AddRequest addAttribute(final String attributeDescription, final Object... values) {
        entry.addAttribute(attributeDescription, values);
        return this;
    }

    @Override
    public AddRequest clearAttributes() {
        entry.clearAttributes();
        return this;
    }

    @Override
    public boolean containsAttribute(final Attribute attribute,
            final Collection<? super ByteString> missingValues) {
        return entry.containsAttribute(attribute, missingValues);
    }

    @Override
    public boolean containsAttribute(final String attributeDescription, final Object... values) {
        return entry.containsAttribute(attributeDescription, values);
    }

    @Override
    public boolean equals(final Object object) {
        return entry.equals(object);
    }

    @Override
    public Iterable<Attribute> getAllAttributes() {
        return entry.getAllAttributes();
    }

    @Override
    public Iterable<Attribute> getAllAttributes(final AttributeDescription attributeDescription) {
        return entry.getAllAttributes(attributeDescription);
    }

    @Override
    public Iterable<Attribute> getAllAttributes(final String attributeDescription) {
        return entry.getAllAttributes(attributeDescription);
    }

    @Override
    public Attribute getAttribute(final AttributeDescription attributeDescription) {
        return entry.getAttribute(attributeDescription);
    }

    @Override
    public Attribute getAttribute(final String attributeDescription) {
        return entry.getAttribute(attributeDescription);
    }

    @Override
    public int getAttributeCount() {
        return entry.getAttributeCount();
    }

    @Override
    public DN getName() {
        return entry.getName();
    }

    @Override
    public int hashCode() {
        return entry.hashCode();
    }

    @Override
    public AttributeParser parseAttribute(final AttributeDescription attributeDescription) {
        return entry.parseAttribute(attributeDescription);
    }

    @Override
    public AttributeParser parseAttribute(final String attributeDescription) {
        return entry.parseAttribute(attributeDescription);
    }

    @Override
    public boolean removeAttribute(final Attribute attribute,
            final Collection<? super ByteString> missingValues) {
        return entry.removeAttribute(attribute, missingValues);
    }

    @Override
    public boolean removeAttribute(final AttributeDescription attributeDescription) {
        return entry.removeAttribute(attributeDescription);
    }

    @Override
    public AddRequest removeAttribute(final String attributeDescription, final Object... values) {
        entry.removeAttribute(attributeDescription, values);
        return this;
    }

    @Override
    public boolean replaceAttribute(final Attribute attribute) {
        return entry.replaceAttribute(attribute);
    }

    @Override
    public AddRequest replaceAttribute(final String attributeDescription, final Object... values) {
        entry.replaceAttribute(attributeDescription, values);
        return this;
    }

    @Override
    public AddRequest setName(final DN dn) {
        entry.setName(dn);
        return this;
    }

    @Override
    public AddRequest setName(final String dn) {
        entry.setName(dn);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AddRequest(name=");
        builder.append(getName());
        builder.append(", attributes=");
        builder.append(getAllAttributes());
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

    @Override
    AddRequest getThis() {
        return this;
    }

}
