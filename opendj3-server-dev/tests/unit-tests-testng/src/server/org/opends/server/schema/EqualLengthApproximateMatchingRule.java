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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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

import static java.util.Collections.*;

/**
 * This class implements an extremely simple approximate matching rule that will
 * consider two values approximately equal only if they have the same length. It
 * is intended purely for testing purposes.
 */
class EqualLengthApproximateMatchingRule implements MatchingRuleImpl
{
  static final String EQUAL_LENGTH_APPROX_MR_NAME = "equalLengthApproximateMatch";
  static final String EQUAL_LENGTH_APPROX_MR_OID = "1.3.6.1.4.1.26027.1.999.26";
  static final String EQUAL_LENGTH_APPROX_MR_SYNTAX_OID = SchemaConstants.SYNTAX_DIRECTORY_STRING_OID;

  @Override
  public ByteString normalizeAttributeValue(Schema schema, ByteSequence value) throws DecodeException
  {
    // Any value is acceptable, so we can just return a copy of the value.
    return value.toByteString();
  }

  @Override
  public Comparator<ByteSequence> comparator(final Schema schema)
  {
    return new Comparator<ByteSequence>()
    {
      @Override
      public int compare(final ByteSequence o1, final ByteSequence o2)
      {
        return o1.length() - o2.length();
      }
    };
  }

  @Override
  public Assertion getAssertion(final Schema schema, final ByteSequence assertionValue) throws DecodeException
  {
    final ByteString normAssertion = normalizeAttributeValue(schema, assertionValue);
    return new Assertion()
    {
      @Override
      public ConditionResult matches(final ByteSequence normalizedAttributeValue)
      {
        return ConditionResult.valueOf(normalizedAttributeValue.length() == normAssertion.length());
      }

      @Override
      public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException
      {
        return factory.createMatchAllQuery();
      }
    };
  }

  @Override
  public Assertion getSubstringAssertion(final Schema schema, final ByteSequence subInitial,
      final List<? extends ByteSequence> subAnyElements, final ByteSequence subFinal) throws DecodeException
  {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public Assertion getGreaterOrEqualAssertion(final Schema schema, final ByteSequence value) throws DecodeException
  {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public Assertion getLessOrEqualAssertion(final Schema schema, final ByteSequence value) throws DecodeException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public boolean isIndexingSupported()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public Collection<? extends Indexer> getIndexers()
  {
    return emptyList();
  }

}
