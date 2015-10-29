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

import org.forgerock.i18n.LocalizedIllegalArgumentException;

/**
 * An entry, comprising of a distinguished name and zero or more attributes.
 * <p>
 * Some methods require a schema in order to decode their parameters (e.g.
 * {@link #addAttribute(String, Object...)} and {@link #setName(String)}). In
 * these cases the default schema is used unless an alternative schema is
 * specified in the {@code Entry} constructor. The default schema is not used
 * for any other purpose. In particular, an {@code Entry} may contain attributes
 * which have been decoded using different schemas.
 * <p>
 * When determining whether or not an entry already contains a particular
 * attribute, attribute descriptions will be compared using
 * {@link AttributeDescription#matches}.
 * <p>
 * Full LDAP modify semantics are provided via the {@link #addAttribute},
 * {@link #removeAttribute}, and {@link #replaceAttribute} methods.
 * <p>
 * Implementations should specify any constraints or special behavior.
 * Specifically:
 * <ul>
 * <li>Which methods are supported.
 * <li>The order in which attributes are returned using the
 * {@link #getAllAttributes()} method.
 * <li>How existing attributes are modified during calls to
 * {@link #addAttribute}, {@link #removeAttribute}, and
 * {@link #replaceAttribute} and the conditions, if any, where a reference to
 * the passed in attribute is maintained.
 * </ul>
 *
 * @see Entries
 */
public interface Entry {

    /**
     * Ensures that this entry contains the provided attribute and values
     * (optional operation). This method has the following semantics:
     * <ul>
     * <li>If this entry does not already contain an attribute with a
     * {@link AttributeDescription#matches matching} attribute description, then
     * this entry will be modified such that it contains {@code attribute}, even
     * if it is empty.
     * <li>If this entry already contains an attribute with a
     * {@link AttributeDescription#matches matching} attribute description, then
     * the attribute values contained in {@code attribute} will be merged with
     * the existing attribute values.
     * </ul>
     * <p>
     * <b>NOTE:</b> When {@code attribute} is non-empty, this method implements
     * LDAP Modify add semantics.
     *
     * @param attribute
     *            The attribute values to be added to this entry, merging with
     *            any existing attribute values.
     * @return {@code true} if this entry changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this entry does not permit attributes or their values to
     *             be added.
     * @throws NullPointerException
     *             If {@code attribute} was {@code null}.
     */
    boolean addAttribute(Attribute attribute);

    /**
     * Ensures that this entry contains the provided attribute and values
     * (optional operation). This method has the following semantics:
     * <ul>
     * <li>If this entry does not already contain an attribute with a
     * {@link AttributeDescription#matches matching} attribute description, then
     * this entry will be modified such that it contains {@code attribute}, even
     * if it is empty.
     * <li>If this entry already contains an attribute with a
     * {@link AttributeDescription#matches matching} attribute description, then
     * the attribute values contained in {@code attribute} will be merged with
     * the existing attribute values.
     * </ul>
     * <p>
     * <b>NOTE:</b> When {@code attribute} is non-empty, this method implements
     * LDAP Modify add semantics.
     *
     * @param attribute
     *            The attribute values to be added to this entry, merging with
     *            any existing attribute values.
     * @param duplicateValues
     *            A collection into which duplicate values will be added, or
     *            {@code null} if duplicate values should not be saved.
     * @return {@code true} if this entry changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this entry does not permit attributes or their values to
     *             be added.
     * @throws NullPointerException
     *             If {@code attribute} was {@code null}.
     */
    boolean addAttribute(Attribute attribute, Collection<? super ByteString> duplicateValues);

    /**
     * Ensures that this entry contains the provided attribute and values
     * (optional operation). This method has the following semantics:
     * <ul>
     * <li>If this entry does not already contain an attribute with a
     * {@link AttributeDescription#matches matching} attribute description, then
     * this entry will be modified such that it contains {@code attribute}, even
     * if it is empty.
     * <li>If this entry already contains an attribute with a
     * {@link AttributeDescription#matches matching} attribute description, then
     * the attribute values contained in {@code attribute} will be merged with
     * the existing attribute values.
     * </ul>
     * <p>
     * The attribute description will be decoded using the schema associated
     * with this entry (usually the default schema).
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     * <p>
     * <b>NOTE:</b> When {@code attribute} is non-empty, this method implements
     * LDAP Modify add semantics.
     *
     * @param attributeDescription
     *            The name of the attribute whose values are to be added.
     * @param values
     *            The attribute values to be added to this entry, merging any
     *            existing attribute values.
     * @return This entry.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the schema associated with this entry.
     * @throws UnsupportedOperationException
     *             If this entry does not permit attributes or their values to
     *             be added.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    Entry addAttribute(String attributeDescription, Object... values);

    /**
     * Removes all the attributes from this entry (optional operation).
     *
     * @return This entry.
     * @throws UnsupportedOperationException
     *             If this entry does not permit attributes to be removed.
     */
    Entry clearAttributes();

    /**
     * Returns {@code true} if this entry contains all of the attribute values
     * contained in {@code attribute}. If {@code attribute} is empty then this
     * method will return {@code true} if the attribute is present in this
     * entry, regardless of how many values it contains.
     *
     * @param attribute
     *            The attribute values whose presence in this entry is to be
     *            tested.
     * @param missingValues
     *            A collection into which missing values will be added, or
     *            {@code null} if missing values should not be saved.
     * @return {@code true} if this entry contains all of the attribute values
     *         contained in {@code attribute}.
     * @throws NullPointerException
     *             If {@code attribute} was {@code null}.
     */
    boolean containsAttribute(Attribute attribute, Collection<? super ByteString> missingValues);

    /**
     * Returns {@code true} if this entry contains all of the attribute values
     * contained in {@code values}. If {@code values} is {@code null} or empty
     * then this method will return {@code true} if the attribute is present in
     * this entry, regardless of how many values it contains.
     * <p>
     * The attribute description will be decoded using the schema associated
     * with this entry (usually the default schema).
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeDescription
     *            The name of the attribute whose presence in this entry is to
     *            be tested.
     * @param values
     *            The attribute values whose presence in this entry is to be
     *            tested, which may be {@code null}.
     * @return {@code true} if this entry contains all of the attribute values
     *         contained in {@code values}.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the schema associated with this entry.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    boolean containsAttribute(String attributeDescription, Object... values);

    /**
     * Returns {@code true} if {@code object} is an entry which is equal to this
     * entry. Two entries are considered equal if their distinguished names are
     * equal, they both have the same number of attributes, and every attribute
     * contained in the first entry is also contained in the second entry.
     *
     * @param object
     *            The object to be tested for equality with this entry.
     * @return {@code true} if {@code object} is an entry which is equal to this
     *         entry, or {@code false} if not.
     */
    boolean equals(Object object);

    /**
     * Returns an {@code Iterable} containing all of the attributes in this
     * entry. The returned {@code Iterable} may be used to remove attributes if
     * permitted by this entry.
     *
     * @return An {@code Iterable} containing all of the attributes.
     */
    Iterable<Attribute> getAllAttributes();

    /**
     * Returns an {@code Iterable} containing all the attributes in this entry
     * having an attribute description which is a sub-type of the provided
     * attribute description. The returned {@code Iterable} may be used to
     * remove attributes if permitted by this entry.
     *
     * @param attributeDescription
     *            The name of the attributes to be returned.
     * @return An {@code Iterable} containing the matching attributes.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    Iterable<Attribute> getAllAttributes(AttributeDescription attributeDescription);

    /**
     * Returns an {@code Iterable} containing all the attributes in this entry
     * having an attribute description which is a sub-type of the provided
     * attribute description. The returned {@code Iterable} may be used to
     * remove attributes if permitted by this entry.
     * <p>
     * The attribute description will be decoded using the schema associated
     * with this entry (usually the default schema).
     *
     * @param attributeDescription
     *            The name of the attributes to be returned.
     * @return An {@code Iterable} containing the matching attributes.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the schema associated with this entry.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    Iterable<Attribute> getAllAttributes(String attributeDescription);

    /**
     * Returns the named attribute contained in this entry, or {@code null} if
     * it is not included with this entry.
     *
     * @param attributeDescription
     *            The name of the attribute to be returned.
     * @return The named attribute, or {@code null} if it is not included with
     *         this entry.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    Attribute getAttribute(AttributeDescription attributeDescription);

    /**
     * Returns the named attribute contained in this entry, or {@code null} if
     * it is not included with this entry.
     * <p>
     * The attribute description will be decoded using the schema associated
     * with this entry (usually the default schema).
     *
     * @param attributeDescription
     *            The name of the attribute to be returned.
     * @return The named attribute, or {@code null} if it is not included with
     *         this entry.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the schema associated with this entry.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    Attribute getAttribute(String attributeDescription);

    /**
     * Returns the number of attributes in this entry.
     *
     * @return The number of attributes.
     */
    int getAttributeCount();

    /**
     * Returns the string representation of the distinguished name of this
     * entry.
     *
     * @return The string representation of the distinguished name.
     */
    DN getName();

    /**
     * Returns the hash code for this entry. It will be calculated as the sum of
     * the hash codes of the distinguished name and all of the attributes.
     *
     * @return The hash code for this entry.
     */
    int hashCode();

    /**
     * Returns a parser for the named attribute contained in this entry.
     *
     * @param attributeDescription
     *            The name of the attribute to be parsed.
     * @return A parser for the named attribute.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    AttributeParser parseAttribute(AttributeDescription attributeDescription);

    /**
     * Returns a parser for the named attribute contained in this entry.
     * <p>
     * The attribute description will be decoded using the schema associated
     * with this entry (usually the default schema).
     *
     * @param attributeDescription
     *            The name of the attribute to be parsed.
     * @return A parser for the named attribute.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the schema associated with this entry.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    AttributeParser parseAttribute(String attributeDescription);

    /**
     * Removes all of the attribute values contained in {@code attribute} from
     * this entry if it is present (optional operation). If {@code attribute} is
     * empty then the entire attribute will be removed if it is present.
     * <p>
     * <b>NOTE:</b> This method implements LDAP Modify delete semantics.
     *
     * @param attribute
     *            The attribute values to be removed from this entry, which may
     *            be empty if the entire attribute is to be removed.
     * @param missingValues
     *            A collection into which missing values will be added, or
     *            {@code null} if missing values should not be saved.
     * @return {@code true} if this entry changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this entry does not permit attributes or their values to
     *             be removed.
     * @throws NullPointerException
     *             If {@code attribute} was {@code null}.
     */
    boolean removeAttribute(Attribute attribute, Collection<? super ByteString> missingValues);

    /**
     * Removes the named attribute from this entry if it is present (optional
     * operation). If this attribute does not contain the attribute, the call
     * leaves this entry unchanged and returns {@code false}.
     *
     * @param attributeDescription
     *            The name of the attribute to be removed.
     * @return {@code true} if this entry changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this entry does not permit attributes to be removed.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    boolean removeAttribute(AttributeDescription attributeDescription);

    /**
     * Removes all of the attribute values contained in {@code values} from the
     * named attribute in this entry if it is present (optional operation). If
     * {@code values} is {@code null} or empty then the entire attribute will be
     * removed if it is present.
     * <p>
     * The attribute description will be decoded using the schema associated
     * with this entry (usually the default schema).
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     * <p>
     * <b>NOTE:</b> This method implements LDAP Modify delete semantics.
     *
     * @param attributeDescription
     *            The name of the attribute whose values are to be removed.
     * @param values
     *            The attribute values to be removed from this entry, which may
     *            be {@code null} or empty if the entire attribute is to be
     *            removed.
     * @return This entry.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the schema associated with this entry.
     * @throws UnsupportedOperationException
     *             If this entry does not permit attributes or their values to
     *             be removed.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    Entry removeAttribute(String attributeDescription, Object... values);

    /**
     * Adds all of the attribute values contained in {@code attribute} to this
     * entry, replacing any existing attribute values (optional operation). If
     * {@code attribute} is empty then the entire attribute will be removed if
     * it is present.
     * <p>
     * <b>NOTE:</b> This method implements LDAP Modify replace semantics as
     * described in <a href="http://tools.ietf.org/html/rfc4511#section-4.6"
     * >RFC 4511 - Section 4.6. Modify Operation</a>.
     *
     * @param attribute
     *            The attribute values to be added to this entry, replacing any
     *            existing attribute values, and which may be empty if the
     *            entire attribute is to be removed.
     * @return {@code true} if this entry changed as a result of this call.
     * @throws UnsupportedOperationException
     *             If this entry does not permit attributes or their values to
     *             be replaced.
     * @throws NullPointerException
     *             If {@code attribute} was {@code null}.
     */
    boolean replaceAttribute(Attribute attribute);

    /**
     * Adds all of the attribute values contained in {@code values} to this
     * entry, replacing any existing attribute values (optional operation). If
     * {@code values} is {@code null} or empty then the entire attribute will be
     * removed if it is present.
     * <p>
     * The attribute description will be decoded using the schema associated
     * with this entry (usually the default schema).
     * <p>
     * Any attribute values which are not instances of {@code ByteString} will
     * be converted using the {@link ByteString#valueOfObject(Object)} method.
     * <p>
     * <b>NOTE:</b> This method implements LDAP Modify replace semantics as
     * described in <a href="http://tools.ietf.org/html/rfc4511#section-4.6"
     * >RFC 4511 - Section 4.6. Modify Operation</a>.
     *
     * @param attributeDescription
     *            The name of the attribute whose values are to be replaced.
     * @param values
     *            The attribute values to be added to this entry, replacing any
     *            existing attribute values, and which may be {@code null} or
     *            empty if the entire attribute is to be removed.
     * @return This entry.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the schema associated with this entry.
     * @throws UnsupportedOperationException
     *             If this entry does not permit attributes or their values to
     *             be replaced.
     * @throws NullPointerException
     *             If {@code attribute} was {@code null}.
     */
    Entry replaceAttribute(String attributeDescription, Object... values);

    /**
     * Sets the distinguished name of this entry (optional operation).
     *
     * @param dn
     *            The distinguished name.
     * @return This entry.
     * @throws UnsupportedOperationException
     *             If this entry does not permit the distinguished name to be
     *             set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    Entry setName(DN dn);

    /**
     * Sets the distinguished name of this entry (optional operation).
     * <p>
     * The distinguished name will be decoded using the schema associated with
     * this entry (usually the default schema).
     *
     * @param dn
     *            The string representation of the distinguished name.
     * @return This entry.
     * @throws LocalizedIllegalArgumentException
     *             If {@code dn} could not be decoded using the schema
     *             associated with this entry.
     * @throws UnsupportedOperationException
     *             If this entry does not permit the distinguished name to be
     *             set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    Entry setName(String dn);

    /**
     * Returns a string representation of this entry.
     *
     * @return The string representation of this entry.
     */
    String toString();
}
