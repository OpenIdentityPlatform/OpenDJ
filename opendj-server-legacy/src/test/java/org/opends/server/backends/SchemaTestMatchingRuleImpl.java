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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.backends;



import java.util.Collection;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleImpl;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;


/**
 * This class implements an equality matching rule that is intended for testing
 * purposes within the server (e.g., in conjunction with matching rule uses).
 * For all practical purposes, it behaves like the standard caseIgnoreMatch
 * matching rule.
 */
public class SchemaTestMatchingRuleImpl implements MatchingRuleImpl
{
  /** The matching rule impl that will do all the real work behind the scenes. */
  private final MatchingRule caseIgnoreMatchingRule;

  /**
   * Creates a new instance of this matching rule.
   */
  public SchemaTestMatchingRuleImpl()
  {
    caseIgnoreMatchingRule = CoreSchema.getCaseIgnoreMatchingRule();
  }

  @Override
  public ByteString normalizeAttributeValue(Schema schema, ByteSequence value)
         throws DecodeException
  {
    return caseIgnoreMatchingRule.normalizeAttributeValue(value);
  }

  @Override
  public Assertion getAssertion(Schema schema, ByteSequence assertionValue) throws DecodeException
  {
    return caseIgnoreMatchingRule.getAssertion(assertionValue);
  }

  @Override
  public Assertion getSubstringAssertion(Schema schema, ByteSequence subInitial,
      List<? extends ByteSequence> subAnyElements, ByteSequence subFinal) throws DecodeException
  {
    return caseIgnoreMatchingRule.getSubstringAssertion(subInitial, subAnyElements, subFinal);
  }

  @Override
  public Assertion getGreaterOrEqualAssertion(Schema schema, ByteSequence value) throws DecodeException
  {
    return caseIgnoreMatchingRule.getGreaterOrEqualAssertion(value);
  }

  @Override
  public Assertion getLessOrEqualAssertion(Schema schema, ByteSequence value) throws DecodeException
  {
    return caseIgnoreMatchingRule.getLessOrEqualAssertion(value);
  }

  @Override
  public Collection<? extends Indexer> createIndexers(IndexingOptions options)
  {
    return caseIgnoreMatchingRule.createIndexers(options);
  }

}

