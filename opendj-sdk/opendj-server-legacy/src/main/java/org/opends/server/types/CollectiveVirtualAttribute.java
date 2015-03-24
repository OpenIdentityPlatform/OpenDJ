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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.server.types;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;

/**
 * This class defines a collective virtual attribute, which is a
 * special kind of attribute whose values do not actually exist
 * in persistent storage but rather are obtained dynamically
 * from applicable collective attribute subentry.
 */
public class CollectiveVirtualAttribute extends AbstractAttribute
{
  /** The attribute this collective virtual attribute is based on. */
  private Attribute attribute;

  /**
   * Creates a new collective virtual attribute.
   * @param attribute The attribute this collective
   *                  virtual attribute is based on.
   */
  public CollectiveVirtualAttribute(Attribute attribute) {
    this.attribute = attribute;
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult approximatelyEqualTo(ByteString assertionValue) {
    return attribute.approximatelyEqualTo(assertionValue);
  }

  /** {@inheritDoc} */
  @Override
  public boolean contains(ByteString value) {
    return attribute.contains(value);
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult matchesEqualityAssertion(ByteString assertionValue)
  {
    return attribute.matchesEqualityAssertion(assertionValue);
  }

  /** {@inheritDoc} */
  @Override
  public AttributeType getAttributeType() {
    return attribute.getAttributeType();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getOptions() {
    return attribute.getOptions();
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult greaterThanOrEqualTo(ByteString assertionValue) {
    return attribute.greaterThanOrEqualTo(assertionValue);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isVirtual() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public Iterator<ByteString> iterator() {
    return attribute.iterator();
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult lessThanOrEqualTo(ByteString assertionValue) {
    return attribute.lessThanOrEqualTo(assertionValue);
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult matchesSubstring(ByteString subInitial,
          List<ByteString> subAny, ByteString subFinal) {
    return attribute.matchesSubstring(subInitial, subAny, subFinal);
  }

  /** {@inheritDoc} */
  @Override
  public int size() {
    return attribute.size();
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
    return attribute.hashCode();
  }

  /** {@inheritDoc} */
  @Override
  public void toString(StringBuilder buffer) {
    attribute.toString(buffer);
  }
}
