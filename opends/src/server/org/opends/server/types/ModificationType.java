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
 * This enumeration defines the set of possible modification types
 * that may be used for an attribute modification.  This is based on
 * the LDAP specification defined in RFC 2251.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum ModificationType
{
  /**
   * The modification type that indicates that the associated
   * attribute values should be added to the entry.
   */
  ADD(0),



  /**
   * The modification type that indicates that the associated
   * attribute or set of values should be removed from the entry.
   */
  DELETE(1),



  /**
   * The modification type that indicates that the associated
   * attribute should be used to replace any existing values for that
   * attribute in the entry.
   */
  REPLACE(2),



  /**
   * The modification type that indicates that the value of the
   * associated attribute should be incremented by a specified amount
   * as defined in RFC 4525.
   */
  INCREMENT(3);



  // The integer value for this modification type.
  private int intValue;



  /**
   * Creates a new modification type with the provided integer value.
   *
   * @param  intValue  The integer value for this modification type.
   */
  private ModificationType(int intValue)
  {
    this.intValue = intValue;
  }



  /**
   * Retrieves the integer value for this modification type.
   *
   * @return  The integer value for this modification type.
   */
  public int intValue()
  {
    return intValue;
  }



  /**
   * Retrieves a string representation of this authentication type.
   *
   * @return  A string representation of this authentication type.
   */
  public String toString()
  {
    switch (intValue)
    {
      case 0:
        return "Add";
      case 1:
        return "Delete";
      case 2:
        return "Replace";
      case 3:
        return "Increment";
      default:
        return "Unknown";
    }
  }
}

