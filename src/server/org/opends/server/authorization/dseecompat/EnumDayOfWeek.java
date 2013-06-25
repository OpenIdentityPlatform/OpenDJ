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

package org.opends.server.authorization.dseecompat;

import java.util.Calendar;

/**
 * This class provides an enumeration of the allowed dayofweek types.
 */
public enum EnumDayOfWeek {

    /**
     * The enumeration type when the bind rule has specified dayofweek type of
     * "mon".
     */
    DAY_MONDAY      ("mon"),
    /**
     * The enumeration type when the bind rule has specified dayofweek type of
     * "tue" .
     */
    DAY_TUESDAY     ("tue"),
    /**
     * The enumeration type when the bind rule has specified dayofweek type of
     * "wed".
     */
    DAY_WEDNESDAY   ("wed"),
    /**
     * The enumeration type when the bind rule has specified dayofweek type of
     * "thu".
     */
    DAY_THURSDAY    ("thu"),
    /**
     * The enumeration type when the bind rule has specified dayofweek type of
     * "fri".
     */
    DAY_FRIDAY      ("fri"),
    /**
     * The enumeration type when the bind rule has specified dayofweek type of
     * "sat".
     */
    DAY_SATURDAY    ("sat"),
    /**
     * The enumeration type when the bind rule has specified dayofweek type of
     * "sun".
     */
    DAY_SUNDAY      ("sun");

    /*
    * The bind rule dayofweek type name.
     */
    private String day = null;

    /**
     * Creates a new enumeration type for the specified bind rule dayofweek
     * type.
     * @param day  The day name.
     */
    EnumDayOfWeek (String day){
        this.day = day;
    }

    /**
     * Creates a new enumeration type for the specified bind rule dayofweek
     * type.
     * @param day  The boolean type name.
     * @return  True if the keyword is equal to the specified name.
     */
    public boolean isDayOfWeek(String day){
        return day.equalsIgnoreCase(this.day);
    }

    /**
     * Create a new enumeration type for the specified dayofweek type name.
     * @param day  The name of the enumeration to create.
     * @return A new enumeration type for the name or null if the name is
     * not valid.
     */
    public static EnumDayOfWeek createDayOfWeek(String day)
    {
        if (day != null){
            for (EnumDayOfWeek t : EnumDayOfWeek.values()){
                if (t.isDayOfWeek(day)){
                    return t;
                }
            }
        }
        return null;
    }

    /*
     * TODO Evaluate supporting alternative forms for days of the week.
     *
     *  Should we support alternate forms for the names of the days of the
     *  week in the isDayOfWeek() or createdayOfWeek() method?  In particular,
     *  should we handle the case in which the user provided the full name
     *  (e.g., "monday" instead of "mon")?
     */
    /**
     *  Return a enumeration relating to a Calendar day of week field.
     * @param day The day of week index to get.
     * @return  An enumeration corresponding to the wanted day of the week or
     * null if the day index is invalid.
     */
    public static EnumDayOfWeek getDayOfWeek(int day)
    {
        switch(day){
        case Calendar.SUNDAY:
            return DAY_SUNDAY;

        case Calendar.MONDAY:
            return DAY_MONDAY;

        case Calendar.TUESDAY:
            return DAY_TUESDAY;

        case Calendar.WEDNESDAY:
            return DAY_WEDNESDAY;

        case Calendar.THURSDAY:
            return DAY_THURSDAY;

        case Calendar.FRIDAY:
            return DAY_FRIDAY;

        case Calendar.SATURDAY:
            return DAY_SATURDAY;
        }
        return null;
    }
}
