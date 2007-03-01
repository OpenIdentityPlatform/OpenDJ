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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.monitors;



import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.InitializationException;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;



/**
 * This class defines a Directory Server monitor provider that can be used to
 * collect information about the system and the JVM on which the Directory
 * Server is running.
 */
public class SystemInfoMonitorProvider
       extends MonitorProvider
{



  /**
   * Initializes this monitor provider.
   */
  public SystemInfoMonitorProvider()
  {
    super("System Info Monitor Provider");

    // No initialization should be performed here.
  }



  /**
   * Initializes this monitor provider based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this monitor provider.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeMonitorProvider(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  public String getMonitorInstanceName()
  {
    return "System Information";
  }



  /**
   * Retrieves the length of time in milliseconds that should elapse between
   * calls to the <CODE>updateMonitorData()</CODE> method.  A negative or zero
   * return value indicates that the <CODE>updateMonitorData()</CODE> method
   * should not be periodically invoked.
   *
   * @return  The length of time in milliseconds that should elapse between
   *          calls to the <CODE>updateMonitorData()</CODE> method.
   */
  public long getUpdateInterval()
  {
    // This monitor does not need to run periodically.
    return 0;
  }



  /**
   * Performs any processing periodic processing that may be desired to update
   * the information associated with this monitor.  Note that best-effort
   * attempts will be made to ensure that calls to this method come
   * <CODE>getUpdateInterval()</CODE> milliseconds apart, but no guarantees will
   * be made.
   */
  public void updateMonitorData()
  {
    // This monitor does not need to run periodically.
    return;
  }



  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return  A set of attributes containing monitor data that should be
   *          returned to the client if the corresponding monitor entry is
   *          requested.
   */
  public ArrayList<Attribute> getMonitorData()
  {
    ArrayList<Attribute> attrs = new ArrayList<Attribute>(12);

    attrs.add(createAttribute("javaVersion",
                              System.getProperty("java.version")));
    attrs.add(createAttribute("javaVendor", System.getProperty("java.vendor")));
    attrs.add(createAttribute("jvmVersion",
                              System.getProperty("java.vm.version")));
    attrs.add(createAttribute("jvmVendor",
                              System.getProperty("java.vm.vendor")));
    attrs.add(createAttribute("javaHome",
                              System.getProperty("java.home")));
    attrs.add(createAttribute("classPath",
                              System.getProperty("java.class.path")));
    attrs.add(createAttribute("workingDirectory",
                              System.getProperty("user.dir")));

    String osInfo = System.getProperty("os.name") + " " +
                    System.getProperty("os.version") + " " +
                    System.getProperty("os.arch");
    attrs.add(createAttribute("operatingSystem", osInfo));

    try
    {
      attrs.add(createAttribute("systemName",
                     InetAddress.getLocalHost().getCanonicalHostName()));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }


    Runtime runtime = Runtime.getRuntime();
    attrs.add(createAttribute("availableCPUs",
                              String.valueOf(runtime.availableProcessors())));
    attrs.add(createAttribute("maxMemory",
                              String.valueOf(runtime.maxMemory())));
    attrs.add(createAttribute("usedMemory",
                              String.valueOf(runtime.totalMemory())));
    attrs.add(createAttribute("freeUsedMemory",
                              String.valueOf(runtime.freeMemory())));


    return attrs;
  }



  /**
   * Constructs an attribute using the provided information.  It will have the
   * default syntax.
   *
   * @param  name   The name to use for the attribute.
   * @param  value  The value to use for the attribute.
   *
   * @return  The attribute created from the provided information.
   */
  private Attribute createAttribute(String name, String value)
  {
    AttributeType attrType = DirectoryServer.getDefaultAttributeType(name);

    ASN1OctetString encodedValue = new ASN1OctetString(value);
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);

    try
    {
      values.add(new AttributeValue(encodedValue,
                                    attrType.normalize(encodedValue)));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      values.add(new AttributeValue(encodedValue, encodedValue));
    }

    return new Attribute(attrType, name, values);
  }
}

