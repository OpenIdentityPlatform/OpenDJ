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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.types;

/**
 * This enumeration defines the set of possible operation types that
 * may be processed by the Directory Server.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum OperationType
{
  /** The operation type for abandon operations. */
  ABANDON("ABANDON"),
  /** The operation type for add operations. */
  ADD("ADD"),
  /** The operation type for bind operations. */
  BIND("BIND"),
  /** The operation type for compare operations. */
  COMPARE("COMPARE"),
  /** The operation type for delete operations. */
  DELETE("DELETE"),
  /** The operation type for extended operations. */
  EXTENDED("EXTENDED"),
  /** The operation type for modify operations. */
  MODIFY("MODIFY"),
  /** The operation type for modify DN operations. */
  MODIFY_DN("MODIFYDN"),
  /** The operation type for search operations. */
  SEARCH("SEARCH"),
  /** The operation type for unbind operations. */
  UNBIND("UNBIND");

  /** The string representation of this operation type. */
  private final String operationName;

  /**
   * Creates a new operation type with the provided operation name.
   *
   * @param  operationName  The operation name for this operation
   *                        type.
   */
  private OperationType(String operationName)
  {
    this.operationName = operationName;
  }

  /**
   * Retrieves the human-readable name for this operation type.
   *
   * @return  The human-readable name for this operation type.
   */
  public final String getOperationName()
  {
    return operationName;
  }

  /**
   * Retrieves a string representation of this operation type.
   *
   * @return  A string representation of this operation type.
   */
  @Override
  public final String toString()
  {
    return operationName;
  }
}
