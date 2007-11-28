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
import javax.xml.transform.*;

public class GenerateOpenDSTestSpecs 
{
  private static String strParentDirName; 
  private static String strOutputDirName;
  private static String strFileFormat;
  private static String strTestType;
  private static File fileList;
  private ArrayList <Object> arrayParsedData;

  public GenerateOpenDSTestSpecs()
  {
  }
    
  public static void main(String[] args)
  {
    // retrieve input
    if(args.length == 3)
    {
      strParentDirName = new String(args[0]);
      strOutputDirName = new String(args[1]);
      strFileFormat = new String(args[2]);
    }
    else
    {
      usage();
      System.exit(0);
    }

    // validate input
    File fileDirName = new File(strParentDirName);
    
    if(!fileDirName.isDirectory())
    {
      fatalMsg(fileDirName + " is not a directory.");
      System.exit(0);
    }
    else
    {
      System.out.println("Using file directory, " + strParentDirName);
    }

    File outputDirName = new File(strOutputDirName);
    if(!outputDirName.isDirectory())
    {
      fatalMsg(outputDirName + " is not a directory.");
      System.exit(0);
    }
    else
    {
      System.out.println("Using output directory, " + strOutputDirName);
    }

    // java files are assumed to be from the unit-integration tests.
    // xml files are assumed to be from the functional tests. 
    if((strFileFormat.startsWith("java")) || (strFileFormat.startsWith("xml")))
    {
        System.out.println("Using file format " + strFileFormat.toString());
        if (strFileFormat.startsWith("java"))
            strTestType = "Int";
        else if (strFileFormat.startsWith("xml"))
            strTestType = "Func";
    }
    else
    {
        fatalMsg(strFileFormat + " is not supported in this version");
        System.exit(0);
    }
    System.out.println("Now running......");
    
    // create list of files to parse
    TestFileList listFiles = new TestFileList(strParentDirName);
    listFiles.createFileArray();
    
    // clean out the list of files
    listFiles.purgeFilesWithSubstring("svn");
    if(strFileFormat.startsWith("java"))
    {
        listFiles.keepFilesWithSubstring("java");
    }
    else if(strFileFormat.startsWith("xml"))
    {
        listFiles.keepFilesWithSubstring("xml");
    }
    
    // The ArrayList object, arrayFiles, contains the list of files that must be parsed.
    ArrayList arrayFiles = listFiles.getFileArray();
    
    // print out list of files
    //for(int i=0; i<arrayFiles.size(); i++)
    //{
    //  File gotFile = (File)(arrayFiles.get(i));
    //  System.out.println("File number " + Integer.toString(i) + " is " + gotFile.toString()); 
    //} 

    // The ArrayList object, arrayDirs, contains the list of directories where the files will be found.
    ArrayList arrayDirs = listFiles.getDirArray();
    listFiles.purgeDirsWithSubstring("svn");
    
    // print out list of directories
    //for(int i=0; i<arrayDirs.size(); i++)
    //{
    //  File gotDir = (File)(arrayDirs.get(i));
    //  System.out.println("Directory number " + Integer.toString(i) + " is " + gotDir.toString());
    //} 

    // Assume each directory will result in an output xml file
    // There will be one ParseData object for each directory. 
    // Each ParseData object holds the test specs for all tests in that directory.
    // The ArrayList object, arrayTests, contains all the ParseData objects.
    ArrayList <Object>arrayTests = new ArrayList<Object>();

    // For each directory, parse out the data from each file within that directory.
    for(int i=0; i<arrayDirs.size(); i++)
    {
      // parse out data from all java files in a directory
      File gotDir = (File)(arrayDirs.get(i));
      String parsedDir = parseSuite(gotDir, strParentDirName);
      ParseData parseData = new ParseData(parsedDir, strFileFormat);

      try
      {
        arrayTests.add((ArrayData)(parseData.ParseFile(parsedDir, arrayFiles, strParentDirName)));
      }
      catch(Exception e)
      {
	e.printStackTrace();
	System.exit(0);
      }

    }

    // create the output xml files from the ParsedData objects
    for(int i = 0; i < arrayTests.size(); i++)
    {
      ArrayData arrayData = (ArrayData)(arrayTests.get(i));
      if(arrayData.size() > 0)
      {
          if(strFileFormat.startsWith("java"))
          {
            WriteXMLFile_int xmlFile = new WriteXMLFile_int(arrayData.getTestSuite(0));
            try
            {
              xmlFile.MakeXMLFile(arrayData, strOutputDirName);
            }
            catch(Exception e)
            {
              e.printStackTrace();
            }
          }
         else if(strFileFormat.startsWith("xml"))
         { 
            WriteXMLFile_xml xmlFile = new WriteXMLFile_xml(arrayData.getTestSuite(0));
            WriteHTMLFile htmlFile = new WriteHTMLFile(arrayData.getTestSuite(0));
            try
            {
              xmlFile.MakeXMLFile(arrayData, strOutputDirName);
              htmlFile.MakeHTMLFile(arrayData, strOutputDirName,strParentDirName);
            }
            catch(Exception e)
            {
              e.printStackTrace();
            }
         }
           
      }
         
    }

    // Write the index file
    if(strFileFormat.startsWith("xml"))
    {

      // Index.xml
      try {
      File indexFile = new File(strOutputDirName + "/index.xml");
      FileWriter indexFileout = new FileWriter(indexFile);

      indexFileout.write("<?xml version=\"1.0\"?>\n\n");
      indexFileout.write("<qa>\n");
      indexFileout.write("  <doc>\n");
      
      for(int k = 0; k < arrayTests.size(); k++)
      {

        ArrayData testSuitePath = (ArrayData)(arrayTests.get(k));
        if(testSuitePath.size() > 0)
        {   
 
          String specPath=testSuitePath.getTestSuite(0);
          
          String specName = (new File(specPath)).getName();

          String specFile=strOutputDirName + "/" + specPath + "/" + specName + ".html";
          
          indexFileout.write("    <testspec name=\"" + specName + "\" location=\"" + specFile + "\"/>\n");
        }
      }
      
      indexFileout.write("  </doc>\n");
      indexFileout.write("</qa>\n");
      indexFileout.close(); 
      
      }
      catch (Exception e) {
        e.printStackTrace( );
      }
    
      // Index.html
      File xmlFilename= new File(strOutputDirName + "/index.xml");
      File xslFilename = new File(strParentDirName + "/../shared/xsl/testspec-index-stylesheet.xsl");
      File htmlFilename = new File(strOutputDirName + "/index.html");
      try{
        TransformerFactory transFactory = TransformerFactory.newInstance();

        Transformer transformer = transFactory.newTransformer
          (new javax.xml.transform.stream.StreamSource(xslFilename));

        transformer.transform
          (new javax.xml.transform.stream.StreamSource(xmlFilename),
           new javax.xml.transform.stream.StreamResult
           (new FileOutputStream(htmlFilename))
        );
      }

      catch (Exception e) {
        e.printStackTrace( );
      }

    }
    
    System.out.println("Files successfully written to the output directory.");

  }

  private static void usage()
  {
    System.out.println("Version 01202007");
    System.out.println("This program will parse files that are used for testing and create an xml file that is used for generating test specification html pages.");
    System.out.println("The program will recursively search for files from the directory that is passed in from the parameter.");
    System.out.println("This version will only recursively search one or two levels below the \"directory to files\" which is passed in as a parameter.");
    System.out.println("The file formats that are currently supported are \"java\" and \"xml\".");
    System.out.println("Usage:");
    System.out.println("    java GenerateOpenDSTestSpecs [directory to files] [directory for output files] [file format]");
  }

  private static void fatalMsg(String str)
  {
    System.out.println(str);
    System.out.println("exiting.....");
  }

  private static String parseSuite(File inDir, String strParentDir)
  {
      String tmpStr = new String(inDir.toString());
      int index = tmpStr.indexOf(strParentDir) + strParentDir.length() + 1;
      String subStr = tmpStr.substring(index);
      
      return subStr;
      
  }
  
}
