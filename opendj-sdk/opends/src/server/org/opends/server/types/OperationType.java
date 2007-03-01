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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



/**
 * This enumeration defines the set of possible operation types that
 * may be processed by the Directory Server.
 */
public enum OperationType
{
  /**
   * The operation type for abandon operations.
   */
  ABANDON("ABANDON"),



  /**
   * The operation type for add operations.
   */
  ADD("ADD"),



  /**
   * The operation type for bind operations.
   */
  BIND("BIND"),



  /**
   * The operation type for compare operations.
   */
  COMPARE("COMPARE"),



  /**
   * The operation type for delete operations.
   */
  DELETE("DELETE"),



  /**
   * The operation type for extended operations.
   */
  EXTENDED("EXTENDED"),



  /**
   * The operation type for modify operations.
   */
  MODIFY("MODIFY"),



  /**
   * The operation type for modify DN operations.
   */
  MODIFY_DN("MODIFYDN"),



  /**
   * The operation type for search operations.
   */
  SEARCH("SEARCH"),



  /**
   * The operation type for unbind operations.
   */
  UNBIND("UNBIND");



  // The string representation of this operation type.
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
  public final String toString()
  {
    return operationName;
  }
}

