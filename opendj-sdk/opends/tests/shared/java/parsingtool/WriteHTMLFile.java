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
import javax.xml.transform.*;
import java.net.*;

public class WriteHTMLFile
{
  private String group;
  private ArrayData arrayData;
  private ArrayList <String> strIndividualSteps;

  public WriteHTMLFile(String inGroup)
  {
    group = inGroup;
    strIndividualSteps = null;
  }
    
  public void MakeHTMLFile(ArrayData arrayData, String strDir, String strParentDir) throws IOException
  {
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

    String xmlFilename;
    String htmlFilename;
    File xslFilename = new File(strParentDir + "/../shared/xsl/testspec-stylesheet.xsl");

    if(group.indexOf("/") < 0)
    {
        xmlFilename = strDirName + "/" + group + ".xml";
        htmlFilename = strDirName + "/" + group + ".html";
    }
    else
    {
        String tmpStr = new String(group);
        int index = tmpStr.indexOf("/") + 1;
        String subStr = tmpStr.substring(index);
        xmlFilename = strDirName + "/" + subStr + ".xml";
        htmlFilename = strDirName + "/" + subStr + ".html";
    }
      
    System.out.println("Processing: " + xmlFilename);
    
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

}
