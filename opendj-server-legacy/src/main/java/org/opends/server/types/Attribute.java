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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;

/**
 * This class defines a data structure for storing and interacting
 * with an attribute that may be used in the Directory Server.
 * <p>
 * Attributes are immutable and therefore any attempts to modify them
 * will result in an {@link UnsupportedOperationException}.
 * <p>
 * There are two types of attribute: real attributes and virtual attributes.
 * Real attributes can be created using the {@link AttributeBuilder} class
 * or by using the various static factory methods in the {@link Attributes} class,
 * whereas virtual attributes are represented using the {@link VirtualAttribute} class.
 * New attribute implementations can be implemented by either implementing this interface
 * or by extending {@link AbstractAttribute}.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate = false,
    mayExtend = false,
    mayInvoke = true)
public interface Attribute extends Iterable<ByteString>
{
  /** Marks code that can be removed once we are switching from server's to SDK's {@code Attribute}. */
  public @interface RemoveOnceSwitchingAttributes
  {
    /** Free-form comment. */
    String comment() default "";
  }

  /**
   * Indicates whether this attribute has any value(s) that are
   * approximately equal to the provided value.
   *
   * @param assertionValue
   *          The assertion value for which to make the determination.
   * @return {@link ConditionResult#UNDEFINED} if this attribute does not have
   *         an approximate matching rule, {@link ConditionResult#TRUE} if at
   *         least one value is approximately equal to the provided
   *         value, or {@link ConditionResult#FALSE} otherwise.
   */
  ConditionResult approximatelyEqualTo(ByteString assertionValue);

  /**
   * Indicates whether this attribute contains the specified value.
   *
   * @param value
   *          The value for which to make the determination.
   * @return {@code true} if this attribute has the specified
   *         value, or {@code false} if not.
   */
  boolean contains(ByteString value);

  /**
   * Indicates whether this attribute contains all the values in the
   * collection.
   *
   * @param values
   *          The set of values for which to make the determination.
   * @return {@code true} if this attribute contains all the
   *         values in the provided collection, or {@code false}
   *         if it does not contain at least one of them.
   */
  boolean containsAll(Collection<?> values);

  /**
   * Indicates whether this attribute matches the specified assertion value.
   *
   * @param assertionValue
   *          The assertion value for which to make the determination.
   * @return {@code true} if this attribute matches the specified assertion
   *         value, or {@code false} if not.
   */
  ConditionResult matchesEqualityAssertion(ByteString assertionValue);

  /**
   * Indicates whether the provided object is an attribute that is
   * equal to this attribute. It will be considered equal if the
   * attribute type, set of values, and set of options are equal.
   *
   * @param o
   *          The object for which to make the determination.
   * @return {@code true} if the provided object is an
   *         attribute that is equal to this attribute, or
   *         {@code false} if not.
   */
  @Override
  boolean equals(Object o);

  /**
   * Retrieves the attribute description for this attribute.
   *
   * @return The attribute description for this attribute.
   */
  AttributeDescription getAttributeDescription();

  /**
   * Indicates whether this attribute has any value(s) that are
   * greater than or equal to the provided value.
   *
   * @param assertionValue
   *          The assertion value for which to make the determination.
   * @return {@link ConditionResult#UNDEFINED} if this attribute does not have
   *         an ordering matching rule, {@link ConditionResult#TRUE} if at
   *         least one value is greater than or equal to the provided
   *         assertion value, or {@link ConditionResult#FALSE} otherwise.
   */
  ConditionResult greaterThanOrEqualTo(ByteString assertionValue);

  /**
   * Retrieves the hash code for this attribute. It will be calculated as the sum of the hash code
   * for the attribute type and all values.
   *
   * @return The hash code for this attribute.
   */
  @Override
  int hashCode();

  /**
   * Returns {@code true} if this attribute contains no
   * attribute values.
   *
   * @return {@code true} if this attribute contains no
   *         attribute values.
   */
  boolean isEmpty();

  /**
   * Indicates whether this is a real attribute (persisted) rather than a virtual attribute
   * (dynamically computed).
   *
   * @return {@code true} if this is a real attribute.
   */
  boolean isReal();

  /**
   * Indicates whether this is a virtual attribute (dynamically computed) rather than a real
   * attribute (persisted).
   *
   * @return {@code true} if this is a virtual attribute.
   */
  boolean isVirtual();

  /**
   * Returns an iterator over the attribute values in this attribute.
   * The attribute values are returned in the order in which they were
   * added this attribute. The returned iterator does not support
   * attribute value removals via {@link Iterator#remove()}.
   *
   * @return An iterator over the attribute values in this attribute.
   */
  @Override
  Iterator<ByteString> iterator();

  /**
   * Indicates whether this attribute has any value(s) that are less
   * than or equal to the provided value.
   *
   * @param assertionValue
   *          The assertion value for which to make the determination.
   * @return {@link ConditionResult#UNDEFINED} if this attribute does not have
   *         an ordering matching rule, {@link ConditionResult#TRUE} if at
   *         least one value is less than or equal to the provided
   *         assertion value, or {@link ConditionResult#FALSE} otherwise.
   */
  ConditionResult lessThanOrEqualTo(ByteString assertionValue);

  /**
   * Indicates whether this attribute has any value(s) that match the
   * provided substring.
   *
   * @param subInitial
   *          The subInitial component to use in the determination.
   * @param subAny
   *          The subAny components to use in the determination.
   * @param subFinal
   *          The subFinal component to use in the determination.
   * @return {@link ConditionResult#UNDEFINED} if this attribute does not have
   *         a substring matching rule, {@link ConditionResult#TRUE} if at
   *         least one value matches the provided substring, or
   *         {@link ConditionResult#FALSE} otherwise.
   */
  ConditionResult matchesSubstring(ByteString subInitial,
      List<ByteString> subAny, ByteString subFinal);

  /**
   * Returns the number of attribute values in this attribute.
   *
   * @return The number of attribute values in this attribute.
   */
  int size();

  /**
   * Retrieves a one-line string representation of this attribute.
   *
   * @return A one-line string representation of this attribute.
   */
  @Override
  String toString();

  /**
   * Appends a one-line string representation of this attribute to the
   * provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  void toString(StringBuilder buffer);
}
