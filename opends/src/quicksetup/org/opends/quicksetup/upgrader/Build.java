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

package org.opends.quicksetup.upgrader;

import java.net.URL;

/**
   * Representation of an OpenDS build package.
 */
public class Build {

  private URL url;
  private String id;

  /**
   * Creates an instance.
   * @param url where the build package can be accessed
   * @param id of the new build
   */
  Build(URL url, String id) {
    this.url = url;
    this.id = id;
  }

  /**
   * Gets the URL where the build can be accessed.
   * @return URL representing access to the build package
   */
  public URL getUrl() {
    return url;
  }

  /**
   * Gets the builds ID number, a 14 digit number representing the time
   * the build was created.
   * @return String represenging the build
   */
  public String getId() {
    return id;
  }

  /**
   * Gets a string appropriate for presentation to a user.
   * @return String representing this build
   */
  public String getDisplayName() {
    return getId();
  }

  /**
   * {@inheritDoc}
   */
  public String toString() {
    return getDisplayName();
  }
}
