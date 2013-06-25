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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.core;
import org.opends.messages.Message;


import org.opends.server.types.*;


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

  /**
   * {@inheritDoc}
   */
  @Override
  public AuthenticationInfo getAuthenticationInfo()
  {
    return getOperation().getAuthenticationInfo();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AuthenticationType getAuthenticationType()
  {
    return getOperation().getAuthenticationType();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getAuthFailureReason()
  {
    return getOperation().getAuthFailureReason();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getBindDN()
  {
    return getOperation().getBindDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getRawBindDN()
  {
    return getOperation().getRawBindDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Entry getSASLAuthUserEntry()
  {
    return getOperation().getSASLAuthUserEntry();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getSASLCredentials()
  {
    return getOperation().getSASLCredentials();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getSASLMechanism()
  {
    return getOperation().getSASLMechanism();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getServerSASLCredentials()
  {
    return getOperation().getServerSASLCredentials();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getSimplePassword()
  {
    return getOperation().getSimplePassword();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getUserEntryDN()
  {
    return getOperation().getUserEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAuthenticationInfo(AuthenticationInfo authInfo)
  {
    getOperation().setAuthenticationInfo(authInfo);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAuthFailureReason(Message reason)
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

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawBindDN(ByteString rawBindDN)
  {
    getOperation().setRawBindDN(rawBindDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSASLAuthUserEntry(Entry saslAuthUserEntry)
  {
    getOperation().setSASLAuthUserEntry(saslAuthUserEntry);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSASLCredentials(String saslMechanism,
      ByteString saslCredentials)
  {
    getOperation().setSASLCredentials(saslMechanism, saslCredentials);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setServerSASLCredentials(ByteString serverSASLCredentials)
  {
    getOperation().setServerSASLCredentials(serverSASLCredentials);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSimplePassword(ByteString simplePassword)
  {
    getOperation().setSimplePassword(simplePassword);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setUserEntryDN(DN userEntryDN){
    getOperation().setUserEntryDN(userEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return getOperation().toString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setProtocolVersion(String protocolVersion)
  {
    getOperation().setProtocolVersion(protocolVersion);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getProtocolVersion()
  {
    return getOperation().getProtocolVersion();
  }

}
