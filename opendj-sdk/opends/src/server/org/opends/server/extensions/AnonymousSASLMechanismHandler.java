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
package org.opends.server.extensions;



import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class provides an implementation of a SASL mechanism, as defined in RFC
 * 4505, that does not perform any authentication.  That is, anyone attempting
 * to bind with this SASL mechanism will be successful and will be given the
 * rights of an unauthenticated user.  The request may or may not include a set
 * of SASL credentials which will serve as trace information.  If provided,
 * then that trace information will be written to the server error log.
 */
public class AnonymousSASLMechanismHandler
       extends SASLMechanismHandler
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.AnonymousSASLMechanismHandler";



  /**
   * Creates a new instance of this SASL mechanism handler.  No initialization
   * should be done in this method, as it should all be performed in the
   * <CODE>initializeSASLMechanismHandler</CODE> method.
   */
  public AnonymousSASLMechanismHandler()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Initializes this SASL mechanism handler based on the information in the
   * provided configuration entry.  It should also register itself with the
   * Directory Server for the particular kinds of SASL mechanisms that it
   * will process.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this SASL mechanism handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeSASLMechanismHandler(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeSASLMechanismHandler",
                      String.valueOf(configEntry));


    // No real implementation is required.  Simply register with the Directory
    // Server for the ANONYMOUS mechanism.
    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_ANONYMOUS,
                                                 this);
  }




  /**
   * Processes the provided SASL bind operation.  Note that if the SASL
   * processing gets far enough to be able to map the associated request to a
   * user entry (regardless of whether the authentication is ultimately
   * successful), then this method must call the
   * <CODE>BindOperation.setSASLAuthUserEntry</CODE> to provide it with the
   * entry for the user that attempted to authenticate.
   *
   * @param  bindOperation  The SASL bind operation to be processed.
   */
  public void processSASLBind(BindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "processSASLBind",
                      String.valueOf(bindOperation));


    // See if the client provided SASL credentials including trace information.
    // If so, then log it to the error log.
    ByteString saslCredentials = bindOperation.getSASLCredentials();
    if (saslCredentials != null)
    {
      String credString = saslCredentials.stringValue();
      if (credString.length() > 0)
      {
        logError(ErrorLogCategory.REQUEST_HANDLING,
                 ErrorLogSeverity.INFORMATIONAL, MSGID_SASLANONYMOUS_TRACE,
                 bindOperation.getConnectionID(),
                 bindOperation.getOperationID(), credString);

      }
    }


    // Authenticate the client anonymously and indicate that the bind was
    // successful.
    AuthenticationInfo authInfo = new AuthenticationInfo();
    bindOperation.getClientConnection().setAuthenticationInfo(authInfo);
    bindOperation.setResultCode(ResultCode.SUCCESS);
  }



  /**
   * Indicates whether the specified SASL mechanism is password-based or uses
   * some other form of credentials (e.g., an SSL client certificate or Kerberos
   * ticket).
   *
   * @param  mechanism  The name of the mechanism for which to make the
   *                    determination.  This will only be invoked with names of
   *                    mechanisms for which this handler has previously
   *                    registered.
   *
   * @return  <CODE>true</CODE> if this SASL mechanism is password-based, or
   *          <CODE>false</CODE> if it uses some other form of credentials.
   */
  public boolean isPasswordBased(String mechanism)
  {
    assert debugEnter(CLASS_NAME, "isPasswordBased", String.valueOf(mechanism));

    // This is not a password-based mechanism.
    return false;
  }



  /**
   * Indicates whether the specified SASL mechanism should be considered secure
   * (i.e., it does not expose the authentication credentials in a manner that
   * is useful to a third-party observer, and other aspects of the
   * authentication are generally secure).
   *
   * @param  mechanism  The name of the mechanism for which to make the
   *                    determination.  This will only be invoked with names of
   *                    mechanisms for which this handler has previously
   *                    registered.
   *
   * @return  <CODE>true</CODE> if this SASL mechanism should be considered
   *          secure, or <CODE>false</CODE> if not.
   */
  public boolean isSecure(String mechanism)
  {
    assert debugEnter(CLASS_NAME, "isSecure", String.valueOf(mechanism));

    // This is not a secure mechanism.
    return false;
  }
}

