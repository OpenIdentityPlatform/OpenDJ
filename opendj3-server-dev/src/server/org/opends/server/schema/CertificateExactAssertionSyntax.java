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
 *      Portions Copyright 2013-2014 Manuel Gaupp
 */
package org.opends.server.schema;



import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.ByteSequence;


import static org.opends.messages.SchemaMessages.*;
import org.forgerock.i18n.LocalizableMessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;


/**
 * This class defines the Certificate Exact Assertion attribute syntax,
 * which contains components for matching X.509 certificates.
 */
public class CertificateExactAssertionSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
  public CertificateExactAssertionSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(AttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
          EMR_CASE_IGNORE_OID, SYNTAX_CERTIFICATE_EXACT_ASSERTION_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
          OMR_CASE_IGNORE_OID, SYNTAX_CERTIFICATE_EXACT_ASSERTION_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
          SMR_CASE_IGNORE_OID, SYNTAX_CERTIFICATE_EXACT_ASSERTION_NAME);
    }
  }



  /**
   * {@inheritDoc}
   */
  public String getName()
  {
    return SYNTAX_CERTIFICATE_EXACT_ASSERTION_NAME;
  }



  /**
   * {@inheritDoc}
   */
  public String getOID()
  {
    return SYNTAX_CERTIFICATE_EXACT_ASSERTION_OID;
  }



  /**
   * {@inheritDoc}
   */
  public String getDescription()
  {
    return SYNTAX_CERTIFICATE_EXACT_ASSERTION_DESCRIPTION;
  }



  /**
   * {@inheritDoc}
   */
  public MatchingRule getEqualityMatchingRule()
  {
    return defaultEqualityMatchingRule;
  }



  /**
   * {@inheritDoc}
   */
  public MatchingRule getOrderingMatchingRule()
  {
    return defaultOrderingMatchingRule;
  }



  /**
   * {@inheritDoc}
   */
  public MatchingRule getSubstringMatchingRule()
  {
    return defaultSubstringMatchingRule;
  }



  /**
   * {@inheritDoc}
   */
  public MatchingRule getApproximateMatchingRule()
  {
    // Approximate matching will not be allowed by default.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public boolean valueIsAcceptable(ByteSequence value,
                                   LocalizableMessageBuilder invalidReason)
  {
    // This method will never be called because this syntax is only used
    // within assertions.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isBEREncodingRequired()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isHumanReadable()
  {
    return true;
  }
}

