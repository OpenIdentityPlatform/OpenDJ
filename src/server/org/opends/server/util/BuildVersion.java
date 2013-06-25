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
 *      Portions copyright 2013 ForgeRock AS.
 */

package org.opends.server.util;

import static org.opends.messages.ToolMessages.ERR_BUILDVERSION_NOT_FOUND;
import static org.opends.messages.ToolMessages.ERR_BUILDVERSION_MALFORMED;
import static org.opends.messages.ToolMessages.ERR_BUILDVERSION_MISMATCH;
import static org.opends.server.config.ConfigConstants.CONFIG_DIR_NAME;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;

/**
 * Represents a particular version of OpenDJ useful for making comparisons
 * between versions.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false, mayExtend = false, mayInvoke = true)
public final class BuildVersion implements Comparable<BuildVersion>
{

  private final int major;
  private final int minor;
  private final int point;
  private final long rev;
  private static final BuildVersion BINARY_VERSION = new BuildVersion(
      DynamicConstants.MAJOR_VERSION, DynamicConstants.MINOR_VERSION,
      DynamicConstants.POINT_VERSION, DynamicConstants.REVISION_NUMBER);

  /**
   * Returns the build version as specified by the dynamic constants.
   *
   * @return The build version as specified by the dynamic constants.
   */
  public static BuildVersion binaryVersion()
  {
    return BINARY_VERSION;
  }

  /**
   * Reads the instance version from config/buildinfo.
   *
   * @return The instance version from config/buildinfo.
   * @throws InitializationException
   *           If an error occurred while reading or parsing the version.
   */
  public static BuildVersion instanceVersion() throws InitializationException
  {
    final String buildInfo =
        DirectoryServer.getInstanceRoot() + File.separator + CONFIG_DIR_NAME
            + File.separator + "buildinfo";
    BufferedReader reader = null;
    try
    {
      reader = new BufferedReader(new FileReader(buildInfo));
      final String s = reader.readLine();
      if (s != null)
      {
        return valueOf(s);
      }
      else
      {
        throw new InitializationException(ERR_BUILDVERSION_MALFORMED
            .get(buildInfo));
      }
    }
    catch (IOException e)
    {
      throw new InitializationException(ERR_BUILDVERSION_NOT_FOUND
          .get(buildInfo));
    }
    catch (final IllegalArgumentException e)
    {
      throw new InitializationException(ERR_BUILDVERSION_MALFORMED
          .get(buildInfo));
    }
    finally
    {
      if (reader != null)
      {
        try
        {
          reader.close();
        }
        catch (final Exception e)
        {
          // Ignore.
        }
      }
    }
  }

  /**
   * Checks if the binary version is the same than the instance version.
   *
   * @throws InitializationException
   *           Sends an exception if the version mismatch.
   */
  public static void checkVersionMismatch() throws InitializationException
  {
    if (!BuildVersion.binaryVersion().toString().equals(
        BuildVersion.instanceVersion().toString()))
    {
      throw new InitializationException(ERR_BUILDVERSION_MISMATCH.get(
          BuildVersion.binaryVersion().toString(), BuildVersion
              .instanceVersion().toString()));
    }
  }

  /**
   * Parses the string argument as a build version. The string must be of the
   * form:
   *
   * <pre>
   * major.minor.point.rev
   * </pre>
   *
   * @param s
   *          The string to be parsed as a build version.
   * @return The parsed build version.
   * @throws IllegalArgumentException
   *           If the string does not contain a parsable build version.
   */
  public static BuildVersion valueOf(final String s)
      throws IllegalArgumentException
  {
    final String[] fields = s.split("\\.");
    if (fields.length != 4)
    {
      throw new IllegalArgumentException("Invalid version string " + s);
    }
    final int major = Integer.parseInt(fields[0]);
    final int minor = Integer.parseInt(fields[1]);
    final int point = Integer.parseInt(fields[2]);
    final long rev = Long.parseLong(fields[3]);
    return new BuildVersion(major, minor, point, rev);
  }

  /**
   * Creates a new build version using the provided version information.
   *
   * @param major
   *          Major release version number.
   * @param minor
   *          Minor release version number.
   * @param point
   *          Point release version number.
   * @param rev
   *          VCS revision number.
   */
  public BuildVersion(final int major, final int minor, final int point,
      final long rev)
  {
    this.major = major;
    this.minor = minor;
    this.point = point;
    this.rev = rev;
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(final BuildVersion version)
  {
    if (major == version.major)
    {
      if (minor == version.minor)
      {
        if (point == version.point)
        {
          if (rev == version.rev)
          {
            return 0;
          }
          else if (rev < version.rev)
          {
            return -1;
          }
        }
        else if (point < version.point)
        {
          return -1;
        }
      }
      else if (minor < version.minor)
      {
        return -1;
      }
    }
    else if (major < version.major)
    {
      return -1;
    }
    return 1;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(final Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    else if (obj instanceof BuildVersion)
    {
      final BuildVersion other = (BuildVersion) obj;
      return (major == other.major) && (minor == other.minor)
          && (point == other.point) && (rev == other.rev);
    }
    else
    {
      return false;
    }
  }

  /**
   * Returns the major release version number.
   *
   * @return The major release version number.
   */
  public int getMajorVersion()
  {
    return major;
  }

  /**
   * Returns the minor release version number.
   *
   * @return The minor release version number.
   */
  public int getMinorVersion()
  {
    return minor;
  }

  /**
   * Returns the point release version number.
   *
   * @return The point release version number.
   */
  public int getPointVersion()
  {
    return point;
  }

  /**
   * Returns the VCS revision number.
   *
   * @return The VCS revision number.
   */
  public long getRevisionNumber()
  {
    return rev;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return Arrays.hashCode(new int[] { major, minor, point, (int) (rev >>> 32),
      (int) (rev & 0xFFFFL) });
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append(major);
    builder.append('.');
    builder.append(minor);
    builder.append('.');
    builder.append(point);
    builder.append('.');
    builder.append(rev);
    return builder.toString();
  }

}
