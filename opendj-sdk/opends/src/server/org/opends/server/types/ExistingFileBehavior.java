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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



/**
 * This enumeration defines the set of possible behaviors that should
 * be taken when attempting to write to a file that already exists.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum ExistingFileBehavior
{
  /**
   * The file behavior that indicates that the data written should be
   * appended to any existing file.
   */
  APPEND("append"),



  /**
   * The file behavior that indicates that the data written should
   * overwrite any existing file.
   */
  OVERWRITE("overwrite"),



  /**
   * The file behavior that indicates that the write should fail if
   * the specified file already exists.
   */
  FAIL("fail");



  // The name to use for this existing file behavior.
  private String name;



  /**
   * Creates a new existing file behavior with the specified name.
   *
   * @param  name  The name for this existing file behavior.
   */
  private ExistingFileBehavior(String name)
  {
    this.name = name;
  }



  /**
   * Retrieves the name for this existing file behavior.
   *
   * @return  The name for this existing file behavior.
   */
  public String getName()
  {
    return name;
  }



  /**
   * Retrieves a string representation of this existing file behavior.
   *
   * @return  A string representation of this existing file behavior.
   */
  public String toString()
  {
    return name;
  }
}

