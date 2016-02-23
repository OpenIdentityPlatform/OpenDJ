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
 */
package org.opends.server.util;

/**
 * This enumeration defines the set of possible change types.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum ChangeOperationType
{
  /** The change type for add operations. */
  ADD("ADD", "add"),

  /** The change type for delete operations. */
  DELETE("DELETE", "delete"),

  /** The change type for modify operations. */
  MODIFY("MODIFY", "modify"),

  /** The change type for modify DN operations. */
  MODIFY_DN("MODIFY_DN", "moddn");



  /**
   * The name of this change type as it should appear in the "changetype" field
   * in LDIF records.
   */
  private String ldifChangeType;

  /** The user-friendly name given to this change type. */
  private String type;

  /**
   * Creates a change type with the given string value.
   *
   * @param  type            The string value for this change type.
   * @param  ldifChangeType  The change type as it should appear in the
   *                         "changetype" field in LDIF records.
   */
  private ChangeOperationType(String type, String ldifChangeType)
  {
    this.type           = type;
    this.ldifChangeType = ldifChangeType;
  }


  /**
   * Retrieves the human-readable name this change type.
   *
   * @return  The human-readable name for this change type.
   */
  public String getType()
  {
    return type;
  }


  /**
   * Retrieves the name of the change type as it should appear in LDIF
   * "changetype" records.
   *
   * @return  The name of the change type as it should appear in LDIF
   *          "changetype" records.
   */
  public String getLDIFChangeType()
  {
    return ldifChangeType;
  }


  /**
   * Retrieves a string representation of this type.
   *
   * @return  A string representation of this type.
   */
  @Override
  public String toString()
  {
    return type;
  }
}

