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
 */
package org.opends.server.core;
import org.opends.messages.Message;


import org.opends.server.types.*;


/**
 * This abstract class wraps/decorates a given bind operation.
 * This class will be extended by sub-classes to enhance the
 * functionnality of the BindOperationBasis.
 */
public abstract class BindOperationWrapper extends OperationWrapper
       implements BindOperation
{
  // The wrapped operation.
  private BindOperation bind;

  /**
   * Creates a new bind operation based on the provided bind operation.
   *
   * @param bind The bind operation to wrap
   */
  protected BindOperationWrapper(BindOperation bind)
  {
    super(bind);
    this.bind = bind;
  }

  /**
   * {@inheritDoc}
   */
  public AuthenticationInfo getAuthenticationInfo()
  {
    return bind.getAuthenticationInfo();
  }

  /**
   * {@inheritDoc}
   */
  public AuthenticationType getAuthenticationType()
  {
    return bind.getAuthenticationType();
  }

  /**
   * {@inheritDoc}
   */
  public Message getAuthFailureReason()
  {
    return bind.getAuthFailureReason();
  }

  /**
   * {@inheritDoc}
   */
  public DN getBindDN()
  {
    return bind.getBindDN();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawBindDN()
  {
    return bind.getRawBindDN();
  }

  /**
   * {@inheritDoc}
   */
  public Entry getSASLAuthUserEntry()
  {
    return bind.getSASLAuthUserEntry();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getSASLCredentials()
  {
    return bind.getSASLCredentials();
  }

  /**
   * {@inheritDoc}
   */
  public String getSASLMechanism()
  {
    return bind.getSASLMechanism();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getServerSASLCredentials()
  {
    return bind.getServerSASLCredentials();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getSimplePassword()
  {
    return bind.getSimplePassword();
  }

  /**
   * {@inheritDoc}
   */
  public DN getUserEntryDN()
  {
    return bind.getUserEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public void setAuthenticationInfo(AuthenticationInfo authInfo)
  {
    bind.setAuthenticationInfo(authInfo);
  }

  /**
   * {@inheritDoc}
   */
  public void setAuthFailureReason(Message reason)
  {
    if (DirectoryServer.returnBindErrorMessages())
    {
      bind.appendErrorMessage(reason);
    }
    else
    {
      bind.setAuthFailureReason(reason);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void setRawBindDN(ByteString rawBindDN)
  {
    bind.setRawBindDN(rawBindDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setSASLAuthUserEntry(Entry saslAuthUserEntry)
  {
    bind.setSASLAuthUserEntry(saslAuthUserEntry);
  }

  /**
   * {@inheritDoc}
   */
  public void setSASLCredentials(String saslMechanism,
      ByteString saslCredentials)
  {
    bind.setSASLCredentials(saslMechanism, saslCredentials);
  }

  /**
   * {@inheritDoc}
   */
  public void setServerSASLCredentials(ByteString serverSASLCredentials)
  {
    bind.setServerSASLCredentials(serverSASLCredentials);
  }

  /**
   * {@inheritDoc}
   */
  public void setSimplePassword(ByteString simplePassword)
  {
    bind.setSimplePassword(simplePassword);
  }

  /**
   * {@inheritDoc}
   */
  public void setUserEntryDN(DN userEntryDN){
    bind.setUserEntryDN(userEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return bind.toString();
  }

  /**
   * {@inheritDoc}
   */
  public void setProtocolVersion(String protocolVersion)
  {
    bind.setProtocolVersion(protocolVersion);
  }

  /**
   * {@inheritDoc}
   */
  public String getProtocolVersion()
  {
    return bind.getProtocolVersion();
  }

}
