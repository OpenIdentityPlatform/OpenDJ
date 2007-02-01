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
 * by brackets "[]" replaced with your own identifying * information:
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

public class TestFileList 
{
  private File fileParentDirName; 
  private ArrayList <Object> arrayFiles;
  private ArrayList <Object> arrayDirs;

  public TestFileList(String inParentDir)
  {
    fileParentDirName = new File(inParentDir);
  }

  public ArrayList createFileArray()
  { 
    // Retrieve list of files to parse
    File fileAndDirList[] = fileParentDirName.listFiles();

    // create an array object that can be increased and decreased in size
    arrayFiles = new ArrayList<Object>(fileAndDirList.length);
    for(int i=0; i<fileAndDirList.length; i++)
    {
      arrayFiles.add((File)(fileAndDirList[i]));
    }

    // find the files in subdirectories
    arrayDirs = new ArrayList<Object>();
    int intStartNumElements = arrayFiles.size();
    for(int i=0; i<intStartNumElements; i++)
    {
      File fileChildDirName = (File)(arrayFiles.get(i));
      if(fileChildDirName.isDirectory())
      {
	arrayDirs.add((File)(fileChildDirName));
        File fileTmpList[] = fileChildDirName.listFiles();
        for(int j=0; j<fileTmpList.length; j++)
        {
	  if(fileTmpList[j].isFile())
          {
	      arrayFiles.add((File)(fileTmpList[j]));
          }
          else if(fileTmpList[j].isDirectory())
          {
              arrayDirs.add((File)(fileTmpList[j]));
              File fileTmpList2[] = fileTmpList[j].listFiles();
              for(int j2=0; j2<fileTmpList2.length; j2++)
              {
                    if(fileTmpList2[j2].isFile())
                    {
                        arrayFiles.add((File)(fileTmpList2[j2]));
                    }
              }
              
          }
	  //System.out.println("Found " + fileTmpList[j].toString());
	}
      } 

    }

    // clean out file array
    for(int i=arrayFiles.size()-1; i>=0; i--)
    {
      File fileTmp = (File)(arrayFiles.get(i));
      if(fileTmp.isDirectory())
	arrayFiles.remove(i);
    }

    return arrayFiles;    
  }

  public void purgeFilesWithSubstring(String subStr)
  {
    for(int i=arrayFiles.size()-1; i>=0; i--)
    {
      File fileTmp = (File)(arrayFiles.get(i));
      if(fileTmp.toString().indexOf(subStr) >= 0)
	arrayFiles.remove(i);
    }

  }

  public void keepFilesWithSubstring(String subStr)
  {
    for(int i=arrayFiles.size()-1; i>=0; i--)
    {
      File fileTmp = (File)(arrayFiles.get(i));
      if(fileTmp.toString().indexOf(subStr) < 0)
	arrayFiles.remove(i);
    }

  }

  public void purgeDirsWithSubstring(String subStr)
  {
    for(int i=arrayDirs.size()-1; i>=0; i--)
    {
      File fileTmp = (File)(arrayDirs.get(i));
      if(fileTmp.toString().indexOf(subStr) >= 0)
	arrayDirs.remove(i);
    }

  }

  public void keepDirsWithSubstring(String subStr)
  {
    for(int i=arrayDirs.size()-1; i>=0; i--)
    {
      File fileTmp = (File)(arrayDirs.get(i));
      if(fileTmp.toString().indexOf(subStr) < 0)
	arrayDirs.remove(i);
    }

  }

  public ArrayList getFileArray()
  {
    return arrayFiles;
  }

  public ArrayList getDirArray()
  {
    return arrayDirs;
  }

}
