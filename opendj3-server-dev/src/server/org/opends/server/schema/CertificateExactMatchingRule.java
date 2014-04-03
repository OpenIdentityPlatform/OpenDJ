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
 *      Portions Copyright 2013-2014 Manuel Gaupp
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;



import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;

import javax.security.auth.x500.X500Principal;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.GSERException;
import org.opends.server.protocols.asn1.GSERParser;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the certificateExactMatch matching rule defined
 * in X.509 and referenced in RFC 4523.
 */
class CertificateExactMatchingRule
       extends EqualityMatchingRule
{
  /**
   * The GSER identifier for the serialNumber named value.
   */
  private static final String GSER_ID_SERIALNUMBER = "serialNumber";



  /**
   * The GSER identifier for the issuer named value.
   */
  private static final String GSER_ID_ISSUER = "issuer";



  /**
   * The GSER identifier for the rdnSequence IdentifiedChoiceValue.
   */
  private static final String GSER_ID_RDNSEQUENCE = "rdnSequence";
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /**
   * Creates a new instance of this certificateExactMatch matching rule.
   */
  public CertificateExactMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton(EMR_CERTIFICATE_EXACT_NAME);
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_CERTIFICATE_EXACT_OID;
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
    return EMR_CERTIFICATE_EXACT_DESCRIPTION;
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
    return SYNTAX_CERTIFICATE_EXACT_ASSERTION_OID;
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
    // The normalized form of this value is the GSER encoded ....
    final BigInteger serialNumber;
    final String dnstring;
    String certificateIssuer;

    // Read the X.509 Certificate and extract serialNumber and issuerDN
    try
    {
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      InputStream inputStream = new ByteArrayInputStream(value.toByteArray());
      X509Certificate certValue = (X509Certificate) certFactory
              .generateCertificate(inputStream);

      serialNumber = certValue.getSerialNumber();
      X500Principal issuer = certValue.getIssuerX500Principal();
      dnstring = issuer.getName(X500Principal.RFC2253);
    }
    catch (CertificateException ce)
    {
      // There seems to be a problem while parsing the certificate.
      logger.trace(WARN_CERTIFICATE_MATCH_PARSE_ERROR, ce.getMessage());

      // return the raw bytes as a fall back
      return value.toByteString();
    }

    // Normalize the DN
    try
    {
      DN dn = DN.valueOf(dnstring);
      certificateIssuer = dn.toNormalizedString();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      // We couldn't normalize the DN for some reason.  If we're supposed to use
      // strict syntax enforcement, then throw an exception.  Otherwise, log a
      // message and just try our best.
      LocalizableMessage message = ERR_CERTIFICATE_MATCH_INVALID_DN.get(
              dnstring, getExceptionMessage(e));

      switch (DirectoryServer.getSyntaxEnforcementPolicy())
      {
        case REJECT:
          throw DecodeException.error(message);
        case WARN:
          logger.error(message);
          break;
      }
      certificateIssuer= toLowerCase(dnstring);
    }

    // Create the encoded value
    return createEncodedValue(serialNumber,certificateIssuer);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString normalizeAssertionValue(ByteSequence value)
      throws DecodeException
  {
    // validate and normalize the GSER structure
    // according to the definitions from RFC 4523, Appendix A.1
    final BigInteger serialNumber;
    final String dnstring;
    String certificateIssuer;

    final GSERParser parser;
    String identifier;

    parser = new GSERParser(value.toString());

    try
    {
      // the String starts with a sequence
      parser.readStartSequence();
    }
    catch (Exception e)
    {
      logger.traceException(e);
      // Assume the assertion value is a certificate and parse issuer and serial
      // number. If the value is not even a certificate then the raw bytes will
      // be returned.
      return normalizeAttributeValue(value);
    }

    try
    {
      // the first namedValue is serialNumber
      identifier = parser.nextNamedValueIdentifier();
      if (!identifier.equals(GSER_ID_SERIALNUMBER))
      {
        LocalizableMessage message = ERR_CERTIFICATE_MATCH_IDENTIFIER_NOT_FOUND
                            .get(GSER_ID_SERIALNUMBER);
        throw DecodeException.error(message);
      }

      // The value for the serialNumber
      serialNumber = parser.nextBigInteger();

      // separator
      parser.skipSeparator();

      // the next namedValue is issuer
      identifier = parser.nextNamedValueIdentifier();
      if (!identifier.equals(GSER_ID_ISSUER))
      {
        LocalizableMessage message = ERR_CERTIFICATE_MATCH_IDENTIFIER_NOT_FOUND
                            .get(GSER_ID_ISSUER);
        throw DecodeException.error(message);
      }

      // expecting "rdnSequence:"
      identifier = parser.nextChoiceValueIdentifier();
      if (!identifier.equals(GSER_ID_RDNSEQUENCE))
      {
        LocalizableMessage message = ERR_CERTIFICATE_MATCH_IDENTIFIER_NOT_FOUND
                            .get(GSER_ID_RDNSEQUENCE);
        throw DecodeException.error(message);
      }

      // now the issuer dn
      dnstring = parser.nextString();

      // Closing the Sequence
      parser.readEndSequence();

      // There should not be additional characters
      if (parser.hasNext())
      {
        LocalizableMessage message = ERR_CERTIFICATE_MATCH_EXPECTED_END.get();
        switch (DirectoryServer.getSyntaxEnforcementPolicy())
        {
          case REJECT:
            throw DecodeException.error(message);
          case WARN:
            logger.error(message);
            break;
        }
      }
    }
    catch (GSERException e)
    {
      LocalizableMessage message = ERR_CERTIFICATE_MATCH_GSER_INVALID.get(
                          getExceptionMessage(e));
      throw DecodeException.error(message);
    }

    // Normalize the DN
    try
    {
      DN dn = DN.valueOf(dnstring);
      certificateIssuer = dn.toNormalizedString();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      // We couldn't normalize the DN for some reason.  If we're supposed to use
      // strict syntax enforcement, then throw an exception.  Otherwise, log a
      // message and just try our best.
      LocalizableMessage message = ERR_CERTIFICATE_MATCH_INVALID_DN.get(
              dnstring, getExceptionMessage(e));

      switch (DirectoryServer.getSyntaxEnforcementPolicy())
      {
        case REJECT:
          throw DecodeException.error(message);
        case WARN:
          logger.error(message);
          break;
      }
      certificateIssuer = toLowerCase(dnstring);
    }

    // Create the encoded value
    return createEncodedValue(serialNumber,certificateIssuer);
  }



  /**
   * Creates the value containing serialNumber and issuer DN.
   *
   * @param serial the serialNumber
   * @param issuerDN the issuer DN String
   *
   * @return the encoded ByteString
   */
  private static ByteString createEncodedValue(BigInteger serial,
                                               String issuerDN)
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    builder.append(StaticUtils.getBytes(issuerDN));
    builder.append((byte) 0); // Separator
    builder.append(serial.toByteArray());
    return builder.toByteString();
  }

  /** {@inheritDoc} */
  @Override
  public Assertion getAssertion(ByteSequence assertionValue) throws DecodeException
  {
    final ByteString normAssertionValue = normalizeAssertionValue(assertionValue);
    return getEqualityAssertion(normAssertionValue);
  }

}

