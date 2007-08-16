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

package org.opends.server.util;

/**
 * Represents a particular version of OpenDS useful for making
 * comparisons between versions.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class BuildVersion implements Comparable<BuildVersion> {

  /** Major release number. */
  int major;

  /** Minor release number. */
  int minor;

  /** Point release number. */
  int point;

  /** Subversion revision number. */
  long rev;

  /**
   * Creates a new instance using current build data.
   *
   * @return BuildVersion representing current data
   */
  static public BuildVersion getCurrent() {
    return new BuildVersion(
            DynamicConstants.MAJOR_VERSION,
            DynamicConstants.MINOR_VERSION,
            DynamicConstants.POINT_VERSION,
            DynamicConstants.REVISION_NUMBER);
  }

  /**
   * Constructs an instance from build data.
   * @param major release number
   * @param minor release number
   * @param point release number
   * @param rev Subversion revision number
   */
  public BuildVersion(int major, int minor, int point, long rev) {
    this.major = major;
    this.minor = minor;
    this.point = point;
    this.rev = rev;
  }

  /**
   * Gets the major release number.
   * @return int major release number
   */
  public int getMajorVersion() {
    return major;
  }

  /**
   * Gets the minor release number.
   * @return int minor release number
   */
  public int getMinorVersion() {
    return minor;
  }

  /**
   * Gets the point release number.
   * @return int point release number
   */
  public int getPointVersion() {
    return point;
  }

  /**
   * Gets the Subversion revision number.
   * @return long Subversion revision number
   */
  public long getRevisionNumber() {
    return rev;
  }

  /**
   * Retrieves an integer value that indicates the relative order between this
   * build version and the provided build version object.
   *
   * @param  version  The build version object for which to make the
   *                  determination.
   *
   * @return  A negative integer if this build version should be ordered before
   *          the provided build version in a sorted list, a positive integer if
   *          this build version should be ordered after the provided build
   *          version in a sorted list, or zero if there is no difference in the
   *          relative order between the build version objects.
   */
  public int compareTo(BuildVersion version) {
    if (major == version.major) {
      if (minor == version.minor) {
        if (point == version.point) {
          if (rev == version.rev) {
            return 0;
          } else if (rev < version.rev) {
            return -1;
          }
        } else if (point < version.point) {
          return -1;
        }
      } else if (minor < version.minor) {
        return -1;
      }
    } else if (major < version.major) {
      return -1;
    }
    return 1;
  }

}
