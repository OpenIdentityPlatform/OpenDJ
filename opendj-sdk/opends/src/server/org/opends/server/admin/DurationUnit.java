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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * This enumeration defines various duration units.
 */
public enum DurationUnit {

  /**
   * A day unit.
   */
  DAYS((long) 24 * 60 * 60 * 1000, "d", "days"),

  /**
   * An hour unit.
   */
  HOURS((long) 60 * 60 * 1000, "h", "hours"),

  /**
   * A millisecond unit.
   */
  MILLI_SECONDS(1L, "ms", "milliseconds"),

  /**
   * A minute unit.
   */
  MINUTES((long) 60 * 1000, "m", "minutes"),

  /**
   * A second unit.
   */
  SECONDS(1000L, "s", "seconds"),

  /**
   * A week unit.
   */
  WEEKS((long) 7 * 24 * 60 * 60 * 1000, "w", "weeks");

  // A lookup table for resolving a unit from its name.
  private static final Map<String, DurationUnit> nameToUnit;
  static {
    nameToUnit = new HashMap<String, DurationUnit>();

    for (DurationUnit unit : DurationUnit.values()) {
      nameToUnit.put(unit.shortName, unit);
      nameToUnit.put(unit.longName, unit);
    }
  }



  /**
   * Get the unit corresponding to the provided unit name.
   *
   * @param s
   *          The name of the unit. Can be the abbreviated or long
   *          name and can contain white space and mixed case
   *          characters.
   * @return Returns the unit corresponding to the provided unit name.
   * @throws IllegalArgumentException
   *           If the provided name did not correspond to a known
   *           duration unit.
   */
  public static DurationUnit getUnit(String s) throws IllegalArgumentException {
    DurationUnit unit = nameToUnit.get(s.trim().toLowerCase());
    if (unit == null) {
      throw new IllegalArgumentException("Illegal duration unit \"" + s + "\"");
    }
    return unit;
  }



  /**
   * Parse the provided duration string and return its equivalent
   * duration in milliseconds. The duration string must specify the
   * unit e.g. "10s". This method will parse duration string
   * representations produced from the {@link #toString(long)} method.
   * Therefore, a duration can comprise of multiple duration
   * specifiers, for example <code>1d15m25s</code>.
   *
   * @param s
   *          The duration string to be parsed.
   * @return Returns the parsed duration in milliseconds.
   * @throws NumberFormatException
   *           If the provided duration string could not be parsed.
   * @see #toString(long)
   */
  public static long parseValue(String s) throws NumberFormatException {
    return parseValue(s, null);
  }



  /**
   * Parse the provided duration string and return its equivalent
   * duration in milliseconds. This method will parse duration string
   * representations produced from the {@link #toString(long)} method.
   * Therefore, a duration can comprise of multiple duration
   * specifiers, for example <code>1d15m25s</code>.
   *
   * @param s
   *          The duration string to be parsed.
   * @param defaultUnit
   *          The default unit to use if there is no unit specified in
   *          the duration string, or <code>null</code> if the
   *          string must always contain a unit.
   * @return Returns the parsed duration in milliseconds.
   * @throws NumberFormatException
   *           If the provided duration string could not be parsed.
   * @see #toString(long)
   */
  public static long parseValue(String s, DurationUnit defaultUnit)
      throws NumberFormatException {
    String ns = s.trim();
    if (ns.length() == 0) {
      throw new NumberFormatException("Empty duration value \"" + s + "\"");
    }

    Pattern p1 = Pattern.compile("^\\s*((\\d+)\\s*w)?" + "\\s*((\\d+)\\s*d)?"
        + "\\s*((\\d+)\\s*h)?" + "\\s*((\\d+)\\s*m)?" + "\\s*((\\d+)\\s*s)?"
        + "\\s*((\\d+)\\s*ms)?\\s*$", Pattern.CASE_INSENSITIVE);
    Matcher m1 = p1.matcher(ns);
    if (m1.matches()) {
      // Value must be of the form produced by toString(long).
      String weeks = m1.group(2);
      String days = m1.group(4);
      String hours = m1.group(6);
      String minutes = m1.group(8);
      String seconds = m1.group(10);
      String ms = m1.group(12);

      long duration = 0;

      try {
        if (weeks != null) {
          duration += Long.valueOf(weeks) * WEEKS.getDuration();
        }

        if (days != null) {
          duration += Long.valueOf(days) * DAYS.getDuration();
        }

        if (hours != null) {
          duration += Long.valueOf(hours) * HOURS.getDuration();
        }

        if (minutes != null) {
          duration += Long.valueOf(minutes) * MINUTES.getDuration();
        }

        if (seconds != null) {
          duration += Long.valueOf(seconds) * SECONDS.getDuration();
        }

        if (ms != null) {
          duration += Long.valueOf(ms) * MILLI_SECONDS.getDuration();
        }
      } catch (NumberFormatException e) {
        throw new NumberFormatException("Invalid duration value \"" + s + "\"");
      }

      return duration;
    } else {
      // Value must be a floating point number followed by a unit.
      Pattern p2 = Pattern.compile("^\\s*(\\d+(\\.\\d+)?)\\s*(\\w+)?\\s*$");
      Matcher m2 = p2.matcher(ns);

      if (!m2.matches()) {
        throw new NumberFormatException("Invalid duration value \"" + s + "\"");
      }

      // Group 1 is the float.
      double d;
      try {
        d = Double.valueOf(m2.group(1));
      } catch (NumberFormatException e) {
        throw new NumberFormatException("Invalid duration value \"" + s + "\"");
      }

      // Group 3 is the unit.
      String unitString = m2.group(3);
      DurationUnit unit;
      if (unitString == null) {
        if (defaultUnit == null) {
          throw new NumberFormatException("Invalid duration value \"" + s
              + "\"");
        } else {
          unit = defaultUnit;
        }
      } else {
        try {
          unit = getUnit(unitString);
        } catch (IllegalArgumentException e) {
          throw new NumberFormatException("Invalid duration value \"" + s
              + "\"");
        }
      }

      return unit.toMilliSeconds(d);
    }
  }



  /**
   * Returns a string representation of the provided duration. The
   * string representation can be parsed using the
   * {@link #parseValue(String)} method. The string representation is
   * comprised of one or more of the number of weeks, days, hours,
   * minutes, seconds, and milliseconds. Here are some examples:
   *
   * <pre>
   * toString(0)       // 0 ms
   * toString(999)     // 999 ms
   * toString(1000)    // 1 s
   * toString(1500)    // 1 s 500 ms
   * toString(3650000) // 1 h 50 s
   * toString(3700000) // 1 h 1 m 40 s
   * </pre>
   *
   * @param duration
   *          The duration in milliseconds.
   * @return Returns a string representation of the provided duration.
   * @throws IllegalArgumentException
   *           If the provided duration is negative.
   * @see #parseValue(String)
   * @see #parseValue(String, DurationUnit)
   */
  public static String toString(long duration) throws IllegalArgumentException {
    if (duration < 0) {
      throw new IllegalArgumentException("Negative duration " + duration);
    }

    if (duration == 0) {
      return "0 ms";
    }

    DurationUnit[] units = new DurationUnit[] { WEEKS, DAYS, HOURS, MINUTES,
        SECONDS, MILLI_SECONDS };
    long remainder = duration;
    StringBuilder builder = new StringBuilder();
    boolean isFirst = true;
    for (DurationUnit unit : units) {
      long count = remainder / unit.getDuration();
      if (count > 0) {
        if (!isFirst) {
          builder.append(' ');
        }
        builder.append(count);
        builder.append(' ');
        builder.append(unit.getShortName());
        remainder = remainder - (count * unit.getDuration());
        isFirst = false;
      }
    }
    return builder.toString();
  }

  // The long name of the unit.
  private final String longName;

  // The abbreviation of the unit.
  private final String shortName;

  // The size of the unit in milliseconds.
  private final long sz;



  // Private constructor.
  private DurationUnit(long sz, String shortName, String longName) {
    this.sz = sz;
    this.shortName = shortName;
    this.longName = longName;
  }



  /**
   * Converts the specified duration in milliseconds to this unit.
   *
   * @param duration
   *          The duration in milliseconds.
   * @return Returns milliseconds in this unit.
   */
  public double fromMilliSeconds(long duration) {
    return ((double) duration / sz);
  }



  /**
   * Get the number of milliseconds that this unit represents.
   *
   * @return Returns the number of milliseconds that this unit
   *         represents.
   */
  public long getDuration() {
    return sz;
  }



  /**
   * Get the long name of this unit.
   *
   * @return Returns the long name of this unit.
   */
  public String getLongName() {
    return longName;
  }



  /**
   * Get the abbreviated name of this unit.
   *
   * @return Returns the abbreviated name of this unit.
   */
  public String getShortName() {
    return shortName;
  }



  /**
   * Converts the specified duration in this unit to milliseconds.
   *
   * @param duration
   *          The duration as a quantity of this unit.
   * @return Returns the number of milliseconds that the duration
   *         represents.
   */
  public long toMilliSeconds(double duration) {
    return (long) (sz * duration);
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation returns the abbreviated name of this duration
   * unit.
   */
  @Override
  public String toString() {
    return shortName;
  }
}
