/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.util;

/**
 * This file contains a number of constants that are used throughout the
 * Directory Server source.  It was dynamically generated as part of the
 * Directory Server build process and should not be edited directly.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class DynamicConstants
{
  /**
   * The official full product name for the Directory Server.
   */
  public static String PRODUCT_NAME = "${project.name}";

  /**
   * The short product name for the Directory Server.
   */
  public static String SHORT_NAME = "${shortProductName}";

  /**
   * The major version number for the Directory Server.
   */
  public static int MAJOR_VERSION = ${parsedVersion.majorVersion};

  /**
   * The minor version number for the Directory Server.
   */
  public static int MINOR_VERSION = ${parsedVersion.minorVersion};

  /**
   * The point version number for the Directory Server.
   */
  public static int POINT_VERSION = ${parsedVersion.incrementalVersion};

  /**
   * The official build number for the Directory Server.
   */
  public static int BUILD_NUMBER = ${parsedVersion.buildNumber};

  /**
   * The version qualifier string for the Directory Server.
   */
  public static String VERSION_QUALIFIER = "${parsedVersion.qualifier}";

  /**
   * The set of bug IDs for fixes included in this build of the Directory
   * Server.
   */
  public static String FIX_IDS = "${patchFixIds}";

  /**
   * The build ID for the generated build of the Directory Server.
   */
  public static String BUILD_ID = "${buildDateTime}";

  /**
   * The username of the user that created this build.
   */
  public static String BUILD_USER = "${user.name}";

  /**
   * The Java version used to generate this build.
   */
  public static String BUILD_JAVA_VERSION = "${java.version}";

  /**
   * The vendor for the Java version used to generate this build.
   */
  public static String BUILD_JAVA_VENDOR = "${java.vendor}";

  /**
   * The JVM version used to generate this build.
   */
  public static String BUILD_JVM_VERSION = "${java.vm.version}";

  /**
   * The vendor for the JVM used to generate this build.
   */
  public static String BUILD_JVM_VENDOR = "${java.vm.vendor}";

  /**
   * The operating system on which this build was generated.
   */
  public static String BUILD_OS = "${os.name} ${os.version} ${os.arch}";

  /**
   * Indicates whether this is a debug build of the Directory Server that may
   * include additional debugging facilities not available in standard release
   * versions.
   */
  public static boolean DEBUG_BUILD = ${isDebugBuild};

  /**
   * The Subversion revision number on which this build is based.
   */
  public static long REVISION_NUMBER = ${buildRevision};

  /**
   * The Subversion url repository location on which this build is based.
   */
  public static String URL_REPOSITORY = "${scm.url}";

  /**
   * The documentation home.
   */
  public static String DOC_REFERENCE_HOME =
      "${docHomepageUrl}";

  /**
   * The documentation url.
   */
  public static String DOC_REFERENCE_WIKI =
      "${docWikiUrl}";

  /**
   * The documentation url.
   */
  public static String DOC_QUICK_REFERENCE_GUIDE =
      "${docGuideRefUrl}";

  /**
   * The administration guide.
   */
   public static String ADMINISTRATION_GUIDE_URL =
      "${docGuideAdminUrl}";

  /**
   * A string representation of the version number.
   */
  public static String VERSION_NUMBER_STRING = String.format("%s.%s.%s", MAJOR_VERSION, MINOR_VERSION, POINT_VERSION);

  /**
   * A string representation of the version number.
   */
  public static String OPENDJ_NUMBER_STRING = VERSION_NUMBER_STRING;

  /**
   * A string representation of the release version.
   */
  public static String RELEASE_VERSION_STRING =
       OPENDJ_NUMBER_STRING;


  /**
   * Test if a specific class is provided to overwrite the BUILD definitions
   * By the release definitions provided by
   * org.opends.server.util.ReleaseDefinition
   */

  static {

     try {
        Class c;
        if (org.opends.server.util.SetupUtils.isWebStart())
        {
          Class<?> cS = Class.forName("org.opends.server.util.SetupUtils");
          java.net.URL[] urls = new java.net.URL[]
          {
            cS.getProtectionDomain().getCodeSource().getLocation()
          };
          ClassLoader webstartClassLoader = new java.net.URLClassLoader(urls);
          c = webstartClassLoader.loadClass(
              "org.opends.server.util.ReleaseDefinition");
        }
        else
        {
          c = Class.forName("org.opends.server.util.ReleaseDefinition");
        }
        Object obj = c.newInstance();

        try {
         PRODUCT_NAME = (String)c.getField("PRODUCT_NAME").get(obj);
        }
        catch (Exception ex) {}
        try {
         SHORT_NAME = (String)c.getField("SHORT_NAME").get(obj);
        }
        catch (Exception ex) {}
        try {
         MAJOR_VERSION = (Integer)c.getField("MAJOR_VERSION").get(obj);
        }
        catch (Exception ex) {}
        try {
         MINOR_VERSION = (Integer)c.getField("MINOR_VERSION").get(obj);
        }
        catch (Exception ex) {}
        try {
         POINT_VERSION = (Integer)c.getField("POINT_VERSION").get(obj);
        }
        catch (Exception ex) {}
        try {
         BUILD_NUMBER = (Integer)c.getField("BUILD_NUMBER").get(obj);
        }
        catch (Exception ex) {}
        try {
         VERSION_QUALIFIER = (String)c.getField("VERSION_QUALIFIER").get(obj);
        }
        catch (Exception ex) {}
        try {
         FIX_IDS = (String)c.getField("FIX_IDS").get(obj);
        }
        catch (Exception ex) {}
        try{
         BUILD_ID = (String)c.getField("BUILD_ID").get(obj);
        }
        catch (Exception ex) {}
        try{
         BUILD_USER = (String)c.getField("BUILD_USER").get(obj);
        }
        catch (Exception ex) {}
        try{
         REVISION_NUMBER = (Long)c.getField("REVISION_NUMBER").get(obj);
        }
        catch (Exception ex) {}
        try{
         BUILD_JAVA_VERSION = (String)c.getField("BUILD_JAVA_VERSION").get(obj);
        }
        catch (Exception ex) {}
        try{
         BUILD_JAVA_VENDOR = (String)c.getField("BUILD_JAVA_VENDOR").get(obj);
        }
        catch (Exception ex) {}
        try{
         BUILD_JVM_VERSION = (String)c.getField("BUILD_JVM_VERSION").get(obj);
        }
        catch (Exception ex) {}
        try{
         BUILD_JVM_VENDOR = (String)c.getField("BUILD_JVM_VENDOR").get(obj);
        }
        catch (Exception ex) {}
        try{
         BUILD_OS = (String)c.getField("BUILD_OS").get(obj);
        }
        catch (Exception ex) {}
        try{
         DEBUG_BUILD = (Boolean)c.getField("DEBUG_BUILD").get(obj);
        }
        catch (Exception ex) {}
        try{
         URL_REPOSITORY = (String)c.getField("URL_REPOSITORY").get(obj);
        }
        catch (Exception ex) {}
        try{
         DOC_REFERENCE_WIKI =
            (String)c.getField("DOC_REFERENCE_WIKI").get(obj);
        }
        catch(Exception ex) {}
        try{
         DOC_QUICK_REFERENCE_GUIDE =
            (String)c.getField("DOC_QUICK_REFERENCE_GUIDE").get(obj);
        }
        catch(Exception ex) {}
        try{
         ADMINISTRATION_GUIDE_URL =
           (String)c.getField("ADMINISTRATION_GUIDE_URL").get(obj);
        }
        catch(Exception ex) {}
        try{
         VERSION_NUMBER_STRING =
                 (String)c.getField("VERSION_NUMBER_STRING").get(obj);
        }
        catch (Exception ex) {}
        try{
         RELEASE_VERSION_STRING = VERSION_NUMBER_STRING
                 + " (OpenDJ version = "
                 + OPENDJ_NUMBER_STRING + ")" ;
        }
        catch (Exception ex) {}
      } catch (Exception ex) {
      }
  }
   /**
   * A compact version string for this product, suitable for use in path
   * names and similar cases.
   */
  public static String COMPACT_VERSION_STRING =
       SHORT_NAME + "-" + VERSION_NUMBER_STRING;

  /**
   * A full version string for this product.
   */
  public static String FULL_VERSION_STRING = PRODUCT_NAME + " " + RELEASE_VERSION_STRING
      + (VERSION_QUALIFIER != null && !VERSION_QUALIFIER.isEmpty() ? "-" + VERSION_QUALIFIER : "")
      + (FIX_IDS != null && !FIX_IDS.isEmpty() ? "+" + FIX_IDS : "");

  /**
   * A printable version string for this product.
   */
  public static final String PRINTABLE_VERSION_STRING =
       FULL_VERSION_STRING + System.getProperty("line.separator") +
       "Build " + BUILD_ID + System.getProperty("line.separator");

}
