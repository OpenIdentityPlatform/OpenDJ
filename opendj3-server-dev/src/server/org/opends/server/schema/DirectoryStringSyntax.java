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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS
 */
package org.opends.server.schema;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.DirectoryStringAttributeSyntaxCfg;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;


/**
 * This class defines the directory string attribute syntax, which is simply a
 * set of UTF-8 characters.  By default, they will be treated in a
 * case-insensitive manner, and equality, ordering, substring, and approximate
 * matching will be allowed.
 */
public class DirectoryStringSyntax
       extends AttributeSyntax<DirectoryStringAttributeSyntaxCfg>
       implements ConfigurationChangeListener<DirectoryStringAttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  // The default approximate matching rule for this syntax.
  private MatchingRule defaultApproximateMatchingRule;

  // Indicates whether we will allow zero-length values.
  private boolean allowZeroLengthValues;

  // The reference to the configuration for this directory string syntax.
  private DirectoryStringAttributeSyntaxCfg currentConfig;

  // The default equality matching rule for this syntax.
  private MatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private MatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private MatchingRule defaultSubstringMatchingRule;


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
   * {@inheritDoc}
   */
  @Override
  public void initializeSyntax(DirectoryStringAttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultApproximateMatchingRule =
         DirectoryServer.getApproximateMatchingRule(AMR_DOUBLE_METAPHONE_OID);
    if (defaultApproximateMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_APPROXIMATE_MATCHING_RULE,
          AMR_DOUBLE_METAPHONE_OID, SYNTAX_DIRECTORY_STRING_NAME);
    }

    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
          EMR_CASE_IGNORE_OID, SYNTAX_DIRECTORY_STRING_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
          OMR_CASE_IGNORE_OID, SYNTAX_DIRECTORY_STRING_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
          SMR_CASE_IGNORE_OID, SYNTAX_DIRECTORY_STRING_NAME);
    }


    // This syntax is one of the Directory Server's core syntaxes and therefore
    // it may be instantiated at times without a configuration entry.  If that
    // is the case, then we'll exit now before doing anything that could require
    // access to that entry.
    if (configuration == null)
    {
      return;
    }

    currentConfig = configuration;
    currentConfig.addDirectoryStringChangeListener(this);
    allowZeroLengthValues = currentConfig.isAllowZeroLengthValues();
  }



  /**
   * Performs any finalization that may be necessary for this attribute syntax.
   */
  @Override
  public void finalizeSyntax()
  {
    currentConfig.removeDirectoryStringChangeListener(this);
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  @Override
  public String getName()
  {
    return SYNTAX_DIRECTORY_STRING_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  @Override
  public String getOID()
  {
    return SYNTAX_DIRECTORY_STRING_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  @Override
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
  @Override
  public MatchingRule getEqualityMatchingRule()
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
  @Override
  public MatchingRule getOrderingMatchingRule()
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
  @Override
  public MatchingRule getSubstringMatchingRule()
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
  @Override
  public MatchingRule getApproximateMatchingRule()
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
  @Override
  public boolean valueIsAcceptable(ByteSequence value,
                                   LocalizableMessageBuilder invalidReason)
  {
    if (allowZeroLengthValues || (value.length() > 0))
    {
      return true;
    }
    else
    {
      invalidReason.append(
              ERR_ATTR_SYNTAX_DIRECTORYSTRING_INVALID_ZEROLENGTH_VALUE.get());
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
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      DirectoryStringAttributeSyntaxCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration will always be acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
              DirectoryStringAttributeSyntaxCfg configuration)
  {
    currentConfig = configuration;
    allowZeroLengthValues = configuration.isAllowZeroLengthValues();

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isBEREncodingRequired()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isHumanReadable()
  {
    return true;
  }
}

