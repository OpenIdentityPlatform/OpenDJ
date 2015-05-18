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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRuleImpl;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;

import static org.forgerock.opendj.ldap.Assertion.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * Matching rule used to establish an order between historical information and index them.
 */
public final class HistoricalCsnOrderingMatchingRuleImpl implements MatchingRuleImpl
{
  private static final String ORDERING_ID = "changeSequenceNumberOrderingMatch";

  private final Collection<? extends Indexer> indexers = Collections.singleton(new HistoricalIndexer());

  /** Indexer for the matching rule. */
  private final class HistoricalIndexer implements Indexer
  {
    @Override
    public void createKeys(Schema schema, ByteSequence value, Collection<ByteString> keys) throws DecodeException
    {
      keys.add(normalizeAttributeValue(schema, value));
    }

    @Override
    public String getIndexID()
    {
      return ORDERING_ID;
    }
  }

  /** {@inheritDoc} */
  @Override
  public ByteString normalizeAttributeValue(Schema schema, ByteSequence value) throws DecodeException
  {
    /*
     * Change the format of the value to index and start with the serverId. In
     * that manner, the search response time is optimized for a particular
     * serverId. The format of the key is now : serverId + timestamp + seqNum
     */
    try
    {
      int csnIndex = value.toString().indexOf(':') + 1;
      String csn = value.subSequence(csnIndex, csnIndex + 28).toString();
      ByteStringBuilder builder = new ByteStringBuilder(14);
      builder.append(hexStringToByteArray(csn.substring(16, 20)));
      builder.append(hexStringToByteArray(csn.substring(0, 16)));
      builder.append(hexStringToByteArray(csn.substring(20, 28)));
      return builder.toByteString();
    }
    catch (Exception e)
    {
      // This should never occur in practice since these attributes are managed
      // internally.
      throw DecodeException.error(WARN_INVALID_SYNC_HIST_VALUE.get(value), e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getAssertion(final Schema schema, final ByteSequence value) throws DecodeException
  {
    final ByteString normAssertion = normalizeAttributeValue(schema, value);
    return new Assertion()
    {
      @Override
      public ConditionResult matches(final ByteSequence attributeValue)
      {
        return ConditionResult.valueOf(attributeValue.compareTo(normAssertion) < 0);
      }

      @Override
      public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException
      {
        return factory.createRangeMatchQuery(ORDERING_ID, ByteString.empty(), normAssertion, false, false);
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
    final ByteString normAssertion = normalizeAttributeValue(schema, value);
    return new Assertion()
    {
      @Override
      public ConditionResult matches(final ByteSequence normalizedAttributeValue)
      {
        return ConditionResult.valueOf(normalizedAttributeValue.compareTo(normAssertion) >= 0);
      }

      @Override
      public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException
      {
        return factory.createRangeMatchQuery(ORDERING_ID, normAssertion, ByteString.empty(), true, false);
      }
    };
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getLessOrEqualAssertion(Schema schema, ByteSequence value) throws DecodeException
  {
    final ByteString normAssertion = normalizeAttributeValue(schema, value);
    return new Assertion()
    {
      @Override
      public ConditionResult matches(final ByteSequence normalizedAttributeValue)
      {
        return ConditionResult.valueOf(normalizedAttributeValue.compareTo(normAssertion) <= 0);
      }

      @Override
      public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException
      {
        return factory.createRangeMatchQuery(ORDERING_ID, ByteString.empty(), normAssertion, false, true);
      }
    };
  }

  /** {@inheritDoc} */
  @Override
  public Collection<? extends Indexer> createIndexers(IndexingOptions options)
  {
    return indexers;
  }

}
