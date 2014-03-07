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

import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.api.ExtensibleIndexer;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;

/**
 * An implementation of an Indexer for attribute substrings.
 */
public class SubstringIndexer extends ExtensibleIndexer
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private SubstringMatchingRule substringRule;
  private IndexingOptions indexingOptions;

  /**
   * Create a new attribute substring indexer for the given index configuration.
   *
   * @param attributeType
   *          The attribute type for which an indexer is required.
   * @param indexingOptions
   *          The decomposed substring length.
   */
  public SubstringIndexer(AttributeType attributeType,
      IndexingOptions indexingOptions)
  {
    this.substringRule = attributeType.getSubstringMatchingRule();
    this.indexingOptions = indexingOptions;
  }

  /** {@inheritDoc} */
  @Override
  public String getIndexID()
  {
    // TODO Auto-generated method stub
    throw new RuntimeException();
  }

  /** {@inheritDoc} */
  @Override
  public String getExtensibleIndexID()
  {
    return "substring";
  }

  /**
   * Decompose an attribute value into a set of substring index keys.
   * The ID of the entry containing this value should be inserted
   * into the list of each of these keys.
   *
   * @param attrValue A byte array containing the normalized attribute value
   * @param keys A set into which the keys will be inserted.
   */
  @Override
  public void getKeys(AttributeValue attrValue, Set<byte[]> keys)
  { // TODO merge with ExtensibleIndexer.getKeys(attrValue, keys);
    try
    {
      byte[] value = substringRule.normalizeAttributeValue(attrValue.getValue()).toByteArray();

      // Example: The value is ABCDE and the substring length is 3.
      // We produce the keys ABC BCD CDE DE E
      // To find values containing a short substring such as DE,
      // iterate through keys with prefix DE. To find values
      // containing a longer substring such as BCDE, read keys
      // BCD and CDE.
      final int substringKeySize = indexingOptions.substringKeySize();
      for (int i = 0, remain = value.length; remain > 0; i++, remain--)
      {
        int len = Math.min(substringKeySize, remain);
        keys.add(makeSubstringKey(value, i, len));
      }
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
    }
  }

  /**
   * Makes a byte array representing a substring index key for
   * one substring of a value.
   *
   * @param bytes The byte array containing the value
   * @param pos The starting position of the substring
   * @param len The length of the substring
   * @return A byte array containing a substring key
   */
  private byte[] makeSubstringKey(byte[] bytes, int pos, int len)
  {
    byte[] keyBytes = new byte[len];
    System.arraycopy(bytes, pos, keyBytes, 0, len);
    return keyBytes;
  }

}
