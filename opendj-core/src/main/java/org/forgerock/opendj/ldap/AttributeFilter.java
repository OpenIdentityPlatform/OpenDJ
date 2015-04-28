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
 *      Copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static org.forgerock.opendj.ldap.Attributes.renameAttribute;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;

import com.forgerock.opendj.util.Iterables;

/**
 * A configurable factory for filtering the attributes exposed by an entry. An
 * {@code AttributeFilter} is useful for performing fine-grained access control,
 * selecting attributes based on search request criteria, and selecting
 * attributes based on post- and pre- read request control criteria.
 * <p>
 * In cases where methods accept a string based list of attribute descriptions,
 * the following special attribute descriptions are permitted:
 * <ul>
 * <li><b>*</b> - include all user attributes
 * <li><b>+</b> - include all operational attributes
 * <li><b>1.1</b> - exclude all attributes
 * <li><b>@<i>objectclass</i></b> - include all attributes identified by the
 * named object class.
 * </ul>
 */
public final class AttributeFilter {
    // TODO: exclude specific attributes, matched values, custom predicates, etc.
    private boolean includeAllOperationalAttributes;
    /** Depends on constructor. */
    private boolean includeAllUserAttributes;
    private boolean typesOnly;

    /**
     * Use a map so that we can perform membership checks as well as recover the
     * user requested attribute description.
     */
    private Map<AttributeDescription, AttributeDescription> requestedAttributes = Collections
            .emptyMap();

    /**
     * Creates a new attribute filter which will include all user attributes but
     * no operational attributes.
     */
    public AttributeFilter() {
        includeAllUserAttributes = true;
    }

    /**
     * Creates a new attribute filter which will include the attributes
     * identified by the provided search request attribute list. Attributes will
     * be decoded using the default schema. See the class description for
     * details regarding the types of supported attribute description.
     *
     * @param attributeDescriptions
     *            The names of the attributes to be included with each entry.
     */
    public AttributeFilter(final Collection<String> attributeDescriptions) {
        this(attributeDescriptions, Schema.getDefaultSchema());
    }

    /**
     * Creates a new attribute filter which will include the attributes
     * identified by the provided search request attribute list. Attributes will
     * be decoded using the provided schema. See the class description for
     * details regarding the types of supported attribute description.
     *
     * @param attributeDescriptions
     *            The names of the attributes to be included with each entry.
     * @param schema
     *            The schema The schema to use when parsing attribute
     *            descriptions and object class names.
     */
    public AttributeFilter(final Collection<String> attributeDescriptions, final Schema schema) {
        if (attributeDescriptions == null || attributeDescriptions.isEmpty()) {
            // Fast-path for common case.
            includeAllUserAttributes = true;
        } else {
            for (final String attribute : attributeDescriptions) {
                includeAttribute(attribute, schema);
            }
        }
    }

    /**
     * Creates a new attribute filter which will include the attributes
     * identified by the provided search request attribute list. Attributes will
     * be decoded using the default schema. See the class description for
     * details regarding the types of supported attribute description.
     *
     * @param attributeDescriptions
     *            The names of the attributes to be included with each entry.
     */
    public AttributeFilter(final String... attributeDescriptions) {
        this(Arrays.asList(attributeDescriptions));
    }

    /**
     * Returns a modifiable filtered copy of the provided entry.
     *
     * @param entry
     *            The entry to be filtered and copied.
     * @return The modifiable filtered copy of the provided entry.
     */
    public Entry filteredCopyOf(final Entry entry) {
        return new LinkedHashMapEntry(filteredViewOf(entry));
    }

    /**
     * Returns an unmodifiable filtered view of the provided entry. The returned
     * entry supports all operations except those which modify the contents of
     * the entry.
     *
     * @param entry
     *            The entry to be filtered.
     * @return The unmodifiable filtered view of the provided entry.
     */
    public Entry filteredViewOf(final Entry entry) {
        return new AbstractEntry() {

            @Override
            public boolean addAttribute(final Attribute attribute,
                    final Collection<? super ByteString> duplicateValues) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Entry clearAttributes() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Iterable<Attribute> getAllAttributes() {
                /*
                 * Unfortunately we cannot efficiently re-use the iterators in
                 * {@code Iterators} because we need to transform and filter in
                 * a single step. Transformation is required in order to ensure
                 * that we return an attribute whose name is the same as the one
                 * requested by the user.
                 */
                return new Iterable<Attribute>() {
                    private boolean hasNextMustIterate = true;
                    private final Iterator<Attribute> iterator = entry.getAllAttributes().iterator();
                    private Attribute next = null;

                    @Override
                    public Iterator<Attribute> iterator() {
                        return new Iterator<Attribute>() {
                            @Override
                            public boolean hasNext() {
                                if (hasNextMustIterate) {
                                    hasNextMustIterate = false;
                                    while (iterator.hasNext()) {
                                        final Attribute attribute = iterator.next();
                                        final AttributeDescription ad = attribute.getAttributeDescription();
                                        final AttributeType at = ad.getAttributeType();
                                        final AttributeDescription requestedAd = requestedAttributes.get(ad);
                                        if (requestedAd != null) {
                                            next = renameAttribute(attribute, requestedAd);
                                            return true;
                                        } else if ((at.isOperational() && includeAllOperationalAttributes)
                                                || (!at.isOperational() && includeAllUserAttributes)) {
                                            next = attribute;
                                            return true;
                                        }
                                    }
                                    next = null;
                                    return false;
                                } else {
                                    return next != null;
                                }
                            }

                            @Override
                            public Attribute next() {
                                if (!hasNext()) {
                                    throw new NoSuchElementException();
                                }
                                hasNextMustIterate = true;
                                return filterAttribute(next);
                            }

                            @Override
                            public void remove() {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }

                    @Override
                    public String toString() {
                        return Iterables.toString(this);
                    }
                };
            }

            @Override
            public Attribute getAttribute(final AttributeDescription attributeDescription) {
                /*
                 * It is tempting to filter based on the passed in attribute
                 * description, but we may get inaccurate results due to
                 * placeholder attribute names.
                 */
                final Attribute attribute = entry.getAttribute(attributeDescription);
                if (attribute != null) {
                    final AttributeDescription ad = attribute.getAttributeDescription();
                    final AttributeType at = ad.getAttributeType();
                    final AttributeDescription requestedAd = requestedAttributes.get(ad);
                    if (requestedAd != null) {
                        return filterAttribute(renameAttribute(attribute, requestedAd));
                    } else if ((at.isOperational() && includeAllOperationalAttributes)
                            || (!at.isOperational() && includeAllUserAttributes)) {
                        return filterAttribute(attribute);
                    }
                }
                return null;
            }

            @Override
            public int getAttributeCount() {
                return Iterables.size(getAllAttributes());
            }

            @Override
            public DN getName() {
                return entry.getName();
            }

            @Override
            public Entry setName(final DN dn) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Specifies whether or not all operational attributes should be included in
     * filtered entries. By default operational attributes are not included.
     *
     * @param include
     *            {@code true} if operational attributes should be included in
     *            filtered entries.
     * @return A reference to this attribute filter.
     */
    public AttributeFilter includeAllOperationalAttributes(final boolean include) {
        this.includeAllOperationalAttributes = include;
        return this;
    }

    /**
     * Specifies whether or not all user attributes should be included in
     * filtered entries. By default user attributes are included.
     *
     * @param include
     *            {@code true} if user attributes should be included in filtered
     *            entries.
     * @return A reference to this attribute filter.
     */
    public AttributeFilter includeAllUserAttributes(final boolean include) {
        this.includeAllUserAttributes = include;
        return this;
    }

    /**
     * Specifies that the named attribute should be included in filtered
     * entries.
     *
     * @param attributeDescription
     *            The name of the attribute to be included in filtered entries.
     * @return A reference to this attribute filter.
     */
    public AttributeFilter includeAttribute(final AttributeDescription attributeDescription) {
        allocatedRequestedAttributes();
        requestedAttributes.put(attributeDescription, attributeDescription);
        return this;
    }

    /**
     * Specifies that the named attribute should be included in filtered
     * entries. The attribute will be decoded using the default schema. See the
     * class description for details regarding the types of supported attribute
     * description.
     *
     * @param attributeDescription
     *            The name of the attribute to be included in filtered entries.
     * @return A reference to this attribute filter.
     */
    public AttributeFilter includeAttribute(final String attributeDescription) {
        return includeAttribute(attributeDescription, Schema.getDefaultSchema());
    }

    /**
     * Specifies that the named attribute should be included in filtered
     * entries. The attribute will be decoded using the provided schema. See the
     * class description for details regarding the types of supported attribute
     * description.
     *
     * @param attributeDescription
     *            The name of the attribute to be included in filtered entries.
     * @param schema
     *            The schema The schema to use when parsing attribute
     *            descriptions and object class names.
     * @return A reference to this attribute filter.
     */
    public AttributeFilter includeAttribute(final String attributeDescription, final Schema schema) {
        if (attributeDescription.equals("*")) {
            includeAllUserAttributes = true;
        } else if (attributeDescription.equals("+")) {
            includeAllOperationalAttributes = true;
        } else if (attributeDescription.equals("1.1")) {
            // Ignore - by default no attributes are included.
        } else if (attributeDescription.startsWith("@") && attributeDescription.length() > 1) {
            final String objectClassName = attributeDescription.substring(1);
            final ObjectClass objectClass = schema.getObjectClass(objectClassName);
            if (objectClass != null) {
                allocatedRequestedAttributes();
                for (final AttributeType at : objectClass.getRequiredAttributes()) {
                    final AttributeDescription ad = AttributeDescription.create(at);
                    requestedAttributes.put(ad, ad);
                }
                for (final AttributeType at : objectClass.getOptionalAttributes()) {
                    final AttributeDescription ad = AttributeDescription.create(at);
                    requestedAttributes.put(ad, ad);
                }
            }
        } else {
            allocatedRequestedAttributes();
            final AttributeDescription ad =
                    AttributeDescription.valueOf(attributeDescription, schema);
            requestedAttributes.put(ad, ad);
        }
        return this;
    }

    @Override
    public String toString() {
        if (!includeAllOperationalAttributes
                && !includeAllUserAttributes
                && requestedAttributes.isEmpty()) {
            return "1.1";
        }

        final StringBuilder builder = new StringBuilder();
        if (includeAllUserAttributes) {
            builder.append('*');
        }
        if (includeAllOperationalAttributes) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append('+');
        }
        for (final AttributeDescription requestedAttribute : requestedAttributes.keySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(requestedAttribute);
        }
        return builder.toString();
    }

    /**
     * Specifies whether or not filtered attributes are to contain both
     * attribute descriptions and values, or just attribute descriptions.
     *
     * @param typesOnly
     *            {@code true} if only attribute descriptions (and not values)
     *            are to be included, or {@code false} (the default) if both
     *            attribute descriptions and values are to be included.
     * @return A reference to this attribute filter.
     */
    public AttributeFilter typesOnly(final boolean typesOnly) {
        this.typesOnly = typesOnly;
        return this;
    }

    private void allocatedRequestedAttributes() {
        if (requestedAttributes.isEmpty()) {
            requestedAttributes = new HashMap<>();
        }
    }

    private Attribute filterAttribute(final Attribute attribute) {
        return typesOnly
            ? Attributes.emptyAttribute(attribute.getAttributeDescription())
            : attribute;
    }
}
