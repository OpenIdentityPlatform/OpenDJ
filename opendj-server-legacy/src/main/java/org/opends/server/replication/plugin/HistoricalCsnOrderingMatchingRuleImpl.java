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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
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
import org.opends.server.replication.common.CSN;

import static org.forgerock.opendj.ldap.Assertion.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/** Matching rule used to establish an order between historical information and index them. */
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

    @Override
    public String keyToHumanReadableString(ByteSequence key)
    {
      ByteString bs = new ByteStringBuilder()
          .appendBytes(key.subSequence(2, 10))
          .appendBytes(key.subSequence(0, 2))
          .appendBytes(key.subSequence(10, 14))
          .toByteString();
      return CSN.valueOf(bs).toStringUI();
    }
  }

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
      return new ByteStringBuilder(14)
          .appendBytes(hexStringToByteArray(csn.substring(16, 20)))
          .appendBytes(hexStringToByteArray(csn.substring(0, 16)))
          .appendBytes(hexStringToByteArray(csn.substring(20, 28)))
          .toByteString();
    }
    catch (Exception e)
    {
      // This should never occur in practice since these attributes are managed internally.
      throw DecodeException.error(WARN_INVALID_SYNC_HIST_VALUE.get(value), e);
    }
  }

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

  @Override
  public Assertion getSubstringAssertion(Schema schema, ByteSequence subInitial,
      List<? extends ByteSequence> subAnyElements, ByteSequence subFinal) throws DecodeException
  {
    return UNDEFINED_ASSERTION;
  }

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

  @Override
  public Collection<? extends Indexer> createIndexers(IndexingOptions options)
  {
    return indexers;
  }
}
