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

import java.util.List;

import org.opends.server.types.*;

/**
 * This abstract class wraps/decorates a given moddn operation.
 * This class will be extended by sub-classes to enhance the
 * functionnality of the ModifyDNOperationBasis.
 */
public abstract class ModifyDNOperationWrapper
  extends OperationWrapper
  implements ModifyDNOperation
{
  ModifyDNOperation modifyDN;

  /**
   * Creates a new moddn operation based on the provided moddn operation.
   *
   * @param modifyDN The moddn operation to wrap
   */
  public ModifyDNOperationWrapper(ModifyDNOperation modifyDN)
  {
    super(modifyDN);
    this.modifyDN = modifyDN;
  }

  /**
   * {@inheritDoc}
   */
  public void addModification(Modification modification) {
    modifyDN.addModification(modification);
  }

  /**
   * {@inheritDoc}
   */
  public boolean deleteOldRDN() {
    return modifyDN.deleteOldRDN();
  }

  /**
   * {@inheritDoc}
   */
  public long getChangeNumber() {
    return modifyDN.getChangeNumber();
  }

  /**
   * {@inheritDoc}
   */
  public DN getEntryDN() {
    return modifyDN.getEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public List<Modification> getModifications() {
    return modifyDN.getModifications();
  }

  /**
   * {@inheritDoc}
   */
  public RDN getNewRDN() {
    return modifyDN.getNewRDN();
  }

  /**
   * {@inheritDoc}
   */
  public DN getNewSuperior() {
    return modifyDN.getNewSuperior();
  }

  /**
   * {@inheritDoc}
   */
  public Entry getOriginalEntry() {
    return modifyDN.getOriginalEntry();
  }

  /**
   * {@inheritDoc}
   */
  public DN getProxiedAuthorizationDN() {
    return modifyDN.getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawEntryDN() {
    return modifyDN.getRawEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawNewRDN() {
    return modifyDN.getRawNewRDN();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawNewSuperior() {
    return modifyDN.getRawNewSuperior();
  }

  /**
   * {@inheritDoc}
   */
  public Entry getUpdatedEntry() {
    return modifyDN.getUpdatedEntry();
  }

  /**
   * {@inheritDoc}
   */
  public void setChangeNumber(long changeNumber) {
    modifyDN.setChangeNumber(changeNumber);
  }

  /**
   * {@inheritDoc}
   */
  public void setDeleteOldRDN(boolean deleteOldRDN) {
    modifyDN.setDeleteOldRDN(deleteOldRDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawEntryDN(ByteString rawEntryDN) {
    modifyDN.setRawEntryDN(rawEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawNewRDN(ByteString rawNewRDN) {
    modifyDN.setRawNewRDN(rawNewRDN);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawNewSuperior(ByteString rawNewSuperior) {
    modifyDN.setRawNewSuperior(rawNewSuperior);
  }

  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN dn)
  {
    modifyDN.setProxiedAuthorizationDN(dn);
  }

  /**
   * {@inheritDoc}
   */
  public DN getNewDN()
  {
    return modifyDN.getNewDN();
  }
}
