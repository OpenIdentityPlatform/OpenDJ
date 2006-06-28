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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.schema;



import java.util.List;

import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the caseIgnoreIA5SubstringsMatch matching rule defined
 * in RFC 2252.
 */
public class CaseIgnoreIA5SubstringMatchingRule
       extends SubstringMatchingRule
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.schema.CaseIgnoreIA5SubstringMatchingRule";



  /**
   * Creates a new instance of this caseIgnoreSubstringsMatch matching rule.
   */
  public CaseIgnoreIA5SubstringMatchingRule()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Initializes this matching rule based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this matching rule.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem that is not
   *                                   configuration-related occurs during
   *                                   initialization.
   */
  public void initializeMatchingRule(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeMatchingRule",
                      String.valueOf(configEntry));

    // No initialization is required.
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  public String getName()
  {
    assert debugEnter(CLASS_NAME, "getName");

    return SMR_CASE_IGNORE_IA5_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return SMR_CASE_IGNORE_IA5_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    // There is no standard description for this matching rule.
    return null;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  public String getSyntaxOID()
  {
    assert debugEnter(CLASS_NAME, "getSyntaxOID");

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
   * @throws  DirectoryException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  public ByteString normalizeValue(ByteString value)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "normalizeValue", String.valueOf(value));

    StringBuilder buffer = new StringBuilder();
    toLowerCase(value.value(), buffer, true);

    int bufferLength = buffer.length();
    if (bufferLength == 0)
    {
      if (value.value().length > 0)
      {
        // This should only happen if the value is composed entirely of spaces.
        // In that case, the normalized value is a single space.
        return new ASN1OctetString(" ");
      }
      else
      {
        // The value is empty, so it is already normalized.
        return new ASN1OctetString();
      }
    }


    // Replace any consecutive spaces with a single space, and watch out for
    // non-ASCII characters.
    boolean logged = false;
    for (int pos = bufferLength-1; pos > 0; pos--)
    {
      char c = buffer.charAt(pos);
      if (c == ' ')
      {
        if (buffer.charAt(pos-1) == ' ')
        {
          buffer.delete(pos, pos+1);
        }
      }
      else if ((c & 0x7F) != c)
      {
        // This is not a valid character for an IA5 string.  If strict syntax
        // enforcement is enabled, then we'll throw an exception.  Otherwise,
        // we'll get rid of the character.
        int    msgID   = MSGID_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER;
        String message = getMessage(msgID, value.stringValue(), c);

        switch (DirectoryServer.getSyntaxEnforcementPolicy())
        {
          case REJECT:
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID);
          case WARN:
            if (! logged)
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              logged = true;
            }

            buffer.delete(pos, pos+1);
            break;

          default:
            buffer.delete(pos, pos+1);
            break;
        }
      }
    }

    return new ASN1OctetString(buffer.toString());
  }



  /**
   * Normalizes the provided value fragment into a form that can be used to
   * efficiently compare values.
   *
   * @param  substring  The value fragment to be normalized.
   *
   * @return  The normalized form of the value fragment.
   *
   * @throws  DirectoryException  If the provided value fragment is not
   *                              acceptable according to the associated syntax.
   */
  public ByteString normalizeSubstring(ByteString substring)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "normalizeSubstring");

    // In this case, the process for normalizing a substring is the same as
    // normalizing a full value with the exception that it may include an
    // opening or trailing space.
    StringBuilder buffer = new StringBuilder();
    toLowerCase(substring.value(), buffer, false);

    int bufferLength = buffer.length();
    if (bufferLength == 0)
    {
      if (substring.value().length > 0)
      {
        // This should only happen if the value is composed entirely of spaces.
        // In that case, the normalized value is a single space.
        return new ASN1OctetString(" ");
      }
      else
      {
        // The value is empty, so it is already normalized.
        return substring;
      }
    }


    // Replace any consecutive spaces with a single space, and watch out for
    // non-ASCII characters.
    boolean logged = false;
    for (int pos = bufferLength-1; pos > 0; pos--)
    {
      char c = buffer.charAt(pos);
      if (c == ' ')
      {
        if (buffer.charAt(pos-1) == ' ')
        {
          buffer.delete(pos, pos+1);
        }
      }
      else if ((c & 0x7F) != c)
      {
        // This is not a valid character for an IA5 string.  If strict syntax
        // enforcement is enabled, then we'll throw an exception.  Otherwise,
        // we'll get rid of the character.
        int    msgID   = MSGID_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER;
        String message = getMessage(msgID, substring.stringValue(), c);

        switch (DirectoryServer.getSyntaxEnforcementPolicy())
        {
          case REJECT:
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID);
          case WARN:
            if (! logged)
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              logged = true;
            }

            buffer.delete(pos, pos+1);
            break;

          default:
            buffer.delete(pos, pos+1);
            break;
        }
      }
    }

    return new ASN1OctetString(buffer.toString());
  }



  /**
   * Determines whether the provided value matches the given substring filter
   * components.  Note that any of the substring filter components may be
   * <CODE>null</CODE> but at least one of them must be non-<CODE>null</CODE>.
   *
   * @param  value           The normalized value against which to compare the
   *                         substring components.
   * @param  subInitial      The normalized substring value fragment that should
   *                         appear at the beginning of the target value.
   * @param  subAnyElements  The normalized substring value fragments that
   *                         should appear in the middle of the target value.
   * @param  subFinal        The normalized substring value fragment that should
   *                         appear at the end of the target value.
   *
   * @return  <CODE>true</CODE> if the provided value does match the given
   *          substring components, or <CODE>false</CODE> if not.
   */
  public boolean valueMatchesSubstring(ByteString value, ByteString subInitial,
                                       List<ByteString> subAnyElements,
                                       ByteString subFinal)
  {
    assert debugEnter(CLASS_NAME, "valueMatchesSubstring",
                      String.valueOf(value), String.valueOf(subInitial),
                      String.valueOf(subAnyElements), String.valueOf(subFinal));


    byte[] valueBytes = value.value();
    int valueLength = valueBytes.length;

    int pos = 0;
    if (subInitial != null)
    {
      byte[] initialBytes = subInitial.value();
      int initialLength = initialBytes.length;
      if (initialLength > valueLength)
      {
        return false;
      }

      for (; pos < initialLength; pos++)
      {
        if (initialBytes[pos] != valueBytes[pos])
        {
          return false;
        }
      }
    }


    if ((subAnyElements != null) && (! subAnyElements.isEmpty()))
    {
      for (ByteString element : subAnyElements)
      {
        byte[] anyBytes = element.value();
        int anyLength = anyBytes.length;

        int end = valueLength - anyLength;
        boolean match = false;
        for (; pos <= end; pos++)
        {
          if (anyBytes[0] == valueBytes[pos])
          {
            boolean subMatch = true;
            for (int i=1; i < anyLength; i++)
            {
              if (anyBytes[i] != valueBytes[pos+i])
              {
                subMatch = false;
                break;
              }
            }

            if (subMatch)
            {
              match = subMatch;
              break;
            }
          }
        }

        if (match)
        {
          pos += anyLength;
        }
        else
        {
          return false;
        }
      }
    }


    if (subFinal != null)
    {
      byte[] finalBytes = subFinal.value();
      int finalLength = finalBytes.length;

      if ((valueLength - finalLength) < pos)
      {
        return false;
      }

      pos = valueLength - finalLength;
      for (int i=0; i < finalLength; i++,pos++)
      {
        if (finalBytes[i] != valueBytes[pos])
        {
          return false;
        }
      }
    }


    return true;
  }
}

