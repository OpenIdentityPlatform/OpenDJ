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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.forgerock.i18n.LocalizedIllegalArgumentException;

import org.forgerock.util.Reject;

/**
 * An implementation of the {@code Attribute} interface with predictable
 * iteration order.
 * <p>
 * Internally, attribute values are stored in a linked list and it's this list
 * which defines the iteration ordering, which is the order in which elements
 * were inserted into the set (insertion-order). This ordering is particularly
 * useful in LDAP where clients generally appreciate having things returned in
 * the same order they were presented.
 * <p>
 * All operations are supported by this implementation.
 */
public final class LinkedAttribute extends AbstractAttribute {

    private static abstract class Impl {

        abstract boolean add(LinkedAttribute attribute, ByteString value);

        abstract void clear(LinkedAttribute attribute);

        abstract boolean contains(LinkedAttribute attribute, ByteString value);

        boolean containsAll(final LinkedAttribute attribute, final Collection<?> values) {
            // TODO: could optimize if objects is a LinkedAttribute having the
            // same equality matching rule.
            for (final Object value : values) {
                if (!contains(attribute, ByteString.valueOfObject(value))) {
                    return false;
                }
            }
            return true;
        }

        abstract ByteString firstValue(LinkedAttribute attribute);

        abstract Iterator<ByteString> iterator(LinkedAttribute attribute);

        abstract boolean remove(LinkedAttribute attribute, ByteString value);

        abstract <T> boolean retainAll(LinkedAttribute attribute, Collection<T> values,
                Collection<? super T> missingValues);

        abstract int size(LinkedAttribute attribute);
    }

    private static final class MultiValueImpl extends Impl {

        @Override
        boolean add(final LinkedAttribute attribute, final ByteString value) {
            final ByteString normalizedValue = normalizeValue(attribute, value);
            return attribute.multipleValues.put(normalizedValue, value) == null;
        }

        @Override
        void clear(final LinkedAttribute attribute) {
            attribute.multipleValues = null;
            attribute.pimpl = ZERO_VALUE_IMPL;
        }

        @Override
        boolean contains(final LinkedAttribute attribute, final ByteString value) {
            return attribute.multipleValues.containsKey(normalizeValue(attribute, value));
        }

        @Override
        ByteString firstValue(final LinkedAttribute attribute) {
            return attribute.multipleValues.values().iterator().next();
        }

        @Override
        Iterator<ByteString> iterator(final LinkedAttribute attribute) {
            return new Iterator<ByteString>() {
                private Impl expectedImpl = MULTI_VALUE_IMPL;

                private Iterator<ByteString> iterator = attribute.multipleValues.values()
                        .iterator();

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public ByteString next() {
                    if (attribute.pimpl != expectedImpl) {
                        throw new ConcurrentModificationException();
                    } else {
                        return iterator.next();
                    }
                }

                @Override
                public void remove() {
                    if (attribute.pimpl != expectedImpl) {
                        throw new ConcurrentModificationException();
                    } else {
                        iterator.remove();

                        // Resize if we have removed the second to last value.
                        if (attribute.multipleValues != null
                                && attribute.multipleValues.size() == 1) {
                            resize(attribute);
                            iterator = attribute.pimpl.iterator(attribute);
                        }

                        // Always update since we may change to single or zero
                        // value
                        // impl.
                        expectedImpl = attribute.pimpl;
                    }
                }

            };
        }

        @Override
        boolean remove(final LinkedAttribute attribute, final ByteString value) {
            final ByteString normalizedValue = normalizeValue(attribute, value);
            if (attribute.multipleValues.remove(normalizedValue) != null) {
                resize(attribute);
                return true;
            } else {
                return false;
            }
        }

        @Override
        <T> boolean retainAll(final LinkedAttribute attribute, final Collection<T> values,
                final Collection<? super T> missingValues) {
            // TODO: could optimize if objects is a LinkedAttribute having the
            // same equality matching rule.
            if (values.isEmpty()) {
                clear(attribute);
                return true;
            }

            final Map<ByteString, T> valuesToRetain = new HashMap<>(values.size());
            for (final T value : values) {
                valuesToRetain.put(normalizeValue(attribute, ByteString.valueOfObject(value)), value);
            }

            boolean modified = false;
            final Iterator<ByteString> iterator = attribute.multipleValues.keySet().iterator();
            while (iterator.hasNext()) {
                final ByteString normalizedValue = iterator.next();
                if (valuesToRetain.remove(normalizedValue) == null) {
                    modified = true;
                    iterator.remove();
                }
            }

            if (missingValues != null) {
                missingValues.addAll(valuesToRetain.values());
            }

            resize(attribute);

            return modified;
        }

        @Override
        int size(final LinkedAttribute attribute) {
            return attribute.multipleValues.size();
        }

        private void resize(final LinkedAttribute attribute) {
            // May need to resize if initial size estimate was wrong (e.g. all
            // values in added collection were the same).
            switch (attribute.multipleValues.size()) {
            case 0:
                attribute.multipleValues = null;
                attribute.pimpl = ZERO_VALUE_IMPL;
                break;
            case 1:
                final Map.Entry<ByteString, ByteString> e =
                        attribute.multipleValues.entrySet().iterator().next();
                attribute.singleValue = e.getValue();
                attribute.normalizedSingleValue = e.getKey();
                attribute.multipleValues = null;
                attribute.pimpl = SINGLE_VALUE_IMPL;
                break;
            default:
                // Nothing to do.
                break;
            }
        }
    }

    private static final class SingleValueImpl extends Impl {

        @Override
        boolean add(final LinkedAttribute attribute, final ByteString value) {
            final ByteString normalizedValue = normalizeValue(attribute, value);
            if (attribute.normalizedSingleValue().equals(normalizedValue)) {
                return false;
            }

            attribute.multipleValues = new LinkedHashMap<>(2);
            attribute.multipleValues.put(attribute.normalizedSingleValue, attribute.singleValue);
            attribute.multipleValues.put(normalizedValue, value);
            attribute.singleValue = null;
            attribute.normalizedSingleValue = null;
            attribute.pimpl = MULTI_VALUE_IMPL;

            return true;
        }

        @Override
        void clear(final LinkedAttribute attribute) {
            attribute.singleValue = null;
            attribute.normalizedSingleValue = null;
            attribute.pimpl = ZERO_VALUE_IMPL;
        }

        @Override
        boolean contains(final LinkedAttribute attribute, final ByteString value) {
            final ByteString normalizedValue = normalizeValue(attribute, value);
            return attribute.normalizedSingleValue().equals(normalizedValue);
        }

        @Override
        ByteString firstValue(final LinkedAttribute attribute) {
            if (attribute.singleValue != null) {
                return attribute.singleValue;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        Iterator<ByteString> iterator(final LinkedAttribute attribute) {
            return new Iterator<ByteString>() {
                private Impl expectedImpl = SINGLE_VALUE_IMPL;

                private boolean hasNext = true;

                @Override
                public boolean hasNext() {
                    return hasNext;
                }

                @Override
                public ByteString next() {
                    if (attribute.pimpl != expectedImpl) {
                        throw new ConcurrentModificationException();
                    } else if (hasNext) {
                        hasNext = false;
                        return attribute.singleValue;
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                @Override
                public void remove() {
                    if (attribute.pimpl != expectedImpl) {
                        throw new ConcurrentModificationException();
                    } else if (hasNext || attribute.singleValue == null) {
                        throw new IllegalStateException();
                    } else {
                        clear(attribute);
                        expectedImpl = attribute.pimpl;
                    }
                }

            };
        }

        @Override
        boolean remove(final LinkedAttribute attribute, final ByteString value) {
            if (contains(attribute, value)) {
                clear(attribute);
                return true;
            } else {
                return false;
            }
        }

        @Override
        <T> boolean retainAll(final LinkedAttribute attribute, final Collection<T> values,
                final Collection<? super T> missingValues) {
            // TODO: could optimize if objects is a LinkedAttribute having the
            // same equality matching rule.
            if (values.isEmpty()) {
                clear(attribute);
                return true;
            }

            final ByteString normalizedSingleValue = attribute.normalizedSingleValue();
            boolean retained = false;
            for (final T value : values) {
                final ByteString normalizedValue =
                        normalizeValue(attribute, ByteString.valueOfObject(value));
                if (normalizedSingleValue.equals(normalizedValue)) {
                    if (missingValues == null) {
                        // We can stop now.
                        return false;
                    }
                    retained = true;
                } else if (missingValues != null) {
                    missingValues.add(value);
                }
            }

            if (!retained) {
                clear(attribute);
                return true;
            } else {
                return false;
            }
        }

        @Override
        int size(final LinkedAttribute attribute) {
            return 1;
        }
    }

    private static final class ZeroValueImpl extends Impl {

        @Override
        boolean add(final LinkedAttribute attribute, final ByteString value) {
            attribute.singleValue = value;
            attribute.pimpl = SINGLE_VALUE_IMPL;
            return true;
        }

        @Override
        void clear(final LinkedAttribute attribute) {
            // Nothing to do.
        }

        @Override
        boolean contains(final LinkedAttribute attribute, final ByteString value) {
            return false;
        }

        @Override
        boolean containsAll(final LinkedAttribute attribute, final Collection<?> values) {
            return values.isEmpty();
        }

        @Override
        ByteString firstValue(final LinkedAttribute attribute) {
            throw new NoSuchElementException();
        }

        @Override
        Iterator<ByteString> iterator(final LinkedAttribute attribute) {
            return new Iterator<ByteString>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public ByteString next() {
                    if (attribute.pimpl != ZERO_VALUE_IMPL) {
                        throw new ConcurrentModificationException();
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                @Override
                public void remove() {
                    if (attribute.pimpl != ZERO_VALUE_IMPL) {
                        throw new ConcurrentModificationException();
                    } else {
                        throw new IllegalStateException();
                    }
                }

            };
        }

        @Override
        boolean remove(final LinkedAttribute attribute, final ByteString value) {
            return false;
        }

        @Override
        <T> boolean retainAll(final LinkedAttribute attribute, final Collection<T> values,
                final Collection<? super T> missingValues) {
            if (missingValues != null) {
                missingValues.addAll(values);
            }
            return false;
        }

        @Override
        int size(final LinkedAttribute attribute) {
            return 0;
        }

    }

    /**
     * An attribute factory which can be used to create new linked attributes.
     */
    public static final AttributeFactory FACTORY = new AttributeFactory() {
        @Override
        public Attribute newAttribute(final AttributeDescription attributeDescription) {
            return new LinkedAttribute(attributeDescription);
        }
    };

    private static final MultiValueImpl MULTI_VALUE_IMPL = new MultiValueImpl();
    private static final SingleValueImpl SINGLE_VALUE_IMPL = new SingleValueImpl();
    private static final ZeroValueImpl ZERO_VALUE_IMPL = new ZeroValueImpl();

    private final AttributeDescription attributeDescription;
    private Map<ByteString, ByteString> multipleValues;
    private ByteString normalizedSingleValue;
    private Impl pimpl = ZERO_VALUE_IMPL;
    private ByteString singleValue;

    /**
     * Creates a new attribute having the same attribute description and
     * attribute values as {@code attribute}.
     *
     * @param attribute
     *            The attribute to be copied.
     * @throws NullPointerException
     *             If {@code attribute} was {@code null}.
     */
    public LinkedAttribute(final Attribute attribute) {
        this.attributeDescription = attribute.getAttributeDescription();

        if (attribute instanceof LinkedAttribute) {
            final LinkedAttribute other = (LinkedAttribute) attribute;
            this.pimpl = other.pimpl;
            this.singleValue = other.singleValue;
            this.normalizedSingleValue = other.normalizedSingleValue;
            if (other.multipleValues != null) {
                this.multipleValues = new LinkedHashMap<>(other.multipleValues);
            }
        } else {
            addAll(attribute);
        }
    }

    /**
     * Creates a new attribute having the specified attribute description and no
     * attribute values.
     *
     * @param attributeDescription
     *            The attribute description.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    public LinkedAttribute(final AttributeDescription attributeDescription) {
        Reject.ifNull(attributeDescription);
        this.attributeDescription = attributeDescription;
    }

    /**
     * Creates a new attribute having the specified attribute description and
     * single attribute value.
     * <p>
     * If {@code value} is not an instance of {@code ByteString} then it will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param value
     *            The single attribute value.
     * @throws NullPointerException
     *             If {@code attributeDescription} or {@code value} was
     *             {@code null} .
     */
    public LinkedAttribute(final AttributeDescription attributeDescription, final Object value) {
        this(attributeDescription);
        add(value);
    }

    /**
     * Creates a new attribute having the specified attribute description and
     * attribute values.
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param values
     *            The attribute values.
     * @throws NullPointerException
     *             If {@code attributeDescription} or {@code values} was
     *             {@code null}.
     */
    public LinkedAttribute(final AttributeDescription attributeDescription,
            final Object... values) {
        this(attributeDescription);
        add(values);
    }

    /**
     * Creates a new attribute having the specified attribute description and
     * attribute values.
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param values
     *            The attribute values.
     * @throws NullPointerException
     *             If {@code attributeDescription} or {@code values} was
     *             {@code null}.
     */
    public LinkedAttribute(final AttributeDescription attributeDescription,
            final Collection<?> values) {
        this(attributeDescription);
        addAll(values, null);
    }

    /**
     * Creates a new attribute having the specified attribute description and no
     * attribute values. The attribute description will be decoded using the
     * default schema.
     *
     * @param attributeDescription
     *            The attribute description.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the default schema.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    public LinkedAttribute(final String attributeDescription) {
        this(AttributeDescription.valueOf(attributeDescription));
    }

    /**
     * Creates a new attribute having the specified attribute description and
     * attribute values. The attribute description will be decoded using the
     * default schema.
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param values
     *            The attribute values.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the default schema.
     * @throws NullPointerException
     *             If {@code attributeDescription} or {@code values} was
     *             {@code null}.
     */
    public LinkedAttribute(final String attributeDescription, final Collection<?> values) {
        this(attributeDescription);
        addAll(values, null);
    }

    /**
     * Creates a new attribute having the specified attribute description and
     * single attribute value. The attribute description will be decoded using
     * the default schema.
     * <p>
     * If {@code value} is not an instance of {@code ByteString} then it will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param value
     *            The single attribute value.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the default schema.
     * @throws NullPointerException
     *             If {@code attributeDescription} or {@code value} was
     *             {@code null} .
     */
    public LinkedAttribute(final String attributeDescription, final Object value) {
        this(attributeDescription);
        add(ByteString.valueOfObject(value));
    }

    /**
     * Creates a new attribute having the specified attribute description and
     * attribute values. The attribute description will be decoded using the
     * default schema.
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The attribute description.
     * @param values
     *            The attribute values.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the default schema.
     * @throws NullPointerException
     *             If {@code attributeDescription} or {@code values} was
     *             {@code null}.
     */
    public LinkedAttribute(final String attributeDescription, final Object... values) {
        this(attributeDescription);
        add(values);
    }

    /** {@inheritDoc} */
    @Override
    public boolean add(final ByteString value) {
        Reject.ifNull(value);
        return pimpl.add(this, value);
    }

    /** {@inheritDoc} */
    @Override
    public void clear() {
        pimpl.clear(this);
    }

    /** {@inheritDoc} */
    @Override
    public boolean contains(final Object value) {
        Reject.ifNull(value);
        return pimpl.contains(this, ByteString.valueOfObject(value));
    }

    /** {@inheritDoc} */
    @Override
    public boolean containsAll(final Collection<?> values) {
        Reject.ifNull(values);
        return pimpl.containsAll(this, values);
    }

    /** {@inheritDoc} */
    @Override
    public ByteString firstValue() {
        return pimpl.firstValue(this);
    }

    /** {@inheritDoc} */
    @Override
    public AttributeDescription getAttributeDescription() {
        return attributeDescription;
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<ByteString> iterator() {
        return pimpl.iterator(this);
    }

    /** {@inheritDoc} */
    @Override
    public boolean remove(final Object value) {
        Reject.ifNull(value);
        return pimpl.remove(this, ByteString.valueOfObject(value));
    }

    /** {@inheritDoc} */
    @Override
    public <T> boolean retainAll(final Collection<T> values,
            final Collection<? super T> missingValues) {
        Reject.ifNull(values);
        return pimpl.retainAll(this, values, missingValues);
    }

    /** {@inheritDoc} */
    @Override
    public int size() {
        return pimpl.size(this);
    }

    /** Lazily computes the normalized single value. */
    private ByteString normalizedSingleValue() {
        if (normalizedSingleValue == null) {
            normalizedSingleValue = normalizeValue(this, singleValue);
        }
        return normalizedSingleValue;
    }

}
