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


import org.opends.server.types.ByteString;
import org.opends.server.types.DN;


/**
 * This abstract class wraps/decorates a given delete operation.
 * This class will be extended by sub-classes to enhance the
 * functionality of the DeleteOperationBasis.
 */
public abstract class DeleteOperationWrapper extends
    OperationWrapper<DeleteOperation> implements DeleteOperation
{

  /**
   * Creates a new delete operation based on the provided delete operation.
   *
   * @param delete The delete operation to wrap
   */
  public DeleteOperationWrapper(DeleteOperation delete)
  {
    super(delete);
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
  public ByteString getRawEntryDN()
  {
    return getOperation().getRawEntryDN();
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
  public final long getChangeNumber()
  {
    return getOperation().getChangeNumber();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void setChangeNumber(long changeNumber)
  {
    getOperation().setChangeNumber(changeNumber);
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
  public DN getProxiedAuthorizationDN()
  {
    return getOperation().getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    getOperation().setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
