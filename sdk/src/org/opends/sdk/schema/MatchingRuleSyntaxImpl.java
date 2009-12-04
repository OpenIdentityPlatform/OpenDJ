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



import static com.sun.opends.sdk.messages.Messages.*;
import static org.opends.sdk.schema.SchemaConstants.*;

import org.opends.sdk.ByteSequence;
import org.opends.sdk.DecodeException;

import com.sun.opends.sdk.util.Message;
import com.sun.opends.sdk.util.MessageBuilder;
import com.sun.opends.sdk.util.StaticUtils;
import com.sun.opends.sdk.util.SubstringReader;



/**
 * This class implements the matching rule description syntax, which is
 * used to hold matching rule definitions in the server schema. The
 * format of this syntax is defined in RFC 2252.
 */
final class MatchingRuleSyntaxImpl extends AbstractSyntaxImpl
{

  @Override
  public String getEqualityMatchingRule()
  {
    return EMR_OID_FIRST_COMPONENT_OID;
  }



  public String getName()
  {
    return SYNTAX_MATCHING_RULE_NAME;
  }



  public boolean isHumanReadable()
  {
    return true;
  }



  /**
   * Indicates whether the provided value is acceptable for use in an
   * attribute with this syntax. If it is not, then the reason may be
   * appended to the provided buffer.
   * 
   * @param schema
   *          The schema in which this syntax is defined.
   * @param value
   *          The value for which to make the determination.
   * @param invalidReason
   *          The buffer to which the invalid reason should be appended.
   * @return <CODE>true</CODE> if the provided value is acceptable for
   *         use with this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(Schema schema, ByteSequence value,
      MessageBuilder invalidReason)
  {
    // We'll use the decodeMatchingRule method to determine if the value
    // is acceptable.
    try
    {
      final String definition = value.toString();
      final SubstringReader reader = new SubstringReader(definition);

      // We'll do this a character at a time. First, skip over any
      // leading whitespace.
      reader.skipWhitespaces();

      if (reader.remaining() <= 0)
      {
        // This means that the value was empty or contained only
        // whitespace. That is illegal.
        final Message message = ERR_ATTR_SYNTAX_MR_EMPTY_VALUE.get();
        final DecodeException e = DecodeException.error(message);
        StaticUtils.DEBUG_LOG.throwing("MatchingRuleSyntax",
            "valueIsAcceptable", e);
        throw e;
      }

      // The next character must be an open parenthesis. If it is not,
      // then that is an error.
      final char c = reader.read();
      if (c != '(')
      {
        final Message message =
            ERR_ATTR_SYNTAX_MR_EXPECTED_OPEN_PARENTHESIS.get(
                definition, (reader.pos() - 1), String.valueOf(c));
        final DecodeException e = DecodeException.error(message);
        StaticUtils.DEBUG_LOG.throwing("MatchingRuleSyntax",
            "valueIsAcceptable", e);
        throw e;
      }

      // Skip over any spaces immediately following the opening
      // parenthesis.
      reader.skipWhitespaces();

      // The next set of characters must be the OID.
      SchemaUtils.readOID(reader);
      String syntax = null;

      // At this point, we should have a pretty specific syntax that
      // describes what may come next, but some of the components are
      // optional and it would be pretty easy to put something in the
      // wrong order, so we will be very flexible about what we can
      // accept. Just look at the next token, figure out what it is and
      // how to treat what comes after it, then repeat until we get to
      // the end of the value. But before we start, set default values
      // for everything else we might need to know.
      while (true)
      {
        final String tokenName = SchemaUtils.readTokenName(reader);

        if (tokenName == null)
        {
          // No more tokens.
          break;
        }
        else if (tokenName.equalsIgnoreCase("name"))
        {
          SchemaUtils.readNameDescriptors(reader);
        }
        else if (tokenName.equalsIgnoreCase("desc"))
        {
          // This specifies the description for the matching rule. It is
          // an arbitrary string of characters enclosed in single
          // quotes.
          SchemaUtils.readQuotedString(reader);
        }
        else if (tokenName.equalsIgnoreCase("obsolete"))
        {
          // This indicates whether the matching rule should be
          // considered obsolete. We do not need to do any more parsing
          // for this token.
        }
        else if (tokenName.equalsIgnoreCase("syntax"))
        {
          syntax = SchemaUtils.readOID(reader);
        }
        else if (tokenName.matches("^X-[A-Za-z_-]+$"))
        {
          // This must be a non-standard property and it must be
          // followed by either a single definition in single quotes or
          // an open parenthesis followed by one or more values in
          // single quotes separated by spaces followed by a close
          // parenthesis.
          SchemaUtils.readExtensions(reader);
        }
        else
        {
          final Message message =
              ERR_ATTR_SYNTAX_ILLEGAL_TOKEN.get(tokenName);
          final DecodeException e = DecodeException.error(message);
          StaticUtils.DEBUG_LOG.throwing("MatchingRuleSyntax",
              "valueIsAcceptable", e);
          throw e;
        }
      }

      // Make sure that a syntax was specified.
      if (syntax == null)
      {
        final Message message =
            ERR_ATTR_SYNTAX_MR_NO_SYNTAX.get(definition);
        final DecodeException e = DecodeException.error(message);
        StaticUtils.DEBUG_LOG.throwing("MatchingRuleSyntax",
            "valueIsAcceptable", e);
        throw e;
      }
      return true;
    }
    catch (final DecodeException de)
    {
      invalidReason.append(de.getMessageObject());
      return false;
    }
  }
}
