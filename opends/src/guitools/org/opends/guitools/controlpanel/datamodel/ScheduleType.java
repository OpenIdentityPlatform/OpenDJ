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
package org.opends.guitools.controlpanel.datamodel;

import java.util.Date;

/**
 * The class to be used to describe the task schedule.
 *
 */
public class ScheduleType
{
  /**
   * The different type of schedules.
   *
   */
  public enum Type
  {
    /**
     * Launch now.
     */
    LAUNCH_NOW,
    /**
     * Launch later in a specific date.
     */
    LAUNCH_LATER,
    /**
     * Launch periodically.
     */
    LAUNCH_PERIODICALLY
  }

  private Type type;
  private Date launchLaterDate;
  private String cronValue;
  private String toString;
  private int hashCode;

  private ScheduleType()
  {
  }

  /**
   * Returns a schedule instance that launches the task now.
   * @return a schedule instance that launches the task now.
   */
  public static ScheduleType createLaunchNow()
  {
    ScheduleType schedule = new ScheduleType();
    schedule.type = Type.LAUNCH_NOW;
    schedule.toString = schedule.calculateToString();
    schedule.hashCode = schedule.calculateHashCode();
    return schedule;
  }

  /**
   * Returns a schedule instance that launches the task at a given date.
   * @param date the Date at which the task must be launched.
   * @return a schedule instance that launches the task at a given date.
   */
  public static ScheduleType createLaunchLater(Date date)
  {
    ScheduleType schedule = new ScheduleType();
    schedule.type = Type.LAUNCH_LATER;
    schedule.launchLaterDate = date;
    schedule.toString = schedule.calculateToString();
    schedule.hashCode = schedule.calculateHashCode();
    return schedule;
  }

  /**
   * Returns a schedule instance that launches the task using a cron schedule.
   * @param cron the String containing the cron schedule.
   * @return a schedule instance that launches the task using a cron schedule.
   */
  public static ScheduleType createCron(String cron)
  {
    ScheduleType schedule = new ScheduleType();
    schedule.type = Type.LAUNCH_PERIODICALLY;
    schedule.cronValue = cron;
    schedule.toString = schedule.calculateToString();
    schedule.hashCode = schedule.calculateHashCode();
    return schedule;
  }

  /**
   * Returns the type of the schedule.
   * @return the type of the schedule.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the date on which the task will be launched.
   * @return the date on which the task will be launched.
   */
  public Date getLaunchLaterDate()
  {
    return launchLaterDate;
  }

  /**
   * Returns the CRON String representation of the schedule.
   * @return the CRON String representation of the schedule.
   */
  public String getCronValue()
  {
    return cronValue;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object o)
  {
    boolean equals;
    if (o != null)
    {
      if (o == this)
      {
        equals = true;
      }
      else
      {
        equals = toString().equals(o.toString());
      }
    }
    else
    {
      equals = false;
    }
    return equals;
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return toString;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return hashCode;
  }

  /**
   * Calculates the hashCode.
   * To be called after the calculateToString is called.
   * @return the value of the hashCode.
   */
  private int calculateHashCode()
  {
    return 32 + toString.hashCode();
  }

  private String calculateToString()
  {
    String toString;
    switch (type)
    {
    case LAUNCH_NOW:
      toString = "Schedule Type: Launch Now";
      break;
    case LAUNCH_LATER:
      toString = "Schedule Type: Launch Later at date "+launchLaterDate;
      break;
    case LAUNCH_PERIODICALLY:
      toString = "Schedule Type: periodical schedule "+cronValue;
      break;
      default:
        throw new RuntimeException("Invalid type: "+type);
    }
    return toString;
  }
}
