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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;


import java.util.List;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Modification;
import org.opends.server.types.RawModification;


/**
 * This abstract class wraps/decorates a given modify operation.
 * This class will be extended by sub-classes to enhance the
 * functionnality of the ModifyOperationBasis.
 */
public abstract class ModifyOperationWrapper extends OperationWrapper
       implements ModifyOperation
{
  // The wrapped operation.
  private ModifyOperation modify;

  /**
   * Creates a new modify operation based on the provided modify operation.
   *
   * @param modify The modify operation to wrap
   */
  protected ModifyOperationWrapper(ModifyOperation modify)
  {
    super(modify);
    this.modify = modify;
  }

  /**
   * {@inheritDoc}
   */
  public void addModification(Modification modification)
    throws DirectoryException
  {
    modify.addModification(modification);
  }

  /**
   * {@inheritDoc}
   */
  public void addRawModification(RawModification rawModification)
  {
    modify.addRawModification(rawModification);
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object obj)
  {
    return modify.equals(obj);
  }

  /**
   * {@inheritDoc}
   */
  public DN getEntryDN()
  {
    return modify.getEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public List<Modification> getModifications()
  {
    return modify.getModifications();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawEntryDN()
  {
    return modify.getRawEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public List<RawModification> getRawModifications()
  {
    return modify.getRawModifications();
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return modify.hashCode();
  }

  /**
   * {@inheritDoc}
   */
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    modify.setRawEntryDN(rawEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawModifications(List<RawModification> rawModifications)
  {
    modify.setRawModifications(rawModifications);
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return modify.toString();
  }

  /**
   * {@inheritDoc}
   */
  public final long getChangeNumber(){
    return modify.getChangeNumber();
  }

  /**
   * {@inheritDoc}
   */
  public void setChangeNumber(long changeNumber)
  {
    modify.setChangeNumber(changeNumber);
  }

  /**
   * {@inheritDoc}
   */
  public DN getProxiedAuthorizationDN()
  {
    return modify.getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN){
    modify.setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
