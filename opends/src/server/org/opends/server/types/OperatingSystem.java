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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;

/**
 * This class defines an enumeration that may be used to identify
 * the operating system on which the JVM is running.
 *
 * NOTE: to share code this class is used in SetupUtils and should
 * not contain any dependency with other classes (not even with
 * classes in this package).
 * If this class is modified to depend on other classes it will break
 * the quicksetup.  If this must be done, the references to this
 * class in SetupUtils must be removed.
 */
public enum OperatingSystem
{
  /**
   * The value indicating the AIX operating system.
   */
  AIX("AIX"),



  /**
   * The value indicating the FreeBSD operating system.
   */
  FREEBSD("FreeBSD"),



  /**
   * The value indicating the HP-UX operating system.
   */
  HPUX("HP-UX"),



  /**
   * The value indicating the Linux operating system.
   */
  LINUX("Linux"),



  /**
   * The value indicating the Mac OS X operating system.
   */
  MACOS("Mac OS X"),



  /**
   * The value indicating the Solaris operating system.
   */
  SOLARIS("Solaris"),



  /**
   * The value indicating the Windows operating system.
   */
  WINDOWS("Windows"),



  /**
   * The value indicating the z/OS operating system.
   */
  ZOS("z/OS"),



  /**
   * The value indicating an unknown operating system.
   */
  UNKNOWN("Unknown");



  // The human-readable name for this operating system.
  private String osName;



  /**
   * Creates a new operating system value with the provided name.
   *
   * @param  osName  The human-readable name for the operating system.
   */
  private OperatingSystem(String osName)
  {
    this.osName = osName;
  }



  /**
   * Retrieves the human-readable name of this operating system.
   *
   * @return  The human-readable name for this operating system.
   */
  public String toString()
  {
    return osName;
  }



  /**
   * Retrieves the operating system for the provided name.  The name
   * provided should come from the <CODE>os.name</CODE> system
   * property.
   *
   * @param  osName  The name for which to retrieve the corresponding
   *                 operating system.
   *
   * @return  The operating system for the provided name.
   */
  public static OperatingSystem forName(String osName)
  {
    if (osName == null)
    {
      return UNKNOWN;
    }


    String lowerName = osName.toLowerCase();

    if ((lowerName.indexOf("solaris") >= 0) ||
        (lowerName.indexOf("sunos") >= 0))
    {
      return SOLARIS;
    }
    else if (lowerName.indexOf("linux") >= 0)
    {
      return LINUX;
    }
    else if ((lowerName.indexOf("hp-ux") >= 0) ||
             (lowerName.indexOf("hp ux") >= 0) ||
             (lowerName.indexOf("hpux") >= 0))
    {
      return HPUX;
    }
    else if (lowerName.indexOf("aix") >= 0)
    {
      return AIX;
    }
    else if (lowerName.indexOf("windows") >= 0)
    {
      return WINDOWS;
    }
    else if ((lowerName.indexOf("freebsd") >= 0) ||
             (lowerName.indexOf("free bsd") >= 0))
    {
      return FREEBSD;
    }
    else if ((lowerName.indexOf("macos") >= 0) ||
             (lowerName.indexOf("mac os") >= 0))
    {
      return MACOS;
    }
    else  if (lowerName.indexOf("z/os") >= 0)
    {
      return ZOS;
    }
    else
    {
      return UNKNOWN;
    }
  }



  /**
   * Indicates whether the provided operating system is UNIX-based.
   * UNIX-based operating systems include Solaris, Linux, HP-UX, AIX,
   * FreeBSD, and Mac OS X.
   *
   * @param  os  The operating system for which to make the
   *             determination.
   *
   * @return  <CODE>true</CODE> if the provided operating system is
   *          UNIX-based, or <CODE>false</CODE> if not.
   */
  public static boolean isUNIXBased(OperatingSystem os)
  {
    switch (os)
    {
      case SOLARIS:
      case LINUX:
      case HPUX:
      case AIX:
      case FREEBSD:
      case MACOS:
        return true;
      default:
        return false;
    }
  }
}

