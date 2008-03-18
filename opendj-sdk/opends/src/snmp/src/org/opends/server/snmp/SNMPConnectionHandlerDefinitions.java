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

import java.util.HashSet;
import java.util.Set;

/**
 * SNMP definitions.
 */
public class SNMPConnectionHandlerDefinitions {

  // SNMP Version supported
  public static String SNMP_VERSION_V1 = "v1";
  public static String SNMP_VERSION_V2 = "v2";
  public static String SNMP_VERSION_V3 = "v3";
  
  /**
   * List of Supported SNMP Version
   */
  public static Set<String> SUPPORTED_SNMP_VERSION=new HashSet<String>();
  static {
    SUPPORTED_SNMP_VERSION.add(SNMP_VERSION_V1);
    SUPPORTED_SNMP_VERSION.add(SNMP_VERSION_V2);
    SUPPORTED_SNMP_VERSION.add(SNMP_VERSION_V3);
  }
 
  // Domain for SNMP MBeans
  public static final String SNMP_DOMAIN =
          "org.opends.server.snmp:";
  // Domain for Monitor MBeans
  public static final String JMX_DOMAIN =
          "org.opends.server:";
  
  // Name of monitor Mbeans
  public static final String MONITOR_CLIENT_CONNECTIONS_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-Client_Connections";
  public static final String MONITOR_ENTRY_CACHES_OBJECTNANE =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-Entry_Caches";
  public static final String MONITOR_JVM_MEMORY_USAGE_OBJECTNAME =
          "RootDSE,Rdn1=cn-monitor,Rdn2=cn-JVM_Memory_Usage";
  public static final String MONITOR_JVM_STACK_TRACE_OBJECTNAME = "" +
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-JVM_Stack_Trace";
  public static final String MONITOR_SYSTEM_INFORMATION_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-System_Information";
  public static final String MONITOR_VERSION_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-Version";
  public static final String MONITOR_WORK_QUEUE_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-Work_Queue";
  public static final String MONITOR_ADMIN_ROOT_BACKEND_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-adminRoot_Backend";
  public static final String MONITOR_ADSTRUSTSTORE_BACKEND_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-adstruststore_Backend";
  public static final String MONITOR_BACKUP_BACKEND_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-backup_Backend";
  public static final String MONITOR_MONITOR_BACKEND_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-monitor_Backend";
  public static final String MONITOR_SCHEMA_BACKEND_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-schema_Backend";
  public static final String MONITOR_TASKS_BACKEND_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-tasks_Backend";
  public static final String MONITOR_USERROOT_BACKEND_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-userRoot_Backend";
  public static final String MONITOR_USERROOT_DATABASE_ENVIRONMENT_OBJECTNAME =
          "rootDSE,Rdn1=cn-monitor,Rdn2=cn-userRoot_Database_Environment";
}
