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

public class ParseData 
{
  private String suite;
  private String fileFormat;
  private ArrayData arrayData;

  public ParseData(String inName, String inFileFormat)
  {
    suite = inName;
    fileFormat = inFileFormat;
    arrayData = new ArrayData();
  }

  // Parse out data from all the files in the directory.
  // Place the data into an ArrayData object.
  // Return the ArrayData object.
  public ArrayData ParseFile(String fileDir, ArrayList arrayFiles, String strParentDirName) throws IOException
  {
      String currTestSuite = strParentDirName + "/" + fileDir;

      for(int j=0; j<arrayFiles.size(); j++)
      {
        File currFile = (File)(arrayFiles.get(j));
        if(currFile.toString().indexOf(currTestSuite) >= 0)
        {
          // synthesize the filename for the current file to parse
          String filename = currTestSuite + "/" + currFile.getName();
        
          File inputFile = new File(filename);
          
          // Final check to make sure inputFile is a real file
          if(inputFile.isFile())
          {
              FileInputStream fis = new FileInputStream(inputFile);
              LineNumberReader fin = new LineNumberReader(new InputStreamReader(fis));
              
              String tmpStr; 
              while((tmpStr = fin.readLine()) != null) 
              {
                // First check the line to see if there is any QA test marker, #@, at all.
                if(tmpStr.indexOf("#@") >= 0)
                {
                  if(tmpStr.indexOf("#@TestSuiteName") >= 0)
                  {
                    arrayData.setTestSuiteName(StripSubstring(tmpStr, "#@TestSuiteName"));
                    //System.out.println("Test suite name is " + arrayData.getTestSuiteName());
                  }
                  else if(tmpStr.indexOf("#@TestGroupName") >= 0)
                  {
                    arrayData.setGroupName(StripSubstring(tmpStr, "#@TestGroupName"));
                  }
                  else if(tmpStr.indexOf("#@TestGroupPurpose") >= 0)
                  {
                    arrayData.setGroupPurpose(MultipleLines(tmpStr, "#@TestGroupPurpose", fin));
                  }
                  else if(tmpStr.indexOf("#@TestSubgroupName") >= 0)
                  {
                    arrayData.setSubgroupName(StripSubstring(tmpStr, "#@TestSubgroupName"));
                  }
                  else if(tmpStr.indexOf("#@TestSuitePurpose") >= 0)
                  {
                    arrayData.setTestSuitePurpose(MultipleLines(tmpStr, "#@TestSuitePurpose", fin));
                  }
                  else if(tmpStr.indexOf("#@TestSuiteID") >= 0)
                  {
                    arrayData.setTestSuiteID(StripSubstring(tmpStr, "#@TestSuiteID"));
                  }
                  else if(tmpStr.indexOf("#@TestSuiteGroup") >= 0)
                  {
                    arrayData.setTestSuiteGroup(StripSubstring(tmpStr, "#@TestSuiteGroup"));
                  }
                  else if(tmpStr.indexOf("#@TestSuitePreamble") >= 0)
                  {
                    arrayData.setTestSuitePreamble(StripSubstring(tmpStr, "#@TestSuitePreamble"));
                  }
                  else if(tmpStr.indexOf("#@TestSuitePostamble") >= 0)
                  {
                    arrayData.setTestSuitePostamble(StripSubstring(tmpStr, "#@TestSuitePostamble"));
                  }
                  else if(tmpStr.indexOf("#@TestName") >= 0)
                  {
                    arrayData.setTestName(StripSubstring(tmpStr, "#@TestName"));
                    arrayData.setTestSuite(fileDir);
                  }
                  else if(tmpStr.indexOf("#@TestMarker") >= 0)
                  {
                    arrayData.setTestMarker(StripSubstring(tmpStr, "#@TestMarker"));
                  }
                  else if(tmpStr.indexOf("#@TestID") >= 0)
                  {
                    arrayData.setTestID(StripSubstring(tmpStr, "#@TestID"));
                  }
                  else if(tmpStr.indexOf("#@TestIssue") >= 0)
                  {
                    arrayData.setTestIssue(StripSubstring(tmpStr, "#@TestIssue"));
                  }
                  else if(tmpStr.indexOf("#@TestGroup") >= 0)
                  {
                    arrayData.setTestGroup(StripSubstring(tmpStr, "#@TestGroup"));
                  }
                  else if(tmpStr.indexOf("#@TestScript") >= 0)
                  {
                    arrayData.setTestScript(StripSubstring(tmpStr, "#@TestScript"));
                  }
                  else if(tmpStr.indexOf("#@TestHTMLLink") >= 0)
                  {
                    arrayData.setTestHTMLLink(StripSubstring(tmpStr, "#@TestHTMLLink"));
                  }
                  else if(tmpStr.indexOf("#@TestPreamble") >= 0)
                  {
                    arrayData.setTestPreamble(StripSubstring(tmpStr, "#@TestPreamble"));
                  }
                  else if(tmpStr.indexOf("#@TestStep") >= 0)
                  {
                    ArrayList <String> strIndividualSteps = new ArrayList<String>();
                    fin.mark(1000);

                    while(tmpStr.indexOf("#@TestStep") >= 0)
                    {
                        String strStep = StripSubstring(tmpStr, "#@TestStep");
                        tmpStr = fin.readLine();
                        
                        while((tmpStr.indexOf("#@")) < 0)
                        {
                          strStep = strStep + " " + tmpStr.toString();
                          tmpStr = fin.readLine();
                        }
                        
                        strIndividualSteps.add(strStep);
                    }

                    arrayData.setTestSteps(strIndividualSteps);
                    fin.reset();
                    tmpStr = fin.readLine();
                    while(tmpStr.indexOf("#@") < 0 || tmpStr.indexOf("#@TestStep") >= 0)
                    {
                        fin.mark(100);
                        tmpStr = fin.readLine();
                    }
                    fin.reset();
                  }
                  else if(tmpStr.indexOf("#@TestPostamble") >= 0)
                  {
                    arrayData.setTestPostamble(StripSubstring(tmpStr, "#@TestPostamble"));
                  }
                  else if(tmpStr.indexOf("#@TestPurpose") >= 0)
                  {
                    arrayData.setTestPurpose(MultipleLines(tmpStr, "#@TestPurpose", fin));
                  }
                  else if(tmpStr.indexOf("#@TestResult") >= 0)
                  {
                    if(fileFormat.startsWith("xml"))
                    {
                        arrayData.setTestResult(MultipleLines(tmpStr, "#@TestResult", fin));
                    }
                    else if(fileFormat.startsWith("java"))
                    {
                        arrayData.setTestResult(StripSubstring(tmpStr, "#@TestResult"));

                        // parse test purpose from java doc comments
                        fin.mark(1000);
                        String oneMoLine = new String(fin.readLine().trim());
                        while((oneMoLine.indexOf("/**")) < 0)
                        {
                          oneMoLine = new String(fin.readLine().trim());
                        }
                        oneMoLine = new String(fin.readLine().trim());

                        String strPurpose = StripSubstring(oneMoLine, "*");
                        oneMoLine = new String(fin.readLine().trim());
                        while((oneMoLine.indexOf("@")) < 0)
                        {
                          strPurpose += " ";
                          strPurpose += StripSubstring(oneMoLine.toString().trim(), "*");
                          oneMoLine = new String(fin.readLine().trim());
                        }
                        fin.reset();
                        int starIndex = strPurpose.lastIndexOf("*");
                        if(starIndex > 0)
                        {
                          String strPurpose2 = new String(strPurpose.substring(0, starIndex));
                          arrayData.setTestPurpose(strPurpose2.toString());
                        }
                        else
                        {
                          arrayData.setTestPurpose(strPurpose.toString());
                        }
                    }
                    
                  }
                }

              }
              fin.close();
           }
        }

      }

      return arrayData;
  }

  private String StripSubstring(String tmpStr, String subStr)
  {
    int index = tmpStr.indexOf(subStr);
    String retStr = tmpStr.substring(index + subStr.length());
    return (retStr.trim());
  }

  private String MultipleLines(String tmpStr, String tag, LineNumberReader fin) throws IOException
  {
    String currValue = StripSubstring(tmpStr, tag);
    
    fin.mark(1000);
    
    String oneMoLine = new String(fin.readLine().trim());
    while((oneMoLine.indexOf("#@") < 0) && (oneMoLine.indexOf("-->") < 0)) {
      currValue = currValue + " " + oneMoLine;
      oneMoLine = new String(fin.readLine().trim());
    }
    
    fin.reset();
    
    return currValue;
  }
  
}
