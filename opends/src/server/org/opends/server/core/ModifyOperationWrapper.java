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
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.core;


import java.util.List;

import org.opends.server.types.*;


/**
 * This abstract class wraps/decorates a given modify operation.
 * This class will be extended by sub-classes to enhance the
 * functionality of the ModifyOperationBasis.
 */
public abstract class ModifyOperationWrapper extends
    OperationWrapper<ModifyOperation> implements ModifyOperation
{

  /**
   * Creates a new modify operation based on the provided modify operation.
   *
   * @param modify The modify operation to wrap
   */
  protected ModifyOperationWrapper(ModifyOperation modify)
  {
    super(modify);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addModification(Modification modification)
    throws DirectoryException
  {
    getOperation().addModification(modification);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addRawModification(RawModification rawModification)
  {
    getOperation().addRawModification(rawModification);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getEntryDN()
  {
    return getOperation().getEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Modification> getModifications()
  {
    return getOperation().getModifications();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getRawEntryDN()
  {
    return getOperation().getRawEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<RawModification> getRawModifications()
  {
    return getOperation().getRawModifications();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    getOperation().setRawEntryDN(rawEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawModifications(List<RawModification> rawModifications)
  {
    getOperation().setRawModifications(rawModifications);
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
  public final long getChangeNumber(){
    return getOperation().getChangeNumber();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setChangeNumber(long changeNumber)
  {
    getOperation().setChangeNumber(changeNumber);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getProxiedAuthorizationDN()
  {
    return getOperation().getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN){
    getOperation().setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
