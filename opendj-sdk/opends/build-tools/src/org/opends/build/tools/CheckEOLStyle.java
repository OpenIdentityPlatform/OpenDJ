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



import java.io.File;
import java.util.LinkedList;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCClient;



/**
 * This class provides an implementation of an Ant task that may be used to
 * verify that any file marked for addition to the repository has the
 * "svn:eol-style" property set with a value of "native".
 */
public class CheckEOLStyle
       extends Task
{
  /**
   * The name of the system property that may be used to prevent property
   * errors from failing the build.
   */
  public static final String IGNORE_ERRORS_PROPERTY =
       "org.opends.server.IgnoreEOLStyleErrors";



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
   * Specifies the path to the root of the Subversion workspace to check.
   *
   * @param  workspace  The path to the root of the Subversion workspace to
   *                    check.
   */
  public void setWorkspace(String workspace)
  {
    this.workspace = workspace;
  }



  /**
   * Performs the appropriate processing needed for this task.  In this case,
   * it uses SVNKit to identify all files marked for addition to the Subversion
   * repository and verify that the file has the svn:eol-style property set with
   * a value of native.
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


    // Get the current status for all files in the fileset.  For any files
    // marked for addition, check the svn:eol-style property.
    SVNStatusClient statusClient   = new SVNStatusClient(null, null);
    SVNWCClient     propertyClient = new SVNWCClient(null, null);

    LinkedList<String> problemFiles = new LinkedList<String>();
    for (FileSet fileSet : filesetList)
    {
      DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
      for (String relativePath : scanner.getIncludedFiles())
      {
        String filePath = scanner.getBasedir() + File.separator + relativePath;
        File targetFile = new File(filePath);

        try
        {
          SVNStatus svnStatus = statusClient.doStatus(targetFile, false);
          if (svnStatus == null)
          {
            System.err.println("WARNING:  Could not determine Subversion " +
                               "status for file " + filePath);
            System.err.println("No further EOL style checking will be " +
                               "performed.");
            return;
          }

          SVNStatusType statusType = svnStatus.getContentsStatus();
          if (statusType == SVNStatusType.STATUS_ADDED)
          {
            SVNPropertyData propertyData =
                 propertyClient.doGetProperty(targetFile, "svn:eol-style",
                                              SVNRevision.BASE,
                                              SVNRevision.WORKING, false);
            if ((propertyData == null) ||
                (! propertyData.getValue().equals("native")))
            {
              problemFiles.add(filePath);
            }
          }
        }
        catch (SVNException svnException)
        {
          System.err.println("WARNING:  Could not check eol-style property " +
                             "for file " + filePath + ":  " + svnException);
          System.err.println("No further EOL style checking will be " +
                             "performed.");
          return;
        }
      }
    }


    if (! problemFiles.isEmpty())
    {
      System.err.println("WARNING:  Property 'svn:eol-style' not set to " +
                         "'native' for the following files:");
      for (String filename : problemFiles)
      {
        System.err.println("     " + filename);
      }

      String ignoreStr = getProject().getProperty(IGNORE_ERRORS_PROPERTY);
      if ((ignoreStr == null) || (! ignoreStr.equalsIgnoreCase("true")))
      {
        throw new BuildException("Fix these reported problems before " +
                                 "proceeding, or use '-D" +
                                 IGNORE_ERRORS_PROPERTY + "=true' to " +
                                 "ignore svn:eol-style warnings.");
      }
    }
  }
}

