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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRuleImpl;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;

import static org.forgerock.opendj.ldap.Assertion.*;
import static org.opends.server.schema.SchemaConstants.*;

/**
 * Abstract implementation for password matching rules.
 */
abstract class AbstractPasswordEqualityMatchingRuleImpl implements MatchingRuleImpl
{

  private static final String EQUALITY_ID =  EMR_AUTH_PASSWORD_NAME;

  private final Collection<? extends Indexer> indexers = Collections.singleton(new Indexer()
  {
    @Override
    public void createKeys(Schema schema, ByteSequence value, Collection<ByteString> keys) throws DecodeException
    {
      keys.add(normalizeAttributeValue(schema, value));
    }

    @Override
    public String getIndexID()
    {
      return EQUALITY_ID;
    }
  });

  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param schema The schema.
   * @param value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DecodeException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeAttributeValue(Schema schema, ByteSequence value) throws DecodeException
  {
    // We will not alter the value in any way
    return value.toByteString();
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getAssertion(final Schema schema, final ByteSequence assertionValue) throws DecodeException
  {
    final ByteString normalizedAssertionValue = normalizeAttributeValue(schema, assertionValue);
    return new Assertion()
    {
      @Override
      public ConditionResult matches(final ByteSequence normalizedAttributeValue)
      {
        return valuesMatch(normalizedAttributeValue, normalizedAssertionValue);
      }

      @Override
      public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException
      {
        return factory.createExactMatchQuery(EQUALITY_ID, normalizedAssertionValue);
      }
    };
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getSubstringAssertion(Schema schema, ByteSequence subInitial,
      List<? extends ByteSequence> subAnyElements, ByteSequence subFinal) throws DecodeException
  {
    return UNDEFINED_ASSERTION;
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getGreaterOrEqualAssertion(Schema schema, ByteSequence value) throws DecodeException
  {
    return UNDEFINED_ASSERTION;
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getLessOrEqualAssertion(Schema schema, ByteSequence value) throws DecodeException
  {
    return UNDEFINED_ASSERTION;
  }

  /** {@inheritDoc} */
  @Override
  public Collection<? extends Indexer> createIndexers(IndexingOptions options)
  {
    return indexers;
  }

  /**
   * Indicates whether the provided attribute value should be considered a match
   * for the given assertion value.  This will only be used for the purpose of
   * extensible matching.  Other forms of matching against equality matching
   * rules should use the <CODE>areEqual</CODE> method.
   *
   * @param  attributeValue  The attribute value in a form that has been
   *                         normalized according to this matching rule.
   * @param  assertionValue  The assertion value in a form that has been
   *                         normalized according to this matching rule.
   *
   * @return  <CODE>true</CODE> if the attribute value should be considered a
   *          match for the provided assertion value, or <CODE>false</CODE> if
   *          not.
   */
  protected abstract ConditionResult valuesMatch(ByteSequence attributeValue, ByteSequence assertionValue);

}
