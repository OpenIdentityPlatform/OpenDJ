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
package org.opends.server.extensions;



import org.opends.server.admin.std.server.SASLMechanismHandlerCfg;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

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
       extends SASLMechanismHandler<SASLMechanismHandlerCfg>
{
  /**
   * Creates a new instance of this SASL mechanism handler.  No initialization
   * should be done in this method, as it should all be performed in the
   * <CODE>initializeSASLMechanismHandler</CODE> method.
   */
  public AnonymousSASLMechanismHandler()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeSASLMechanismHandler(SASLMechanismHandlerCfg
                                                  configuration)
         throws ConfigException, InitializationException
  {
    // No real implementation is required.  Simply register with the Directory
    // Server for the ANONYMOUS mechanism.
    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_ANONYMOUS,
                                                 this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeSASLMechanismHandler()
  {
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_ANONYMOUS);
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSASLBind(BindOperation bindOperation)
  {
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
    bindOperation.setAuthenticationInfo(authInfo);
    bindOperation.setResultCode(ResultCode.SUCCESS);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isPasswordBased(String mechanism)
  {
    // This is not a password-based mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSecure(String mechanism)
  {
    // This is not a secure mechanism.
    return false;
  }
}

