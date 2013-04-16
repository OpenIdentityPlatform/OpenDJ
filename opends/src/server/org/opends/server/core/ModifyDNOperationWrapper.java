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

import java.util.List;

import org.opends.server.types.*;

/**
 * This abstract class wraps/decorates a given moddn operation.
 * This class will be extended by sub-classes to enhance the
 * functionality of the ModifyDNOperationBasis.
 */
public abstract class ModifyDNOperationWrapper extends
    OperationWrapper<ModifyDNOperation> implements ModifyDNOperation
{

  /**
   * Creates a new moddn operation based on the provided moddn operation.
   *
   * @param modifyDN The moddn operation to wrap
   */
  public ModifyDNOperationWrapper(ModifyDNOperation modifyDN)
  {
    super(modifyDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addModification(Modification modification) {
    getOperation().addModification(modification);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean deleteOldRDN() {
    return getOperation().deleteOldRDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getChangeNumber() {
    return getOperation().getChangeNumber();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getEntryDN() {
    return getOperation().getEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Modification> getModifications() {
    return getOperation().getModifications();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RDN getNewRDN() {
    return getOperation().getNewRDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getNewSuperior() {
    return getOperation().getNewSuperior();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Entry getOriginalEntry() {
    return getOperation().getOriginalEntry();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getProxiedAuthorizationDN() {
    return getOperation().getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getRawEntryDN() {
    return getOperation().getRawEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getRawNewRDN() {
    return getOperation().getRawNewRDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getRawNewSuperior() {
    return getOperation().getRawNewSuperior();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Entry getUpdatedEntry() {
    return getOperation().getUpdatedEntry();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setChangeNumber(long changeNumber) {
    getOperation().setChangeNumber(changeNumber);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setDeleteOldRDN(boolean deleteOldRDN) {
    getOperation().setDeleteOldRDN(deleteOldRDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawEntryDN(ByteString rawEntryDN) {
    getOperation().setRawEntryDN(rawEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawNewRDN(ByteString rawNewRDN) {
    getOperation().setRawNewRDN(rawNewRDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawNewSuperior(ByteString rawNewSuperior) {
    getOperation().setRawNewSuperior(rawNewSuperior);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setProxiedAuthorizationDN(DN dn)
  {
    getOperation().setProxiedAuthorizationDN(dn);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getNewDN()
  {
    return getOperation().getNewDN();
  }

}
