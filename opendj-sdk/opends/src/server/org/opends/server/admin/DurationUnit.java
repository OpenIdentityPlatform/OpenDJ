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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
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
   * A millisecond unit.
   */
  MILLI_SECONDS(1L, "ms", "milliseconds"),

  /**
   * A second unit.
   */
  SECONDS(1000L, "s", "seconds"),

  /**
   * A minute unit.
   */
  MINUTES((long) 60 * 1000, "m", "minutes"),

  /**
   * An hour unit.
   */
  HOURS((long) 60 * 60 * 1000, "h", "hours"),

  /**
   * A day unit.
   */
  DAYS((long) 24 * 60 * 60 * 1000, "d", "days"),

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
   * duration in milli-seconds. The duration string must specify the
   * unit e.g. "10s".
   *
   * @param s
   *          The duration string to be parsed.
   * @return Returns the parsed duration in milli-seconds.
   * @throws NumberFormatException
   *           If the provided duration string could not be parsed.
   */
  public static long parseValue(String s) throws NumberFormatException {
    return parseValue(s, null);
  }



  /**
   * Parse the provided duration string and return its equivalent
   * duration in milli-seconds.
   *
   * @param s
   *          The duration string to be parsed.
   * @param defaultUnit
   *          The default unit to use if there is no unit specified in
   *          the duration string, or <code>null</code> if the
   *          string must always contain a unit.
   * @return Returns the parsed duration in milli-seconds.
   * @throws NumberFormatException
   *           If the provided duration string could not be parsed.
   */
  public static long parseValue(String s, DurationUnit defaultUnit)
      throws NumberFormatException {
    // Value must be a floating point number followed by a unit.
    Pattern p = Pattern.compile("^\\s*(\\d+(\\.\\d+)?)\\s*(\\w+)?\\s*$");
    Matcher m = p.matcher(s);

    if (!m.matches()) {
      throw new NumberFormatException("Invalid duration value \"" + s + "\"");
    }

    // Group 1 is the float.
    double d;
    try {
      d = Double.valueOf(m.group(1));
    } catch (NumberFormatException e) {
      throw new NumberFormatException("Invalid duration value \"" + s + "\"");
    }

    // Group 3 is the unit.
    String unitString = m.group(3);
    DurationUnit unit;
    if (unitString == null) {
      if (defaultUnit == null) {
        throw new NumberFormatException("Invalid duration value \"" + s + "\"");
      } else {
        unit = defaultUnit;
      }
    } else {
      try {
        unit = getUnit(unitString);
      } catch (IllegalArgumentException e) {
        throw new NumberFormatException("Invalid duration value \"" + s + "\"");
      }
    }

    return unit.toMilliSeconds(d);
  }

  // The size of the unit in milli-seconds.
  private final long sz;

  // The abbreviation of the unit.
  private final String shortName;

  // The long name of the unit.
  private final String longName;



  // Private constructor.
  private DurationUnit(long sz, String shortName, String longName) {
    this.sz = sz;
    this.shortName = shortName;
    this.longName = longName;
  }



  /**
   * Converts the specified duration in milli-seconds to this unit.
   *
   * @param duration
   *          The duration in milli-seconds.
   * @return Returns milli-seconds in this unit.
   */
  public double fromMilliSeconds(long duration) {
    return ((double) duration / sz);
  }



  /**
   * Get the best-fit unit for the specified duration in this unit.
   * For example, if this unit is minutes and the duration 120 is
   * provided, then the best fit unit is hours: 2h. Similarly, if the
   * duration is 0.5, then the best fit unit will by seconds: 30s.
   *
   * @param duration
   *          The duration.
   * @return Returns the best-fit unit for the specified duration in
   *         this unit.
   */
  public DurationUnit getBestFitUnit(double duration) {
    long ms = toMilliSeconds(duration);
    if (ms == 0) {
      return this;
    } else if (ms > 0) {
      for (DurationUnit unit : new DurationUnit[] { WEEKS, DAYS, HOURS,
          MINUTES, SECONDS }) {
        if ((ms % unit.sz) == 0) {
          return unit;
        }
      }
    }
    return MILLI_SECONDS;
  }



  /**
   * Get the number of milli-seconds that this unit represents.
   *
   * @return Returns the number of milli-seconds that this unit
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
   * Converts the specified duration in this unit to milli-seconds.
   *
   * @param duration
   *          The duration as a quantity of this unit.
   * @return Returns the number of milli-seconds that the duration
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
