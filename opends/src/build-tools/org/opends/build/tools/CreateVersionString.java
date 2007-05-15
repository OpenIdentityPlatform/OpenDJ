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



import java.text.DecimalFormat;

import org.apache.tools.ant.Task;



/**
 * This class provides an implemenation of an Ant task that may be used to
 * construct the full version number string that the Directory Server should
 * use.  The value of the version number string will be stored in an Ant
 * property.
 */
public class CreateVersionString
       extends Task
{
  // The name of the property in which the revision number should be set.
  private String propertyName = null;



  /**
   * Specifies the name of the Ant property into which the Subversion revision
   * number will be stored.
   *
   * @param  propertyName  The name of the Ant property into which the
   *                       Subversion revision number will be stored.
   */
  public void setProperty(String propertyName)
  {
    this.propertyName = propertyName;
  }



  /**
   * Performs the appropriate processing needed for this task.  In this case,
   * it uses SVNKit to identify the current revision number for the local
   * workspace and store it in a specified property.
   */
  @Override()
  public void execute()
  {
    StringBuilder versionString = new StringBuilder();

    versionString.append(getProject().getProperty("MAJOR_VERSION"));
    versionString.append(".");
    versionString.append(getProject().getProperty("MINOR_VERSION"));
    versionString.append(".");
    versionString.append(getProject().getProperty("POINT_VERSION"));

    String versionQualifier = getProject().getProperty("VERSION_QUALIFIER");
    versionString.append(versionQualifier);

    try
    {
      int buildNumber =
           Integer.parseInt(getProject().getProperty("BUILD_NUMBER"));
      if (buildNumber > 0)
      {
        versionString.append("-build");
        versionString.append(new DecimalFormat("000").format(buildNumber));
      }
    } catch (NumberFormatException nfe) {}

    getProject().setNewProperty(propertyName, versionString.toString());
  }
}

