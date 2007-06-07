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
import java.util.EnumSet;

/**
   * Representation of an OpenDS build package.
 */
public class Build implements Comparable<Build> {

  /**
   * Describes build types.
   */
  enum Category {

    /**
     * Daily build descriptor.
     */
    DAILY("Daily Build"), // DO NOT i18n

    /**
     * Weekly build descriptor.
     */
    WEEKLY("Weekly Build"), // DO NOT i18n

    /**
     * Release build descriptor.
     */
    RELEASE("Release Build"); // DO NOT i18n

    /**
     * Creates a Category from its 'key' String value.
     * @param s String representing a key
     * @return the Category corresponding to the input <code>key</code>; null
     * if the input string is not a category key
     */
    public static Category fromString(String s) {
      Category category = null;
      for (Category c : EnumSet.allOf(Category.class)) {
        if (c.key.equals(s)) {
          category = c;
          break;
        }
      }
      return category;
    }

    String key;

    private Category(String key) {
      this.key = key;
    }

    /**
     * Gets the string that represents this category in
     * the build information page.
     * @return String key
     */
    public String getKey() {
      return key;
    }

  }

  private URL downloadUrl;
  private String displayName;
  private Category category;

  /**
   * Creates an instance.
   * @param displayName where the build package can be accessed
   * @param downloadUrl of the new build
   * @param category build category
   */
  Build(String displayName, URL downloadUrl, Category category) {
    this.displayName = displayName;
    this.downloadUrl = downloadUrl;
    this.category = category;
  }

  /**
   * Gets the URL where the build can be accessed.
   * @return URL representing access to the build package
   */
  public URL getUrl() {
    return this.downloadUrl;
  }

  /**
   * Gets a string appropriate for presentation to a user.
   * @return String representing this build
   */
  public String getDisplayName() {
    return this.displayName;
  }

  /**
   * Gets the category of this build.
   * @return Category indicating the type of this build.
   */
  public Category getCategory() {
    return this.category;
  }

  /**
   * {@inheritDoc}
   */
  public String toString() {
    return getDisplayName();
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(Build o) {
    if (o == null) throw new NullPointerException();
    int c = getCategory().compareTo(o.getCategory());
    if (c == 0) {
      c = getDisplayName().compareTo(o.getDisplayName());
    }
    return c;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode() {
    int hc = 11;
    Category cat = getCategory();
    if (cat != null) {
      hc = 31 * hc + cat.hashCode();
    }
    String disp = getDisplayName();
    if (disp != null) {
      hc = 31 * hc + disp.hashCode();
    }
    return hc;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object obj) {
    if (this == obj) return true;
    boolean eq = false;
    if (obj != null && obj instanceof Build) {
      Category thisCat = getCategory();
      Category thatCat = ((Build)obj).getCategory();
      if ((thisCat != null && thisCat.equals(thatCat)) ||
          (thisCat == null && thatCat == null)) {
        String thisDisp = getDisplayName();
        String thatDisp = ((Build)obj).getDisplayName();
        eq = ((thisDisp != null && thisDisp.equals(thatDisp)) ||
                (thisDisp == null && thatDisp == null));
      }
    }
    return eq;
  }
}
