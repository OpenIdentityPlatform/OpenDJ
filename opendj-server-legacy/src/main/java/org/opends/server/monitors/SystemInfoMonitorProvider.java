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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.monitors;

import static org.opends.server.util.ServerConstants.*;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.api.MonitorData;
import org.forgerock.opendj.server.config.server.SystemInfoMonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;

/**
 * This class defines a Directory Server monitor provider that can be used to
 * collect information about the system and the JVM on which the Directory
 * Server is running.
 */
public class SystemInfoMonitorProvider
       extends MonitorProvider<SystemInfoMonitorProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  @Override
  public void initializeMonitorProvider(
                   SystemInfoMonitorProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }

  @Override
  public String getMonitorInstanceName()
  {
    return "System Information";
  }

  @Override
  public MonitorData getMonitorData()
  {
    MonitorData attrs = new MonitorData(13);

    attrs.add("javaVersion", System.getProperty("java.version"));
    attrs.add("javaVendor", System.getProperty("java.vendor"));
    attrs.add("jvmVersion", System.getProperty("java.vm.version"));
    attrs.add("jvmVendor", System.getProperty("java.vm.vendor"));
    attrs.add("javaHome", System.getProperty("java.home"));
    attrs.add("classPath", System.getProperty("java.class.path"));
    attrs.add("workingDirectory", System.getProperty("user.dir"));

    String osInfo = System.getProperty("os.name") + " " +
                    System.getProperty("os.version") + " " +
                    System.getProperty("os.arch");
    attrs.add("operatingSystem", osInfo);
    String sunOsArchDataModel = System.getProperty("sun.arch.data.model");
    if (sunOsArchDataModel != null)
    {
      String jvmArch = sunOsArchDataModel;
      if (! sunOsArchDataModel.toLowerCase().equals("unknown"))
      {
        jvmArch += "-bit";
      }
      attrs.add("jvmArchitecture", jvmArch);
    }
    else
    {
      attrs.add("jvmArchitecture", "unknown");
    }

    try
    {
      attrs.add("systemName", InetAddress.getLocalHost().getCanonicalHostName());
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }


    Runtime runtime = Runtime.getRuntime();
    attrs.add("availableCPUs", runtime.availableProcessors());
    attrs.add("maxMemory", runtime.maxMemory());
    attrs.add("usedMemory", runtime.totalMemory());
    attrs.add("freeUsedMemory", runtime.freeMemory());
    String installPath = DirectoryServer.getServerRoot();
    if (installPath != null)
    {
      attrs.add("installPath", installPath);
    }
    String instancePath = DirectoryServer.getInstanceRoot();
    if (instancePath != null)
    {
      attrs.add("instancePath", instancePath);
    }

    // Get the JVM input arguments.
    RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
    List<String> jvmArguments = rtBean.getInputArguments();
    if (jvmArguments != null && ! jvmArguments.isEmpty())
    {
      StringBuilder argList = new StringBuilder();
      for (String jvmArg : jvmArguments)
      {
        if (argList.length() > 0)
        {
          argList.append(" ");
        }

        argList.append("\"");
        argList.append(jvmArg);
        argList.append("\"");
      }

      attrs.add("jvmArguments", argList);
    }

    try
    {
      final SSLContext context = SSLContext.getDefault();
      final SSLParameters parameters = context.getSupportedSSLParameters();
      attrs.add(ATTR_SUPPORTED_TLS_PROTOCOLS, Arrays.asList(parameters.getProtocols()));
      attrs.add(ATTR_SUPPORTED_TLS_CIPHERS, Arrays.asList(parameters.getCipherSuites()));
    }
    catch (Exception e)
    {
      // A default SSL context should always be available.
      attrs.add(ATTR_SUPPORTED_TLS_PROTOCOLS, Collections.emptyList());
      attrs.add(ATTR_SUPPORTED_TLS_CIPHERS, Collections.emptyList());
    }

    return attrs;
  }
}
