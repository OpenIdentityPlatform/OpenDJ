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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.core;


/**
 * This abstract class wraps/decorates a given abandon operation. This class
 * will be extended by sub-classes to enhance the functionality of the
 * AbandonOperationBasis.
 */
public abstract class AbandonOperationWrapper extends
    OperationWrapper<AbandonOperation> implements AbandonOperation
{

  /**
   * Creates a new abandon operation wrapper based on the provided abandon
   * operation.
   *
   * @param abandon
   *          The abandon operation to wrap
   */
  public AbandonOperationWrapper(AbandonOperation abandon)
  {
    super(abandon);
  }

  /** {@inheritDoc} */
  @Override
  public int getIDToAbandon()
  {
    return getOperation().getIDToAbandon();
  }

}
