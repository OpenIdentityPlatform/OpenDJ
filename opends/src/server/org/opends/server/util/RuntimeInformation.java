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
 *      Portions Copyright 2008 Sun Microsystems, Inc.
 */

 package org.opends.server.util;
 import static org.opends.server.loggers.ErrorLogger.logError;
 import static org.opends.messages.RuntimeMessages.*;
 import static org.opends.messages.CoreMessages.*;
 import static org.opends.server.util.DynamicConstants.*;
 import org.opends.server.core.DirectoryServer;
 import java.net.InetAddress;
 import java.lang.management.RuntimeMXBean;
 import java.lang.management.ManagementFactory;
 import java.util.List;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.sleepycat.je.JEVersion;


 /**
  * This class is used to gather and display information from the runtime
  * environment.
  */
 public class RuntimeInformation {


   private static boolean is64Bit=false;

   static {
     String arch = System.getProperty("sun.arch.data.model");
     if (arch != null) {
       try {
         is64Bit = Integer.parseInt(arch) == 64;
       } catch (NumberFormatException ex) {
         //Default to 32 bit.
       }
     }
   }

   /**
    * Returns whether the architecture of the JVM we are running under is 64-bit
    * or not.
    *
    * @return <CODE>true</CODE> if the JVM architecture we running under is
    * 64-bit and <CODE>false</CODE> otherwise.
    */
   public static boolean is64Bit() {
     return is64Bit;
   }

   /**
    * Returns a string representing the JVM input arguments as determined by the
    * MX runtime bean. The individual arguments are separated by commas.
    *
    * @return  A string representation of the JVM input arguments.
    */
   private static String getInputArguments() {
     int count=0;
     RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
     StringBuilder argList = new StringBuilder();
     List<String> jvmArguments = rtBean.getInputArguments();
     if ((jvmArguments != null) && (! jvmArguments.isEmpty())) {
       for (String jvmArg : jvmArguments) {
         if (argList.length() > 0)  {
           argList.append(" ");
         }
         argList.append("\"");
         argList.append(jvmArg);
         argList.append("\"");
         count++;
         if (count < jvmArguments.size())  {
           argList.append(",");
         }
       }
     }
     return argList.toString();
   }

   /**
    * Writes runtime information to a print stream.
    */
   public static void printInfo() {
     System.out.println(NOTE_VERSION.get(DirectoryServer.getVersionString()));
     System.out.println(NOTE_BUILD_ID.get(BUILD_ID));
     System.out.println(
             NOTE_JAVA_VERSION.get(System.getProperty("java.version")));
     System.out.println(
             NOTE_JAVA_VENDOR.get(System.getProperty("java.vendor")));
     System.out.println(
             NOTE_JVM_VERSION.get(System.getProperty("java.vm.version")));
     System.out.println(
             NOTE_JVM_VENDOR.get(System.getProperty("java.vm.vendor")));
     System.out.println(
             NOTE_JAVA_HOME.get(System.getProperty("java.home")));
     System.out.println(
             NOTE_JAVA_CLASSPATH.get(System.getProperty("java.class.path")));
     System.out.println(
             NOTE_JE_VERSION.get(JEVersion.CURRENT_VERSION.toString()));
     System.out.println(
             NOTE_CURRENT_DIRECTORY.get(System.getProperty("user.dir")));
     System.out.println(
             NOTE_OPERATING_SYSTEM.get(System.getProperty("os.name") + " " +
                     System.getProperty("os.version") + " " +
                     System.getProperty("os.arch")));
     String sunOsArchDataModel = System.getProperty("sun.arch.data.model");
     if (sunOsArchDataModel != null) {
       if (! sunOsArchDataModel.toLowerCase().equals("unknown")) {
         System.out.println(NOTE_JVM_ARCH.get(sunOsArchDataModel + "-bit"));
       }
     }
     else{
       System.out.println(NOTE_JVM_ARCH.get("unknown"));
     }
     try {
       System.out.println(NOTE_SYSTEM_NAME.get(InetAddress.getLocalHost().
               getCanonicalHostName()));
     }
     catch (Exception e) {
       System.out.println(NOTE_SYSTEM_NAME.get("Unknown (" + e + ")"));
     }
     System.out.println(NOTE_AVAILABLE_PROCESSORS.get(Runtime.getRuntime().
             availableProcessors()));
     System.out.println(NOTE_MAX_MEMORY.get(Runtime.getRuntime().maxMemory()));
     System.out.println(
             NOTE_TOTAL_MEMORY.get(Runtime.getRuntime().totalMemory()));
     System.out.println(
             NOTE_FREE_MEMORY.get(Runtime.getRuntime().freeMemory()));
   }

   /**
     * Returns the physical memory size, in bytes, of the hardware we are
     * running on.
     *
     * @return Bytes of physical memory of the hardware we are running on.
     */
  private static long getPhysicalMemorySize()
  {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    try
    {
      // Assuming the RuntimeMXBean has been registered in mbs
      ObjectName oname = new ObjectName(
          ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
      // Check if this MXBean contains Sun's extension
      if (mbs.isInstanceOf(oname, "com.sun.management.OperatingSystemMXBean")) {
          // Get platform-specific attribute "TotalPhysicalMemorySize"
          Long l = (Long) mbs.getAttribute(oname, "TotalPhysicalMemorySize");
          return l ;
      }
      else
      {
        return -1;
      }
    }
    catch (Exception e)
    {
      return -1;
    }
   }

   /**
    * Returns a string representing the fully qualified domain name.
    *
    * @return A string representing the fully qualified domain name or the
    * string "unknown" if an exception was thrown.
    */
   private static String getHostName() {
     String host;
     try {
       host=InetAddress.getLocalHost().getCanonicalHostName();
     }
     catch (Exception e) {
       host="Unknown (" + e + ")";
     }
     return host;
   }

   /**
    * Returns string representing operating system name,
    * version and architecture.
    *
    * @return String representing the operating system information the JVM is
    * running under.
    */
   private static String getOSInfo() {
    return System.getProperty("os.name") + " " +
           System.getProperty("os.version") + " " +
           System.getProperty("os.arch");
   }

   /**
    * Return string representing the architecture of the JVM we are running
    * under.
    *
    * @return A string representing the architecture of the JVM we are running
    * under or "unknown" if the architecture cannot be determined.
    */
   private static String getArch() {
     String sunOsArchDataModel = System.getProperty("sun.arch.data.model");
     if (sunOsArchDataModel != null) {
       if (! sunOsArchDataModel.toLowerCase().equals("unknown")) {
         return (sunOsArchDataModel + "-bit");
       }
     }
     return "unknown";
   }

   /**
    * Write runtime information to error log.
    */
   public static void logInfo() {
    logError(NOTE_JVM_INFO.get(System.getProperty("java.vm.version"),
                               System.getProperty("java.vm.vendor"),
                               getArch(),Runtime.getRuntime().maxMemory()));
    Long physicalMemorySize = getPhysicalMemorySize();
    if (physicalMemorySize != -1)
    {
      logError(NOTE_JVM_HOST.get(getHostName(), getOSInfo(),
          physicalMemorySize, Runtime.getRuntime().availableProcessors()));
    }
    else
    {
      logError(NOTE_JVM_HOST_WITH_UNKNOWN_PHYSICAL_MEM.get(getHostName(),
          getOSInfo(), Runtime.getRuntime().availableProcessors()));
    }
    logError(NOTE_JVM_ARGS.get(getInputArguments()));
   }
 }
