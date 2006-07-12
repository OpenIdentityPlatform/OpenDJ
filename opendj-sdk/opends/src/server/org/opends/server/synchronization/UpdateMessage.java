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
package org.opends.server.synchronization;

import java.io.Serializable;
import java.util.zip.DataFormatException;

import org.opends.server.core.Operation;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPException;

/**
 * Abstract class that must be extended to define a message
 * used for sending Updates between servers.
 */
public abstract class UpdateMessage extends SynchronizationMessage
                                    implements Serializable,
                                               Comparable<UpdateMessage>
{
  /**
   * The ChangeNumber of this update.
   */
  protected ChangeNumber changeNumber;

  /**
   * True when the update must use assured replication.
   */
  private boolean assuredFlag = false;

  /**
   * Get the ChangeNumber from the message.
   * @return the ChangeNumber
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
  }

  /**
   * Get a boolean indicating if the Update must be processed as an
   * Asynchronous or as an assured synchronization.
   *
   * @return Returns the assuredFlag.
   */
  public boolean isAssured()
  {
    return assuredFlag;
  }

  /**
   * Set the Update message as an assured message.
   */
  public void setAssured()
  {
    assuredFlag = true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj)
  {
    if (obj.getClass() != this.getClass())
      return false;
    return changeNumber.equals(((UpdateMessage)obj).changeNumber);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    return changeNumber.hashCode();
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(UpdateMessage msg)
  {
    return changeNumber.compareTo(msg.getChangeNumber());
  }

  /**
   * Create and Operation from the message.
   *
   * @param   conn connection to use when creating the message
   * @return  the created Operation
   * @throws  LDAPException In case of LDAP decoding exception.
   * @throws  ASN1Exception In case of ASN1 decoding exception.
   * @throws DataFormatException In case of bad msg format.
   */
  public abstract Operation createOperation(InternalClientConnection conn)
         throws LDAPException, ASN1Exception, DataFormatException;


  /**
   * {@inheritDoc}
   */
  @Override
  public UpdateMessage processReceive(SynchronizationDomain domain)
  {
    domain.receiveUpdate(this);
    return this;
  }
}
