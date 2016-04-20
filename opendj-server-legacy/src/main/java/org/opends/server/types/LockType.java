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
 * This enumeration defines a set of lock types that can be used when
 * requesting that a lock be obtained for an entry or some other
 * object.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum LockType
{
  /** The lock type that indicates that a read lock should be obtained for the associated object. */
  READ("Read"),



  /** The lock type that indicates that a write lock should be obtained for the associated object. */
  WRITE("Write"),



  /** The lock type that indicates that no lock should be obtained for the associated object. */
  NONE("None");



  /** The human-readable name for this lock type. */
  private String name;



  /**
   * Creates a new lock type element with the provided name.
   *
   * @param  name  The name of the lock type element to create.
   */
  private LockType(String name)
  {
    this.name = name;
  }



  /**
   * Retrieves the name of this lock type element.
   *
   * @return  The name of this lock type element.
   */
  public String getName()
  {
    return name;
  }



  /**
   * Retrieves a string representation of this lock type element.
   *
   * @return  A string representation of this lock type element.
   */
  @Override
  public String toString()
  {
    return name;
  }
}

