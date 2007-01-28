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
package org.opends.build.tools;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.LinkedList;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;



/**
 * This class provides an implementation of an Ant task that may be used to
 * verify that the copyright dates of all modified files in the workspace have
 * been updated to include the current year.  In particular, for all files in
 * one of the specified filesets that have been created or modified in the
 * local workspace, it will parse the file for any line which appears to be
 * a comment and contains the word "copyright".  If the line does not also
 * contain the current year, then it will be flagged as a potential violation to
 * be addressed.
 */
public class CheckCopyrightDates
       extends Task
{
  /**
   * The name of the system property that may be used to prevent copyright date
   * problems from failing the build.
   */
  public static final String IGNORE_ERRORS_PROPERTY =
       "org.opends.server.IgnoreCopyrightDateErrors";



  // A list of all the filesets to be checked.
  private LinkedList<FileSet> filesetList = new LinkedList<FileSet>();

  // The path to the root of the Subversion workspace to check.
  private String workspace = null;



  /**
   * Adds the provided fileset to the list of filesets that should be checked.
   *
   * @param  fileset  A fileset containing a list of files that should be
   *                  checked.
   */
  public void addFileset(FileSet fileset)
  {
    filesetList.add(fileset);
  }



  /**
   * Specifies the path to the root of the Subversion workspace for which to
   * retrieve the revision number.
   *
   * @param  workspace  The path to the root of the Subversion workspace for
   *                    which to retrieve the revision number.
   */
  public void setWorkspace(String workspace)
  {
    this.workspace = workspace;
  }



  /**
   * Performs the appropriate processing needed for this task.  In this case,
   * it uses SVNKit to identify all modified files in the current workspace.
   * For all source files, look for comment lines containing the word
   * "copyright" and make sure at least one of them contains the current year.
   */
  @Override()
  public void execute()
  {
    // Make sure that at least one fileset was provided.
    if (filesetList.isEmpty())
    {
      throw new BuildException("ERROR:  No filesets were specified to " +
                               "indicate which files should be checked.");
    }


    File workspacePath;
    if ((workspace == null) || (workspace.length() == 0))
    {
      workspacePath = getProject().getBaseDir();
    }
    else
    {
      workspacePath = new File(workspace);
    }
    String workspacePathString = workspacePath.getAbsolutePath() +
                                 File.separator;


    // Get the year to use in the determination.
    GregorianCalendar calendar = new GregorianCalendar();
    int year = calendar.get(GregorianCalendar.YEAR);
    String yearString = String.valueOf(year);


    // Get the current status for all files in the fileset.  For any files with
    // local changes, see if there are any files that potentially have the wrong
    // copyright year.
    SVNStatusClient svnClient = new SVNStatusClient(null, null);

    LinkedList<String> problemFiles = new LinkedList<String>();
    for (FileSet fileSet : filesetList)
    {
      DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
      for (String relativePath : scanner.getIncludedFiles())
      {
        String filePath = scanner.getBasedir() + File.separator + relativePath;

        try
        {
          SVNStatus svnStatus = svnClient.doStatus(new File(filePath), false);
          if (svnStatus == null)
          {
            System.err.println("WARNING:  Could not determine Subversion " +
                               "status for file " + filePath);
            System.err.println("No further copyright date checking will be " +
                               "performed.");
            return;
          }

          SVNStatusType statusType = svnStatus.getContentsStatus();
          if ((statusType == SVNStatusType.STATUS_ADDED) ||
              (statusType == SVNStatusType.STATUS_MODIFIED) ||
              (statusType == SVNStatusType.STATUS_UNVERSIONED))
          {
            BufferedReader reader = null;
            try
            {
              boolean copyrightFound   = false;
              boolean correctYearFound = false;
              reader = new BufferedReader(new FileReader(filePath));
              String line = reader.readLine();
              while (line != null)
              {
                String lowerLine = line.toLowerCase().trim();
                if (isCommentLine(lowerLine))
                {
                  int copyrightPos = lowerLine.indexOf("copyright");
                  if (copyrightPos > 0)
                  {
                    copyrightFound = true;
                    if (lowerLine.indexOf(yearString) > 0)
                    {
                      correctYearFound = true;
                      break;
                    }
                  }
                }

                line = reader.readLine();
              }

              if (copyrightFound && (! correctYearFound))
              {
                if (filePath.startsWith(workspacePathString))
                {
                  problemFiles.add(filePath.substring(
                                                 workspacePathString.length()));
                }
                else
                {
                  problemFiles.add(filePath);
                }
              }
            }
            catch (IOException ioe)
            {
              System.err.println("ERROR:  Could not read file " + filePath +
                                 " to check copyright date.");
              System.err.println("No further copyright date checking will be " +
                                 "performed.");
              return;
            }
            finally
            {
              try
              {
                if (reader != null)
                {
                  reader.close();
                }
              } catch (Exception e) {}
            }
          }
        }
        catch (SVNException svnException)
        {
          System.err.println("WARNING:  Could not determine Subversion " +
                             "status for file " + filePath + ":  " +
                             svnException);
          System.err.println("No further copyright date checking will be " +
                             "performed.");
          return;
        }
      }
    }


    if (! problemFiles.isEmpty())
    {
      System.err.println("WARNING:  Potential copyright year updates needed " +
                         "for the following files:");
      for (String filename : problemFiles)
      {
        System.err.println("     " + filename);
      }

      String ignoreStr = getProject().getProperty(IGNORE_ERRORS_PROPERTY);
      if ((ignoreStr == null) || (! ignoreStr.equalsIgnoreCase("true")))
      {
        throw new BuildException("Fix copyright date problems before " +
                                 "proceeding, or use '-D" +
                                 IGNORE_ERRORS_PROPERTY + "=true' to " +
                                 "ignore copyright warnings.");
      }
    }
  }



  /**
   * Indicates whether the provided line appears to be a comment line.  It will
   * check for a number of common comment indicators in Java source files,
   * shell scripts, XML files, and LDIF files.
   *
   * @param  lowerLine  The line to be checked.  It should have been coverted to
   *                    all lowercase characters and any leading spaces
   *                    removed.
   *
   * @return  {@code true} if it appears that the line is a comment line, or
   *          {@code false} if not.
   */
  private static boolean isCommentLine(String lowerLine)
  {
    if (lowerLine.startsWith("/*") ||
        lowerLine.startsWith("*") ||
        lowerLine.startsWith("//") ||
        lowerLine.startsWith("#") ||
        lowerLine.startsWith("<!--") ||
        lowerLine.startsWith("!"))
    {
      return true;
    }
    else
    {
      return false;
    }
  }
}

