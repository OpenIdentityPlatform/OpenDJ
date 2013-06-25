/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.types;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This class defines a collective virtual attribute, which is a
 * special kind of attribute whose values do not actually exist
 * in persistent storage but rather are obtained dynamically
 * from applicable collective attribute subentry.
 */
public class CollectiveVirtualAttribute extends AbstractAttribute
{
  // The attribute this collective virtual attribute is based on.
  private Attribute attribute;

  /**
   * Creates a new collective virtual attribute.
   * @param attribute The attribute this collective
   *                  virtual attribute is based on.
   */
  public CollectiveVirtualAttribute(Attribute attribute) {
    this.attribute = attribute;
  }

  /**
   * {@inheritDoc}
   */
  public ConditionResult approximatelyEqualTo(AttributeValue value) {
    return attribute.approximatelyEqualTo(value);
  }

  /**
   * {@inheritDoc}
   */
  public boolean contains(AttributeValue value) {
    return attribute.contains(value);
  }

  /**
   * {@inheritDoc}
   */
  public AttributeType getAttributeType() {
    return attribute.getAttributeType();
  }

  /**
   * {@inheritDoc}
   */
  public Set<String> getOptions() {
    return attribute.getOptions();
  }

  /**
   * {@inheritDoc}
   */
  public ConditionResult greaterThanOrEqualTo(AttributeValue value) {
    return attribute.greaterThanOrEqualTo(value);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isVirtual() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public Iterator<AttributeValue> iterator() {
    return attribute.iterator();
  }

  /**
   * {@inheritDoc}
   */
  public ConditionResult lessThanOrEqualTo(AttributeValue value) {
    return attribute.lessThanOrEqualTo(value);
  }

  /**
   * {@inheritDoc}
   */
  public ConditionResult matchesSubstring(ByteString subInitial,
          List<ByteString> subAny, ByteString subFinal) {
    return attribute.matchesSubstring(subInitial, subAny, subFinal);
  }

  /**
   * {@inheritDoc}
   */
  public int size() {
    return attribute.size();
  }

  /**
   * {@inheritDoc}
   */
  public void toString(StringBuilder buffer) {
    attribute.toString(buffer);
  }
}
