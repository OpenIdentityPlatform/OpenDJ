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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;



/**
 * This enumeration defines a result that could be returned from a
 * boolean operation that may evaluate to true or false, but may also
 * be undefined (i.e., "maybe").  A result of undefined indicates that
 * further investigation may be required.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum ConditionResult
{
  /**
   * Indicates that the result of the associated check returned
   * "true".
   */
  TRUE("true"),



  /**
   * Indicates that the result of the associated check returned
   * "false".
   */
  FALSE("false"),



  /**
   * Indicates that the associated check did not yield a definitive
   * result and that additional checking might be required.
   */
  UNDEFINED("undefined");



  // The human-readable name for this result.
  private String resultName;



  /**
   * Creates a new condition result with the provided name.
   *
   * @param  resultName  The human-readable name for this condition
   *                     result.
   */
  private ConditionResult(String resultName)
  {
    this.resultName = resultName;
  }

  /**
   Returns the logical inverse of a ConditionResult value. The inverse
   of the UNDEFINED value is UNDEFINED.

   @param value The value to invert.
   @return The logical inverse of the supplied value.
   */
  public static ConditionResult inverseOf(ConditionResult value) {
    switch (value) {
      case TRUE:
        return FALSE;
      case FALSE:
        return TRUE;
      case UNDEFINED:
        return UNDEFINED;
    }
    assert false : "internal error: missing switch case" ;
    return UNDEFINED;
  }


  /**
   * Retrieves the human-readable name for this condition result.
   *
   * @return  The human-readable name for this condition result.
   */
  public String toString()
  {
    return resultName;
  }
}

