/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import org.opends.server.types.*;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;

/**
 * This abstract class wraps/decorates a given bind operation.
 * This class will be extended by sub-classes to enhance the
 * functionality of the BindOperationBasis.
 */
public abstract class BindOperationWrapper extends
    OperationWrapper<BindOperation> implements BindOperation
{
  /**
   * Creates a new bind operation based on the provided bind operation.
   *
   * @param bind The bind operation to wrap
   */
  protected BindOperationWrapper(BindOperation bind)
  {
    super(bind);
  }

  @Override
  public AuthenticationInfo getAuthenticationInfo()
  {
    return getOperation().getAuthenticationInfo();
  }

  @Override
  public AuthenticationType getAuthenticationType()
  {
    return getOperation().getAuthenticationType();
  }

  @Override
  public LocalizableMessage getAuthFailureReason()
  {
    return getOperation().getAuthFailureReason();
  }

  @Override
  public DN getBindDN()
  {
    return getOperation().getBindDN();
  }

  @Override
  public ByteString getRawBindDN()
  {
    return getOperation().getRawBindDN();
  }

  @Override
  public Entry getSASLAuthUserEntry()
  {
    return getOperation().getSASLAuthUserEntry();
  }

  @Override
  public ByteString getSASLCredentials()
  {
    return getOperation().getSASLCredentials();
  }

  @Override
  public String getSASLMechanism()
  {
    return getOperation().getSASLMechanism();
  }

  @Override
  public ByteString getServerSASLCredentials()
  {
    return getOperation().getServerSASLCredentials();
  }

  @Override
  public ByteString getSimplePassword()
  {
    return getOperation().getSimplePassword();
  }

  @Override
  public DN getUserEntryDN()
  {
    return getOperation().getUserEntryDN();
  }

  @Override
  public void setAuthenticationInfo(AuthenticationInfo authInfo)
  {
    getOperation().setAuthenticationInfo(authInfo);
  }

  @Override
  public void setAuthFailureReason(LocalizableMessage reason)
  {
    if (DirectoryServer.returnBindErrorMessages())
    {
      getOperation().appendErrorMessage(reason);
    }
    else
    {
      getOperation().setAuthFailureReason(reason);
    }
  }

  @Override
  public void setRawBindDN(ByteString rawBindDN)
  {
    getOperation().setRawBindDN(rawBindDN);
  }

  @Override
  public void setSASLAuthUserEntry(Entry saslAuthUserEntry)
  {
    getOperation().setSASLAuthUserEntry(saslAuthUserEntry);
  }

  @Override
  public void setSASLCredentials(String saslMechanism,
      ByteString saslCredentials)
  {
    getOperation().setSASLCredentials(saslMechanism, saslCredentials);
  }

  @Override
  public void setServerSASLCredentials(ByteString serverSASLCredentials)
  {
    getOperation().setServerSASLCredentials(serverSASLCredentials);
  }

  @Override
  public void setSimplePassword(ByteString simplePassword)
  {
    getOperation().setSimplePassword(simplePassword);
  }

  @Override
  public void setUserEntryDN(DN userEntryDN){
    getOperation().setUserEntryDN(userEntryDN);
  }

  @Override
  public String toString()
  {
    return getOperation().toString();
  }

  @Override
  public void setProtocolVersion(String protocolVersion)
  {
    getOperation().setProtocolVersion(protocolVersion);
  }

  @Override
  public String getProtocolVersion()
  {
    return getOperation().getProtocolVersion();
  }
}
