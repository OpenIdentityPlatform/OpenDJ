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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
import java.io.*;
import java.lang.*;
import java.util.ArrayList;

public class WriteXMLFile_xml {
  private String group;

  private ArrayData arrayData;

  private ArrayList<String> strIndividualSteps;

  public WriteXMLFile_xml(String inGroup) {
    group = inGroup;
    strIndividualSteps = null;
  }

  public void MakeXMLFile(ArrayData arrayData, String strDir)
      throws IOException {
    String strDirName = strDir + "/" + group;
    File fileDirName = new File(strDirName);
    if (!fileDirName.isDirectory()) {
      if (!fileDirName.mkdirs()) {
        System.out.println("Could not create directory, " + strDirName);
        System.out.println("Exiting.....");
        System.exit(0);
      }
    }

    String strFilename;
    if (group.indexOf("/") < 0) {
      strFilename = strDirName + "/" + group + ".xml";
    } else {
      String tmpStr = new String(group);
      int index = tmpStr.indexOf("/") + 1;
      String subStr = tmpStr.substring(index);
      strFilename = strDirName + "/" + subStr + ".xml";
    }

    //System.out.println("Processing: " + strFilename);
    
    File fileOutput = new File(strFilename);
    FileWriter fwOutput = new FileWriter(fileOutput);

    fwOutput.write("<?xml version=\"1.0\"?>\n\n");

    fwOutput.write("<qa>\n");

    fwOutput.write("  <!-- A section describing each product under test -->\n");
    fwOutput.write("  <product name=\"OpenDS\">\n\n");

    fwOutput.write("    <!-- A section describing each testphase-->\n");
    fwOutput.write("    <testphase name=\"Functional\">\n\n");

    fwOutput.write("      <!-- A section describing each testgroup-->\n");
    fwOutput.write("      <testgroup name=\"" + arrayData.getGroupName(0)
        + "\">\n");

    fwOutput.write("        <grouppurpose>" + arrayData.getGroupPurpose(0)
        + "</grouppurpose>\n");
    fwOutput.write("        <subgroupname>" + arrayData.getSubgroupName(0)
        + "</subgroupname>\n");
    fwOutput.write("        <category name=\"" + group + "\"/>\n");
    fwOutput.write("        <groupid>" + group + "</groupid>\n");
    fwOutput.write("        <purpose></purpose>\n");
    fwOutput.write("        <version></version>\n");
    fwOutput.write("        <url>https://opends.dev.java.net</url>\n\n");

    fwOutput.write("        <!-- A section describing each testsuite-->\n");
    for (int i = 0; i < arrayData.sizeSuites(); i++) {
      String currTestSuite = new String(arrayData.getTestSuiteName(i));
      String currTestGroup = arrayData.getTestGroup(i);
      String currTestScript = arrayData.getTestScript(i);
      String currTestHTMLLink = "";
      fwOutput.write("        <testsuite name=\"" + currTestSuite + "\">\n");
      fwOutput.write("          <suitepurpose>" + arrayData.getTestSuitePurpose(i)
          + "</suitepurpose>\n");
      fwOutput.write("          <suiteid>" + arrayData.getTestSuiteID(i)
          + "</suiteid>\n");
      fwOutput.write("          <suitegroup>" + arrayData.getTestSuiteGroup(i)
          + "</suitegroup>\n");

      fwOutput.write("         <!-- A section describing each testcase-->\n");

      for (int j = 0; j < arrayData.size(); j++) {
        if (currTestSuite.indexOf(arrayData.getTestMarker(j)) == 0) {
          fwOutput.write("         <testcase name=\""
              + arrayData.getTestName(j) + "\">\n");
          fwOutput.write("            <testid>" + arrayData.getTestID(j)
              + "</testid>\n");
          fwOutput.write("            <testissue>" + arrayData.getTestIssue(j)
              + "</testissue>\n");
          fwOutput.write("            <group>" + currTestGroup + "</group>\n");
          fwOutput.write("            <suite>" + currTestSuite.toString()
              + "</suite>\n");
          fwOutput.write("            <purpose>" + arrayData.getTestPurpose(j)
              + "</purpose>\n");
          fwOutput.write("            <testscript>" + currTestScript
              + "</testscript>\n");
          fwOutput.write("            <steps>\n");
          strIndividualSteps = arrayData.getTestSteps(j);

          for (int j2 = 0; j2 < strIndividualSteps.size(); j2++) {
            fwOutput.write("              <step>\n");
            if (strIndividualSteps.size() > 1)
              fwOutput.write(Integer.toString(j2 + 1) + ". "
                  + strIndividualSteps.get(j2) + "\n");
            else
              fwOutput.write(strIndividualSteps.get(j2) + "\n");
            fwOutput.write("              </step>\n");
          }
          fwOutput.write("            </steps>\n");
          String tmpString = new String(arrayData.getTestPreamble(j));
          if (tmpString.length() == 0) {
            fwOutput.write("            <preamble>None</preamble>\n");
          } else {
            fwOutput.write("            <preamble>" + tmpString
                + "</preamble>\n");
          }
          tmpString = new String(arrayData.getTestPostamble(j));
          if (tmpString == null || tmpString.length() == 0) {
            fwOutput.write("            <postamble>None</postamble>\n");
          } else {
            fwOutput.write("            <postamble>" + tmpString
                + "</postamble>\n");
          }
          fwOutput.write("            <postamble>"
              + arrayData.getTestPostamble(j) + "</postamble>\n");
          fwOutput.write("            <result>\n");
          fwOutput.write("             " + arrayData.getTestResult(j) + "\n");
          fwOutput.write("            </result>\n");
          fwOutput.write("         </testcase>\n\n");
        }
      }
      fwOutput.write("      </testsuite>\n\n");
    }

    fwOutput.write("      </testgroup>\n");
    fwOutput.write("    </testphase>\n");
    fwOutput.write("  </product>\n");
    fwOutput.write("</qa>\n");

    fwOutput.close();

  }

}
