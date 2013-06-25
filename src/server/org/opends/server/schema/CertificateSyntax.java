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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2012 Forgerock AS
 */
package org.opends.server.schema;



import java.util.List;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CertificateAttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;

import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Reader;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.SchemaMessages.*;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.protocols.asn1.ASN1Constants.*;


/**
 * This class implements the certificate attribute syntax.  This should be
 * restricted to holding only X.509 certificates, but we will accept any set of
 * bytes.  It will be treated much like the octet string attribute syntax.
 */
public class CertificateSyntax
       extends AttributeSyntax<CertificateAttributeSyntaxCfg>
       implements ConfigurationChangeListener<CertificateAttributeSyntaxCfg>
{
  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private OrderingMatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;

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
         DirectoryServer.getEqualityMatchingRule(EMR_OCTET_STRING_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_OCTET_STRING_OID, SYNTAX_CERTIFICATE_NAME));
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_OCTET_STRING_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE.get(
          OMR_OCTET_STRING_OID, SYNTAX_CERTIFICATE_NAME));
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_OCTET_STRING_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(
          SMR_OCTET_STRING_OID, SYNTAX_CERTIFICATE_NAME));
    }

    this.config = configuration;
    config.addCertificateChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      CertificateAttributeSyntaxCfg configuration,
      List<Message> unacceptableReasons)
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
  public String getSyntaxName()
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
                                   MessageBuilder invalidReason)
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
          reader.peekType() != UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.readStartSequence();

      // CertificateContent SEQUENCE
      if (!reader.hasNextElement() ||
          reader.peekType() != UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.readStartSequence();

      // Optional Version
      if (reader.hasNextElement() &&
          reader.peekType() == (TYPE_MASK_CONTEXT | TYPE_MASK_CONSTRUCTED))
      {
        reader.readStartExplicitTag();
        if (!reader.hasNextElement() ||
            reader.peekType() != UNIVERSAL_INTEGER_TYPE)
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
          reader.peekType() != UNIVERSAL_INTEGER_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // signature AlgorithmIdentifier
      if (!reader.hasNextElement() ||
          reader.peekType() != UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // issuer name (SEQUENCE as of X.501, 9.2)
      if (!reader.hasNextElement() ||
          reader.peekType() != UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // validity (SEQUENCE)
      if (!reader.hasNextElement() ||
          reader.peekType() != UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // subject name (SEQUENCE as of X.501, 9.2)
      if (!reader.hasNextElement() ||
          reader.peekType() != UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // SubjectPublicKeyInfo (SEQUENCE)
      if (!reader.hasNextElement() ||
          reader.peekType() != UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // OPTIONAL issuerUniqueIdentifier
      if (reader.hasNextElement() &&
          reader.peekType() == (TYPE_MASK_CONTEXT + 1))
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
          reader.peekType() == (TYPE_MASK_CONTEXT + 2))
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
          reader.peekType() == ((TYPE_MASK_CONTEXT|TYPE_MASK_CONSTRUCTED) + 3))
      {
        if (x509Version < 2)
        {
          // only valid in v3
          invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
          return false;
        }
        reader.readStartExplicitTag(); // read Tag
        if (!reader.hasNextElement() ||
            reader.peekType() != UNIVERSAL_SEQUENCE_TYPE)
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
          reader.peekType() != UNIVERSAL_SEQUENCE_TYPE)
      {
        invalidReason.append(ERR_SYNTAX_CERTIFICATE_NOTVALID.get());
        return false;
      }
      reader.skipElement();

      // ENCRYPTED HASH BIT STRING
      if (!reader.hasNextElement() ||
          reader.peekType() != UNIVERSAL_BIT_STRING_TYPE)
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
    catch (ASN1Exception e)
    {
      System.out.println(e.getMessageObject());
      invalidReason.append(e.getMessageObject());
      return false;
    }

    // The basic structure of the value is an X.509 certificate
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isBinary()
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

