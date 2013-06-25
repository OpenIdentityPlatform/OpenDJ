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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

import netscape.ldap.util.GetOpt;
import com.ibm.staf.STAFHandle;

public class PSearch {

  public static void main(String[] args) {
    String usage = "Usage: psearch -h <hostname> -p <port> -b <suffix>" + "[-D bindDN] [-w bindPW]" + "-f <fileURL+file name>" + "-s" + "-n <number of thread>" + " -o <add,modify,delete,moddn>"+ " -l";
    String hostname = "localhost";
    int portnumber = 1389; //LDAPv3.DEFAULT_PORT;
    int nbThreads = 1;//number of thread by default
    // Check for these options. -H means to print out a usage message.
    GetOpt options = new GetOpt("h:p:b:D:w:H:f:n:o:s:l", args);

    // Get the arguments specified for each option.
    String host = options.getOptionParam('h');
    // host
    if (options.hasOption('h')) {
      if (host == null) {
        // usage
        System.out.println(usage);
        System.exit(1);
      } else {
        hostname = host;
      }
    }
    String port = options.getOptionParam('p');
    // If a port number was specified, convert the port value
    // to an integer.
    if (port != null) {
      try {
        portnumber = java.lang.Integer.parseInt(port);
      } catch (java.lang.Exception e) {
        System.out.println("Invalid port number: " + port);
        System.out.println(usage);
        System.exit(1);
      }
    }
    //number of thread
    String nbT = options.getOptionParam('n');
    if (nbT != null) {
      try {
        nbThreads = java.lang.Integer.parseInt(nbT);
      } catch (java.lang.Exception e) {
        System.out.println("Invalid Thread number: " + nbT);
        System.out.println(usage);
        System.exit(1);
      }
    }
    // PSearch suffix
    String suffix = options.getOptionParam('b');

    String bindDN = options.getOptionParam('D');

    String bindPW = options.getOptionParam('w');

    //operations all by default
    String operation = PSearchOperations.ALL;
    if (options.hasOption('o')) {
      String opParam = options.getOptionParam('o');
      if (opParam.equals("add")) {
        operation = PSearchOperations.ADD;
      } else if (opParam.equals("modify")) {
        operation = PSearchOperations.MODIFY;
      } else if (opParam.equals("delete")) {
        operation = PSearchOperations.DELETE;
      } else if (opParam.equals("moddn")) {
        operation = PSearchOperations.MODDN;
      } else if (opParam.equals("all")) {
        operation = PSearchOperations.ALL;
        ;
      } else {
        System.out.println("Invalid operation type: " + opParam);
        System.out.println(usage);
        System.exit(1);
      }
    }

    // to disable the log files
    boolean useFile = false;
    String fileName = "logLile";
    if (options.hasOption('f')) {
      useFile = options.hasOption('f');
      fileName = options.getOptionParam('f');
    }

    // to enable diff format
    boolean ldifFormat = options.hasOption('l');

    // to enable system out logs
    boolean output = options.hasOption('s');

    System.out.println("Connecting to " + hostname + ":" + portnumber +
            " as \"" + bindDN + "\"" +
            " on suffix \"" + suffix + "\"" +
            " on operation \"" + operation + "\"" +
            " use file: \"" + useFile + "\" output: \"" + output + "\"");
    //start all thread


    for (int i = 0; i < nbThreads; i++) {
      PSearchOperations ps = new PSearchOperations(i, hostname, portnumber, bindDN, bindPW, suffix);
      if (useFile) {
        ps.useLogFile(useFile);
        ps.setLogFile(fileName);
      }
      ps.setOutput(output);
      ps.setLdifFormat(ldifFormat);
      ps.setOperation(operation);
      ps.start();
    }
    
    try {
      STAFHandle handle = new STAFHandle("PSearch listener");
      handle.submit2(hostname, "SEM", "PULSE EVENT PSearch/Ready");
      handle.submit2(hostname, "SEM", "WAIT EVENT PSearch tests/Completed");
      handle.submit2(hostname, "SEM", "DELETE EVENT PSearch tests/Completed");
      System.exit(0);
    } catch (Exception e) {
      System.out.println("STAF Handle fail");
    }

  }
}
