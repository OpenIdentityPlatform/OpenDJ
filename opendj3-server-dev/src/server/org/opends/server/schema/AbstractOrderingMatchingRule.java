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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;


import java.util.Comparator;

import org.forgerock.opendj.ldap.*;
import org.opends.server.api.AbstractMatchingRule;
import org.opends.server.api.NotImplementedAssertion;
import org.opends.server.api.OrderingMatchingRule;

/**
 * This class defines the set of methods and structures that must be implemented
 * by a Directory Server module that implements a matching rule used for
 * ordering matching.
 */
public abstract class AbstractOrderingMatchingRule
    extends AbstractMatchingRule
    implements OrderingMatchingRule
{

  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  @Override
  public String getDescription()
  {
    // There is no standard description for this matching rule.
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getAssertion(final ByteSequence value)
      throws DecodeException
  {
    final ByteString assertionValue = normalizeAssertionValue(value);
    return new NotImplementedAssertion()
    {
      @Override
      public ConditionResult matches(ByteSequence attributeValue)
      {
        return ConditionResult.valueOf(compareValues(attributeValue, assertionValue) < 0);
      }
    };
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getGreaterOrEqualAssertion(ByteSequence value) throws DecodeException
  {
    final ByteString normAssertion = normalizeAssertionValue(value);
    return new NotImplementedAssertion()
    {
      @Override
      public ConditionResult matches(final ByteSequence normalizedAttributeValue)
      {
        return ConditionResult.valueOf(compareValues(normalizedAttributeValue, normAssertion) >= 0);
      }
    };
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getLessOrEqualAssertion(ByteSequence value) throws DecodeException
  {
    final ByteString normAssertion = normalizeAssertionValue(value);
    return new NotImplementedAssertion()
    {
      @Override
      public ConditionResult matches(final ByteSequence normalizedAttributeValue)
      {
        return ConditionResult.valueOf(compareValues(normalizedAttributeValue, normAssertion) <= 0);
      }
    };
  }

  /** {@inheritDoc} */
  @Override
  public Comparator<ByteSequence> comparator()
  {
    return new Comparator<ByteSequence>()
    {
      @Override
      public int compare(ByteSequence o1, ByteSequence o2)
      {
        return AbstractOrderingMatchingRule.this.compare(o1.toByteArray(), o2.toByteArray());
      }
    };
  }


}
