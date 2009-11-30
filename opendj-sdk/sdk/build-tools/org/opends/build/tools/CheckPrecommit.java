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
package org.opends.build.tools;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.LinkedList;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNWCClient;



/**
 * This class provides an implementation of an Ant task that may be used to
 * perform various checks to deteermine whether a file is suitable to be
 * committed.  This includes:
 * <UL>
 *   <LI>Make sure that the file has the correct "svn:eol-style" property
 *       value.</LI>
 *   <LI>If a file contains a line that appears to be a comment and includes the
 *       word "copyright", then it should contain the current year.</LI>
 * </UL>
 */
public class CheckPrecommit
       extends Task
       implements ISVNStatusHandler
{
  /**
   * The name of the system property that may be used to prevent copyright date
   * problems from failing the build.
   */
  public static final String IGNORE_COPYRIGHT_ERRORS_PROPERTY =
       "org.opends.server.IgnoreCopyrightDateErrors";



  /**
   * The name of the system property that may be used to prevent svn eol-style
   * problems from failing the build.
   */
  public static final String IGNORE_EOLSTYLE_ERRORS_PROPERTY =
       "org.opends.server.IgnoreEOLStyleErrors";



  /**
   *
   */
  public static final HashSet<String> CHECKED_EXTENSIONS =
       new HashSet<String>();
  static
  {
    CHECKED_EXTENSIONS.add("java");
    CHECKED_EXTENSIONS.add("xml");
    CHECKED_EXTENSIONS.add("xsd");
    CHECKED_EXTENSIONS.add("xsl");
    CHECKED_EXTENSIONS.add("html");
    CHECKED_EXTENSIONS.add("sh");
    CHECKED_EXTENSIONS.add("bat");
    CHECKED_EXTENSIONS.add("ldif");
    CHECKED_EXTENSIONS.add("txt");
    CHECKED_EXTENSIONS.add("c");
    CHECKED_EXTENSIONS.add("h");
    CHECKED_EXTENSIONS.add("mc");
    CHECKED_EXTENSIONS.add("Makefile");
  }



  // The path to the directory that is the base of the workspace.
  private File workspacePath;

  // The set of files that appear to have problems with the EOL style.
  private LinkedList<String> eolStyleProblemFiles = new LinkedList<String>();

  // The set of files that appear to have problems with the copyright date.
  private LinkedList<String> copyrightProblemFiles = new LinkedList<String>();

  // The path to the root of the Subversion workspace to check.
  private String workspace = null;

  // The string representation of the current year.
  private String yearString;

  // The overall SVN Client Manager. required with svnkit 1.2.x
  private static SVNClientManager ourClientManager =
          SVNClientManager.newInstance();
  // The property client used to look at file properties.
  private SVNWCClient propertyClient;



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
    if ((workspace == null) || (workspace.length() == 0))
    {
      workspacePath = getProject().getBaseDir();
    }
    else
    {
      workspacePath = new File(workspace);
    }


    // Get the year to use in the determination.
    GregorianCalendar calendar = new GregorianCalendar();
    int year = calendar.get(GregorianCalendar.YEAR);
    yearString = String.valueOf(year);


    // Process the base directory and all of its subdirectories.
    propertyClient = ourClientManager.getWCClient();

    try
    {
      long status = ourClientManager.getStatusClient().doStatus(workspacePath, SVNRevision.WORKING, 
              SVNDepth.INFINITY, false, false, false, false, this, null);
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.err.println("WARNING:  Encountered an error while examining " +
                         "Subversion status:  " + e);
      System.err.println("No further checks will be performed.");
      return;
    }

    boolean fail = false;

    if (! eolStyleProblemFiles.isEmpty())
    {
      System.err.println("WARNING:  Potential svn:eol-style updates needed " +
                         "for the following files:");
      for (String filename : eolStyleProblemFiles)
      {
        System.err.println("     " + filename);
      }

      String ignoreProp =
           getProject().getProperty(IGNORE_EOLSTYLE_ERRORS_PROPERTY);
      if ((ignoreProp == null) || (! ignoreProp.equalsIgnoreCase("true")))
      {
        fail = true;
        System.err.println("Fix svn:eol-style problems before proceeding, or " +
                           "use '-D" + IGNORE_EOLSTYLE_ERRORS_PROPERTY +
                           "=true' to ignore svn eol-style warnings.");
      }
    }

    if (! copyrightProblemFiles.isEmpty())
    {
      System.err.println("WARNING:  Potential copyright year updates needed " +
                         "for the following files:");
      for (String filename : copyrightProblemFiles)
      {
        System.err.println("     " + filename);
      }

      String ignoreProp =
           getProject().getProperty(IGNORE_COPYRIGHT_ERRORS_PROPERTY);
      if ((ignoreProp == null) || (! ignoreProp.equalsIgnoreCase("true")))
      {
        fail = true;
        System.err.println("Fix copyright date problems before proceeding, " +
                           "or use '-D" + IGNORE_COPYRIGHT_ERRORS_PROPERTY +
                           "=true' to ignore copyright warnings.");
      }
    }

    if (fail)
    {
      throw new BuildException();
    }
  }



  /**
   * Examines the provided status item to determine whether the associated file
   * is acceptable.
   *
   * @param  status  The SVN status information for the file of interest.
   */
  public void handleStatus(SVNStatus status)
  {
    File file = status.getFile();
    if ((! file.exists()) || (! file.isFile()))
    {
      // The file doesn't exist (which probably means it's been deleted) or
      // isn't a regular file, so we'll ignore it.
      return;
    }

    String fileName = file.getName();
    int lastPeriodPos = fileName.lastIndexOf('.');
    if (lastPeriodPos > 0)
    {
      String extension = fileName.substring(lastPeriodPos+1);
      if (! CHECKED_EXTENSIONS.contains(extension.toLowerCase()))
      {
        // The file doesn't have an extension that we care about, so skip it.
        return;
      }
    }
    else
    {
      // The file doesn't have an extension.  We'll still want to check it if
      // it's in a resource/bin directory.
      File parentDirectory = file.getParentFile();
      if ((parentDirectory == null) ||
          (! parentDirectory.getName().equals("bin")))
      {
        return;
      }

      parentDirectory = parentDirectory.getParentFile();
      if ((parentDirectory == null) ||
          (! parentDirectory.getName().equals("resource")))
      {
        return;
      }
    }


    String filePath = file.getAbsolutePath();
    if (filePath.startsWith(workspacePath.getPath() + "/"))
    {
      filePath = filePath.substring(workspacePath.getPath().length() + 1);
    }


    // Check to make sure that the file has the correct EOL style.
    try
    {
      SVNPropertyData propertyData =
           propertyClient.doGetProperty(file, "svn:eol-style",
                                        SVNRevision.BASE,
                                        SVNRevision.WORKING);
      if ((propertyData == null) ||
          (! propertyData.getValue().getString().equals("native")))
      {
        eolStyleProblemFiles.add(filePath);
      }
    }
    catch (SVNException se)
    {
      // This could happen if the file isn't under version control.  If so, then
      // we can't check the eol-style but we should at least be able to check
      // the copyright dates, so keep going.
    }


    // Check to see whether the file has a comment line containing a copyright
    // without the current year.
    BufferedReader reader = null;
    try
    {
      boolean copyrightFound   = false;
      boolean correctYearFound = false;
      reader = new BufferedReader(new FileReader(file));
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
        copyrightProblemFiles.add(filePath);
      }
    }
    catch (IOException ioe)
    {
      System.err.println("ERROR:  Could not read file " + filePath +
                         " to check copyright date.");
      System.err.println("No further copyright date checking will be " +
                         "performed.");
      throw new RuntimeException();
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
        lowerLine.startsWith("rem") ||
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

