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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import java.util.Collection;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.api.ExtensibleIndexer;
import org.opends.server.api.MatchingRule;
import org.opends.server.types.AttributeType;

/**
 * An implementation of an Indexer for attribute substrings.
 */
public class SubstringIndexer extends ExtensibleIndexer
{

  private final MatchingRule substringRule;

  /**
   * Create a new attribute substring indexer for the given index configuration.
   *
   * @param attributeType
   *          The attribute type for which an indexer is required.
   */
  public SubstringIndexer(AttributeType attributeType)
  {
    this.substringRule = attributeType.getSubstringMatchingRule();
  }

  /** {@inheritDoc} */
  @Override
  public String getIndexID()
  {
    throw new RuntimeException("Code is not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public String getExtensibleIndexID()
  {
    return "substring";
  }

  /** {@inheritDoc} */
  @Override
  public void createKeys(Schema schema, ByteSequence value,
      IndexingOptions options, Collection<ByteString> keys)
      throws DecodeException
  { // FIXME Code similar to
    // AbstractSubstringMatchingRuleImpl.SubstringIndexer.createKeys()
    ByteString normValue = substringRule.normalizeAttributeValue(value);
    final int substringKeySize = options.substringKeySize();

    // Example: The value is ABCDE and the substring length is 3.
    // We produce the keys ABC BCD CDE DE E
    // To find values containing a short substring such as DE,
    // iterate through keys with prefix DE. To find values
    // containing a longer substring such as BCDE, read keys
    // BCD and CDE.
    for (int i = 0, remain = normValue.length(); remain > 0; i++, remain--)
    {
      int len = Math.min(substringKeySize, remain);
      keys.add(normValue.subSequence(i, i + len));
    }
  }

}
