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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.common;

/**
 * The MutableBoolean wraps a boolean in a mutable way.
 * This can be usable when one wishes to use a boolean object with condition
 * variables.
 */
public class MutableBoolean
{
  boolean value;

  /**
   * A MutableBoolean with the given initial value.
   *
   * @param value  The initial value of the mutable Boolean
   */
  public MutableBoolean(boolean value)
  {
    this.value = value;
  }

  /**
   * Retrieves the current value of this MutableBoolean.
   *
   * @return The current value of this MutableBoolean.
   */
  public boolean get()
  {
    return value;
  }

  /**
   * Sets the current value of this MutableBoolean.
   *
   * @param value The new value of this MutableBoolean.
   */
  public void set(boolean value)
  {
    this.value = value;
  }
}
