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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * An attribute, comprising of an attribute description and zero or more
 * attribute values.
 * <p>
 * Any methods which perform comparisons between attribute values use the
 * equality matching rule associated with the attribute description.
 * <p>
 * Any methods which accept {@code Object} based attribute values convert the
 * attribute values to instances of {@code ByteString} using
 * {@link ByteString#valueOfObject(Object)}.
 */
public interface Attribute extends Set<ByteString> {
    // TODO: matching against attribute value assertions.

    /**
     * Adds {@code value} to this attribute if it is not already present
     * (optional operation). If this attribute already contains {@code value},
     * the call leaves the attribute unchanged and returns {@code false}.
     *
     * @param value
     *            The attribute value to be added to this attribute.
     * @return {@code true} if this attribute changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this attribute does not support addition of attribute
     *             values.
     * @throws NullPointerException
     *             If {@code value} was {@code null}.
     */
    boolean add(ByteString value);

    /**
     * Adds all of the provided attribute values to this attribute if they are
     * not already present (optional operation).
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param values
     *            The attribute values to be added to this attribute.
     * @return {@code true} if this attribute changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this attribute does not support addition of attribute
     *             values.
     * @throws NullPointerException
     *             If {@code values} was {@code null}.
     */
    boolean add(Object... values);

    /**
     * Adds all of the attribute values contained in {@code values} to this
     * attribute if they are not already present (optional operation).
     * <p>
     * An invocation of this method is equivalent to:
     *
     * <pre>
     * attribute.addAll(values, null);
     * </pre>
     *
     * @param values
     *            The attribute values to be added to this attribute.
     * @return {@code true} if this attribute changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this attribute does not support addition of attribute
     *             values.
     * @throws NullPointerException
     *             If {@code values} was {@code null}.
     */
    boolean addAll(Collection<? extends ByteString> values);

    /**
     * Adds all of the attribute values contained in {@code values} to this
     * attribute if they are not already present (optional operation). Any
     * attribute values which are already present will be added to
     * {@code duplicateValues} if specified.
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param <T>
     *            The type of the attribute value objects being added.
     * @param values
     *            The attribute values to be added to this attribute.
     * @param duplicateValues
     *            A collection into which duplicate values will be added, or
     *            {@code null} if duplicate values should not be saved.
     * @return {@code true} if this attribute changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this attribute does not support addition of attribute
     *             values.
     * @throws NullPointerException
     *             If {@code values} was {@code null}.
     */
    <T> boolean addAll(Collection<T> values, Collection<? super T> duplicateValues);

    /**
     * Removes all of the attribute values from this attribute (optional
     * operation). This attribute will be empty after this call returns.
     *
     * @throws UnsupportedOperationException
     *             If this attribute does not support removal of attribute
     *             values.
     */
    void clear();

    /**
     * Returns {@code true} if this attribute contains {@code value}.
     * <p>
     * If {@code value} is not an instance of {@code ByteString} then it will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param value
     *            The attribute value whose presence in this attribute is to be
     *            tested.
     * @return {@code true} if this attribute contains {@code value}, or
     *         {@code false} if not.
     * @throws NullPointerException
     *             If {@code value} was {@code null}.
     */
    boolean contains(Object value);

    /**
     * Returns {@code true} if this attribute contains all of the attribute
     * values contained in {@code values}.
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param values
     *            The attribute values whose presence in this attribute is to be
     *            tested.
     * @return {@code true} if this attribute contains all of the attribute
     *         values contained in {@code values}, or {@code false} if not.
     * @throws NullPointerException
     *             If {@code values} was {@code null}.
     */
    boolean containsAll(Collection<?> values);

    /**
     * Returns {@code true} if {@code object} is an attribute which is equal to
     * this attribute. Two attributes are considered equal if their attribute
     * descriptions are equal, they both have the same number of attribute
     * values, and every attribute value contained in the first attribute is
     * also contained in the second attribute.
     *
     * @param object
     *            The object to be tested for equality with this attribute.
     * @return {@code true} if {@code object} is an attribute which is equal to
     *         this attribute, or {@code false} if not.
     */
    boolean equals(Object object);

    /**
     * Returns the first attribute value in this attribute.
     *
     * @return The first attribute value in this attribute.
     * @throws NoSuchElementException
     *             If this attribute is empty.
     */
    ByteString firstValue();

    /**
     * Returns the first attribute value in this attribute decoded as a UTF-8
     * string.
     *
     * @return The first attribute value in this attribute decoded as a UTF-8
     *         string.
     * @throws NoSuchElementException
     *             If this attribute is empty.
     */
    String firstValueAsString();

    /**
     * Returns the attribute description of this attribute, which includes its
     * attribute type and any options.
     *
     * @return The attribute description.
     */
    AttributeDescription getAttributeDescription();

    /**
     * Returns the string representation of the attribute description of this
     * attribute, which includes its attribute type and any options.
     *
     * @return The string representation of the attribute description.
     */
    String getAttributeDescriptionAsString();

    /**
     * Returns the hash code for this attribute. It will be calculated as the
     * sum of the hash codes of the attribute description and all of the
     * attribute values.
     *
     * @return The hash code for this attribute.
     */
    int hashCode();

    /**
     * Returns {@code true} if this attribute contains no attribute values.
     *
     * @return {@code true} if this attribute contains no attribute values.
     */
    boolean isEmpty();

    /**
     * Returns an iterator over the attribute values in this attribute. The
     * attribute values are returned in no particular order, unless the
     * implementation of this attribute provides such a guarantee.
     *
     * @return An iterator over the attribute values in this attribute.
     */
    Iterator<ByteString> iterator();

    /**
     * Returns a parser for this attribute which can be used for decoding values
     * as different types of object.
     *
     * @return A parser for this attribute.
     */
    AttributeParser parse();

    /**
     * Removes {@code value} from this attribute if it is present (optional
     * operation). If this attribute does not contain {@code value}, the call
     * leaves the attribute unchanged and returns {@code false}.
     * <p>
     * If {@code value} is not an instance of {@code ByteString} then it will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param value
     *            The attribute value to be removed from this attribute.
     * @return {@code true} if this attribute changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this attribute does not support removal of attribute
     *             values.
     * @throws NullPointerException
     *             If {@code value} was {@code null}.
     */
    boolean remove(Object value);

    /**
     * Removes all of the attribute values contained in {@code values} from this
     * attribute if they are present (optional operation).
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     * <p>
     * An invocation of this method is equivalent to:
     *
     * <pre>
     * attribute.removeAll(values, null);
     * </pre>
     *
     * @param values
     *            The attribute values to be removed from this attribute.
     * @return {@code true} if this attribute changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this attribute does not support removal of attribute
     *             values.
     * @throws NullPointerException
     *             If {@code values} was {@code null}.
     */
    boolean removeAll(Collection<?> values);

    /**
     * Removes all of the attribute values contained in {@code values} from this
     * attribute if they are present (optional operation). Any attribute values
     * which are not already present will be added to {@code missingValues} if
     * specified.
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param <T>
     *            The type of the attribute value objects being removed.
     * @param values
     *            The attribute values to be removed from this attribute.
     * @param missingValues
     *            A collection into which missing values will be added, or
     *            {@code null} if missing values should not be saved.
     * @return {@code true} if this attribute changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this attribute does not support removal of attribute
     *             values.
     * @throws NullPointerException
     *             If {@code values} was {@code null}.
     */
    <T> boolean removeAll(Collection<T> values, Collection<? super T> missingValues);

    /**
     * Retains only the attribute values in this attribute which are contained
     * in {@code values} (optional operation).
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     * <p>
     * An invocation of this method is equivalent to:
     *
     * <pre>
     * attribute.retainAll(values, null);
     * </pre>
     *
     * @param values
     *            The attribute values to be retained in this attribute.
     * @return {@code true} if this attribute changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this attribute does not support removal of attribute
     *             values.
     * @throws NullPointerException
     *             If {@code values} was {@code null}.
     */
    boolean retainAll(Collection<?> values);

    /**
     * Retains only the attribute values in this attribute which are contained
     * in {@code values} (optional operation). Any attribute values which are
     * not already present will be added to {@code missingValues} if specified.
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param <T>
     *            The type of the attribute value objects being retained.
     * @param values
     *            The attribute values to be retained in this attribute.
     * @param missingValues
     *            A collection into which missing values will be added, or
     *            {@code null} if missing values should not be saved.
     * @return {@code true} if this attribute changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this attribute does not support removal of attribute
     *             values.
     * @throws NullPointerException
     *             If {@code values} was {@code null}.
     */
    <T> boolean retainAll(Collection<T> values, Collection<? super T> missingValues);

    /**
     * Returns the number of attribute values in this attribute.
     *
     * @return The number of attribute values in this attribute.
     */
    int size();

    /**
     * Returns an array containing all of the attribute values contained in this
     * attribute.
     * <p>
     * If this attribute makes any guarantees as to what order its attribute
     * values are returned by its iterator, this method must return the
     * attribute values in the same order.
     * <p>
     * The returned array will be "safe" in that no references to it are
     * maintained by this attribute. The caller is thus free to modify the
     * returned array.
     *
     * @return An array containing all of the attribute values contained in this
     *         attribute.
     */
    ByteString[] toArray();

    /**
     * Returns an array containing all of the attribute values in this
     * attribute; the runtime type of the returned array is that of the
     * specified array.
     * <p>
     * If the set fits in the specified array, it is returned therein.
     * Otherwise, a new array is allocated with the runtime type of the
     * specified array and the size of this attribute. If this attribute fits in
     * the specified array with room to spare (i.e., the array has more elements
     * than this attribute), the elements in the array immediately following the
     * end of the set is set to {@code null}.
     * <p>
     * If this attribute makes any guarantees as to what order its attribute
     * values are returned by its iterator, this method must return the
     * attribute values in the same order.
     *
     * @param <T>
     *            The type of elements contained in {@code array}.
     * @param array
     *            An array into which the elements of this attribute should be
     *            put.
     * @return An array containing all of the attribute values contained in this
     *         attribute.
     * @throws ArrayStoreException
     *             If the runtime type of {@code array} is not a supertype of
     *             {@code ByteString}.
     * @throws NullPointerException
     *             If {@code array} was {@code null}.
     */
    <T> T[] toArray(T[] array);

    /**
     * Returns a string representation of this attribute.
     *
     * @return The string representation of this attribute.
     */
    String toString();
}
