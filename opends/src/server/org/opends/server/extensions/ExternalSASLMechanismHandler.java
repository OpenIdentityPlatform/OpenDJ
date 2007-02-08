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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a SASL mechanism that relies on some
 * form of authentication that has already been done outside the LDAP layer.  At
 * the present time, this implementation only provides support for SSL-based
 * clients that presented their own certificate to the Directory Server during
 * the negotiation process.  Future implementations may be updated to look in
 * other places to find and evaluate this external authentication information.
 */
public class ExternalSASLMechanismHandler
       extends SASLMechanismHandler
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.ExternalSASLMechanismHandler";



  /**
   * The set of value strings that may be used for the peer certificate
   * validation policy.
   */
  private static final HashSet<String> validationValueStrings;

  static
  {
    validationValueStrings = new HashSet<String>(3);
    validationValueStrings.add(CertificateValidationPolicy.ALWAYS.toString());
    validationValueStrings.add(CertificateValidationPolicy.NEVER.toString());
    validationValueStrings.add(
         CertificateValidationPolicy.IFPRESENT.toString());
  }



  // The attribute type that should hold the certificates to use for the
  // validation.
  private AttributeType certificateAttributeType;

  // Indicates whether to attempt to validate the certificate presented by the
  // client with a certificate in the user's entry.
  private CertificateValidationPolicy validationPolicy;

  // The DN of the configuration entry for this SASL mechanism handler.
  private DN configEntryDN;



  /**
   * Creates a new instance of this SASL mechanism handler.  No initialization
   * should be done in this method, as it should all be performed in the
   * <CODE>initializeSASLMechanismHandler</CODE> method.
   */
  public ExternalSASLMechanismHandler()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeSASLMechanismHandler(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeSASLMechanismHandler",
                      String.valueOf(configEntry));


    this.configEntryDN = configEntry.getDN();


    // See if we should attempt to validate client certificates against those in
    // the corresponding user's entry.
    validationPolicy = CertificateValidationPolicy.NEVER;
    int msgID = MSGID_SASLEXTERNAL_DESCRIPTION_VALIDATION_POLICY;
    MultiChoiceConfigAttribute validateStub =
         new MultiChoiceConfigAttribute(ATTR_CLIENT_CERT_VALIDATION_POLICY,
                                        getMessage(msgID), false, false, false,
                                        validationValueStrings);
    try
    {
      MultiChoiceConfigAttribute validateAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(validateStub);
      if (validateAttr != null)
      {
        validationPolicy = CertificateValidationPolicy.policyForName(
                                validateAttr.activeValue());
        if (validationPolicy == null)
        {
          msgID = MSGID_SASLEXTERNAL_INVALID_VALIDATION_VALUE;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                      validateAttr.activeValue());
          throw new ConfigException(msgID, message);
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeSASLMechanismHandler", e);

      msgID = MSGID_SASLEXTERNAL_CANNOT_GET_VALIDATION_POLICY;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the attribute type to use for validating the certificates.  If none
    // is provided, then default to the userCertificate type.
    String attrTypeName = DEFAULT_VALIDATION_CERT_ATTRIBUTE;
    msgID = MSGID_SASLEXTERNAL_DESCRIPTION_CERTIFICATE_ATTRIBUTE;
    StringConfigAttribute certAttributeStub =
         new StringConfigAttribute(ATTR_VALIDATION_CERT_ATTRIBUTE,
                                   getMessage(msgID), false, false, false);
    try
    {
      StringConfigAttribute certAttributeAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(certAttributeStub);
      if (certAttributeAttr != null)
      {
        attrTypeName = toLowerCase(certAttributeAttr.activeValue());
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeSASLMechanismHandler", e);

      msgID = MSGID_SASLEXTERNAL_CANNOT_GET_CERT_ATTR;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }

    certificateAttributeType = DirectoryServer.getAttributeType(attrTypeName);
    if (certificateAttributeType == null)
    {
      msgID = MSGID_SASLEXTERNAL_UNKNOWN_CERT_ATTR;
      String message = getMessage(msgID, String.valueOf(attrTypeName),
                                  String.valueOf(configEntryDN));
      throw new ConfigException(msgID, message);
    }


    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_EXTERNAL, this);
    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeSASLMechanismHandler()
  {
    assert debugEnter(CLASS_NAME, "finalizeSASLMechanismHandler");

    DirectoryServer.deregisterConfigurableComponent(this);
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_EXTERNAL);
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSASLBind(BindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "processSASLBind",
                      String.valueOf(bindOperation));


    // Get the client connection used for the bind request, and get the
    // security manager for that connection.  If either are null, then fail.
    ClientConnection clientConnection = bindOperation.getClientConnection();
    if (clientConnection == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_NO_CLIENT_CONNECTION;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    ConnectionSecurityProvider securityProvider =
         clientConnection.getConnectionSecurityProvider();
    if (securityProvider == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_NO_SECURITY_PROVIDER;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // Make sure that the client connection is using the TLS security provider.
    // If not, then fail.
    if (! (securityProvider instanceof TLSConnectionSecurityProvider))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_CLIENT_NOT_USING_TLS_PROVIDER;
      String message = getMessage(msgID,
                                  securityProvider.getSecurityMechanismName());
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    TLSConnectionSecurityProvider tlsSecurityProvider =
         (TLSConnectionSecurityProvider) securityProvider;


    // Get the certificate chain that the client presented to the server, if
    // possible.  If there isn't one, then fail.
    java.security.cert.Certificate[] clientCertChain =
         tlsSecurityProvider.getClientCertificateChain();
    if ((clientCertChain == null) || (clientCertChain.length == 0))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_NO_CLIENT_CERT;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // Use the Directory Server certificate mapper to map the client certificate
    // chain to a single user DN.
    Entry userEntry;
    try
    {
      userEntry = DirectoryServer.getCertificateMapper().mapCertificateToUser(
                                                              clientCertChain);
    }
    catch (DirectoryException de)
    {
      assert debugException(CLASS_NAME, "processSASLBind", de);

      bindOperation.setResponseData(de);
      return;
    }


    // If the user DN is null, then we couldn't establish a mapping and
    // therefore the authentication failed.
    if (userEntry == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_NO_MAPPING;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }
    else
    {
      bindOperation.setSASLAuthUserEntry(userEntry);
    }


    // Get the userCertificate attribute from the user's entry for use in the
    // validation process.
    List<Attribute> certAttrList =
         userEntry.getAttribute(certificateAttributeType);
    switch (validationPolicy)
    {
      case ALWAYS:
        if (certAttrList == null)
        {
          if (validationPolicy == CertificateValidationPolicy.ALWAYS)
          {
            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int    msgID   = MSGID_SASLEXTERNAL_NO_CERT_IN_ENTRY;
            String message = getMessage(msgID,
                                        String.valueOf(userEntry.getDN()));
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }
        }
        else
        {
          try
          {
            byte[] certBytes = clientCertChain[0].getEncoded();
            AttributeValue v =
                 new AttributeValue(certificateAttributeType,
                                    new ASN1OctetString(certBytes));

            boolean found = false;
            for (Attribute a : certAttrList)
            {
              if (a.hasValue(v))
              {
                found = true;
                break;
              }
            }

            if (! found)
            {
              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int    msgID   = MSGID_SASLEXTERNAL_PEER_CERT_NOT_FOUND;
              String message = getMessage(msgID,
                                          String.valueOf(userEntry.getDN()));
              bindOperation.setAuthFailureReason(msgID, message);
              return;
            }
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "processSASLBind", e);

            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int    msgID   = MSGID_SASLEXTERNAL_CANNOT_VALIDATE_CERT;
            String message = getMessage(msgID,
                                        String.valueOf(userEntry.getDN()),
                                        stackTraceToSingleLineString(e));
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }
        }
        break;

      case IFPRESENT:
        if (certAttrList != null)
        {
          try
          {
            byte[] certBytes = clientCertChain[0].getEncoded();
            AttributeValue v =
                 new AttributeValue(certificateAttributeType,
                                    new ASN1OctetString(certBytes));

            boolean found = false;
            for (Attribute a : certAttrList)
            {
              if (a.hasValue(v))
              {
                found = true;
                break;
              }
            }

            if (! found)
            {
              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int    msgID   = MSGID_SASLEXTERNAL_PEER_CERT_NOT_FOUND;
              String message = getMessage(msgID,
                                          String.valueOf(userEntry.getDN()));
              bindOperation.setAuthFailureReason(msgID, message);
              return;
            }
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "processSASLBind", e);

            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int    msgID   = MSGID_SASLEXTERNAL_CANNOT_VALIDATE_CERT;
            String message = getMessage(msgID,
                                        String.valueOf(userEntry.getDN()),
                                        stackTraceToSingleLineString(e));
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }
        }
    }


    AuthenticationInfo authInfo =
         new AuthenticationInfo(userEntry, SASL_MECHANISM_EXTERNAL,
                                DirectoryServer.isRootDN(userEntry.getDN()));
    bindOperation.setAuthenticationInfo(authInfo);
    bindOperation.setResultCode(ResultCode.SUCCESS);
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
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

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
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");


    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();

    int msgID = MSGID_SASLEXTERNAL_DESCRIPTION_VALIDATION_POLICY;
    attrList.add(new MultiChoiceConfigAttribute(
                          ATTR_CLIENT_CERT_VALIDATION_POLICY, getMessage(msgID),
                          false, false, false, validationValueStrings,
                          validationPolicy.toString()));

    msgID = MSGID_SASLEXTERNAL_DESCRIPTION_CERTIFICATE_ATTRIBUTE;
    String certTypeStr = certificateAttributeType.getNameOrOID();
    attrList.add(new StringConfigAttribute(ATTR_VALIDATION_CERT_ATTRIBUTE,
                                           getMessage(msgID), false, false,
                                           false, certTypeStr));

    return attrList;
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
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "java.util.List<String>");


    // Look at the validation policy configuration.
    int msgID = MSGID_SASLEXTERNAL_DESCRIPTION_VALIDATION_POLICY;
    MultiChoiceConfigAttribute validateStub =
         new MultiChoiceConfigAttribute(ATTR_CLIENT_CERT_VALIDATION_POLICY,
                                        getMessage(msgID), false, false, false,
                                        validationValueStrings);
    try
    {
      MultiChoiceConfigAttribute validateAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(validateStub);
      if (validateAttr != null)
      {
        if (CertificateValidationPolicy.policyForName(
                 validateAttr.activeValue())== null)
        {
          msgID = MSGID_SASLEXTERNAL_INVALID_VALIDATION_VALUE;
          unacceptableReasons.add(getMessage(msgID,
                                             String.valueOf(configEntryDN),
                                             validateAttr.activeValue()));
          return false;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_SASLEXTERNAL_CANNOT_GET_VALIDATION_POLICY;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      return false;
    }


    // Look at the certificate attribute type configuration.
    String attrTypeName = DEFAULT_VALIDATION_CERT_ATTRIBUTE;
    msgID = MSGID_SASLEXTERNAL_DESCRIPTION_CERTIFICATE_ATTRIBUTE;
    StringConfigAttribute certAttributeStub =
         new StringConfigAttribute(ATTR_VALIDATION_CERT_ATTRIBUTE,
                                   getMessage(msgID), false, false, false);
    try
    {
      StringConfigAttribute certAttributeAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(certAttributeStub);
      if (certAttributeAttr != null)
      {
        attrTypeName = toLowerCase(certAttributeAttr.activeValue());
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_SASLEXTERNAL_CANNOT_GET_CERT_ATTR;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      return false;
    }

    if (DirectoryServer.getAttributeType(attrTypeName) == null)
    {
      msgID = MSGID_SASLEXTERNAL_UNKNOWN_CERT_ATTR;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(attrTypeName),
                                         String.valueOf(configEntryDN)));
      return false;
    }


    // If we've gotten to this point, then everything must be OK.
    return true;
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
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));


    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Look at the validation policy configuration.
    CertificateValidationPolicy newValidationPolicy =
         CertificateValidationPolicy.NEVER;
    int msgID = MSGID_SASLEXTERNAL_DESCRIPTION_VALIDATION_POLICY;
    MultiChoiceConfigAttribute validateStub =
         new MultiChoiceConfigAttribute(ATTR_CLIENT_CERT_VALIDATION_POLICY,
                                        getMessage(msgID), false, false, false,
                                        validationValueStrings);
    try
    {
      MultiChoiceConfigAttribute validateAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(validateStub);
      if (validateAttr != null)
      {
        newValidationPolicy = CertificateValidationPolicy.policyForName(
                                   validateAttr.activeValue());
        if (newValidationPolicy == null)
        {
          resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;

          msgID = MSGID_SASLEXTERNAL_INVALID_VALIDATION_VALUE;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                  validateAttr.activeValue()));
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;

      msgID = MSGID_SASLEXTERNAL_CANNOT_GET_VALIDATION_POLICY;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
    }


    // Look at the certificate attribute type configuration.
    String attrTypeName = DEFAULT_VALIDATION_CERT_ATTRIBUTE;
    msgID = MSGID_SASLEXTERNAL_DESCRIPTION_CERTIFICATE_ATTRIBUTE;
    StringConfigAttribute certAttributeStub =
         new StringConfigAttribute(ATTR_VALIDATION_CERT_ATTRIBUTE,
                                   getMessage(msgID), false, false, false);
    try
    {
      StringConfigAttribute certAttributeAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(certAttributeStub);
      if (certAttributeAttr != null)
      {
        attrTypeName = toLowerCase(certAttributeAttr.activeValue());
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_SASLEXTERNAL_CANNOT_GET_CERT_ATTR;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }

    AttributeType newCertType = DirectoryServer.getAttributeType(attrTypeName);
    if (newCertType == null)
    {
      msgID = MSGID_SASLEXTERNAL_UNKNOWN_CERT_ATTR;
      messages.add(getMessage(msgID, String.valueOf(attrTypeName),
                              String.valueOf(configEntryDN)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }
    }


    // If everything has been successful, then apply any changes that were made.
    if (resultCode == ResultCode.SUCCESS)
    {
      if (newValidationPolicy != validationPolicy)
      {
        validationPolicy = newValidationPolicy;

        if (detailedResults)
        {
          msgID = MSGID_SASLEXTERNAL_UPDATED_VALIDATION_POLICY;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(validationPolicy)));
        }
      }

      if (! certificateAttributeType.equals(newCertType))
      {
        certificateAttributeType = newCertType;

        if (detailedResults)
        {
          msgID = MSGID_SASLEXTERNAL_UPDATED_CERT_ATTR;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                  certificateAttributeType.getNameOrOID()));
        }
      }
    }


    // Return the result to the caller.
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isPasswordBased(String mechanism)
  {
    assert debugEnter(CLASS_NAME, "isPasswordBased", String.valueOf(mechanism));

    // This is not a password-based mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSecure(String mechanism)
  {
    assert debugEnter(CLASS_NAME, "isSecure", String.valueOf(mechanism));

    // This may be considered a secure mechanism.
    return true;
  }
}

