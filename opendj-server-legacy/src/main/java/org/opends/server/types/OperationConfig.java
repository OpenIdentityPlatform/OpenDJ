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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.types;

/** Base for data structures that define configuration for operations. */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public abstract class OperationConfig {

  /** When true indicates that the operation should stop as soon as possible. */
  private boolean cancelled;

  /**
   * Indicates that this operation has been cancelled and the
   * operation if executing should finish as soon as possible.
   */
  public void cancel()
  {
    this.cancelled = true;
  }

  /**
   * Indicates whether this operation has been cancelled.
   *
   * @return boolean where true indicates that this
   *         operation has been cancelled and if currently
   *         executing will finish as soon as possible
   */
  public boolean isCancelled()
  {
    return this.cancelled;
  }
}
