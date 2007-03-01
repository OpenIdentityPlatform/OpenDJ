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
package org.opends.server.loggers;

/**
 * The category class defines a set of standard logging types that
 * can be used to control logging output.
 */
public abstract class LogCategory
{
  /**
   * The non-localized name of the type.
   */
  private final String name;

  /**
   * Create a named type.
   * <p>
   * Note that this constructor is "protected" to allow subclassing.
   *
   * @param name  the name of the Level, for example "SEVERE".
   */
  protected LogCategory(String name) {
    if (name == null) {
      throw new NullPointerException();
    }
    this.name = name;
  }

  /**
   * Return the non-localized string name of the Level.
   *
   * @return non-localized name
   */
  public String getName() {
    return name;
  }

  /**
   * Retrieves the string reprentation of this log category.
   *
   * @return the non-localized name of the Level, for example "INFO".
   */
  public final String toString() {
    return name;
  }
}
