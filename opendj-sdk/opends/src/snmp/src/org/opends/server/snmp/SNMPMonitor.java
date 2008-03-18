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
 */
package org.opends.server.snmp;

import com.sun.management.snmp.SnmpStatusException;
import java.security.PrivilegedAction;


import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Set;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.security.auth.Subject;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.jmx.Credential;
import org.opends.server.protocols.jmx.OpendsJmxPrincipal;
import org.opends.server.types.DebugLogLevel;

/**
 * The SNMPMonitor Class allows to get a singleton SNMPMonitor object allowing
 * to access the JMX cn=monitor MBean.
 */
public class SNMPMonitor {

  /**
   * Debug Tracer for this class
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();
  /**
   * Singleton SNMPMonitor object
   */
  private static SNMPMonitor monitor = null;
  /** 
   * Monitor MBeanServer server
   */
  private MBeanServer server;
  /**
   * Subject Auth to use to access the JMX Mbeans cn=monitor
   */
  private Subject subject = null;
  /**
   * Pattern to use to query the cn=monitor MBeans
   */
  public static ObjectName pattern;

  static {
    try {
      pattern = new ObjectName(
              SNMPConnectionHandlerDefinitions.JMX_DOMAIN +
              "Name=rootDSE,Rdn1=cn-monitor,*");
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
    }
  }

  /**
   * Creates an SNMPMonitor object mapping
   * @param server to use to the mapping
   */
  private SNMPMonitor(MBeanServer server) {
    this.server = server;
    this.subject = new Subject();
    this.subject.getPrincipals().add(new OpendsJmxPrincipal("cn=anonymous"));
    InternalClientConnection clientConnection =
            InternalClientConnection.getRootConnection();
    this.subject.getPrivateCredentials().add(new Credential(clientConnection));
  }

  /**
   * Gets the singleton SNMPMonitor object
   * @param server
   * @return the SNMPMonitor mapping object 
   */
  public static SNMPMonitor getMonitor(MBeanServer server) {
    if (monitor == null) {
      monitor = new SNMPMonitor(server);
    }
    return monitor;
  }

  /**
   * Gets the Connection Handlers Statistics MBean
   * @return the Set<ObjectName> of Connection Handlers Statistics.
   *     If Statistics do not exixist then an empty Set is returned
   */
  public Set<ObjectName> getConnectionHandlersStatistics() {
    Set<ObjectName> results = new HashSet<ObjectName>();
    try {
      Set monitorObjects = this.server.queryNames(SNMPMonitor.pattern, null);
      for (Iterator iter = monitorObjects.iterator(); iter.hasNext();) {
        ObjectName name = (ObjectName) iter.next();
        if ((name.getCanonicalName().contains("Connection_Handler")) &&
                (name.getCanonicalName().endsWith("_Statistics"))) {
          results.add(name);
        }
      }
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
    }
    return results;
  }

  /**
   * Return the ObjectName of the Connection Handler corresponding to
   * the statistics name
   * @param statistics ObjectName
   * @return the Connection Handler ObjectName, null otherwise
   */
  public ObjectName getConnectionHandler(ObjectName statistics) {

    // Check parameter
    if (statistics == null) {
      return null;
    }

    try {
      String value = statistics.getCanonicalName();
      if (!value.endsWith("_Statistics")) {
        return null;
      }
      int index = value.indexOf("_Statistics");
      String name = value.substring(0, index);
      ObjectName connectionHandlerName = new ObjectName(name);

      // Check if the MBean exists
      Set query = this.server.queryNames(connectionHandlerName, null);
      if ((query != null) && (!query.isEmpty())) {
        return connectionHandlerName;
      }
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
    }
    return null;
  }

  /**
   * Return a Set of Connection Handler ObjectNames
   * @return the Set of ObjectNames, an empty Set if no connection handlers
   */
  public Set<ObjectName> getConnectionHandlers() {
    Set monitorObjects;
    Set<ObjectName> results = new HashSet<ObjectName>();
    try {
      monitorObjects = this.server.queryNames(SNMPMonitor.pattern, null);
      for (Iterator iter = monitorObjects.iterator(); iter.hasNext();) {
        ObjectName name = (ObjectName) iter.next();
        if ((name.getCanonicalName().contains("Connection_Handler")) &&
                (!(name.getCanonicalName().endsWith("_Statistics")))) {
          results.add(name);
        }
      }
      return results;
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
      return results;
    }
  }

  /**
   * Returns the ObjectName of the Statistics Connection Handler name 
   * corresponding to the Connection Handler name
   * @param connectionHandlerName
   * @return the ObjectName of the statistics ObjectName, null if the statistics
   * could not be found
   */
  public ObjectName getConnectionHandlerStatistics(
          ObjectName connectionHandlerName) {

    if (connectionHandlerName == null) {
      return null;
    }
    try {
      String value = 
              connectionHandlerName.getCanonicalName().concat("_Statistics");
      ObjectName statistics = new ObjectName(value);
      // Check if the MBean exists
      Set query = this.server.queryNames(statistics, null);
      if ((query != null) && (!query.isEmpty())) {
        return statistics;
      }
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
    }
    return null;
  }

  /**
   * Get the value of the attribute
   * @param name of Mbean as a String
   * @param attribute to look for
   * @return the value of the attribute, null if the attribute could not
   * be found
   */
  public Object getAttribute(String name, String attribute) {
    try {
      ObjectName objName = new ObjectName(
              SNMPConnectionHandlerDefinitions.JMX_DOMAIN +
              "Name=" + name);
      return getAttribute(objName, attribute);
    } catch (Exception ex) {
      if (DebugLogger.debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
      return null;
    }
  }

  /**
   * Gets the value of an attribute
   * @param name of the Mbean
   * @param attribute to look for
   * @return the value of the attribute, null if the attribute value could not 
   * be found
   */
  @SuppressWarnings("unchecked")
  public Object getAttribute(final ObjectName name, final String attribute) {
    return Subject.doAs(this.subject, new PrivilegedAction() {

      public Object run() {
        try {
          Attribute attr = (Attribute) server.getAttribute(name, attribute);
          if (attr != null) {
            return attr.getValue();
          }
        } catch (Exception ex) {
          if (DebugLogger.debugEnabled()) {
            TRACER.debugCaught(DebugLogLevel.ERROR, ex);
          }
        }
        return null;
      }
      });
  }

  /** 
   * Wrapper for SNMP Counter32 
   * @param v value
   * @return a counter32
   */
  public static long counter32Value(long v) {
    if (v > (pow(2, 32) - 1)) {
      return (v % pow(2, 32));
    } else {
      return v;
    }
  }

  /** 
   * Wrapper for SNMP Counter32
   * @param V Value 
   * @return a Counter32
   */
  public static Long counter32Value(Long V) {
    long v = V.longValue();
    if (v > (pow(2, 32) - 1)) {
      return new Long(v % pow(2, 32));
    } else {
      return V;
    }
  }

  /** 
   * Latcher for SNMP Gauge32 
   * @param v value
   * @return a gauge32
   */
  public static long gauge32Value(long v) {
    if (v > (pow(2, 32) - 1)) {
      return (pow(2, 32) - 1);
    } else {
      return v;
    }
  }

  /** 
   * Latcher for SNMP Gauge32
   * @param V value
   * @return a gauge32
   */
  public static Long gauge32Value(Long V) {
    long v = V.longValue();
    if (v > (pow(2, 32) - 1)) {
      return new Long(pow(2, 32) - 1);
    } else {
      return V;
    }
  }

  /**
   * Checker for SNMP INTEGER   
   * @param V value
   * @return an Integer
   * @throws com.sun.management.snmp.SnmpStatusException 
   */
  public static Integer integerValue(Long V) throws SnmpStatusException {
    long v = V.longValue();
    if (v > (pow(2, 31) - 1)) {
      throw new SnmpStatusException("Returned intrumented value size too big");
    }
    Integer ret = new Integer(V.intValue());
    return ret;
  }

  /**
   * pow x^y
   */
  private static long pow(long x, long y) {
    int j = 1;
    long k = x;
    if (y == 0) {
      return 1;
    }
    if (y == 1) {
      return x;
    }
    while (j < y) {
      k = k * x;
      j++;
    }
    return k;
  }
}
