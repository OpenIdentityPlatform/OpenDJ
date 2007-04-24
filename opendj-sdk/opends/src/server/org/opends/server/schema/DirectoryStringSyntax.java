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



import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.AttributeValueDecoder;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the directory string attribute syntax, which is simply a
 * set of UTF-8 characters.  By default, they will be treated in a
 * case-insensitive manner, and equality, ordering, substring, and approximate
 * matching will be allowed.
 */
public class DirectoryStringSyntax
       extends AttributeSyntax
       implements ConfigurableComponent
{



  // The default approximate matching rule for this syntax.
  private ApproximateMatchingRule defaultApproximateMatchingRule;

  // Indicates whether we will allow zero-length values.
  private boolean allowZeroLengthValues;

  // The DN of the configuration entry for this syntax.
  private DN configEntryDN;

  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private OrderingMatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;



  /**
   * A {@link String} attribute value decoder for this syntax.
   */
  public static final AttributeValueDecoder<String> DECODER =
    new AttributeValueDecoder<String>()
  {
    /**
     * {@inheritDoc}
     */
    public String decode(AttributeValue value) throws DirectoryException
    {
      // Make sure that the value is valid.
      value.getNormalizedValue();
      return value.getStringValue();
    }
  };



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public DirectoryStringSyntax()
  {
    super();

  }



  /**
   * Initializes this attribute syntax based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this attribute syntax.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   */
  public void initializeSyntax(ConfigEntry configEntry)
         throws ConfigException
  {
    defaultApproximateMatchingRule =
         DirectoryServer.getApproximateMatchingRule(AMR_DOUBLE_METAPHONE_OID);
    if (defaultApproximateMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_APPROXIMATE_MATCHING_RULE,
               AMR_DOUBLE_METAPHONE_OID, SYNTAX_DIRECTORY_STRING_NAME);
    }

    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
               EMR_CASE_IGNORE_OID, SYNTAX_DIRECTORY_STRING_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
               OMR_CASE_IGNORE_OID, SYNTAX_DIRECTORY_STRING_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
               SMR_CASE_IGNORE_OID, SYNTAX_DIRECTORY_STRING_NAME);
    }


    // This syntax is one of the Directory Server's core syntaxes and therefore
    // it may be instantiated at times without a configuration entry.  If that
    // is the case, then we'll exit now before doing anything that could require
    // access to that entry.
    if (configEntry == null)
    {
      return;
    }


    allowZeroLengthValues = DEFAULT_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS;
    int msgID = MSGID_ATTR_SYNTAX_DIRECTORYSTRING_DESCRIPTION_ALLOW_ZEROLENGTH;
    BooleanConfigAttribute allowZeroLengthStub =
         new BooleanConfigAttribute(ATTR_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute allowZeroLengthAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowZeroLengthStub);
      if (allowZeroLengthAttr != null)
      {
        allowZeroLengthValues = allowZeroLengthAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_ATTR_SYNTAX_DIRECTORYSTRING_CANNOT_DETERMINE_ZEROLENGTH;
      String message = getMessage(msgID, ATTR_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS,
                                  getExceptionMessage(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }


    configEntryDN = configEntry.getDN();
    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * Performs any finalization that may be necessary for this attribute syntax.
   */
  public void finalizeSyntax()
  {
    DirectoryServer.deregisterConfigurableComponent(this);
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_DIRECTORY_STRING_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_DIRECTORY_STRING_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_DIRECTORY_STRING_DESCRIPTION;
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
    return defaultOrderingMatchingRule;
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
    return defaultApproximateMatchingRule;
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
    if (allowZeroLengthValues || (value.value().length > 0))
    {
      return true;
    }
    else
    {
      int msgID = MSGID_ATTR_SYNTAX_DIRECTORYSTRING_INVALID_ZEROLENGTH_VALUE;
      invalidReason.append(getMessage(msgID));
      return false;
    }
  }



  /**
   * Indicates whether zero-length values will be allowed.  This is technically
   * forbidden by the LDAP specification, but it was allowed in earlier versions
   * of the server, and the discussion of the directory string syntax in RFC
   * 2252 does not explicitly state that they are not allowed.
   *
   * @return  <CODE>true</CODE> if zero-length values should be allowed for
   *          attributes with a directory string syntax, or <CODE>false</CODE>
   *          if not.
   */
  public boolean allowZeroLengthValues()
  {
    return allowZeroLengthValues;
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    LinkedList<ConfigAttribute> configAttrs = new LinkedList<ConfigAttribute>();

    int msgID = MSGID_ATTR_SYNTAX_DIRECTORYSTRING_DESCRIPTION_ALLOW_ZEROLENGTH;
    configAttrs.add(new BooleanConfigAttribute(
                             ATTR_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS,
                             getMessage(msgID), false, allowZeroLengthValues));

    return configAttrs;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    boolean configValid = true;


    int msgID = MSGID_ATTR_SYNTAX_DIRECTORYSTRING_DESCRIPTION_ALLOW_ZEROLENGTH;
    BooleanConfigAttribute allowZeroLengthStub =
         new BooleanConfigAttribute(ATTR_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute allowZeroLengthAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowZeroLengthStub);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      configValid = false;

      msgID = MSGID_ATTR_SYNTAX_DIRECTORYSTRING_CANNOT_DETERMINE_ZEROLENGTH;
      unacceptableReasons.add(getMessage(msgID,
                                         ATTR_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS,
                                         getExceptionMessage(e)));
    }


    return configValid;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    boolean newAllowZeroLengthValues = false;
    int msgID = MSGID_ATTR_SYNTAX_DIRECTORYSTRING_DESCRIPTION_ALLOW_ZEROLENGTH;
    BooleanConfigAttribute allowZeroLengthStub =
         new BooleanConfigAttribute(ATTR_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute allowZeroLengthAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowZeroLengthStub);
      if (allowZeroLengthAttr != null)
      {
        newAllowZeroLengthValues = allowZeroLengthAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      resultCode = DirectoryServer.getServerErrorResultCode();

      msgID = MSGID_ATTR_SYNTAX_DIRECTORYSTRING_CANNOT_DETERMINE_ZEROLENGTH;
      messages.add(getMessage(msgID, ATTR_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS,
                              getExceptionMessage(e)));
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      if (newAllowZeroLengthValues != allowZeroLengthValues)
      {
        allowZeroLengthValues = newAllowZeroLengthValues;

        if (detailedResults)
        {
          msgID = MSGID_ATTR_SYNTAX_DIRECTORYSTRING_UPDATED_ALLOW_ZEROLENGTH;
          messages.add(getMessage(msgID, ATTR_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS,
                                  String.valueOf(configEntry.getDN()),
                                  String.valueOf(allowZeroLengthValues)));
        }
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

