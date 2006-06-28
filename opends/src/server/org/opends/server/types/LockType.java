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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



/**
 * This enumeration defines a set of lock types that can be used when
 * requesting that a lock be obtained for an entry or some other
 * object.
 */
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



  // The human-readable name for this lock type.
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

