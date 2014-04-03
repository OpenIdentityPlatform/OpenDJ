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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.api;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;

/**
 * This class provides default implementation of MatchingRule. A
 * matching rule implemented by a Directory Server module must extend
 * this class.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false,
    mayExtend = true,
    mayInvoke = false)
public abstract class AbstractMatchingRule implements MatchingRule
{

  /**
   * Default implementation of assertion.
   */
  public static final class DefaultAssertion implements Assertion
  {
    /** The ID of the DB index to use with this assertion. */
    private final String indexID;
    private final ByteSequence normalizedAssertionValue;

    /**
     * Returns the equality assertion.
     *
     * @param normalizedAssertionValue
     *          The value on which the assertion is built.
     * @return the equality assertion
     */
    public static DefaultAssertion equality(final ByteSequence normalizedAssertionValue)
    {
      return new DefaultAssertion("equality", normalizedAssertionValue);
    }

    /**
     * Returns the approximate assertion.
     *
     * @param normalizedAssertionValue
     *          The value on which the assertion is built.
     * @return the approximate assertion
     */
    static DefaultAssertion approximate(final ByteSequence normalizedAssertionValue)
    {
      return new DefaultAssertion("approximate", normalizedAssertionValue);
    }

    private DefaultAssertion(final String indexID, final ByteSequence normalizedAssertionValue)
    {
      this.indexID = indexID;
      this.normalizedAssertionValue = normalizedAssertionValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConditionResult matches(final ByteSequence normalizedAttributeValue)
    {
      return ConditionResult.valueOf(normalizedAssertionValue.equals(normalizedAttributeValue));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T createIndexQuery(IndexQueryFactory<T> factory)
        throws DecodeException
    {
      return factory.createExactMatchQuery(indexID, normalizedAssertionValue);
    }
  }

  private static final Assertion UNDEFINED_ASSERTION = new Assertion()
  {
    @Override
    public ConditionResult matches(final ByteSequence normalizedAttributeValue)
    {
      return ConditionResult.UNDEFINED;
    }

    @Override
    public <T> T createIndexQuery(IndexQueryFactory<T> factory)
        throws DecodeException
    {
      // Subclassing this class will always work, albeit inefficiently.
      // This is better than throwing an exception for no good reason.
      return factory.createMatchAllQuery();
    }
  };

  /**
   * Returns the normalized form of the assertion value.
   *
   * @param value
   *            The assertion value to normalize.
   * @return the normalized value
   * @throws DecodeException
   *            If a problem occurs.
   */
  public ByteString normalizeAssertionValue(ByteSequence value)
      throws DecodeException
  {
    // Default implementation is to use attribute value normalization.
    return normalizeAttributeValue(value);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final String getNameOrOID()
  {
    Collection<String> names = getNames();
    if (names != null && !names.isEmpty())
    {
      return names.iterator().next();
    }
    return getOID();
  }

  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return The OID of the syntax with which this matching rule is
   *         associated.
   */
  public abstract String getSyntaxOID();

  /** {@inheritDoc} */
  @Override
  public Syntax getSyntax()
  {
    return Schema.getCoreSchema().getSyntax(getSyntaxOID());
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
        return valuesMatch(attributeValue, assertionValue);
      }
    };
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getGreaterOrEqualAssertion(ByteSequence value)
      throws DecodeException
  {
    return UNDEFINED_ASSERTION;
  }



  /** {@inheritDoc} */
  @Override
  public Assertion getLessOrEqualAssertion(ByteSequence value)
      throws DecodeException
  {
    return UNDEFINED_ASSERTION;
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getSubstringAssertion(ByteSequence subInitial,
      List<? extends ByteSequence> subAnyElements, ByteSequence subFinal) throws DecodeException
  {
    return UNDEFINED_ASSERTION;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isObsolete()
  {
    return false;
  }

  /**
   * Indicates whether the provided attribute value should be
   * considered a match for the given assertion value. This will only
   * be used for the purpose of extensible matching. Subclasses
   * should define more specific methods that are appropriate to the
   * matching rule type.
   *
   * @param attributeValue
   *          The attribute value in a form that has been normalized
   *          according to this matching rule.
   * @param assertionValue
   *          The assertion value in a form that has been normalized
   *          according to this matching rule.
   * @return {@code TRUE} if the attribute value should be considered
   *         a match for the provided assertion value, {@code FALSE}
   *         if it does not match, or {@code UNDEFINED} if the result
   *         is undefined.
   */
  protected ConditionResult valuesMatch(
      ByteSequence attributeValue, ByteSequence assertionValue)
  {
    //Default implementation of most rule types.
    return ConditionResult.UNDEFINED;
  }

  private static final Comparator<ByteSequence> DEFAULT_COMPARATOR =
      new Comparator<ByteSequence>()
      {
        @Override
        public int compare(final ByteSequence o1, final ByteSequence o2)
        {
          return o1.compareTo(o2);
        }
      };

  /** {@inheritDoc} */
  @Override
  public Comparator<ByteSequence> comparator()
  {
    return DEFAULT_COMPARATOR;
  }



  /**
   * Retrieves the hash code for this matching rule. It will be
   * calculated as the sum of the characters in the OID.
   *
   * @return The hash code for this matching rule.
   */
  @Override
  public final int hashCode()
  {
    int hashCode = 0;

    String oidString = getOID();
    int oidLength = oidString.length();
    for (int i = 0; i < oidLength; i++)
    {
      hashCode += oidString.charAt(i);
    }

    return hashCode;
  }



  /**
   * Indicates whether the provided object is equal to this matching
   * rule. The provided object will be considered equal to this
   * matching rule only if it is a matching rule with the same OID.
   *
   * @param o
   *          The object for which to make the determination.
   * @return {@code true} if the provided object is equal to this
   *         matching rule, or {@code false} if it is not.
   */
  @Override
  public final boolean equals(Object o)
  {
    if (o == null)
    {
      return false;
    }

    if (this == o)
    {
      return true;
    }

    if (!(o instanceof MatchingRule))
    {
      return false;
    }

    return getOID().equals(((MatchingRule) o).getOID());
  }



  /**
   * Retrieves a string representation of this matching rule in the
   * format defined in RFC 2252.
   *
   * @return A string representation of this matching rule in the
   *         format defined in RFC 2252.
   */
  @Override
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final void toString(StringBuilder buffer)
  {
    buffer.append("( ");
    buffer.append(getOID());
    buffer.append(" NAME ");
    Collection<String> names = getNames();
    if(names.size()>1)
    {
      buffer.append("(");
      for(String name: names)
      {
        buffer.append(" '");
        buffer.append(name);
        buffer.append('\'');
      }
     buffer.append(" )");
    }
    else if (names.size() == 1)
    {
      buffer.append('\'');
      buffer.append(names.iterator().next());
      buffer.append('\'');
    }

    String description = getDescription();
    if ((description != null) && (description.length() > 0))
    {
      buffer.append(" DESC '");
      buffer.append(description);
      buffer.append('\'');
    }

    if (isObsolete())
    {
      buffer.append("' OBSOLETE SYNTAX ");
    }
    else
    {
      buffer.append(" SYNTAX ");
    }

    buffer.append(getSyntax().getOID());
    buffer.append(" )");
  }
}
