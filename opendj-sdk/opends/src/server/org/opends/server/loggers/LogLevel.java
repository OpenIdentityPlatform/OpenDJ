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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.server.loggers;

import static org.opends.server.util.ServerConstants.LOG_SEVERITY_DISABLED;
import static org.opends.server.util.ServerConstants.LOG_SEVERITY_ALL;

import java.util.*;


/**
 * The Level class defines a set of standard logging levels that
 * can be used to control logging output.  The logging Level objects
 * are ordered and are specified by ordered integers.  Enabling logging
 * at a given level also enables logging at all higher levels.
 */
public class LogLevel
{
  private static ArrayList<LogLevel> known =  new ArrayList<LogLevel>();

  /**
   * OFF is a special level that can be used to turn off logging.
   * This level is initialized to <CODE>Integer.MAX_VALUE</CODE>.
   */
  public static final LogLevel DISABLED = new LogLevel(
      LOG_SEVERITY_DISABLED,Integer.MAX_VALUE);



  /**
   * ALL indicates that all messages should be logged.
   * This level is initialized to <CODE>Integer.MIN_VALUE</CODE>.
   */
  public static final LogLevel ALL = new LogLevel(
      LOG_SEVERITY_ALL, Integer.MIN_VALUE);



  /**
   * The non-localized name of the level.
   */
  private final String name;

  /**
   * The integer value of the level.
   */
  private final int value;


  /**
   * Create a named Level with a given integer value.
   * <p>
   * Note that this constructor is "protected" to allow subclassing.
   *
   * @param name  the name of the Level, for example "SEVERE".
   * @param value an integer value for the level.
   */
  protected LogLevel(String name, int value) {
    if (name == null) {
      throw new NullPointerException();
    }
    this.name = name;
    this.value = value;

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
   * Retrieve the string reprentation of this log level.
   *
   * @return the non-localized name of the Level, for example "INFO".
   */
  public final String toString() {
    return name;
  }

  /**
   * Get the integer value for this level.  This integer value
   * can be used for efficient ordering comparisons between
   * Level objects.
   * @return the integer value for this level.
   */
  public final int intValue() {
    return value;
  }

  /**
   * Parse a level name string into a Level.
   * <p>
   * The argument string may consist of either a level name
   * or an integer value.
   * <p>
   * For example:
   * <ul>
   * <li> "SEVERE"
   * <li> "1000"
   * </ul>
   * @param  name   string to be parsed
   * @throws IllegalArgumentException if the value is not valid.
   * Valid values are integers between <CODE>Integer.MIN_VALUE</CODE>
   * and <CODE>Integer.MAX_VALUE</CODE>, and all known level names.
   * Known names are the levels defined by this class (i.e. <CODE>FINE</CODE>,
   * <CODE>FINER</CODE>, <CODE>FINEST</CODE>), or created by this class with
   * appropriate package access, or new levels defined or created
   * by subclasses.
   *
   * @return The parsed value. Passing an integer that corresponds to a
   * known name (eg 700) will return the associated name
   * (eg <CODE>CONFIG</CODE>). Passing an integer that does not (eg 1)
   * will return a new level name initialized to that value.
   */
  public static synchronized LogLevel parse(String name)
      throws IllegalArgumentException {
    // Check that name is not null.
    name.length();

    // Look for a known Level with the given non-localized name.
    for (int i = 0; i < known.size(); i++) {
      LogLevel l = known.get(i);
      if (name.equalsIgnoreCase(l.name)) {
        return l;
      }
    }

    // Now, check if the given name is an integer.  If so,
    // first look for a Level with the given value and then
    // if necessary create one.
    try {
      int x = Integer.parseInt(name);
      for (int i = 0; i < known.size(); i++) {
        LogLevel l = known.get(i);
        if (l.value == x) {
          return l;
        }
      }
      // Create a new Level.
      return new LogLevel(name, x);
    } catch (NumberFormatException ex) {
      // Not an integer.
      // Drop through.
    }

    // OK, we've tried everything and failed
    throw new IllegalArgumentException("Bad level \"" + name + "\"");
  }

  /**
   * Compare two objects for value equality.
   *
   * @param ox the LogLevel object to test.
   * @return true if and only if the two objects have the same level value.
   */
  public boolean equals(Object ox) {
    try {
      LogLevel lx = (LogLevel)ox;
      return (lx.value == this.value);
    } catch (Exception ex) {
      return false;
    }
  }

  /**
   * Retrives the hashcode for this log level. It is just the integer value.
   *
   * @return the hashCode for this log level.
   */
  public int hashCode()
  {
    return this.value;
  }

  /**
   * Returns the string representations of all the levels. All strings will
   * be in lower case.
   * @return The string representations of the levels in lower case.
   */
  public static HashSet<String> getLevelStrings()
  {
    HashSet<String> strings = new HashSet<String>();

    for (int i = 0; i < known.size(); i++)
    {
      strings.add(known.get(i).name.toLowerCase());
    }

    return strings;
  }
}
