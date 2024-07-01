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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.monitors;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.api.MonitorData;
import org.forgerock.opendj.server.config.server.VersionMonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.types.InitializationException;
import org.opends.server.util.DynamicConstants;

/** This class defines a monitor provider that reports Directory Server version information. */
public class VersionMonitorProvider
       extends MonitorProvider<VersionMonitorProviderCfg>
{
  /** The name of the attribute used to provide the product name. */
  public static final String ATTR_PRODUCT_NAME = "productName";
  /** The name of the attribute used to provide the short name. */
  public static final String ATTR_SHORT_NAME = "shortName";
  /** The name of the attribute used to provide the major version number. */
  public static final String ATTR_MAJOR_VERSION = "majorVersion";
  /** The name of the attribute used to provide the minor version number. */
  public static final String ATTR_MINOR_VERSION = "minorVersion";
  /** The name of the attribute used to provide the point version number. */
  public static final String ATTR_POINT_VERSION = "pointVersion";
  /** The name of the attribute used to provide the version qualifier string. */
  public static final String ATTR_VERSION_QUALIFIER = "versionQualifier";
  /** The name of the attribute used to provide the weekly build number. */
  public static final String ATTR_BUILD_NUMBER = "buildNumber";
  /** The name of the attribute used to provide the list of bugfix IDs. */
  public static final String ATTR_FIX_IDS = "fixIDs";
  /** The name of the attribute used to provide the revision in the version control system. */
  public static final String ATTR_REVISION = "revision";
  /** The name of the attribute used to provide the build ID (aka the build timestamp). */
  public static final String ATTR_BUILD_ID = "buildID";
  /** The name of the attribute used to provide the compact version string. */
  public static final String ATTR_COMPACT_VERSION = "compactVersion";
  /** The name of the attribute used to provide the full version string. */
  public static final String ATTR_FULL_VERSION = "fullVersion";

  @Override
  public void initializeMonitorProvider(VersionMonitorProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }

  @Override
  public String getMonitorInstanceName()
  {
    return "Version";
  }

  @Override
  public MonitorData getMonitorData()
  {
    MonitorData attrs = new MonitorData(12);

    attrs.add(ATTR_PRODUCT_NAME, DynamicConstants.PRODUCT_NAME);
    attrs.add(ATTR_SHORT_NAME, DynamicConstants.SHORT_NAME);
    attrs.add(ATTR_MAJOR_VERSION, DynamicConstants.MAJOR_VERSION);
    attrs.add(ATTR_MINOR_VERSION, DynamicConstants.MINOR_VERSION);
    attrs.add(ATTR_POINT_VERSION, DynamicConstants.POINT_VERSION);

    String versionQualifier = DynamicConstants.VERSION_QUALIFIER;
    if (versionQualifier != null && versionQualifier.length() > 0)
    {
      attrs.add(ATTR_VERSION_QUALIFIER, versionQualifier);
    }

    int buildNumber = DynamicConstants.BUILD_NUMBER;
    if (buildNumber > 0)
    {
      attrs.add(ATTR_BUILD_NUMBER, buildNumber);
    }

    String fixIDs = DynamicConstants.FIX_IDS;
    if (fixIDs != null && fixIDs.length() > 0)
    {
      attrs.add(ATTR_FIX_IDS, fixIDs);
    }

    attrs.add(ATTR_REVISION, DynamicConstants.REVISION);
    attrs.add(ATTR_BUILD_ID, DynamicConstants.BUILD_ID);
    attrs.add(ATTR_COMPACT_VERSION, DynamicConstants.COMPACT_VERSION_STRING);
    attrs.add(ATTR_FULL_VERSION, DynamicConstants.FULL_VERSION_STRING);

    return attrs;
  }
}
