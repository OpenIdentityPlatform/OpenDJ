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
 *      Portions copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.Collection;
import java.util.Iterator;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.util.Reject;

import com.forgerock.opendj.util.Iterators;

/**
 * This class contains methods for creating and manipulating attributes.
 */
public final class Attributes {

    /**
     * Empty attribute.
     */
    private static final class EmptyAttribute extends AbstractAttribute {

        private final AttributeDescription attributeDescription;

        private EmptyAttribute(final AttributeDescription attributeDescription) {
            this.attributeDescription = attributeDescription;
        }

        @Override
        public boolean add(final ByteString value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(final Object value) {
            return false;
        }

        @Override
        public AttributeDescription getAttributeDescription() {
            return attributeDescription;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Iterator<ByteString> iterator() {
            return Iterators.emptyIterator();
        }

        @Override
        public boolean remove(final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return 0;
        }

    }

    /**
     * Renamed attribute.
     */
    private static final class RenamedAttribute implements Attribute {

        private final Attribute attribute;
        private final AttributeDescription attributeDescription;

        private RenamedAttribute(final Attribute attribute,
                final AttributeDescription attributeDescription) {
            this.attribute = attribute;
            this.attributeDescription = attributeDescription;
        }

        @Override
        public boolean add(final ByteString value) {
            return attribute.add(value);
        }

        @Override
        public boolean add(final Object... values) {
            return attribute.add(values);
        }

        @Override
        public boolean addAll(final Collection<? extends ByteString> values) {
            return attribute.addAll(values);
        }

        @Override
        public <T> boolean addAll(final Collection<T> values,
                final Collection<? super T> duplicateValues) {
            return attribute.addAll(values, duplicateValues);
        }

        @Override
        public void clear() {
            attribute.clear();
        }

        @Override
        public boolean contains(final Object value) {
            return attribute.contains(value);
        }

        @Override
        public boolean containsAll(final Collection<?> values) {
            return attribute.containsAll(values);
        }

        @Override
        public boolean equals(final Object object) {
            return AbstractAttribute.equals(this, object);
        }

        @Override
        public ByteString firstValue() {
            return attribute.firstValue();
        }

        @Override
        public String firstValueAsString() {
            return attribute.firstValueAsString();
        }

        @Override
        public AttributeDescription getAttributeDescription() {
            return attributeDescription;
        }

        @Override
        public String getAttributeDescriptionAsString() {
            return attributeDescription.toString();
        }

        @Override
        public int hashCode() {
            return AbstractAttribute.hashCode(this);
        }

        @Override
        public boolean isEmpty() {
            return attribute.isEmpty();
        }

        @Override
        public Iterator<ByteString> iterator() {
            return attribute.iterator();
        }

        @Override
        public AttributeParser parse() {
            return attribute.parse();
        }

        @Override
        public boolean remove(final Object value) {
            return attribute.remove(value);
        }

        @Override
        public boolean removeAll(final Collection<?> values) {
            return attribute.removeAll(values);
        }

        @Override
        public <T> boolean removeAll(final Collection<T> values,
                final Collection<? super T> missingValues) {
            return attribute.removeAll(values, missingValues);
        }

        @Override
        public boolean retainAll(final Collection<?> values) {
            return attribute.retainAll(values);
        }

        @Override
        public <T> boolean retainAll(final Collection<T> values,
                final Collection<? super T> missingValues) {
            return attribute.retainAll(values, missingValues);
        }

        @Override
        public int size() {
            return attribute.size();
        }

        @Override
        public ByteString[] toArray() {
            return attribute.toArray();
        }

        @Override
        public <T> T[] toArray(final T[] array) {
            return attribute.toArray(array);
        }

        @Override
        public String toString() {
            return AbstractAttribute.toString(this);
        }

    }

    /**
     * Singleton attribute.
     */
    private static final class SingletonAttribute extends AbstractAttribute {

        private final AttributeDescription attributeDescription;
        private ByteString normalizedValue;
        private final ByteString value;

        private SingletonAttribute(final AttributeDescription attributeDescription,
                final Object value) {
            this.attributeDescription = attributeDescription;
            this.value = ByteString.valueOfObject(value);
        }

        @Override
        public boolean add(final ByteString value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(final Object value) {
            final ByteString normalizedValue = normalizeValue(this, ByteString.valueOfObject(value));
            return normalizedSingleValue().equals(normalizedValue);
        }

        @Override
        public AttributeDescription getAttributeDescription() {
            return attributeDescription;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public Iterator<ByteString> iterator() {
            return Iterators.singletonIterator(value);
        }

        @Override
        public boolean remove(final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return 1;
        }

        /** Lazily computes the normalized single value. */
        private ByteString normalizedSingleValue() {
            if (normalizedValue == null) {
                normalizedValue = normalizeValue(this, value);
            }
            return normalizedValue;
        }

    }

    /**
     * Unmodifiable attribute.
     */
    private static final class UnmodifiableAttribute implements Attribute {

        private final Attribute attribute;

        private UnmodifiableAttribute(final Attribute attribute) {
            this.attribute = attribute;
        }

        @Override
        public boolean add(final ByteString value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean add(final Object... values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(final Collection<? extends ByteString> values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> boolean addAll(final Collection<T> values,
                final Collection<? super T> duplicateValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(final Object value) {
            return attribute.contains(value);
        }

        @Override
        public boolean containsAll(final Collection<?> values) {
            return attribute.containsAll(values);
        }

        @Override
        public boolean equals(final Object object) {
            return object == this || attribute.equals(object);
        }

        @Override
        public ByteString firstValue() {
            return attribute.firstValue();
        }

        @Override
        public String firstValueAsString() {
            return attribute.firstValueAsString();
        }

        @Override
        public AttributeDescription getAttributeDescription() {
            return attribute.getAttributeDescription();
        }

        @Override
        public String getAttributeDescriptionAsString() {
            return attribute.getAttributeDescriptionAsString();
        }

        @Override
        public int hashCode() {
            return attribute.hashCode();
        }

        @Override
        public boolean isEmpty() {
            return attribute.isEmpty();
        }

        @Override
        public Iterator<ByteString> iterator() {
            return Iterators.unmodifiableIterator(attribute.iterator());
        }

        @Override
        public AttributeParser parse() {
            return attribute.parse();
        }

        @Override
        public boolean remove(final Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(final Collection<?> values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> boolean removeAll(final Collection<T> values,
                final Collection<? super T> missingValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(final Collection<?> values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> boolean retainAll(final Collection<T> values,
                final Collection<? super T> missingValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return attribute.size();
        }

        @Override
        public ByteString[] toArray() {
            return attribute.toArray();
        }

        @Override
        public <T> T[] toArray(final T[] array) {
            return attribute.toArray(array);
        }

        @Override
        public String toString() {
            return attribute.toString();
        }
    }

    /**
     * Returns a read-only empty attribute having the specified attribute
     * description. Attempts to modify the returned attribute either directly,
     * or indirectly via an iterator, result in an
     * {@code UnsupportedOperationException}.
     *
     * @param attributeDescription
     *            The attribute description.
     * @return The empty attribute.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    public static Attribute emptyAttribute(final AttributeDescription attributeDescription) {
        return new EmptyAttribute(attributeDescription);
    }

    /**
     * Returns a read-only empty attribute having the specified attribute
     * description. The attribute description will be decoded using the default
     * schema. Attempts to modify the returned attribute either directly, or
     * indirectly via an iterator, result in an
     * {@code UnsupportedOperationException}.
     *
     * @param attributeDescription
     *            The attribute description.
     * @return The empty attribute.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the default schema.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    public static Attribute emptyAttribute(final String attributeDescription) {
        return emptyAttribute(AttributeDescription.valueOf(attributeDescription));
    }

    /**
     * Returns a view of {@code attribute} having a different attribute
     * description. All operations on the returned attribute "pass-through" to
     * the underlying attribute.
     *
     * @param attribute
     *            The attribute to be renamed.
     * @param attributeDescription
     *            The new attribute description for {@code attribute}.
     * @return A renamed view of {@code attribute}.
     * @throws NullPointerException
     *             If {@code attribute} or {@code attributeDescription} was
     *             {@code null}.
     */
    public static Attribute renameAttribute(final Attribute attribute,
            final AttributeDescription attributeDescription) {
        Reject.ifNull(attribute, attributeDescription);

        // Optimize for the case where no renaming is required.
        if (attribute.getAttributeDescription() == attributeDescription) {
            return attribute;
        } else {
            return new RenamedAttribute(attribute, attributeDescription);
        }
    }

    /**
     * Returns a view of {@code attribute} having a different attribute
     * description. All operations on the returned attribute "pass-through" to
     * the underlying attribute. The attribute description will be decoded using
     * the default schema.
     *
     * @param attribute
     *            The attribute to be renamed.
     * @param attributeDescription
     *            The new attribute description for {@code attribute}.
     * @return A renamed view of {@code attribute}.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the default schema.
     * @throws NullPointerException
     *             If {@code attribute} or {@code attributeDescription} was
     *             {@code null}.
     */
    public static Attribute renameAttribute(final Attribute attribute, final String attributeDescription) {
        Reject.ifNull(attribute, attributeDescription);
        return renameAttribute(attribute, AttributeDescription.valueOf(attributeDescription));
    }

    /**
     * Returns a read-only single-valued attribute having the specified
     * attribute description and value. Attempts to modify the returned
     * attribute either directly, or indirectly via an iterator, result in an
     * {@code UnsupportedOperationException}.
     * <p>
     * If {@code value} is not an instance of {@code ByteString} then it will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param value
     *            The single attribute value.
     * @return The single-valued attribute.
     * @throws NullPointerException
     *             If {@code attributeDescription} or {@code value} was
     *             {@code null}.
     */
    public static Attribute singletonAttribute(final AttributeDescription attributeDescription, final Object value) {
        return new SingletonAttribute(attributeDescription, value);
    }

    /**
     * Returns a read-only single-valued attribute having the specified
     * attribute description. The attribute description will be decoded using
     * the default schema. Attempts to modify the returned attribute either
     * directly, or indirectly via an iterator, result in an
     * {@code UnsupportedOperationException}.
     * <p>
     * If {@code value} is not an instance of {@code ByteString} then it will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param value
     *            The single attribute value.
     * @return The single-valued attribute.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the default schema.
     * @throws NullPointerException
     *             If {@code attributeDescription} or {@code value} was
     *             {@code null}.
     */
    public static Attribute singletonAttribute(final String attributeDescription, final Object value) {
        return singletonAttribute(AttributeDescription.valueOf(attributeDescription), value);
    }

    /**
     * Returns a read-only view of {@code attribute}. Query operations on the
     * returned attribute "read-through" to the underlying attribute, and
     * attempts to modify the returned attribute either directly or indirectly
     * via an iterator result in an {@code UnsupportedOperationException}.
     *
     * @param attribute
     *            The attribute for which a read-only view is to be returned.
     * @return A read-only view of {@code attribute}.
     * @throws NullPointerException
     *             If {@code attribute} was {@code null}.
     */
    public static Attribute unmodifiableAttribute(final Attribute attribute) {
        if (attribute instanceof UnmodifiableAttribute) {
            return attribute;
        }
        return new UnmodifiableAttribute(attribute);
    }

    /** Prevent instantiation. */
    private Attributes() {
        // Nothing to do.
    }
}
