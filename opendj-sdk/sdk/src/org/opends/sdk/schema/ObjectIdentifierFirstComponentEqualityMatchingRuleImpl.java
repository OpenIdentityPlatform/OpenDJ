/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.sdk.schema;



import static com.sun.opends.sdk.util.Messages.ERR_ATTR_SYNTAX_EMPTY_VALUE;
import static com.sun.opends.sdk.util.Messages.ERR_ATTR_SYNTAX_EXPECTED_OPEN_PARENTHESIS;

import com.sun.opends.sdk.util.Message;
import org.opends.sdk.Assertion;
import org.opends.sdk.DecodeException;
import org.opends.sdk.util.ByteSequence;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.SubstringReader;



/**
 * This class implements the objectIdentifierFirstComponentMatch
 * matching rule defined in X.520 and referenced in RFC 2252. This rule
 * is intended for use with attributes whose values contain a set of
 * parentheses enclosing a space-delimited set of names and/or
 * name-value pairs (like attribute type or objectclass descriptions) in
 * which the "first component" is the first item after the opening
 * parenthesis.
 */
final class ObjectIdentifierFirstComponentEqualityMatchingRuleImpl
    extends AbstractMatchingRuleImpl
{
  @Override
  public Assertion getAssertion(Schema schema, ByteSequence value)
      throws DecodeException
  {
    final String definition = value.toString();
    final SubstringReader reader = new SubstringReader(definition);
    final String normalized =
        ObjectIdentifierEqualityMatchingRuleImpl.resolveNames(schema,
            SchemaUtils.readOID(reader));

    return new ObjectIdentifierEqualityMatchingRuleImpl.OIDAssertion(
        normalized);
  }



  public ByteString normalizeAttributeValue(Schema schema,
      ByteSequence value) throws DecodeException
  {
    final String definition = value.toString();
    final SubstringReader reader = new SubstringReader(definition);

    // We'll do this a character at a time. First, skip over any leading
    // whitespace.
    reader.skipWhitespaces();

    if (reader.remaining() <= 0)
    {
      // This means that the value was empty or contained only
      // whitespace. That is illegal.
      final Message message = ERR_ATTR_SYNTAX_EMPTY_VALUE.get();
      throw DecodeException.error(message);
    }

    // The next character must be an open parenthesis. If it is not,
    // then that is an error.
    final char c = reader.read();
    if (c != '(')
    {
      final Message message =
          ERR_ATTR_SYNTAX_EXPECTED_OPEN_PARENTHESIS.get(definition,
              (reader.pos() - 1), String.valueOf(c));
      throw DecodeException.error(message);
    }

    // Skip over any spaces immediately following the opening
    // parenthesis.
    reader.skipWhitespaces();

    // The next set of characters must be the OID.
    final String normalized =
        ObjectIdentifierEqualityMatchingRuleImpl.resolveNames(schema,
            SchemaUtils.readOID(reader));
    return ByteString.valueOf(normalized);
  }
}
