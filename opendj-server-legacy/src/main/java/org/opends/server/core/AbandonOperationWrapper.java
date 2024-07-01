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
 * Copyright 2013-2016 ForgeRock AS.
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

  @Override
  public int getIDToAbandon()
  {
    return getOperation().getIDToAbandon();
  }
}
