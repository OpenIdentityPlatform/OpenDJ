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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
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
import org.opends.server.api.PasswordStorageScheme;

import static org.forgerock.opendj.ldap.Assertion.*;
import static org.opends.server.core.DirectoryServer.*;

/**
 * Implementation of the userPasswordMatch matching rule, which can be used
 * to determine whether a clear-text value matches an encoded password.
 * <p>
 * This matching rule serves a similar purpose to the equivalent
 * AuthPasswordEqualityMatchingRule defined in RFC 3112 (http://tools.ietf.org/html/rfc3112).
 */
class UserPasswordEqualityMatchingRule implements MatchingRuleImpl
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String EQUALITY_ID = "equality";

  private final Collection<? extends Indexer> indexers = Collections.singleton(new Indexer()
  {
    @Override
    public void createKeys(Schema schema, ByteSequence value, IndexingOptions options, Collection<ByteString> keys)
        throws DecodeException
    {
      keys.add(normalizeAttributeValue(schema, value));
    }

    @Override
    public String getIndexID()
    {
      return EQUALITY_ID;
    }
  });

  /** {@inheritDoc} */
  @Override
  public Comparator<ByteSequence> comparator(Schema schema)
  {
    return ByteSequence.COMPARATOR;
  }

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
  public Collection<? extends Indexer> getIndexers()
  {
    return indexers;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isIndexingSupported()
  {
    return indexers.isEmpty();
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
  private ConditionResult valuesMatch(ByteSequence attributeValue, ByteSequence assertionValue)
  {
    // We must be able to decode the attribute value using the user password
    // syntax.
    String[] userPWComponents;
    try
    {
      userPWComponents = UserPasswordSyntax.decodeUserPassword(attributeValue.toString());
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return ConditionResult.FALSE;
    }

    // The first element of the array will be the scheme.
    // Make sure that we support the requested scheme.
    PasswordStorageScheme<?> storageScheme = getPasswordStorageScheme(userPWComponents[0]);
    if (storageScheme == null)
    {
      // It's not a scheme that we can support.
      return ConditionResult.FALSE;
    }

    // We support the scheme, so make the determination.
    return ConditionResult.valueOf(
        storageScheme.passwordMatches(assertionValue, ByteString.valueOf(userPWComponents[1])));
  }

}
