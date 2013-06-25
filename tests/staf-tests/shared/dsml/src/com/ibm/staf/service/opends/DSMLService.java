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
 *      Portions Copyright 2009 Sun Microsystems, Inc.
 */
package com.ibm.staf.service.opends;

import com.ibm.staf.*;
import com.ibm.staf.wrapper.*;
import com.ibm.staf.service.*;
import com.ibm.staf.service.opends.tester.*;
import java.util.*;
import java.io.*;
import java.util.ArrayList;

/**
 * DSML Staf service 
 * it allows :
 *  - to test one or multiple DSEE DSML suite testcases (TODO)
 *  - compare testcase result against expected result
 */
public class DSMLService implements STAFServiceInterfaceLevel30 {

  private static final String OP_COMPARE = "COMPARE";
  private static final String OP_CHECK_ERROR_STRINGS = "CHECK_ERROR_STRINGS";
  private static final String OP_HELP = "HELP";
  private static final String OP_FILE = "FILE";
  private static final String OP_EXP_FILE = "EXP_FILE";
  private static final String OP_ISSUE_FILE = "ISSUE_FILE";
  private static final String OP_DIR = "DIR";
  private static final String OP_EXP_DIR = "EXP_DIR";
  private final String MATCH = " matches ";
  private final String DIFFER = " differs from ";
  private String fServiceName;
  private STAFHandle fHandle;
  private String fLocalMachineName = "";
  private STAFLog logger = null;
  // Define any error codes unique to this service
  private static final int kDSMLInvalidSomething = 4001;
  private static final int kErrorStringMatchOffset = 100000000;
  // STAFCommandParsers for each request
  private STAFCommandParser fParser;
  private String fLineSep;

  public STAFResult init(STAFServiceInterfaceLevel30.InitInfo info) {
    try {
      fServiceName = info.name;
      fHandle = new STAFHandle("STAF/Service/" + info.name);
      logger = new STAFLog(STAFLog.HANDLE, "dsmlLog", fHandle,
              STAFLog.Fatal | STAFLog.Error | STAFLog.Warning);
    } catch (STAFException e) {
      return new STAFResult(STAFResult.STAFRegistrationError,
              e.toString());
    }

    // COMPARE parser
    fParser = new STAFCommandParser(0, false);
    fParser.addOption(OP_COMPARE, 1, STAFCommandParser.VALUENOTALLOWED);
    fParser.addOption(OP_FILE, 1, STAFCommandParser.VALUEREQUIRED);
    fParser.addOption(OP_DIR, 1, STAFCommandParser.VALUEREQUIRED);
    fParser.addOption(OP_EXP_FILE, 1, STAFCommandParser.VALUEREQUIRED);
    fParser.addOption(OP_EXP_DIR, 1, STAFCommandParser.VALUEREQUIRED);

    // if you specify COMPARE, RESULT_FILE is required
    fParser.addOptionNeed(OP_EXP_FILE, OP_FILE);
    fParser.addOptionNeed(OP_EXP_DIR, OP_DIR);
    fParser.addOptionNeed(OP_COMPARE, OP_EXP_FILE + " " + OP_EXP_DIR);
    fParser.addOptionNeed(OP_EXP_FILE + " " + OP_EXP_DIR, OP_COMPARE);

    // CHECK_ERROR_STRINGS parser
    fParser.addOption(OP_CHECK_ERROR_STRINGS, 1, STAFCommandParser.VALUENOTALLOWED);
    fParser.addOptionNeed(OP_CHECK_ERROR_STRINGS, OP_FILE);
    STAFResult res = new STAFResult();

    // Resolve the line separator variable for the local machine
    res = STAFUtil.resolveInitVar("{STAF/Config/Sep/Line}", fHandle);

    if (res.rc != STAFResult.Ok) {
      return res;
    }

    fLineSep = res.result;

    // Resolve the machine name variable for the local machine
    res = STAFUtil.resolveInitVar("{STAF/Config/Machine}", fHandle);

    if (res.rc != STAFResult.Ok) {
      return res;
    }

    fLocalMachineName = res.result;

    // Register Help Data
    registerHelpData(
            kDSMLInvalidSomething + 1,
            "Invalid input",
            "missing or wrong input files for results or expected results");

    return new STAFResult(STAFResult.Ok);
  }

  public STAFResult acceptRequest(STAFServiceInterfaceLevel30.RequestInfo info) {
    //delegate the request handling
    StringTokenizer requestTokenizer = new StringTokenizer(info.request);
    String request = requestTokenizer.nextToken().toLowerCase();

    // call the appropriate method to handle the command
    if (request.equalsIgnoreCase(OP_COMPARE)) {
      return handleCompare(info);
    } else if (request.equalsIgnoreCase(OP_CHECK_ERROR_STRINGS)) {
      return handleCheckErrorStrings(info);
    } else if (request.equalsIgnoreCase(OP_HELP)) {
      return handleHelp(info);
    } else {
      return handleInvalidRequest(info);
    }
  }

  public STAFResult term() {
    logger.log(STAFLog.Warning, "termination initiated");
    try {
      // Un-register Help Data

      unregisterHelpData(kDSMLInvalidSomething + 2);

      // Un-register the service handle

      fHandle.unRegister();
    } catch (STAFException ex) {
      return new STAFResult(STAFResult.STAFRegistrationError,
              ex.toString());
    }

    return new STAFResult(STAFResult.Ok);
  }

  private STAFResult handleCheckErrorStrings(STAFServiceInterfaceLevel30.RequestInfo info) {
    // default return will be kDSMLInvalidSomething + 14 marking that
    // no known error string match the erroneous expected file content.
    STAFResult sr = new STAFResult(kDSMLInvalidSomething + 14);
    STAFCommandParseResult parsedRequest = fParser.parse(info.request);
    if (parsedRequest.rc != STAFResult.Ok) {
      return new STAFResult(STAFResult.InvalidRequestString,
              parsedRequest.errorBuffer);
    }
    String resultFile = parsedRequest.optionValue(OP_FILE);
    Properties errProps = new Properties();
    final String RE = ".r";
    final String lblMarker = "_";
    try {
      ClassLoader cl = this.getClass().getClassLoader();
      InputStream in = cl.getResourceAsStream("errorStrings.properties");
      errProps.load(in);
      in.close();
      Enumeration errEnum = errProps.propertyNames();
      while (errEnum.hasMoreElements()) {
        String k = (String) errEnum.nextElement();
        String issueID = null;
        String lbl = null;
        boolean re = k.endsWith(RE);
        int lblNdx = k.indexOf(lblMarker);
        if (lblNdx != -1) {
          if (re) {
            lbl = k.substring(lblNdx + 1, k.length() - RE.length());
          } else {
            lbl = k.substring(lblNdx + 1);
          }
        }
        if (lblNdx != -1) {
          issueID = k.substring(0, lblNdx);
        } else if (re) {
          issueID = k.substring(0, k.length() - RE.length());
        } else {
          issueID = k;
        }
        String v = errProps.getProperty(k);
        BufferedReader rbr = new BufferedReader(new FileReader(resultFile));
        String line = "";
        while ((line = rbr.readLine()) != null) {
          if (re) {
            if (line.matches(v)) {
              sr = new STAFResult(kErrorStringMatchOffset + 
                                  Integer.parseInt(issueID),""+lbl+line);
            }
          } else {
            if (line.indexOf(v) != -1) {
              sr = new STAFResult(kErrorStringMatchOffset + 
                                  Integer.parseInt(issueID),""+lbl+line);
              break;
            }
          }
        }
      }
    } catch (FileNotFoundException fnfe) {
      sr.rc = kDSMLInvalidSomething + 11;
      sr.result = fnfe.getMessage();
    } catch (IOException ioe) {
      sr.rc = kDSMLInvalidSomething + 12;
      sr.result = ioe.getMessage();
    } catch (Exception e) {
      sr.rc = kDSMLInvalidSomething + 13;
      sr.result = e.getMessage();
    }
    return sr;
  }

  private STAFResult handleCompare(STAFServiceInterfaceLevel30.RequestInfo info) {
    STAFResult sr = new STAFResult(0);

    //parse the input request 
    STAFCommandParseResult parsedRequest = fParser.parse(info.request);
    if (parsedRequest.rc != STAFResult.Ok) {
      return new STAFResult(STAFResult.InvalidRequestString,
              parsedRequest.errorBuffer);
    }

    // Resolve any STAF variables in the DIR option's value
    STAFResult res = new STAFResult();

    String file = parsedRequest.optionValue(OP_FILE);
    String exp_file = parsedRequest.optionValue(OP_EXP_FILE);
    String dir = parsedRequest.optionValue(OP_DIR);
    String exp_dir = parsedRequest.optionValue(OP_EXP_DIR);

    if (dir != null && dir.trim().length() > 0) {
      sr = handleCompareDir(exp_dir, dir);
    }

    if (file != null && file.trim().length() > 0) {
      sr = handleCompareFile(exp_file, file);
    }


    return sr;
  }

  /*
   * It's expected that the result and expected result file have the same
   * file path up to the file extension.
   * expected result having extension ".res"
   * result to be compared to expected result ".run"
   * The filePath should exclude the file extension
   * for ex a file like /tmp/add000.res the filePath expected is /tmp/add000
   */
  private STAFResult handleCompareFile(String exp_file, String file) {
    STAFResult sr = new STAFResult(STAFResult.Ok);
    logger.log(STAFLog.Warning,
            "handle File compare for exp_file=[" + exp_file + "], file=[" + file + "]");
    if (!(exp_file.endsWith(DSMLFileFilter.EXPECTED_FILE_EXTENSION) || exp_file.endsWith(DSMLFileFilter.ISSUE_FILE_EXTENSION))) {
      sr.rc = STAFResult.FileReadError;
      sr.result = "invalid input " + exp_file + " should end with " +
              DSMLFileFilter.EXPECTED_FILE_EXTENSION + " or " +
              DSMLFileFilter.ISSUE_FILE_EXTENSION;
    } else if (!file.endsWith(DSMLFileFilter.RUN_FILE_EXTENSION)) {
      sr.rc = STAFResult.FileReadError;
      sr.result = "invalid input " + file + " should end with " + DSMLFileFilter.RUN_FILE_EXTENSION;
    } else {
      File expectedFile, resultFile;
      // read the result and expected content files, and compare theme
      try {
        expectedFile = new File(exp_file);
        resultFile = new File(file);
        String line;
        //reading result into ArrayList of POST results
        BufferedReader resultBuffReader = new BufferedReader(new FileReader(resultFile));
        ArrayList rl = new ArrayList();
        StringBuffer resultContent = new StringBuffer();
        int ln = 0;
        while ((line = resultBuffReader.readLine()) != null) {
          if (line.startsWith("HTTP") && ln > 0) {
            rl.add(resultContent);
            resultContent = new StringBuffer(line + "\n");
          } else {
            resultContent.append(line + "\n");
          }
          ln++;
        }
        rl.add(resultContent.toString());
        resultBuffReader.close();
        //reading expected result of POST results
        BufferedReader expectedBufferReader = new BufferedReader(new FileReader(expectedFile));
        ArrayList erl = new ArrayList();
        StringBuffer expectedContent = new StringBuffer();
        ln = 0;
        while ((line = expectedBufferReader.readLine()) != null) {
          if (line.startsWith("HTTP") && ln > 0) {
            erl.add(expectedContent);
            expectedContent = new StringBuffer(line + "\n");
          } else {
            expectedContent.append(line + "\n");
          }
          ln++;
        }
        erl.add(expectedContent.toString());
        expectedBufferReader.close();
        if (rl.size() != erl.size()) {
          sr.rc = kDSMLInvalidSomething + 3;
          sr.result = "number of results " +
                  resultFile + "[" + rl.size() + "]" +
                  expectedFile + "[" + erl.size() + "]";
        } else {
          boolean identical = true;
          for (int i = 0; i < rl.size(); i++) {
            //starting from 1, because the first is always empty
            identical &= compareResults((String) erl.get(i), (String) rl.get(i));
            logger.log(STAFLog.Warning, "comparing\n" + (String) erl.get(i) + "\nwith\n" + (String) rl.get(i));
            if (identical) {
              // success
              sr.rc = STAFResult.Ok;
              sr.result = resultFile + MATCH + expectedFile;
            } else {
              logger.log(STAFLog.Error, "exp_file=[" + exp_file + "] " +
                      "differ from file=[" + file + "]");

              sr.rc = kDSMLInvalidSomething + 4;
              sr.result = resultFile + DIFFER + expectedFile;
              break;
            }
          }
        }
      } catch (FileNotFoundException fnfe) {
        sr.rc = kDSMLInvalidSomething + 5;
        sr.result = fnfe.getMessage();
      } catch (IOException ioe) {
        sr.rc = kDSMLInvalidSomething + 6;
        sr.result = ioe.getMessage();
      } catch (Exception e) {
        sr.rc = kDSMLInvalidSomething + 7;
        sr.result = e.getMessage();
      }
    }
    return sr;
  }

  private STAFResult handleCompareDir(String exp_dir, String run_dir) {
    logger.log(STAFLog.Warning,
            "handle Directory comparaison exp_dir[" + exp_dir + "],dir[" + run_dir + "]");
    File expDirFile;
    File runDirFile;
    STAFResult sr = new STAFResult(STAFResult.Ok);
    try {
      expDirFile = new File(exp_dir);
      runDirFile = new File(run_dir);
      ArrayList test_a = new ArrayList();
      ArrayList missing_e = new ArrayList();
      ArrayList missing_r = new ArrayList();
      if (expDirFile.isDirectory() && runDirFile.isDirectory()) {
        // find all the files with extension ".res"
        File[] eFiles = expDirFile.listFiles(new DSMLFileFilter(DSMLFileFilter.EXPECTED_FILE_EXTENSION));
        ArrayList ea = new ArrayList(eFiles.length);
        String eName;
        for (int ei = 0; ei < eFiles.length; ei++) {
          eName = eFiles[ei].getName();
          eName = eName.substring(0, eName.lastIndexOf(DSMLFileFilter.EXPECTED_FILE_EXTENSION));
          ea.add(eName);
        }
        // find all the files with extension ".run"
        File[] rFiles = runDirFile.listFiles(new DSMLFileFilter(DSMLFileFilter.RUN_FILE_EXTENSION));
        ArrayList ra = new ArrayList(rFiles.length);
        String rName;
        for (int ri = 0; ri < rFiles.length; ri++) {
          rName = rFiles[ri].getName();
          rName = rName.substring(0, rName.lastIndexOf(DSMLFileFilter.RUN_FILE_EXTENSION));
          ra.add(rName);
          if (!ea.contains(rName)) {
            // get a run file not matching expected result
            missing_e.add(rName);
          } else {
            // expected and run file are both present
            test_a.add(rName);
          }
        }
        // find the missing result file and remove from test_a
        for (int i = 0; i < ea.size(); i++) {
          String e = (String) ea.get(i);
          if (!ra.contains(e)) {
            missing_r.add(e);
          }
        }
        // loop through the test set test_a and compare the files
        for (int i = 0; i < test_a.size(); i++) {
          String tf = (String) test_a.get(i);
          sr = handleCompareFile(
                  exp_dir + File.separator + tf + DSMLFileFilter.EXPECTED_FILE_EXTENSION,
                  run_dir + File.separator + tf + DSMLFileFilter.RUN_FILE_EXTENSION);
        }
      } else {
        logger.log(STAFLog.Warning, "Directory comparaison with invalid input dir : " + exp_dir);
        sr.rc = kDSMLInvalidSomething + 8;
        sr.result = "Directory comparaison with invalid input dir : " + exp_dir;
      }
    } catch (Exception e) {
      sr.rc = kDSMLInvalidSomething + 9;
      sr.result = e.getMessage();
    }
    return sr;
  }

  private boolean compareResults(String expectedResult, String result) throws Exception {

    ResponseChecker dsmlReponse = null;
    ResponseChecker expectedReponse = null;

    //System.out.println("result\n" + result);
    try {
      dsmlReponse = new ResponseChecker(result);
    } catch (Exception e) {
      if (e.getCause() != null) {
        e.printStackTrace();
        throw (new Exception("Response parsing error: " + e.getCause().getMessage()));
      } else {
        e.printStackTrace();
        throw (new Exception("Response parsing error: " + e.getMessage()));
      }
    }


    try {
      expectedReponse = new ResponseChecker(expectedResult);
    } catch (Exception e) {
      if (e.getCause() != null) {
        throw (new Exception("Parsing error for expected result : " + e.getCause().getMessage()));
      } else {
        throw (new Exception("Parsing error for expected result : " + e.getMessage()));
      }
    }

    try {
      return (dsmlReponse.equals(expectedReponse));
    } catch (Exception e) {
      logger.log(STAFLog.Warning, "failed comparing DSML responses exception " + e.getMessage());
      return false;
    }
  }

  private STAFResult handleInvalidRequest(STAFServiceInterfaceLevel30.RequestInfo info) {
    logger.log(STAFLog.Error, "invalid request : [" + info.request + "]");
    STAFResult sr = new STAFResult(kDSMLInvalidSomething + 10);
    sr.result = info.request;
    return sr;
  }

  private STAFResult handleHelp(STAFServiceInterfaceLevel30.RequestInfo info) {
    // Verify the requester has at least trust level 1

    STAFResult trustResult = STAFUtil.validateTrust(
            1, fServiceName, "HELP", fLocalMachineName, info);

    if (trustResult.rc != STAFResult.Ok) {
      return trustResult;
    }

    // Return help text for the service

    return new STAFResult(
            STAFResult.Ok,
            "DSML Service Help :" + fLineSep + fLineSep + OP_COMPARE + "(" +
            OP_EXP_FILE + " filename " + OP_FILE + " filename " + " | " +
            OP_EXP_DIR + " dirname " + OP_DIR + " dirname )" +
            fLineSep + OP_CHECK_ERROR_STRINGS + " " + OP_FILE + " filename " +
            fLineSep + OP_HELP);
  }

  // Register error codes for the STAX Service with the HELP service
  private void registerHelpData(int errorNumber, String info,
          String description) {
    //TODO
  }

  // Un-register error codes for the STAX Service with the HELP service
  private void unregisterHelpData(int errorNumber) {
    //TODO
  }

  private class DSMLFileFilter implements FilenameFilter {

    public static final String EXPECTED_FILE_EXTENSION = ".res";
    public static final String ISSUE_FILE_EXTENSION = ".issue";
    public static final String RUN_FILE_EXTENSION = ".run";
    public static final String INPUT_FILE_EXTENSION = ".dat";
    private ArrayList extList;
    private String exclusiveExtension;

    DSMLFileFilter() {
      extList = new ArrayList();
      exclusiveExtension = null;
      extList.add(EXPECTED_FILE_EXTENSION);
      extList.add(ISSUE_FILE_EXTENSION);
      extList.add(RUN_FILE_EXTENSION);
      extList.add(INPUT_FILE_EXTENSION);
    }

    DSMLFileFilter(String ext) {
      exclusiveExtension = ext;
    }

    public boolean accept(File dir, String name) {
      int li = name.lastIndexOf(".");
      if (li > 0) {
        if (exclusiveExtension != null) {
          //comparing file name extension with exclusively one extension
          if (name.substring(li).equals(exclusiveExtension)) {
            return ((new File(dir + File.separator + name)).isFile());
          } else {
            return false;
          }
        } else {
          // compare file name extension 
          if (extList.contains(name.substring(li))) {
            return ((new File(dir + File.separator + name)).isFile());
          } else {
            return false;
          }
        }
      } else {
        return false;
      }
    }
  }
}
