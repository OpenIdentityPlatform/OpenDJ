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



import java.io.IOException;
import java.util.List;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CertificateAttributeSyntaxCfg;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.ByteSequence;
import org.opends.server.types.ConfigChangeResult;
import org.forgerock.opendj.ldap.ResultCode;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.io.ASN1Reader;

import static org.opends.messages.SchemaMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;


/**
 * This class implements the certificate attribute syntax. It is restricted to
 * accept only X.509 certificates.
 */
public class CertificateSyntax
       extends AttributeSyntax<CertificateAttributeSyntaxCfg>
       implements ConfigurationChangeListener<CertificateAttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  // The default equality matching rule for this syntax.
  private MatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private MatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private MatchingRule defaultSubstringMatchingRule;

  // The current configuration.
  private volatile CertificateAttributeSyntaxCfg config;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public CertificateSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(CertificateAttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getMatchingRule(EMR_CERTIFICATE_EXACT_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
          EMR_CERTIFICATE_EXACT_OID, SYNTAX_CERTIFICATE_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getMatchingRule(OMR_OCTET_STRING_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
          OMR_OCTET_STRING_OID, SYNTAX_CERTIFICATE_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getMatchingRule(SMR_OCTET_STRING_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
          SMR_OCTET_STRING_OID, SYNTAX_CERTIFICATE_NAME);
    }

    this.config = configuration;
    config.addCertificateChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      CertificateAttributeSyntaxCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration is always acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      CertificateAttributeSyntaxCfg configuration)
  {
    this.config = configuration;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getName()
  {
    return SYNTAX_CERTIFICATE_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_CERTIFICATE_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_CERTIFICATE_DESCRIPTION;
  }



  /**
   * Retrieves the default equality matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default equality matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if equality
   *          matches will not be allowed for this type by default.
   */
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
  public MatchingRule getApproximateMatchingRule()
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
  public boolean valueIsAcceptable(ByteSequence value,
                                   LocalizableMessageBuilder invalidReason)
  {
    // Skip validation if strict validation is disabled.
    if (!config.isStrictFormat())
    {
      return true;
    }

    // Validate the ByteSequence against the definitions of X.509, clause 7
    long x509Version=0;
    ASN1Reader reader = ASN1.getReader(value);
    try
    {
      // Certificate SIGNED SEQUENCE
      if (!reader.hasNextElement() ||
          reader.peekType() != ASN1.UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.readStartSequence();

      // CertificateContent SEQUENCE
      if (!reader.hasNextElement() ||
          reader.peekType() != ASN1.UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.readStartSequence();

      // Optional Version
      if (reader.hasNextElement() &&
          reader.peekType() == (ASN1.TYPE_MASK_CONTEXT | ASN1.TYPE_MASK_CONSTRUCTED))
      {
        reader.readStartExplicitTag();
        if (!reader.hasNextElement() ||
            reader.peekType() != ASN1.UNIVERSAL_INTEGER_TYPE)
        {
          invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
          return false;
        }
        x509Version=reader.readInteger();
        if (x509Version < 0 || x509Version >2)
        {
          // invalid Version specified
          invalidReason.append(ERR_SYNTAX_CERTIFICATE_INVALID_VERSION
            .get(x509Version));
          return false;
        }
        if (x509Version == 0)
        {
          // DEFAULT values shall not be included in DER encoded SEQUENCE
          // (X.690, 11.5)
          invalidReason.append(ERR_SYNTAX_CERTIFICATE_INVALID_DER.get());
          return false;
        }
        reader.readEndExplicitTag();
      }

      // serialNumber
      if (!reader.hasNextElement() ||
          reader.peekType() != ASN1.UNIVERSAL_INTEGER_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // signature AlgorithmIdentifier
      if (!reader.hasNextElement() ||
          reader.peekType() != ASN1.UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // issuer name (SEQUENCE as of X.501, 9.2)
      if (!reader.hasNextElement() ||
          reader.peekType() != ASN1.UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // validity (SEQUENCE)
      if (!reader.hasNextElement() ||
          reader.peekType() != ASN1.UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // subject name (SEQUENCE as of X.501, 9.2)
      if (!reader.hasNextElement() ||
          reader.peekType() != ASN1.UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // SubjectPublicKeyInfo (SEQUENCE)
      if (!reader.hasNextElement() ||
          reader.peekType() != ASN1.UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // OPTIONAL issuerUniqueIdentifier
      if (reader.hasNextElement() &&
          reader.peekType() == (ASN1.TYPE_MASK_CONTEXT + 1))
      {
        if (x509Version < 1)
        {
          // only valid in v2 and v3
          invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
          return false;
        }
        reader.skipElement();
      }

      // OPTIONAL subjectUniqueIdentifier
      if (reader.hasNextElement() &&
          reader.peekType() == (ASN1.TYPE_MASK_CONTEXT + 2))
      {
        if (x509Version < 1)
        {
          // only valid in v2 and v3
          invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
          return false;
        }
        reader.skipElement();
      }

      // OPTIONAL extensions
      if (reader.hasNextElement() &&
          reader.peekType() == ((ASN1.TYPE_MASK_CONTEXT|ASN1.TYPE_MASK_CONSTRUCTED) + 3))
      {
        if (x509Version < 2)
        {
          // only valid in v3
          invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
          return false;
        }
        reader.readStartExplicitTag(); // read Tag
        if (!reader.hasNextElement() ||
            reader.peekType() != ASN1.UNIVERSAL_SEQUENCE_TYPE)
        {
          // only valid in v3
          invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
          return false;
        }
        reader.readEndExplicitTag(); // read end Tag
      }

      // There should not be any further ASN.1 elements within this SEQUENCE
      if (reader.hasNextElement())
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.readEndSequence(); // End CertificateContent SEQUENCE

      // AlgorithmIdentifier SEQUENCE
      if (!reader.hasNextElement() ||
          reader.peekType() != ASN1.UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // ENCRYPTED HASH BIT STRING
      if (!reader.hasNextElement() ||
          reader.peekType() != ASN1.UNIVERSAL_BIT_STRING_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // There should not be any further ASN.1 elements within this SEQUENCE
      if (reader.hasNextElement())
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.readEndSequence(); // End Certificate SEQUENCE

      // There should not be any further ASN.1 elements
      if (reader.hasNextElement())
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      // End of the certificate
    }
    catch (DecodeException e)
    {
      invalidReason.append(e.getMessageObject());
      return false;
    }
    catch (IOException e)
    {
      invalidReason.append(e.getMessage());
      return false;
    }

    // The basic structure of the value is an X.509 certificate
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isBEREncodingRequired()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isHumanReadable()
  {
    return false;
  }
}

