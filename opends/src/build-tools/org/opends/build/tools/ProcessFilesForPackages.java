/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.build.tools;

import java.io.File;
import java.io.FileFilter;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * A utility class for the packaging process. It is used by the build.xml to
 * perform the package construction.
 */
public class ProcessFilesForPackages extends Task
{
  /** The source directory name */
  private String sourceDirName;

  /** Files contained in the package */
  private final List<File> files = new LinkedList<File>();

  /** Files which should be excluded from the package */
  private final List<File> excludedFiles = new LinkedList<File>();

  /** Package documentation files */
  private final List<File> docFiles = new LinkedList<File>();

  /** The end-of-line character for this platform */
  private static String EOL = System.getProperty("line.separator");

  /**
   * Returns the source directory name.
   *
   * @return The source directory name.
   */
  public String getSourceDirName()
  {
    return sourceDirName;
  }

  /**
   * Sets the source directory name.
   *
   * @param sourceDirName
   *          The source directory name.
   */
  public void setSourceDirName(String sourceDirName)
  {
    this.sourceDirName = sourceDirName;
  }

  @Override()
  public void execute()
  {
    try
    {
      // Process the filtering of the files contained in the given directory.
      filterFiles(new File(getSourceDirName()));
      files.removeAll(docFiles);
      // Sorts the list.
      Collections.sort(files);
      Collections.sort(docFiles);
      // Returns the properties which can be used fill data.
      getProject().setNewProperty("listRPMFiles", formatAsFileList(files));
      getProject().setNewProperty("listRPMDocFiles",
          formatAsDocList(docFiles));
      getProject().setNewProperty("excludedRPMFiles",
          formatAsExcludedList(excludedFiles));
      getProject().setNewProperty("installRpmFiles", getInstallationFiles());
    }
    catch (Exception e)
    {
      throw new BuildException(
          "An error occurs during filtering package files", e);
    }
  }

  /**
   * Returns the installation files for the RPM package.
   *
   * @return A string containing the installation files for the RPM package.
   */
  private String getInstallationFiles()
  {
    return new StringBuilder("cp -rf \"").append(sourceDirName).append("\"/* .")
        .append(EOL).toString();
  }
  /**
   * Formats the file list to be supported by RPM.
   *
   * @param fileList
   *          The list of files to be processed.
   * @return A string containing the list of files which should be excluded in
   *         the RPM source.
   */
  private String formatAsExcludedList(final List<File> fileList)
  {
    final StringBuilder sb = new StringBuilder();
    for (final File f : fileList)
    {
      sb.append(relativeToSourceDirName(f)).append(EOL);
    }
    return sb.toString();
  }

  /**
   * Formats the file list to be supported by RPM.
   *
   * @param fileList
   *          The list of files to be processed.
   * @return A string containing the list of files which can be included in the
   *         RPM source.
   */
  private String formatAsFileList(final List<File> fileList)
  {
    final StringBuilder sb = new StringBuilder();
    sb.append("%dir %{_prefix}").append(EOL);
    for (final File f : fileList)
    {
      if (f.isDirectory())
      {
        sb.append("%dir %{_prefix}");
      }
      else
      {
        sb.append("%{_prefix}");
      }
      sb.append(relativeToSourceDirName(f)).append(EOL);
    }
    return sb.toString();
  }

  /**
   * Formats the document list to be supported by RPM.
   *
   * @param fileList
   *          The list of files to be processed.
   * @return A string containing the formatted list doc.
   */
  private String formatAsDocList(final List<File> fileList)
  {
    final StringBuilder sb = new StringBuilder();
    for (final File f : fileList)
    {
      // FIXME The folder needs to be copied as well.
      if (!f.isDirectory())
      {
        sb.append(
            relativeToSourceDirName(f)).append(EOL);
      }
    }
    return sb.toString();
  }

  /**
   * Sorts all the files contained in selected directory and fills the
   * appropriate lists.
   *
   * @param dir
   *          The directory where to analyze the files.
   * @throws Exception
   *           If an exception occurs during the process.
   */
  private void filterFiles(final File dir) throws Exception
  {
    final ExcludeFileFilter exFilter = new ExcludeFileFilter();
    final DocFileFilter docFilter = new DocFileFilter();

    // The spaces in path can generate errors. (see OPENDJ-1063)
    final File fdir =
        new File(new URI("file:///"
            + dir.getAbsolutePath().replaceAll(" ", "%20")));

    for (final File f : fdir.listFiles())
    {
      if (f.isDirectory())
      {
        filterFiles(f);
      }
      // Completes the excluded files list.
      if (exFilter.accept(f))
      {
        excludedFiles.add(f);
      }
      else
      {
        files.add(f);
      }
      // Completes the document file list.
      if (docFilter.accept(f))
      {
        docFiles.add(f);
      }
    }
  }

  /**
   * Returns the relative path to given source directory name.
   *
   * @param f
   *          The file we want the relative path.
   * @return A string representing the relative path to the given source
   *         directory name.
   */
  private String relativeToSourceDirName(File f)
  {
    return f.getAbsolutePath().replace(getSourceDirName(), "");
  }

  /**
   * A file filter which excludes all files that belong to other OS.
   */
  static final class ExcludeFileFilter implements FileFilter
  {
    /** {@inheritDoc} */
    public boolean accept(File file)
    {
      final String fileName = file.getName().toLowerCase();
      return (
          fileName.equals("bat")
          || fileName.endsWith(".app")
          || fileName.endsWith(".bat")
          || fileName.endsWith(".exe")
          || file.getAbsolutePath().contains(".app"));
    }
  }

  /**
   * A file filter which accepts only documentation files.
   */
  static final class DocFileFilter implements FileFilter
  {
    /** {@inheritDoc} */
    public boolean accept(File file)
    {
      final String fileName = file.getName().toLowerCase();
      if (file.isDirectory())
      {
        return fileName.equals("legal-notices");
      }
      return (
          !file.getAbsolutePath().contains("/template/")
          && !file.getAbsolutePath().contains("/legals/")
          && (fileName.endsWith(".doc")
          || fileName.endsWith(".txt")
          || fileName.contains("example-plugin")
          || fileName.equals("readme")
          || fileName.equals("opends.license")));
    }
  }
}
