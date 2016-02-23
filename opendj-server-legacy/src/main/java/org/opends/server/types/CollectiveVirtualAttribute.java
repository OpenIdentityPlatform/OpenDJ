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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.Iterator;
import java.util.List;

import org.forgerock.opendj.ldap.AttributeDescription;
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

  @Override
  public ConditionResult approximatelyEqualTo(ByteString assertionValue) {
    return attribute.approximatelyEqualTo(assertionValue);
  }

  @Override
  public boolean contains(ByteString value) {
    return attribute.contains(value);
  }

  @Override
  public ConditionResult matchesEqualityAssertion(ByteString assertionValue)
  {
    return attribute.matchesEqualityAssertion(assertionValue);
  }

  @Override
  public AttributeDescription getAttributeDescription()
  {
    return attribute.getAttributeDescription();
  }

  @Override
  public ConditionResult greaterThanOrEqualTo(ByteString assertionValue) {
    return attribute.greaterThanOrEqualTo(assertionValue);
  }

  @Override
  public boolean isVirtual() {
    return true;
  }

  @Override
  public Iterator<ByteString> iterator() {
    return attribute.iterator();
  }

  @Override
  public ConditionResult lessThanOrEqualTo(ByteString assertionValue) {
    return attribute.lessThanOrEqualTo(assertionValue);
  }

  @Override
  public ConditionResult matchesSubstring(ByteString subInitial,
          List<ByteString> subAny, ByteString subFinal) {
    return attribute.matchesSubstring(subInitial, subAny, subFinal);
  }

  @Override
  public int size() {
    return attribute.size();
  }

  @Override
  public int hashCode()
  {
    return attribute.hashCode();
  }

  @Override
  public void toString(StringBuilder buffer) {
    attribute.toString(buffer);
  }
}
