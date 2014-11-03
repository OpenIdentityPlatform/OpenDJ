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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;



import java.util.Collection;
import java.util.Collections;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static com.forgerock.opendj.util.StringPrepProfile.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the numericStringSubstringsMatch matching rule defined
 * in X.520 and referenced in RFC 2252.
 */
class NumericStringSubstringMatchingRule
       extends SubstringMatchingRule
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new instance of this numericStringSubstringsMatch matching rule.
   */
  public NumericStringSubstringMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton(SMR_NUMERIC_STRING_NAME);
  }


  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return SMR_NUMERIC_STRING_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  @Override
  public String getDescription()
  {
    // There is no standard description for this matching rule.
    return null;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  @Override
  public String getSyntaxOID()
  {
    return SYNTAX_SUBSTRING_ASSERTION_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DecodeException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeAttributeValue(ByteSequence value)
         throws DecodeException
  {
    StringBuilder buffer = new StringBuilder();
    prepareUnicode(buffer, value, TRIM, NO_CASE_FOLD);
    int bufferLength = buffer.length();

    boolean logged = false;
    for (int pos = bufferLength-1; pos > 0; pos--)
    {
      char c = buffer.charAt(pos);
      if (!isDigit(c))
      {
        if (c == ' ')
        {
          buffer.delete(pos, pos+1);
        }
        else
        {
          // This is an illegal character.  Either log it or reject it.
          LocalizableMessage message = WARN_ATTR_SYNTAX_NUMERIC_STRING_ILLEGAL_CHAR.get(
                  value, c, pos);
          logged = reportInvalidSyntax(logged, message);
        }
      }
    }
    if(buffer.length() == 0)
    {
      return ByteString.empty();
    }
    return ByteString.valueOf(value.toString());
  }



  private boolean reportInvalidSyntax(boolean logged, LocalizableMessage message)
      throws DecodeException
  {
    switch (DirectoryServer.getSyntaxEnforcementPolicy())
    {
      case REJECT:
        throw DecodeException.error(message);
      case WARN:
        if (! logged)
        {
          logger.error(message);
          logged = true;
        }
        break;
    }
    return logged;
  }

  /**
   * Normalizes the provided value fragment into a form that can be used to
   * efficiently compare values.
   *
   * @param  substring  The value fragment to be normalized.
   *
   * @return  The normalized form of the value fragment.
   *
   * @throws  DecodeException  If the provided value fragment is not
   *                              acceptable according to the associated syntax.
   */
  @Override
  public ByteString normalizeSubstring(ByteSequence substring)
         throws DecodeException
  {
    return normalizeAttributeValue(substring);
  }
}

