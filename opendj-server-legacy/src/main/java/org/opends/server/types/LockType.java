/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
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
  /**
   * The lock type that indicates that a read lock should be obtained
   * for the associated object.
   */
  READ("Read"),



  /**
   * The lock type that indicates that a write lock should be obtained
   * for the associated object.
   */
  WRITE("Write"),



  /**
   * The lock type that indicates that no lock should be obtained for
   * the associated object.
   */
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
  public String toString()
  {
    return name;
  }
}

