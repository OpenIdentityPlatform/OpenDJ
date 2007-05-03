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
package org.opends.build.tools;

import org.apache.tools.ant.taskdefs.condition.Condition;
import org.apache.tools.ant.BuildException;

/**
 * Ant condition to check whether we have a minimum required Java version.
 */
public class ValidJavaVersion implements Condition
{
  // The minimum required Java version.
  String minVersion;

  /**
   * Set the minVersion attribute.
   * @param minVersion The minimum required Java version.
   */
  public void setMinVersion(String minVersion)
  {
    this.minVersion = minVersion;
  }


  /**
   * Evaluate the condition.
   */
  public boolean eval() throws BuildException
  {
    if (minVersion == null)
    {
      return true;
    }

    String version = System.getProperty("java.version");
    if (version == null)
    {
      return false;
    }

    return version.compareTo(minVersion) >= 0;
  }
}
