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
 * This class implements the authPasswordMatch matching rule defined in RFC
 * 3112.
 */
class AuthPasswordEqualityMatchingRule implements MatchingRuleImpl
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
    return !indexers.isEmpty();
  }

  private ConditionResult valuesMatch(ByteSequence attributeValue, ByteSequence assertionValue)
  {
    // We must be able to decode the attribute value using the authentication
    // password syntax.
    StringBuilder[] authPWComponents;
    try
    {
      authPWComponents = AuthPasswordSyntax.decodeAuthPassword(attributeValue.toString());
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return ConditionResult.FALSE;
    }

    // The first element of the array will be the scheme.
    // Make sure that we support the requested scheme.
    PasswordStorageScheme<?> storageScheme = getAuthPasswordStorageScheme(authPWComponents[0].toString());
    if (storageScheme == null)
    {
      // It's not a scheme that we can support.
      return ConditionResult.FALSE;
    }

    // We support the scheme, so make the determination.
    return ConditionResult.valueOf(
        storageScheme.authPasswordMatches(assertionValue,
                                          authPWComponents[1].toString(),
                                          authPWComponents[2].toString()));
  }

}
