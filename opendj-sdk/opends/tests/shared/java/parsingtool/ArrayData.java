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
import java.io.*;
import java.lang.*;
import java.util.ArrayList;

public class ArrayData 
{
  private ArrayList <Object> testGroupName;
  private ArrayList <Object> testGroupPurpose;
  private ArrayList <Object> testSubgroupName;
  private ArrayList <Object> testSuiteName;
  private ArrayList <Object> testSuitePurpose;
  private ArrayList <Object> testSuiteID;
  private ArrayList <Object> testSuiteGroup;
  private ArrayList <Object> testSuitePreamble;
  private ArrayList <Object> testSuitePostamble;
  private ArrayList <Object> testSuite;
  private ArrayList <Object> testName;
  private ArrayList <Object> testMarker;
  private ArrayList <Object> testID;
  private ArrayList <Object> testIssue;
  private ArrayList <Object> testGroup;
  private ArrayList <Object> testScript;
  private ArrayList <Object> testHTMLLink;
  private ArrayList <Object> testPurpose;
  private ArrayList <Object> testPreamble;
  private ArrayList <Object> testSteps;
  private ArrayList <Object> testPostamble;
  private ArrayList <Object> testResult;

  public ArrayData()
  {
    testGroupName = new ArrayList<Object>();
    testGroupPurpose = new ArrayList<Object>();
    testSubgroupName = new ArrayList<Object>();
    testSuiteName = new ArrayList<Object>();
    testSuitePurpose = new ArrayList<Object>();
    testSuiteID = new ArrayList<Object>();
    testSuiteGroup = new ArrayList<Object>();
    testSuitePreamble = new ArrayList<Object>();
    testSuitePostamble = new ArrayList<Object>();
    testSuite = new ArrayList<Object>();
    testName = new ArrayList<Object>();
    testMarker = new ArrayList<Object>();
    testID = new ArrayList<Object>();
    testIssue = new ArrayList<Object>();
    testGroup = new ArrayList<Object>();
    testScript = new ArrayList<Object>();
    testHTMLLink = new ArrayList<Object>();
    testPurpose = new ArrayList<Object>();
    testPreamble = new ArrayList<Object>();
    testSteps = new ArrayList<Object>();
    testPostamble = new ArrayList<Object>();
    testResult = new ArrayList<Object>();
  }

  public void setGroupName(String inGroupName){ testGroupName.add(inGroupName); }
  public void setGroupPurpose(String inGroupPurpose){ testGroupPurpose.add(inGroupPurpose); }
  public void setSubgroupName(String inSubgroupName){ testSubgroupName.add(inSubgroupName); }
  public void setTestSuiteName(String inTestSuiteName){ testSuiteName.add(inTestSuiteName); }
  public void setTestSuitePurpose(String inTestSuitePurpose){ testSuitePurpose.add(inTestSuitePurpose); }
  public void setTestSuiteID(String inTestSuiteID){ testSuiteID.add(inTestSuiteID); }
  public void setTestSuiteGroup(String inTestSuiteGroup){ testSuiteGroup.add(inTestSuiteGroup); }
  public void setTestSuitePreamble(String inTestSuitePreamble){ testSuitePreamble.add(inTestSuitePreamble); }
  public void setTestSuitePostamble(String inTestSuitePostamble){ testSuitePostamble.add(inTestSuitePostamble); }
  public void setTestSuite(String inTestSuite){ testSuite.add(inTestSuite); }
  public void setTestName(String inTestName){ testName.add(inTestName); }
  public void setTestMarker(String inTestMarker){ testMarker.add(inTestMarker); }
  public void setTestID(String inTestID){ testID.add(inTestID); }
  public void setTestIssue(String inTestIssue){ testIssue.add(inTestIssue); }
  public void setTestGroup(String inTestGroup){ testGroup.add(inTestGroup); }
  public void setTestScript(String inTestScript){ testScript.add(inTestScript); }
  public void setTestHTMLLink(String inTestHTMLLink){ testHTMLLink.add(inTestHTMLLink); }
  public void setTestPurpose(String inTestPurpose){ testPurpose.add(inTestPurpose); }
  public void setTestPreamble(String inTestPreamble){ testPreamble.add(inTestPreamble); }
  public void setTestSteps(ArrayList inTestSteps){ testSteps.add(inTestSteps); }
  public void setTestPostamble(String inTestPostamble){ testPostamble.add(inTestPostamble); }
  public void setTestResult(String inTestResult){ testResult.add(inTestResult); }

  public <Object> ArrayList getGroupName(){ return testGroupName; }
  public <Object> ArrayList getGroupPurpose(){ return testGroupPurpose; }
  public <Object> ArrayList getSubgroupName(){ return testSubgroupName; }
  public <Object> ArrayList getTestSuiteName(){ return testSuiteName; }
  public <Object> ArrayList getTestSuitePurpose(){ return testSuitePurpose; }
  public <Object> ArrayList getTestSuiteID(){ return testSuiteID; }
  public <Object> ArrayList getTestSuiteGroup(){ return testSuiteGroup; }
  public <Object> ArrayList getTestSuitePreamble(){ return testSuitePreamble; }
  public <Object> ArrayList getTestSuitePostamble(){ return testSuitePostamble; }
  public <Object> ArrayList getTestSuite(){ return testSuite; }
  public <Object> ArrayList getTestName(){ return testName; }
  public <Object> ArrayList getTestMarker(){ return testMarker; }
  public <Object> ArrayList getTestID(){ return testID; }
  public <Object> ArrayList getTestIssue(){ return testIssue; }
  public <Object> ArrayList getTestGroup(){ return testGroup; }
  public <Object> ArrayList getTestScript(){ return testScript; }
  public <Object> ArrayList getTestHTMLLink(){ return testHTMLLink; }
  public <Object> ArrayList getTestPurpose(){ return testPurpose; }
  public <Object> ArrayList getTestPreamble(){ return testPreamble; }
  public <Object> ArrayList getTestSteps(){ return testSteps; }
  public <Object> ArrayList getTestPostamble(){ return testPostamble; }
  public <Object> ArrayList getTestResult(){ return testResult; }

  public String getGroupName(int index){ if(testGroupName != null && index < testGroupName.size())  return (String)(testGroupName.get(index)); else return null;}
  public String getGroupPurpose(int index){ if(testGroupPurpose != null && index < testGroupPurpose.size())  return (String)(testGroupPurpose.get(index)); else return null;}
  public String getSubgroupName(int index){ if(testSubgroupName != null && index < testSubgroupName.size())  return (String)(testSubgroupName.get(index)); else return null;}
  public String getTestSuiteName(int index){ if(testSuiteName != null && index < testSuiteName.size())  return (String)(testSuiteName.get(index)); else return null;}
  public String getTestSuitePurpose(int index){ if(testSuitePurpose != null && index < testSuitePurpose.size())  return (String)(testSuitePurpose.get(index)); else return null;}
  public String getTestSuiteID(int index){ if(testSuiteID != null && index < testSuiteID.size())  return (String)(testSuiteID.get(index)); else return null;}
  public String getTestSuiteGroup(int index){ if(testSuiteGroup != null && index < testSuiteGroup.size())  return (String)(testSuiteGroup.get(index)); else return null;}
  public String getTestSuitePreamble(int index){ if(testSuitePreamble != null && index < testSuitePreamble.size())  return (String)(testSuitePreamble.get(index)); else return null;}
  public String getTestSuitePostamble(int index){ if(testSuitePostamble != null && index < testSuitePostamble.size())  return (String)(testSuitePostamble.get(index)); else return null;}
  public String getTestSuite(int index){ if(testSuite != null && index < testSuite.size())  return (String)(testSuite.get(index)); else return null;}
  public String getTestName(int index){ if(testName != null && index < testName.size())  return (String)(testName.get(index)); else return null;}
  public String getTestMarker(int index){ if(testMarker != null && index < testMarker.size())  return (String)(testMarker.get(index)); else return null;}
  public String getTestID(int index){ if(testID != null && index < testID.size())  return (String)(testID.get(index)); else return null;}
  public String getTestIssue(int index){ if(testIssue != null && index < testIssue.size()) return (String)(testIssue.get(index)); else return null;}
  public String getTestGroup(int index){ if(testGroup != null && index < testGroup.size()) return (String)(testGroup.get(index)); else return null;}
  public String getTestScript(int index){ if(testScript != null && index < testScript.size()) return (String)(testScript.get(index)); else return null;}
  public String getTestHTMLLink(int index){ if(testHTMLLink != null && index < testHTMLLink.size()) return (String)(testHTMLLink.get(index)); else return null;}
  public String getTestPurpose(int index){ if(testPurpose != null && index < testPurpose.size()) return (String)(testPurpose.get(index)); else return null;}
  public String getTestPreamble(int index){if(testPreamble != null && index < testPreamble.size()) return (String)(testPreamble.get(index)); else return null;}
  public ArrayList<String> getTestSteps(int index){ if(testSteps != null && index < testSteps.size()) return (ArrayList<String>)(testSteps.get(index)); else return null;}
  public String getTestPostamble(int index){ if(testPostamble != null && index < testPostamble.size()) return (String)(testPostamble.get(index)); else return null;}
  public String getTestResult(int index){ if(testResult != null && index < testResult.size()) return (String)(testResult.get(index)); else return null;}

  public int size(){ return testName.size(); }
  public int sizeSuites(){ return testSuiteName.size(); }
}
