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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.Collection;
import java.util.Iterator;

import com.forgerock.opendj.util.Iterables;
import com.forgerock.opendj.util.Predicate;
import org.forgerock.util.Reject;

/**
 * This class provides a skeletal implementation of the {@code Entry} interface,
 * to minimize the effort required to implement this interface.
 */
public abstract class AbstractEntry implements Entry {

    /** Predicate used for findAttributes. */
    private static final Predicate<Attribute, AttributeDescription> FIND_ATTRIBUTES_PREDICATE =
            new Predicate<Attribute, AttributeDescription>() {

                @Override
                public boolean matches(final Attribute value, final AttributeDescription p) {
                    return value.getAttributeDescription().isSubTypeOf(p);
                }

            };

    /**
     * Sole constructor.
     */
    protected AbstractEntry() {
        // No implementation required.
    }

    @Override
    public boolean addAttribute(final Attribute attribute) {
        return addAttribute(attribute, null);
    }

    @Override
    public Entry addAttribute(final String attributeDescription, final Object... values) {
        addAttribute(new LinkedAttribute(attributeDescription, values), null);
        return this;
    }

    @Override
    public boolean containsAttribute(final Attribute attribute,
            final Collection<? super ByteString> missingValues) {
        final Attribute a = getAttribute(attribute.getAttributeDescription());
        if (a == null) {
            if (missingValues != null) {
                missingValues.addAll(attribute);
            }
            return false;
        } else {
            boolean result = true;
            for (final ByteString value : attribute) {
                if (!a.contains(value)) {
                    if (missingValues != null) {
                        missingValues.add(value);
                    }
                    result = false;
                }
            }
            return result;
        }
    }

    @Override
    public boolean containsAttribute(final String attributeDescription, final Object... values) {
        return containsAttribute(new LinkedAttribute(attributeDescription, values), null);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof Entry) {
            final Entry other = (Entry) object;
            if (!getName().equals(other.getName())) {
                return false;
            }
            // Distinguished name is the same, compare attributes.
            if (getAttributeCount() != other.getAttributeCount()) {
                return false;
            }
            for (final Attribute attribute : getAllAttributes()) {
                final Attribute otherAttribute =
                        other.getAttribute(attribute.getAttributeDescription());
                if (!attribute.equals(otherAttribute)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Iterable<Attribute> getAllAttributes(final AttributeDescription attributeDescription) {
        Reject.ifNull(attributeDescription);

        return Iterables.filteredIterable(getAllAttributes(), FIND_ATTRIBUTES_PREDICATE,
                attributeDescription);
    }

    @Override
    public Iterable<Attribute> getAllAttributes(final String attributeDescription) {
        return getAllAttributes(AttributeDescription.valueOf(attributeDescription));
    }

    @Override
    public Attribute getAttribute(final AttributeDescription attributeDescription) {
        for (final Attribute attribute : getAllAttributes()) {
            final AttributeDescription ad = attribute.getAttributeDescription();
            if (isAssignable(attributeDescription, ad)) {
                return attribute;
            }
        }
        return null;
    }

    @Override
    public Attribute getAttribute(final String attributeDescription) {
        return getAttribute(AttributeDescription.valueOf(attributeDescription));
    }

    @Override
    public int hashCode() {
        int hashCode = getName().hashCode();
        for (final Attribute attribute : getAllAttributes()) {
            hashCode += attribute.hashCode();
        }
        return hashCode;
    }

    @Override
    public AttributeParser parseAttribute(final AttributeDescription attributeDescription) {
        return AttributeParser.parseAttribute(getAttribute(attributeDescription));
    }

    @Override
    public AttributeParser parseAttribute(final String attributeDescription) {
        return AttributeParser.parseAttribute(getAttribute(attributeDescription));
    }

    @Override
    public boolean removeAttribute(final Attribute attribute,
            final Collection<? super ByteString> missingValues) {
        final Iterator<Attribute> i = getAllAttributes().iterator();
        final AttributeDescription attributeDescription = attribute.getAttributeDescription();
        while (i.hasNext()) {
            final Attribute oldAttribute = i.next();
            if (isAssignable(attributeDescription, oldAttribute.getAttributeDescription())) {
                if (attribute.isEmpty()) {
                    i.remove();
                    return true;
                } else {
                    final boolean modified = oldAttribute.removeAll(attribute, missingValues);
                    if (oldAttribute.isEmpty()) {
                        i.remove();
                        return true;
                    }
                    return modified;
                }
            }
        }
        // Not found.
        if (missingValues != null) {
            missingValues.addAll(attribute);
        }
        return false;
    }

    @Override
    public boolean removeAttribute(final AttributeDescription attributeDescription) {
        return removeAttribute(Attributes.emptyAttribute(attributeDescription), null);
    }

    @Override
    public Entry removeAttribute(final String attributeDescription, final Object... values) {
        removeAttribute(new LinkedAttribute(attributeDescription, values), null);
        return this;
    }

    @Override
    public boolean replaceAttribute(final Attribute attribute) {
        if (attribute.isEmpty()) {
            return removeAttribute(attribute.getAttributeDescription());
        } else {
            /*
             * For consistency with addAttribute and removeAttribute, preserve
             * the existing attribute if it already exists.
             */
            final Attribute oldAttribute = getAttribute(attribute.getAttributeDescription());
            if (oldAttribute != null) {
                oldAttribute.clear();
                oldAttribute.addAll(attribute);
            } else {
                addAttribute(attribute, null);
            }
            return true;
        }
    }

    @Override
    public Entry replaceAttribute(final String attributeDescription, final Object... values) {
        replaceAttribute(new LinkedAttribute(attributeDescription, values));
        return this;
    }

    @Override
    public Entry setName(final String dn) {
        return setName(DN.valueOf(dn));
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append('"');
        builder.append(getName());
        builder.append("\":{");
        boolean firstValue = true;
        for (final Attribute attribute : getAllAttributes()) {
            if (!firstValue) {
                builder.append(',');
            }
            builder.append(attribute);
            firstValue = false;
        }
        builder.append('}');
        return builder.toString();
    }

    private boolean isAssignable(final AttributeDescription from, final AttributeDescription to) {
        if (!from.isPlaceHolder()) {
            return from.equals(to);
        } else {
            return from.matches(to);
        }
    }

}
