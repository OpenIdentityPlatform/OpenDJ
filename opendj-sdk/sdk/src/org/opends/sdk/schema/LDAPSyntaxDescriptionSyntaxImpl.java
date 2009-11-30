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



import static org.opends.messages.SchemaMessages.*;
import static org.opends.sdk.schema.SchemaConstants.EMR_OID_FIRST_COMPONENT_OID;
import static org.opends.sdk.schema.SchemaConstants.SYNTAX_LDAP_SYNTAX_NAME;

import java.util.*;
import java.util.regex.Pattern;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.sdk.DecodeException;
import org.opends.sdk.util.ByteSequence;
import org.opends.sdk.util.StaticUtils;
import org.opends.sdk.util.SubstringReader;



/**
 * This class defines the LDAP syntax description syntax, which is used
 * to hold attribute syntax definitions in the schema. The format of
 * this syntax is defined in RFC 2252.
 */
final class LDAPSyntaxDescriptionSyntaxImpl extends AbstractSyntaxImpl
{

  @Override
  public String getEqualityMatchingRule()
  {
    return EMR_OID_FIRST_COMPONENT_OID;
  }



  public String getName()
  {
    return SYNTAX_LDAP_SYNTAX_NAME;
  }



  public boolean isHumanReadable()
  {
    return true;
  }



  public boolean valueIsAcceptable(Schema schema, ByteSequence value,
      MessageBuilder invalidReason)
  {
    // We'll use the decodeNameForm method to determine if the value is
    // acceptable.
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
        final Message message =
            ERR_ATTR_SYNTAX_ATTRSYNTAX_EMPTY_VALUE.get();
        final DecodeException e = DecodeException.error(message);
        StaticUtils.DEBUG_LOG.throwing("LDAPSyntaxDescriptionSyntax",
            "valueIsAcceptable", e);
        throw e;
      }

      // The next character must be an open parenthesis. If it is not,
      // then that is an error.
      final char c = reader.read();
      if (c != '(')
      {
        final Message message =
            ERR_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_OPEN_PARENTHESIS.get(
                definition, (reader.pos() - 1), String.valueOf(c));
        final DecodeException e = DecodeException.error(message);
        StaticUtils.DEBUG_LOG.throwing("LDAPSyntaxDescriptionSyntax",
            "valueIsAcceptable", e);
        throw e;
      }

      // Skip over any spaces immediately following the opening
      // parenthesis.
      reader.skipWhitespaces();

      // The next set of characters must be the OID.
      final String oid = SchemaUtils.readOID(reader);

      Map<String, List<String>> extraProperties =
          Collections.emptyMap();
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
        else if (tokenName.equalsIgnoreCase("desc"))
        {
          // This specifies the description for the syntax. It is an
          // arbitrary string of characters enclosed in single quotes.
          SchemaUtils.readQuotedString(reader);
        }
        else if (tokenName.matches("^X-[A-Za-z_-]+$"))
        {
          // This must be a non-standard property and it must be
          // followed by either a single definition in single quotes or
          // an open parenthesis followed by one or more values in
          // single quotes separated by spaces followed by a close
          // parenthesis.
          if (extraProperties.isEmpty())
          {
            extraProperties = new HashMap<String, List<String>>();
          }
          extraProperties.put(tokenName, SchemaUtils
              .readExtensions(reader));
        }
        else
        {
          final Message message =
              ERR_ATTR_SYNTAX_ILLEGAL_TOKEN.get(tokenName);
          final DecodeException e = DecodeException.error(message);
          StaticUtils.DEBUG_LOG.throwing("LDAPSyntaxDescriptionSyntax",
              "valueIsAcceptable", e);
          throw e;
        }
      }

      for (final Map.Entry<String, List<String>> property : extraProperties
          .entrySet())
      {
        if (property.getKey().equalsIgnoreCase("x-pattern"))
        {
          final Iterator<String> values =
              property.getValue().iterator();
          if (values.hasNext())
          {
            final String pattern = values.next();
            try
            {
              Pattern.compile(values.next());
            }
            catch (final Exception e)
            {
              final Message message =
                  WARN_ATTR_SYNTAX_LDAPSYNTAX_REGEX_INVALID_PATTERN
                      .get(oid, pattern);
              final DecodeException de =
                  DecodeException.error(message, e);
              StaticUtils.DEBUG_LOG.throwing(
                  "LDAPSyntaxDescriptionSyntax", "valueIsAcceptable",
                  de);
              throw de;
            }
            break;
          }
        }
        else if (property.getKey().equalsIgnoreCase("x-enum"))
        {
          final List<String> values = property.getValue();
          for (int i = 0; i < values.size() - 1; i++)
          {
            final String entry = values.get(i);
            for (int j = i + 1; j < values.size(); j++)
            {
              if (entry.equals(values.get(j)))
              {
                final Message message =
                    WARN_ATTR_SYNTAX_LDAPSYNTAX_ENUM_DUPLICATE_VALUE
                        .get(oid, entry, j);
                final DecodeException e = DecodeException.error(message);
                StaticUtils.DEBUG_LOG.throwing(
                    "LDAPSyntaxDescriptionSyntax", "valueIsAcceptable",
                    e);
                throw e;
              }
            }
          }
        }
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
