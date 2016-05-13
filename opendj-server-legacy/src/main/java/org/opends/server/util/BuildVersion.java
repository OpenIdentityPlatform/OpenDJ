/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.util;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

import org.forgerock.util.Utils;
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
  private final String rev;
  private static final BuildVersion BINARY_VERSION = new BuildVersion(
      DynamicConstants.MAJOR_VERSION, DynamicConstants.MINOR_VERSION,
      DynamicConstants.POINT_VERSION, DynamicConstants.REVISION);

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
    final String buildInfo = Paths.get(DirectoryServer.getInstanceRoot(), CONFIG_DIR_NAME, "buildinfo").toString();
    try (final BufferedReader reader = new BufferedReader(new FileReader(buildInfo)))
    {
      final String s = reader.readLine();
      if (s == null)
      {
        throw new InitializationException(ERR_BUILDVERSION_MALFORMED.get(buildInfo));
      }
      return valueOf(s);
    }
    catch (FileNotFoundException e)
    {
      throw new InitializationException(ERR_INSTANCE_NOT_CONFIGURED.get(), e);
    }
    catch (IOException e)
    {
      throw new InitializationException(ERR_BUILDVERSION_NOT_FOUND.get(buildInfo));
    }
    catch (final IllegalArgumentException e)
    {
      throw new InitializationException(ERR_BUILDVERSION_MALFORMED.get(buildInfo));
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
    if (!BuildVersion.binaryVersion().equals(BuildVersion.instanceVersion()))
    {
      throw new InitializationException(
          ERR_BUILDVERSION_MISMATCH.get(BuildVersion.binaryVersion(), BuildVersion.instanceVersion()));
    }
  }

  /**
   * Parses the string argument as a build version. The string must be of the
   * form:
   *
   * <pre>
   * major.minor.point[.rev]
   * </pre>
   *
   * @param s
   *          The string to be parsed as a build version.
   * @return The parsed build version.
   * @throws IllegalArgumentException
   *           If the string does not contain a parsable build version.
   */
  public static BuildVersion valueOf(final String s) throws IllegalArgumentException
  {
    final String[] fields = s.split("\\.");
    final int nbFields = fields.length;
    if (!(nbFields == 3 || nbFields == 4))
    {
      throw new IllegalArgumentException("Invalid version string " + s);
    }
    final int major = Integer.parseInt(fields[0]);
    final int minor = Integer.parseInt(fields[1]);
    final int point = Integer.parseInt(fields[2]);

    if (nbFields == 4)
    {
      return new BuildVersion(major, minor, point, fields[3]);
    }
    else
    {
      return new BuildVersion(major, minor, point);
    }
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
   */
  private BuildVersion(final int major, final int minor, final int point)
  {
    this(major, minor, point, "");
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
   *          VCS revision.
   */
  private BuildVersion(final int major, final int minor, final int point, final String rev)
  {
    this.major = major;
    this.minor = minor;
    this.point = point;
    this.rev = rev;
  }

  @Override
  public int compareTo(final BuildVersion version)
  {
    if (major == version.major)
    {
      if (minor == version.minor)
      {
        if (point == version.point)
        {
          return 0;
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

  @Override
  public boolean equals(final Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    else if (obj instanceof BuildVersion)
    {
      final BuildVersion other = (BuildVersion) obj;
      return major == other.major && minor == other.minor && point == other.point;
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
   * Returns the VCS revision.
   *
   * @return The VCS revision.
   */
  public String getRevision()
  {
    return rev;
  }

  @Override
  public int hashCode()
  {
    return Arrays.hashCode(new int[] { major, minor, point });
  }

  @Override
  public String toString()
  {
    if (!rev.isEmpty())
    {
      return Utils.joinAsString(".", major, minor, point, rev);
    }
    return Utils.joinAsString(".", major, minor, point);
  }

  /**
   * Returns {@code true} if the version is newer than the provided version.
   *
   * @param version
   *          The version to be compared
   * @return {@code true} if the version is newer than the provided version.
   */
  public boolean isNewerThan(final BuildVersion version)
  {
    return this.compareTo(version) >= 0;
  }

  /**
   * Returns {@code true} if the version is older than the provided version.
   *
   * @param version
   *          The version to be compared
   * @return {@code true} if the version is older than the provided version.
   */
  public boolean isOlderThan(final BuildVersion version)
  {
    return this.compareTo(version) <= 0;
  }
}
