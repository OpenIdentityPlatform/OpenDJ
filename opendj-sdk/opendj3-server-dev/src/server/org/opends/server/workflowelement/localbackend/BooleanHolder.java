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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.workflowelement.localbackend;

/**
 * This class holds a boolean value. Allow to use in/out boolean parameters,
 * which contain data to pass into the method like a regular parameter, but can
 * also be used to return data from the method.
 */
class BooleanHolder
{

  /** The boolean value held in this class. */
  boolean value;

  /**
   * Default ctor.
   *
   * @param defaultValue
   *          the default value for this object
   */
  public BooleanHolder(boolean defaultValue)
  {
    this.value = defaultValue;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return Boolean.toString(this.value);
  }
}
