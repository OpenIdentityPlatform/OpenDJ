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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.opends.server.tools.upgrade;



import static org.opends.messages.ToolMessages.*;

import org.opends.messages.Message;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilePermission;
import org.opends.server.util.StaticUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;



/**
 * Utility class for use by applications containing methods for managing file
 * system files. This class handles application notifications for interesting
 * events.
 */
public class FileManager
{

  /**
   * Describes the approach taken to deleting a file or directory.
   */
  public static enum DeletionPolicy
  {

    /**
     * Delete the file or directory immediately.
     */
    DELETE_IMMEDIATELY,

    /**
     * Mark the file or directory for deletion after the JVM has exited.
     */
    DELETE_ON_EXIT,

    /**
     * First try to delete the file immediately. If the deletion was
     * unsuccessful mark the file for deleteion when the JVM has existed.
     */
    DELETE_ON_EXIT_IF_UNSUCCESSFUL

  }



  /**
   * Upgrade's Log.
   */
  static private final Logger LOG = Logger
      .getLogger(UpgradeCli.class.getName());



  private FileManager()
  {
    // do nothing;
  }



  /**
   * Deletes everything below the specified file.
   *
   * @param file
   *          the path to be deleted.
   * @throws IOException
   *           if something goes wrong.
   */
  public static void deleteRecursively(File file) throws IOException
  {
    deleteRecursively(file, null, DeletionPolicy.DELETE_IMMEDIATELY);
  }



  private static void deleteRecursively(File file, FileFilter filter,
      DeletionPolicy deletePolicy) throws IOException
  {
    operateRecursively(new DeleteOperation(file, deletePolicy), filter);
  }



  /**
   * Copies everything below the specified file.
   *
   * @param objectFile
   *          the file to be copied.
   * @param destDir
   *          the directory to copy the file to
   * @param overwrite
   *          overwrite destination files.
   * @return File representing the destination
   * @throws IOException
   *           if something goes wrong.
   */
  public static File copy(File objectFile, File destDir, boolean overwrite)
      throws IOException
  {
    CopyOperation co = new CopyOperation(objectFile, destDir, overwrite);
    co.apply();
    return co.getDestination();
  }



  private static void operateRecursively(FileOperation op, FileFilter filter)
      throws IOException
  {
    File file = op.getObjectFile();
    if (file.exists())
    {
      if (file.isFile())
      {
        if (filter != null)
        {
          if (filter.accept(file))
          {
            op.apply();
          }
        }
        else
        {
          op.apply();
        }
      }
      else
      {
        File[] children = file.listFiles();
        if (children != null)
        {
          for (File aChildren : children)
          {
            FileOperation newOp = op.copyForChild(aChildren);
            operateRecursively(newOp, filter);
          }
        }
        if (filter != null)
        {
          if (filter.accept(file))
          {
            op.apply();
          }
        }
        else
        {
          op.apply();
        }
      }
    }
    else
    {
      LOG.log(Level.INFO, new StringBuilder("File '").append(file.toString())
          .append("' does not exist").toString());
    }
  }



  /**
   * Renames the source file to the target file. If the target file exists it is
   * first deleted. The rename and delete operation return values are checked
   * for success and if unsuccessful, this method throws an exception.
   *
   * @param fileToRename
   *          The file to rename.
   * @param target
   *          The file to which <code>fileToRename</code> will be moved.
   * @throws IOException
   *           If a problem occurs while attempting to rename the file. On the
   *           Windows platform, this typically indicates that the file is in
   *           use by this or another application.
   */
  public static void rename(File fileToRename, File target) throws IOException
  {
    if (fileToRename != null && target != null)
    {
      synchronized (target)
      {
        if (target.exists())
        {
          if (!target.delete())
          {
            throw new IOException(INFO_ERROR_DELETING_FILE.get(
                UpgradeUtils.getPath(target)).toString());
          }
        }
      }
      if (!fileToRename.renameTo(target))
      {
        throw new IOException(INFO_ERROR_RENAMING_FILE.get(
            UpgradeUtils.getPath(fileToRename), UpgradeUtils.getPath(target))
            .toString());
      }
    }
  }



  /**
   * A file operation.
   */
  private static abstract class FileOperation
  {

    private File objectFile = null;



    /**
     * Creates a new file operation.
     *
     * @param objectFile
     *          to be operated on
     */
    public FileOperation(File objectFile)
    {
      this.objectFile = objectFile;
    }



    /**
     * Gets the file to be operated on.
     *
     * @return File to be operated on
     */
    protected File getObjectFile()
    {
      return objectFile;
    }



    /**
     * Make a copy of this class for the child file.
     *
     * @param child
     *          to act as the new file object
     * @return FileOperation as the same type as this class
     */
    abstract public FileOperation copyForChild(File child);



    /**
     * Execute this operation.
     *
     * @throws IOException
     *           if there is a problem.
     */
    abstract public void apply() throws IOException;

  }



  /**
   * A copy operation.
   */
  private static class CopyOperation extends FileOperation
  {

    private File destination;

    private boolean overwrite;



    /**
     * Create a new copy operation.
     *
     * @param objectFile
     *          to copy
     * @param destDir
     *          to copy to
     * @param overwrite
     *          if true copy should overwrite any existing file
     */
    public CopyOperation(File objectFile, File destDir, boolean overwrite)
    {
      super(objectFile);
      this.destination = new File(destDir, objectFile.getName());
      this.overwrite = overwrite;
    }



    /**
     * {@inheritDoc}
     */
    public FileOperation copyForChild(File child)
    {
      return new CopyOperation(child, destination, overwrite);
    }



    /**
     * Returns the destination file that is the result of copying
     * <code>objectFile</code> to <code>destDir</code>.
     *
     * @return The destination file.
     */
    public File getDestination()
    {
      return this.destination;
    }



    /**
     * {@inheritDoc}
     */
    public void apply() throws IOException
    {
      File objectFile = getObjectFile();
      if (objectFile.isDirectory())
      {
        if (!destination.exists())
        {
          destination.mkdirs();
        }
      }
      else
      {

        // If overwriting and the destination exists then kill it
        if (destination.exists() && overwrite)
        {
          deleteRecursively(destination);
        }

        if (!destination.exists())
        {
          if (insureParentsExist(destination))
          {
            final Message message = Message.raw("Copying file '%s' to '%s'",
                objectFile.getAbsolutePath(), destination.getAbsolutePath());
            LOG.log(Level.INFO, message.toString());
            FileInputStream fis = null;
            FileOutputStream fos = null;
            try
            {
              fis = new FileInputStream(objectFile);
              fos = new FileOutputStream(destination);
              byte[] buf = new byte[1024];
              int i;
              while ((i = fis.read(buf)) != -1)
              {
                fos.write(buf, 0, i);
              }
              if (destination.exists())
              {
                // TODO: set the file's permissions. This is made easier in
                // Java 1.6 but until then use the TestUtilities methods
                if (UpgradeUtils.isUnix())
                {
                  FilePermission permissions =
                      getFileSystemPermissions(objectFile);
                  FilePermission.setPermissions(destination, permissions);
                }
              }

            }
            catch (Exception e)
            {
              final Message errMsg = INFO_ERROR_COPYING_FILE.get(
                  objectFile.getAbsolutePath(), destination.getAbsolutePath());
              LOG.log(Level.SEVERE, message.toString());
              throw new IOException(errMsg.toString());
            }
            finally
            {
              StaticUtils.close(fis, fos);
            }
          }
          else
          {
            final Message errMsg = INFO_ERROR_COPYING_FILE.get(
                objectFile.getAbsolutePath(), destination.getAbsolutePath());
            LOG.log(Level.SEVERE, errMsg.toString());
            throw new IOException(errMsg.toString());
          }
        }
        else
        {
          final Message message = Message.raw(
              "Ignoring file '%s' since '%s' already exists",
              objectFile.getAbsolutePath(), destination.getAbsolutePath());

          LOG.log(Level.INFO, message.toString());
        }
      }
    }

  }



  /**
   * Returns the file permission on the selected file.
   *
   * @param file
   *          The file of which we want to extract the permissions.
   * @return A file permission about the concerned file.
   * @throws DirectoryException
   *           If the provided string is not a valid three-digit UNIX mode.
   */
  private static FilePermission getFileSystemPermissions(final File file)
      throws DirectoryException
  {
    final String name = file.getName();
    if (file.getParent().endsWith(
        File.separator + Installation.WINDOWS_BINARIES_PATH_RELATIVE)
        || file.getParent().endsWith(
            File.separator + Installation.UNIX_BINARIES_PATH_RELATIVE))
    {
      if (name.endsWith(".bat"))
      {
        return FilePermission.decodeUNIXMode("644");
      }
      return FilePermission.decodeUNIXMode("755");

    }
    else if (name.endsWith(".sh"))
    {
      return FilePermission.decodeUNIXMode("755");
    }
    else if (name.endsWith(Installation.UNIX_SETUP_FILE_NAME)
        || name.endsWith(Installation.UNIX_UNINSTALL_FILE_NAME)
        || name.endsWith(Installation.UNIX_UPGRADE_FILE_NAME))
    {
      return FilePermission.decodeUNIXMode("755");
    }
    else if (name.endsWith(Installation.MAC_JAVA_APP_STUB_NAME))
    {
      return FilePermission.decodeUNIXMode("755");
    }
    return FilePermission.decodeUNIXMode("644");
  }



  /**
   * Creates the parent directory if it does not already exist.
   *
   * @param f
   *          File for which parentage will be insured
   * @return boolean indicating whether or not the input <code>f</code> has a
   *         parent after this method is invoked.
   */
  private static boolean insureParentsExist(File f)
  {
    File parent = f.getParentFile();
    boolean b = parent.exists();
    if (!b)
    {
      b = parent.mkdirs();
    }
    return b;
  }



  /**
   * A delete operation.
   */
  private static class DeleteOperation extends FileOperation
  {

    private DeletionPolicy deletionPolicy;



    /**
     * Creates a delete operation.
     *
     * @param objectFile
     *          to delete
     * @param deletionPolicy
     *          describing how files will be deleted is to take place after this
     *          program exists. This is useful for cleaning up files that are
     *          currently in use.
     */
    public DeleteOperation(File objectFile, DeletionPolicy deletionPolicy)
    {
      super(objectFile);
      this.deletionPolicy = deletionPolicy;
    }



    /**
     * {@inheritDoc}
     */
    public FileOperation copyForChild(File child)
    {
      return new DeleteOperation(child, deletionPolicy);
    }



    /**
     * {@inheritDoc}
     */
    public void apply() throws IOException
    {
      File file = getObjectFile();
      boolean isFile = file.isFile();

      LOG.log(Level.INFO, "deleting " + (isFile ? " file " : " directory ")
          + file.getAbsolutePath());

      boolean delete = false;
      /*
       * Sometimes the server keeps some locks on the files. TODO: remove this
       * code once stop-ds returns properly when server is stopped.
       */
      int nTries = 5;
      for (int i = 0; i < nTries && !delete; i++)
      {
        if (DeletionPolicy.DELETE_ON_EXIT.equals(deletionPolicy))
        {
          file.deleteOnExit();
          delete = true;
        }
        else
        {
          delete = file.delete();
          if (!delete
              && DeletionPolicy.DELETE_ON_EXIT_IF_UNSUCCESSFUL
                  .equals(deletionPolicy))
          {
            file.deleteOnExit();
            delete = true;
          }
        }
        if (!delete)
        {
          try
          {
            Thread.sleep(1000);
          }
          catch (Exception ex)
          {
            // do nothing;
          }
        }
      }

      if (!delete)
      {
        Message errMsg;
        if (isFile)
        {
          errMsg = INFO_ERROR_DELETING_FILE.get(file.getAbsolutePath());
        }
        else
        {
          errMsg = INFO_ERROR_DELETING_DIRECTORY.get(file.getAbsolutePath());
        }
        throw new IOException(errMsg.toString());
      }
    }
  }

}
