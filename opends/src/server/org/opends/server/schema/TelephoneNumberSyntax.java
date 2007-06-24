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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.schema;



import java.util.List;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.TelephoneNumberAttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the telephone number attribute syntax, which is defined
 * in RFC 2252.  Note that this can have two modes of operation, depending on
 * its configuration.  Most of the time, it will be very lenient when deciding
 * what to accept, and will allow anything but only pay attention to the digits.
 * However, it can also be configured in a "strict" mode, in which case it will
 * only accept values in the E.123 international telephone number format.
 */
public class TelephoneNumberSyntax
       extends AttributeSyntax<TelephoneNumberAttributeSyntaxCfg>
       implements ConfigurationChangeListener<TelephoneNumberAttributeSyntaxCfg>
{
  // Indicates whether this matching rule should operate in strict mode.
  private boolean strictMode;

  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;

  // The current configuration for this telephone number syntax.
  private TelephoneNumberAttributeSyntaxCfg currentConfig;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public TelephoneNumberSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(TelephoneNumberAttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_TELEPHONE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
               EMR_TELEPHONE_OID, SYNTAX_TELEPHONE_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_TELEPHONE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
               SMR_TELEPHONE_OID, SYNTAX_TELEPHONE_NAME);
    }


    // We may or may not have access to the config entry.  If we do, then see if
    // we should use the strict compliance mode.  If not, just assume that we
    // won't.
    strictMode = false;
    if (configuration != null)
    {
      currentConfig = configuration;
      currentConfig.addTelephoneNumberChangeListener(this);
      strictMode = currentConfig.isStrictFormat();
    }
  }



  /**
   * Performs any finalization that may be necessary for this attribute syntax.
   */
  public void finalizeSyntax()
  {
    currentConfig.removeTelephoneNumberChangeListener(this);
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_TELEPHONE_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_TELEPHONE_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_TELEPHONE_DESCRIPTION;
  }



  /**
   * Retrieves the default equality matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default equality matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if equality
   *          matches will not be allowed for this type by default.
   */
  public EqualityMatchingRule getEqualityMatchingRule()
  {
    return defaultEqualityMatchingRule;
  }



  /**
   * Retrieves the default ordering matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default ordering matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if ordering
   *          matches will not be allowed for this type by default.
   */
  public OrderingMatchingRule getOrderingMatchingRule()
  {
    // There is no ordering matching rule by default.
    return null;
  }



  /**
   * Retrieves the default substring matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default substring matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if substring
   *          matches will not be allowed for this type by default.
   */
  public SubstringMatchingRule getSubstringMatchingRule()
  {
    return defaultSubstringMatchingRule;
  }



  /**
   * Retrieves the default approximate matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default approximate matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if approximate
   *          matches will not be allowed for this type by default.
   */
  public ApproximateMatchingRule getApproximateMatchingRule()
  {
    // There is no approximate matching rule by default.
    return null;
  }



  /**
   * Indicates whether the provided value is acceptable for use in an attribute
   * with this syntax.  If it is not, then the reason may be appended to the
   * provided buffer.
   *
   * @param  value          The value for which to make the determination.
   * @param  invalidReason  The buffer to which the invalid reason should be
   *                        appended.
   *
   * @return  <CODE>true</CODE> if the provided value is acceptable for use with
   *          this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(ByteString value,
                                   StringBuilder invalidReason)
  {
    // No matter what, the value can't be empty or null.
    String valueStr;
    if ((value == null) ||
        ((valueStr = value.stringValue().trim()).length() == 0))
    {
      invalidReason.append(getMessage(MSGID_ATTR_SYNTAX_TELEPHONE_EMPTY));
      return false;
    }

    int length = valueStr.length();


    if (strictMode)
    {
      // If the value does not start with a plus sign, then that's not
      // acceptable.
      if (valueStr.charAt(0) != '+')
      {
        int    msgID   = MSGID_ATTR_SYNTAX_TELEPHONE_NO_PLUS;
        String message = getMessage(msgID, valueStr);
        invalidReason.append(message);
        return false;
      }


      // Iterate through the remaining characters in the value.  There must be
      // at least one digit, and it must contain only valid digits and separator
      // characters.
      boolean digitSeen = false;
      for (int i=1; i < length; i++)
      {
        char c = valueStr.charAt(i);
        if (isDigit(c))
        {
          digitSeen = true;
        }
        else if (! isSeparator(c))
        {
          int    msgID   = MSGID_ATTR_SYNTAX_TELEPHONE_ILLEGAL_CHAR;
          String message = getMessage(msgID, valueStr, c, i);
          invalidReason.append(message);
          return false;
        }
      }

      if (! digitSeen)
      {
        int    msgID   = MSGID_ATTR_SYNTAX_TELEPHONE_NO_DIGITS;
        String message = getMessage(msgID, valueStr);
        invalidReason.append(message);
        return false;
      }


      // If we've gotten here, then we'll consider it acceptable.
      return true;
    }
    else
    {
      // If we are not in strict mode, then all non-empty values containing at
      // least one digit will be acceptable.
      for (int i=0; i < length; i++)
      {
        if (isDigit(valueStr.charAt(i)))
        {
          return true;
        }
      }

      // If we made it here, then we didn't find any digits.
      int    msgID   = MSGID_ATTR_SYNTAX_TELEPHONE_NO_DIGITS;
      String message = getMessage(msgID, valueStr);
      invalidReason.append(message);
      return false;
    }
  }



  /**
   * Indicates whether the provided character is a valid separator for telephone
   * number components when operating in strict mode.
   *
   * @param  c  The character for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided character is a valid separator,
   *          or <CODE>false</CODE> if it is not.
   */
  private boolean isSeparator(char c)
  {
    switch (c)
    {
      case ' ':
      case '-':
        return true;
      default:
        return false;
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      TelephoneNumberAttributeSyntaxCfg configuration,
                      List<String> unacceptableReasons)
  {
    // The configuration will always be acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              TelephoneNumberAttributeSyntaxCfg configuration)
  {
    currentConfig = configuration;
    strictMode = configuration.isStrictFormat();

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }
}

