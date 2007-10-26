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

public class WriteXMLFile_int
{
  private String group;
  private ArrayData arrayData;

  public WriteXMLFile_int(String inGroup)
  {
    group = inGroup;
  }

  public void MakeXMLFile(ArrayData arrayData, String strDir) throws IOException
  {
    //System.out.println("For " + group + " the number of suites is " + Integer.toString(arrayData.sizeSuites()));
    //System.out.println("The number of tests is " + Integer.toString(arrayData.size()));

    String strDirName = strDir + "/" + group;
    File fileDirName = new File(strDirName);
    if(!fileDirName.isDirectory())
    {
      if(!fileDirName.mkdirs())
      {
	System.out.println("Could not create directory, " + strDirName);
	System.out.println("Exiting.....");
	System.exit(0);
      } 
    }

    String strFilename = strDirName + "/" + group + ".xml";
    File fileOutput = new File(strFilename);
    FileWriter fwOutput = new FileWriter(fileOutput);

    fwOutput.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
    fwOutput.write("<?xml-stylesheet type=\"text/xsl\" href=\"" + group + ".xsl\"?>\n");
    fwOutput.write("<qa xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"no-schema-yet.xsd\">\n\n");

    fwOutput.write("  <!-- A section describing each product under test -->\n");
    fwOutput.write("  <product name=\"OpenDS\">\n\n");

    fwOutput.write("    <!-- A section describing each testgroup-->\n");
    fwOutput.write("    <testgroup  name=\"Integration\">\n");
    fwOutput.write("      <category name=\"" + group + "\"/>\n");
    fwOutput.write("      <groupid>" + group + "</groupid>\n");
    fwOutput.write("      <purpose></purpose>\n");
    fwOutput.write("      <version></version>\n");
    fwOutput.write("      <url>http://samsonite.central.sun.com/" + group + "</url>\n\n");

    fwOutput.write("      <!-- A section describing each testsuite-->\n");
    for(int i = 0; i < arrayData.sizeSuites(); i++)
    {
      String currTestSuite = new String(arrayData.getTestSuiteName(i));
      //System.out.println("currTestSuite is " + currTestSuite.toString());
      String currTestGroup = arrayData.getTestGroup(i);
      String currTestScript = arrayData.getTestScript(i);
      String currTestHTMLLink = "";
      //String currTestHTMLLink = arrayData.getTestHTMLLink(i);
      fwOutput.write("      <testsuite name=\"" + currTestSuite + "\">\n");
      fwOutput.write("          <purpose>" + arrayData.getTestSuitePurpose(i) + "</purpose>\n");
      fwOutput.write("          <suiteid>" + arrayData.getTestSuiteID(i) + "</suiteid>\n");
      fwOutput.write("          <group>" + arrayData.getTestSuiteGroup(i) + "</group>\n\n");

      fwOutput.write("         <!-- A section describing each testcase-->\n");
      //System.out.println("arrayData size is " + Integer.toString(arrayData.size()));
      for(int j = 0; j < arrayData.size(); j++)
      {
	//System.out.println("Test Marker is\n" + arrayData.getTestMarker(j));
	//System.out.println("currTestSuite is\n" + currTestSuite);
    	//System.out.println(" ");
 	if(currTestSuite.indexOf(arrayData.getTestMarker(j)) >= 0)
	{
          fwOutput.write("         <testcase name=\"" + arrayData.getTestName(j) + "\">\n"); 
          fwOutput.write("            <testid>" + arrayData.getTestID(j) + "</testid>\n");
          //fwOutput.write("            <testissue>" + arrayData.getTestIssue(j) + "</testissue>\n");
          fwOutput.write("            <group>" + currTestGroup + "</group>\n");
          fwOutput.write("            <suite>" + currTestSuite.toString() + "</suite>\n");
          fwOutput.write("            <purpose>" + arrayData.getTestPurpose(j) + "</purpose>\n");
          fwOutput.write("            <testscript>\n");
          fwOutput.write("                        <a href=\"" + currTestHTMLLink +  "\">" + currTestScript + "</a>\n");
          fwOutput.write("            </testscript>\n");
          fwOutput.write("            <steps>\n");
          fwOutput.write("              <step>\n");
          fwOutput.write("               " + arrayData.getTestSteps(j) + "\n");
          fwOutput.write("              </step>\n");
          fwOutput.write("            </steps>\n");
	  String tmpString = new String(arrayData.getTestPreamble(j));
	  if(tmpString.length() == 0)
	  {
            fwOutput.write("            <preamble>None</preamble>\n");
	  }
	  else	
          {
	    fwOutput.write("            <preamble>" + tmpString + "</preamble>\n");
	  }
	  tmpString = new String(arrayData.getTestPostamble(j));
	  if(tmpString.length() == 0)
	  {
            fwOutput.write("            <postamble>None</postamble>\n");
	  }
	  else	
          {
	    fwOutput.write("            <postamble>" + tmpString + "</postamble>\n");
	  }
          fwOutput.write("            <postamble>" + arrayData.getTestPostamble(j) + "</postamble>\n");
          fwOutput.write("            <result>\n");
          fwOutput.write("             " + arrayData.getTestResult(j) + "\n");
          fwOutput.write("            </result>\n");
          fwOutput.write("         </testcase>\n\n");
        }
      }
      fwOutput.write("      </testsuite>\n\n");
    }

    fwOutput.write("    </testgroup>\n");
    fwOutput.write("  </product>\n");
    fwOutput.write("</qa>\n");

    fwOutput.close();

  }

}
