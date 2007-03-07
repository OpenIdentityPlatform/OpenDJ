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

import java.util.ArrayList;

/**
 * The category class defines a set of standard logging types that
 * can be used to control logging output.
 */
public class LogCategory
{
  private static ArrayList<LogCategory> known =  new ArrayList<LogCategory>();


  /**
   * The non-localized name of the type.
   */
  private final String name;

  /**
   * Create a named type.
   * <p>
   * Note that this constructor is "protected" to allow subclassing.
   *
   * @param name  the name of the category, for example "MESSAGE".
   */
  protected LogCategory(String name) {
    if (name == null) {
      throw new NullPointerException();
    }
    this.name = name;

    known.add(this);
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
   * @return the non-localized name of the LogCategory, for example "ENTRY".
   */
  public final String toString() {
    return name;
  }

  /**
   * Parse a category name string into a LogCategory.
   * <p>
   * For example:
   * <ul>
   * <li> "EXIT"
   * <li> "caught"
   * </ul>
   * @param  name   string to be parsed
   * @throws IllegalArgumentException if the value is not valid.
   * Known names are the categories defined by this class or created
   * by this class with appropriate package access, or new levels defined
   * or created by subclasses.
   *
   * @return The parsed category
   */
  public static synchronized LogCategory parse(String name)
      throws IllegalArgumentException {
    // Check that name is not null.
    name.length();

    // Look for a known Level with the given  name.
    for (int i = 0; i < known.size(); i++) {
      LogCategory c = known.get(i);
      if (name.equalsIgnoreCase(c.name)) {
        return c;
      }
    }

    // OK, we've tried everything and failed
    throw new IllegalArgumentException("Bad category \"" + name + "\"");
  }
}
