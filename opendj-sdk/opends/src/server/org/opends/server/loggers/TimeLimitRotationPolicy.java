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

import org.opends.server.util.TimeThread;

/**
 * This class implements a fixed time based rotation policy.
 * Rotation will happen N seconds since the last rotation.
 */
public class TimeLimitRotationPolicy implements RotationPolicy
{

  private long timeInterval = 0;
  private long lastModifiedTime = 0;

  /**
   * Create the time based rotation policy.
   *
   * @param time The time interval between rotations.
   */
  public TimeLimitRotationPolicy(long time)
  {

    timeInterval = time;
    lastModifiedTime = TimeThread.getTime();
  }


  /**
   * This method indicates if the log file should be
   * rotated or not.
   *
   * @return true if the file should be rotated, false otherwise.
   */
  public boolean rotateFile()
  {

    long currTime = TimeThread.getTime();
    if (currTime - lastModifiedTime > timeInterval)
    {
      do
      {
        lastModifiedTime += timeInterval;
      }
      while (lastModifiedTime < currTime);

      // lastModifiedTime = currTime;
      return true;
    }

    return false;
  }

}

