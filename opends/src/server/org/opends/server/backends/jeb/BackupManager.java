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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.DynamicConstants;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.RestoreConfig;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.JebMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * A backup manager for JE backends.
 */
public class BackupManager
{
  /**
   * The common prefix for archive files.
   */
  public static final String BACKUP_BASE_FILENAME = "backup-";

  /**
   * The name of the property that holds the name of the latest log file
   * at the time the backup was created.
   */
  public static final String PROPERTY_LAST_LOGFILE_NAME = "last_logfile_name";

  /**
   * The name of the property that holds the size of the latest log file
   * at the time the backup was created.
   */
  public static final String PROPERTY_LAST_LOGFILE_SIZE = "last_logfile_size";


  /**
   * The name of the entry in an incremental backup archive file
   * containing a list of log files that are unchanged since the
   * previous backup.
   */
  public static final String ZIPENTRY_UNCHANGED_LOGFILES = "unchanged.txt";

  /**
   * The name of a dummy entry in the backup archive file that will act
   * as a placeholder in case a backup is done on an empty backend.
   */
  public static final String ZIPENTRY_EMPTY_PLACEHOLDER = "empty.placeholder";


  /**
   * The backend ID.
   */
  private String backendID;


  /**
   * Construct a backup manager for a JE backend.
   * @param backendID The ID of the backend instance for which a backup
   * manager is required.
   */
  public BackupManager(String backendID)
  {
    this.backendID   = backendID;
  }

  /**
   * Create a backup of the JE backend.  The backup is stored in a single zip
   * file in the backup directory.  If the backup is incremental, then the
   * first entry in the zip is a text file containing a list of all the JE
   * log files that are unchanged since the previous backup.  The remaining
   * zip entries are the JE log files themselves, which, for an incremental,
   * only include those files that have changed.
   * @param configEntry The configuration entry of the backend instance for
   * which the backup is required.
   * @param  backupConfig  The configuration to use when performing the backup.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void createBackup(ConfigEntry configEntry, BackupConfig backupConfig)
       throws DirectoryException
  {
    // Parse our backend configuration.
    Config config = new Config();
    try
    {
      config.initializeConfig(configEntry, null);
    }
    catch (ConfigException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessage(),
                                   e.getMessageID());
    }

    // Get the properties to use for the backup.
    String          backupID        = backupConfig.getBackupID();
    BackupDirectory backupDir       = backupConfig.getBackupDirectory();
    boolean         incremental     = backupConfig.isIncremental();
    String          incrBaseID      = backupConfig.getIncrementalBaseID();
    boolean         compress        = backupConfig.compressData();
    boolean         encrypt         = backupConfig.encryptData();
    boolean         hash            = backupConfig.hashData();
    boolean         signHash        = backupConfig.signHash();


    // Create a hash map that will hold the extra backup property information
    // for this backup.
    HashMap<String,String> backupProperties = new HashMap<String,String>();


    // Get the crypto manager and use it to obtain references to the message
    // digest and/or MAC to use for hashing and/or signing.
    CryptoManager cryptoManager   = DirectoryServer.getCryptoManager();
    Mac           mac             = null;
    MessageDigest digest          = null;
    String        digestAlgorithm = null;
    String        macAlgorithm    = null;

    if (hash)
    {
      if (signHash)
      {
        macAlgorithm = cryptoManager.getPreferredMACAlgorithm();
        backupProperties.put(BACKUP_PROPERTY_MAC_ALGORITHM, macAlgorithm);

        try
        {
          mac = cryptoManager.getPreferredMACProvider();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_JEB_BACKUP_CANNOT_GET_MAC;
          String message = getMessage(msgID, macAlgorithm,
                                      stackTraceToSingleLineString(e));
          throw new DirectoryException(
               DirectoryServer.getServerErrorResultCode(), message, msgID, e);
        }
      }
      else
      {
        digestAlgorithm = cryptoManager.getPreferredMessageDigestAlgorithm();
        backupProperties.put(BACKUP_PROPERTY_DIGEST_ALGORITHM, digestAlgorithm);

        try
        {
          digest = cryptoManager.getPreferredMessageDigest();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_JEB_BACKUP_CANNOT_GET_DIGEST;
          String message = getMessage(msgID, digestAlgorithm,
                                      stackTraceToSingleLineString(e));
          throw new DirectoryException(
               DirectoryServer.getServerErrorResultCode(), message, msgID, e);
        }
      }
    }


    // Date the backup.
    Date backupDate = new Date();

    // If this is an incremental, determine the base backup for this backup.
    HashSet<String> dependencies = new HashSet<String>();
    BackupInfo baseBackup = null;
    File backendDir = config.getBackendDirectory();
/*
    FilenameFilter backupTagFilter = new FilenameFilter()
    {
      public boolean accept(File dir, String name)
      {
        return name.startsWith(BackupInfo.PROPERTY_BACKUP_ID);
      }
    };
*/
    if (incremental)
    {
      if (incrBaseID == null)
      {
        // The default is to use the latest backup as base.
        if (backupDir.getLatestBackup() != null)
        {
          incrBaseID = backupDir.getLatestBackup().getBackupID();
        }
      }

      // Get the set of possible base backups from the current database.
/*
      String[] files = backendDir.list(backupTagFilter);
      if (files == null || files.length == 0)
      {
        // Incremental not allowed until after a full.
        int msgID = MSGID_JEB_INCR_BACKUP_REQUIRES_FULL;
        String msg = getMessage(msgID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     msg, msgID);
      }
      HashSet<String> backups = new HashSet<String>();
      int prefixLen = BackupInfo.PROPERTY_BACKUP_ID.length()+1;
      for (String s : files)
      {
        String actualBaseID = s.substring(prefixLen);
        backups.add(actualBaseID);
      }

      // Check that it makes sense to do this incremental.
      if (incrBaseID == null || !backups.contains(incrBaseID))
      {
        int msgID = MSGID_JEB_INCR_BACKUP_FROM_WRONG_BASE;
        String msg = getMessage(msgID, backups.toString());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     msg, msgID);
      }
*/

      baseBackup = getBackupInfo(backupDir, incrBaseID);
    }

    // Get information about the latest log file from the base backup.
    String latestFileName = null;
    long latestFileSize = 0;
    if (baseBackup != null)
    {
      HashMap<String,String> properties = baseBackup.getBackupProperties();
      latestFileName = properties.get(PROPERTY_LAST_LOGFILE_NAME);
      latestFileSize = Long.parseLong(
           properties.get(PROPERTY_LAST_LOGFILE_SIZE));
    }

    // Create an output stream that will be used to write the archive file.  At
    // its core, it will be a file output stream to put a file on the disk.  If
    // we are to encrypt the data, then that file output stream will be wrapped
    // in a cipher output stream.  The resulting output stream will then be
    // wrapped by a zip output stream (which may or may not actually use
    // compression).
    String archiveFilename = null;
    OutputStream outputStream;
    File archiveFile;
    try
    {
      archiveFilename = BACKUP_BASE_FILENAME + backendID + "-" + backupID;
      if (!encrypt)
      {
        archiveFilename = archiveFilename + ".zip";
      }
      archiveFile = new File(backupDir.getPath(), archiveFilename);
      if (archiveFile.exists())
      {
        int i=1;
        while (true)
        {
          archiveFile = new File(backupDir.getPath(),
                                 archiveFilename  + "." + i);
          if (archiveFile.exists())
          {
            i++;
          }
          else
          {
            archiveFilename = archiveFilename + "." + i;
            break;
          }
        }
      }

      outputStream = new FileOutputStream(archiveFile, false);
      backupProperties.put(BACKUP_PROPERTY_ARCHIVE_FILENAME, archiveFilename);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_JEB_BACKUP_CANNOT_CREATE_ARCHIVE_FILE;
      String message = getMessage(msgID, String.valueOf(archiveFilename),
                                  backupDir.getPath(),
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }


    // If we should encrypt the data, then wrap the output stream in a cipher
    // output stream.
    if (encrypt)
    {
      String cipherAlgorithm = cryptoManager.getPreferredCipherAlgorithm();
      backupProperties.put(BACKUP_PROPERTY_CIPHER_ALGORITHM, cipherAlgorithm);

      Cipher cipher;
      try
      {
        cipher = cryptoManager.getPreferredCipher(Cipher.ENCRYPT_MODE);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_JEB_BACKUP_CANNOT_GET_CIPHER;
        String message = getMessage(msgID, cipherAlgorithm,
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }

      outputStream = new CipherOutputStream(outputStream, cipher);
    }


    // Wrap the file output stream in a zip output stream.
    ZipOutputStream zipStream = new ZipOutputStream(outputStream);

    int    msgID   = MSGID_JEB_BACKUP_ZIP_COMMENT;
    String message = getMessage(msgID, DynamicConstants.PRODUCT_NAME,
                                backupID, backendID);
    zipStream.setComment(message);

    if (compress)
    {
      zipStream.setLevel(Deflater.DEFAULT_COMPRESSION);
    }
    else
    {
      zipStream.setLevel(Deflater.NO_COMPRESSION);
    }

    // Record this backup in the database itself.
/*
    String backupTag = BackupInfo.PROPERTY_BACKUP_ID + "_" + backupID;
    File tagFile = new File(backendDir, backupTag);
    try
    {
      tagFile.createNewFile();
    }
    catch (IOException e)
    {
      assert debugException(CLASS_NAME, "createBackup", e);
      msgID = MSGID_JEB_CANNOT_CREATE_BACKUP_TAG_FILE;
      String msg = getMessage(msgID, backupTag, backendDir.getPath());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   msg, msgID);
    }
*/

    // Get a list of all the log files comprising the database.
    FilenameFilter filenameFilter = new FilenameFilter()
    {
      public boolean accept(File d, String name)
      {
        return name.endsWith(".jdb");
      }
    };

    File[] logFiles;
    try
    {
      logFiles = backendDir.listFiles(filenameFilter);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID   = MSGID_JEB_BACKUP_CANNOT_LIST_LOG_FILES;
      message = getMessage(msgID, backendDir.getAbsolutePath(),
                           stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message, msgID, e);
    }

    // Check to see if backend is empty. If so, insert placeholder entry into
    // archive
    if(logFiles.length <= 0)
    {
      try
      {
        ZipEntry emptyPlaceholder = new ZipEntry(ZIPENTRY_EMPTY_PLACEHOLDER);
        zipStream.putNextEntry(emptyPlaceholder);
      }
      catch (IOException e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
        msgID   = MSGID_JEB_BACKUP_CANNOT_WRITE_ARCHIVE_FILE;
        message = getMessage(msgID, ZIPENTRY_EMPTY_PLACEHOLDER,
            stackTraceToSingleLineString(e));
        throw new DirectoryException(
            DirectoryServer.getServerErrorResultCode(), message, msgID, e);
      }
    }

    // Sort the log files from oldest to youngest since this is the order
    // in which they must be copied.
    // This is easy since the files are created in alphabetical order by JE.
    Arrays.sort(logFiles);

    try
    {
      // Archive the backup tag files.
/*
      File[] tagFiles = backendDir.listFiles(backupTagFilter);
      if (tagFiles != null)
      {
        for (File f : tagFiles)
        {
          try
          {
            archiveFile(zipStream, mac, digest, f);
          }
          catch (IOException e)
          {
            assert debugException(CLASS_NAME, "createBackup", e);
            msgID   = MSGID_JEB_BACKUP_CANNOT_WRITE_ARCHIVE_FILE;
            message = getMessage(msgID, backupTag,
                                 stackTraceToSingleLineString(e));
            throw new DirectoryException(
                 DirectoryServer.getServerErrorResultCode(), message, msgID, e);
          }
        }
      }
*/

      // Process log files that are unchanged from the base backup.
      int indexCurrent = 0;
      if (latestFileName != null)
      {
        ArrayList<String> unchangedList = new ArrayList<String>();
        while (indexCurrent < logFiles.length)
        {
          File logFile = logFiles[indexCurrent];
          String logFileName = logFile.getName();

          // Stop when we get to the first log file that has been
          // written since the base backup.
          int compareResult = logFileName.compareTo(latestFileName);
          if (compareResult > 0 ||
               (compareResult == 0 && logFile.length() != latestFileSize))
          {
            break;
          }

          msgID = MSGID_JEB_BACKUP_FILE_UNCHANGED;
          message = getMessage(msgID, logFileName);
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                   message, msgID);

          unchangedList.add(logFileName);

          indexCurrent++;
        }

        // Write a file containing the list of unchanged log files.
        if (!unchangedList.isEmpty())
        {
          String zipEntryName = ZIPENTRY_UNCHANGED_LOGFILES;
          try
          {
            archiveList(zipStream, mac, digest, zipEntryName, unchangedList);
          }
          catch (IOException e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }
            msgID   = MSGID_JEB_BACKUP_CANNOT_WRITE_ARCHIVE_FILE;
            message = getMessage(msgID, zipEntryName,
                                 stackTraceToSingleLineString(e));
            throw new DirectoryException(
                 DirectoryServer.getServerErrorResultCode(), message, msgID, e);
          }

          // Set the dependency.
          dependencies.add(baseBackup.getBackupID());
        }
      }

      // Write the new log files to the zip file.
      do
      {
        boolean deletedFiles = false;

        while (indexCurrent < logFiles.length)
        {
          File logFile = logFiles[indexCurrent];

          try
          {
            latestFileSize = archiveFile(zipStream, mac, digest, logFile);
            latestFileName = logFile.getName();
          }
          catch (FileNotFoundException e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            // A log file has been deleted by the cleaner since we started.
            deletedFiles = true;
          }
          catch (IOException e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }
            msgID   = MSGID_JEB_BACKUP_CANNOT_WRITE_ARCHIVE_FILE;
            message = getMessage(msgID, logFile.getName(),
                                 stackTraceToSingleLineString(e));
            throw new DirectoryException(
                 DirectoryServer.getServerErrorResultCode(), message, msgID, e);
          }

          indexCurrent++;
        }

        if (deletedFiles)
        {
          // The cleaner is active and has deleted one or more of the log files
          // since we started.  The in-use data from those log files will have
          // been written to new log files, so we must include those new files.
          final String latest = logFiles[logFiles.length-1].getName();
          final long latestSize = latestFileSize;
          FilenameFilter filter = new FilenameFilter()
          {
            public boolean accept(File d, String name)
            {
              if (!name.endsWith(".jdb")) return false;
              int compareTo = name.compareTo(latest);
              if (compareTo > 0) return true;
              if (compareTo == 0 && d.length() > latestSize) return true;
              return false;
            }
          };

          try
          {
            logFiles = backendDir.listFiles(filter);
            indexCurrent = 0;
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            msgID   = MSGID_JEB_BACKUP_CANNOT_LIST_LOG_FILES;
            message = getMessage(msgID, backendDir.getAbsolutePath(),
                                 stackTraceToSingleLineString(e));
            throw new DirectoryException(
                 DirectoryServer.getServerErrorResultCode(), message, msgID, e);
          }

          if (logFiles == null)
          {
            break;
          }

          Arrays.sort(logFiles);

          msgID = MSGID_JEB_BACKUP_CLEANER_ACTIVITY;
          message = getMessage(msgID, logFiles.length);
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                   message, msgID);
        }
        else
        {
          // We are done.
          break;
        }
      }
      while (true);

    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      try
      {
        zipStream.close();
      } catch (Exception e2) {}
    }

    // We're done writing the file, so close the zip stream (which should also
    // close the underlying stream).
    try
    {
      zipStream.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID   = MSGID_JEB_BACKUP_CANNOT_CLOSE_ZIP_STREAM;
      message = getMessage(msgID, archiveFilename, backupDir.getPath(),
                           stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }


    // Get the digest or MAC bytes if appropriate.
    byte[] digestBytes = null;
    byte[] macBytes    = null;
    if (hash)
    {
      if (signHash)
      {
        macBytes = mac.doFinal();
      }
      else
      {
        digestBytes = digest.digest();
      }
    }


    // Create a descriptor for this backup.
    backupProperties.put(PROPERTY_LAST_LOGFILE_NAME, latestFileName);
    backupProperties.put(PROPERTY_LAST_LOGFILE_SIZE,
                         String.valueOf(latestFileSize));
    BackupInfo backupInfo = new BackupInfo(backupDir, backupID,
                                           backupDate, incremental, compress,
                                           encrypt, digestBytes, macBytes,
                                           dependencies, backupProperties);

    try
    {
      backupDir.addBackup(backupInfo);
      backupDir.writeBackupDirectoryDescriptor();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_JEB_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR;
      message = getMessage(msgID, backupDir.getDescriptorPath(),
                           stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
  }



  /**
   * Restore a JE backend from backup, or verify the backup.
   * @param configEntry The configuration entry of the backend instance to be
   * restored.
   * @param  restoreConfig The configuration to use when performing the restore.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void restoreBackup(ConfigEntry configEntry,
                            RestoreConfig restoreConfig)
       throws DirectoryException
  {
    // Get the properties to use for the restore.
    String          backupID        = restoreConfig.getBackupID();
    BackupDirectory backupDir       = restoreConfig.getBackupDirectory();
    boolean         verifyOnly      = restoreConfig.verifyOnly();

    BackupInfo backupInfo = getBackupInfo(backupDir, backupID);

    // Parse our backend configuration.
    Config config = new Config();
    try
    {
      config.initializeConfig(configEntry, null);
    }
    catch (ConfigException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessage(),
                                   e.getMessageID());
    }

    // Create a restore directory with a different name to the backend
    // directory.
    File currentDir = config.getBackendDirectory();
    File restoreDir = new File(currentDir.getPath() + "-restore-" + backupID);
    if (!verifyOnly)
    {
      File[] files = restoreDir.listFiles();
      if (files != null)
      {
        for (File f : files)
        {
          f.delete();
        }
      }
      restoreDir.mkdir();
    }

    // Get the set of restore files that are in dependencies.
    Set<String> includeFiles;
    try
    {
      includeFiles = getUnchanged(backupDir, backupInfo);
    }
    catch (IOException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      int msgID = MSGID_JEB_BACKUP_CANNOT_RESTORE;
      String message = getMessage(msgID, backupInfo.getBackupID(),
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }

    // Restore any dependencies.
    List<BackupInfo> dependents = getDependents(backupDir, backupInfo);
    for (BackupInfo dependent : dependents)
    {
      try
      {
        restoreArchive(restoreDir, restoreConfig, dependent, includeFiles);
      }
      catch (IOException e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
        int msgID = MSGID_JEB_BACKUP_CANNOT_RESTORE;
        String message = getMessage(msgID, dependent.getBackupID(),
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
    }

    // Restore the final archive file.
    try
    {
      restoreArchive(restoreDir, restoreConfig, backupInfo, null);
    }
    catch (IOException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      int msgID = MSGID_JEB_BACKUP_CANNOT_RESTORE;
      String message = getMessage(msgID, backupInfo.getBackupID(),
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }

    // Delete the current backend directory and rename the restore directory.
    if (!verifyOnly)
    {
      File[] files = currentDir.listFiles();
      if (files != null)
      {
        for (File f : files)
        {
          f.delete();
        }
      }
      currentDir.delete();
      if (!restoreDir.renameTo(currentDir))
      {
        int msgID = MSGID_JEB_CANNOT_RENAME_RESTORE_DIRECTORY;
        String msg = getMessage(msgID, restoreDir.getPath(),
                                currentDir.getPath());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     msg, msgID);
      }
    }
  }

  /**
   * Removes the specified backup if it is possible to do so.
   *
   * @param  backupDir  The backup directory structure with which the
   *                          specified backup is associated.
   * @param  backupID         The backup ID for the backup to be removed.
   *
   * @throws  DirectoryException  If it is not possible to remove the specified
   *                              backup for some reason (e.g., no such backup
   *                              exists or there are other backups that are
   *                              dependent upon it).
   */
  public void removeBackup(BackupDirectory backupDir,
                           String backupID)
         throws DirectoryException
  {
    BackupInfo backupInfo = getBackupInfo(backupDir, backupID);
    HashMap<String,String> backupProperties = backupInfo.getBackupProperties();

    String archiveFilename =
         backupProperties.get(BACKUP_PROPERTY_ARCHIVE_FILENAME);
    File archiveFile = new File(backupDir.getPath(), archiveFilename);

    try
    {
      backupDir.removeBackup(backupID);
    }
    catch (ConfigException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessage(),
                                   e.getMessageID());
    }

    try
    {
      backupDir.writeBackupDirectoryDescriptor();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_JEB_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR;
      String message = getMessage(msgID, backupDir.getDescriptorPath(),
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }

    // Remove the archive file.
    archiveFile.delete();

  }



  /**
   * Restore the contents of an archive file.  If the archive is being
   * restored as a dependency, then only files in the specified set
   * are restored, and the restored files are removed from the set.  Otherwise
   * all files from the archive are restored, and files that are to be found
   * in dependencies are added to the set.
   *
   * @param restoreDir     The directory in which files are to be restored.
   * @param restoreConfig  The restore configuration.
   * @param backupInfo     The backup containing the files to be restored.
   * @param includeFiles   The set of files to be restored.  If null, then
   *                       all files are restored.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws IOException   If an I/O exception occurs during the restore.
   */
  private void restoreArchive(File restoreDir,
                              RestoreConfig restoreConfig,
                              BackupInfo backupInfo,
                              Set<String> includeFiles)
       throws DirectoryException,IOException
  {
    BackupDirectory backupDir       = restoreConfig.getBackupDirectory();
    boolean verifyOnly              = restoreConfig.verifyOnly();

    String          backupID        = backupInfo.getBackupID();
    boolean         encrypt         = backupInfo.isEncrypted();
    byte[]          hash            = backupInfo.getUnsignedHash();
    byte[]          signHash        = backupInfo.getSignedHash();

    HashMap<String,String> backupProperties = backupInfo.getBackupProperties();

    String archiveFilename =
         backupProperties.get(BACKUP_PROPERTY_ARCHIVE_FILENAME);
    File archiveFile = new File(backupDir.getPath(), archiveFilename);

    InputStream inputStream = new FileInputStream(archiveFile);

    // Get the crypto manager and use it to obtain references to the message
    // digest and/or MAC to use for hashing and/or signing.
    CryptoManager cryptoManager   = DirectoryServer.getCryptoManager();
    Mac           mac             = null;
    MessageDigest digest          = null;
    String        digestAlgorithm = null;
    String        macAlgorithm    = null;

    if (signHash != null)
    {
      macAlgorithm = backupProperties.get(BACKUP_PROPERTY_MAC_ALGORITHM);

      try
      {
        mac = cryptoManager.getMACProvider(macAlgorithm);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_JEB_BACKUP_CANNOT_GET_MAC;
        String message = getMessage(msgID, macAlgorithm,
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
    }

    if (hash != null)
    {
      digestAlgorithm = backupProperties.get(BACKUP_PROPERTY_DIGEST_ALGORITHM);

      try
      {
        digest = cryptoManager.getMessageDigest(digestAlgorithm);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_JEB_BACKUP_CANNOT_GET_DIGEST;
        String message = getMessage(msgID, digestAlgorithm,
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
    }


    // If the data is encrypted, then wrap the input stream in a cipher
    // input stream.
    if (encrypt)
    {
      String cipherAlgorithm =
           backupProperties.get(BACKUP_PROPERTY_CIPHER_ALGORITHM);

      Cipher cipher;
      try
      {
        cipher = cryptoManager.getCipher(cipherAlgorithm, Cipher.DECRYPT_MODE);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_JEB_BACKUP_CANNOT_GET_CIPHER;
        String message = getMessage(msgID, cipherAlgorithm,
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }

      inputStream = new CipherInputStream(inputStream, cipher);
    }


    // Wrap the file input stream in a zip input stream.
    ZipInputStream zipStream = new ZipInputStream(inputStream);

    // Iterate through the entries in the zip file.
    ZipEntry zipEntry = zipStream.getNextEntry();
    while (zipEntry != null)
    {
      String name = zipEntry.getName();

      if (name.equals(ZIPENTRY_EMPTY_PLACEHOLDER))
      {
        // This entry is treated specially to indicate a backup of an empty
        // backend was attempted.

        zipEntry = zipStream.getNextEntry();
        continue;
      }

      if (name.equals(ZIPENTRY_UNCHANGED_LOGFILES))
      {
        // This entry is treated specially. It is never restored,
        // and its hash is computed on the strings, not the bytes.
        if (mac != null || digest != null)
        {
          // The file name is part of the hash.
          if (mac != null)
          {
            mac.update(getBytes(name));
          }

          if (digest != null)
          {
            digest.update(getBytes(name));
          }

          InputStreamReader reader = new InputStreamReader(zipStream);
          BufferedReader bufferedReader = new BufferedReader(reader);
          String line = bufferedReader.readLine();
          while (line != null)
          {
            if (mac != null)
            {
              mac.update(getBytes(line));
            }

            if (digest != null)
            {
              digest.update(getBytes(line));
            }

            line = bufferedReader.readLine();
          }
        }

        zipEntry = zipStream.getNextEntry();
        continue;
      }

      // See if we need to restore the file.
      File file = new File(restoreDir, name);
      OutputStream outputStream = null;
      if (includeFiles == null || includeFiles.contains(zipEntry.getName()))
      {
        if (!verifyOnly)
        {
          outputStream = new FileOutputStream(file);
        }
      }

      if (outputStream != null || mac != null || digest != null)
      {
        if (verifyOnly)
        {
          int msgID = MSGID_JEB_BACKUP_VERIFY_FILE;
          String message = getMessage(msgID, zipEntry.getName());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                   message, msgID);
        }

        // The file name is part of the hash.
        if (mac != null)
        {
          mac.update(getBytes(name));
        }

        if (digest != null)
        {
          digest.update(getBytes(name));
        }

        // Process the file.
        long totalBytesRead = 0;
        byte[] buffer = new byte[8192];
        int bytesRead = zipStream.read(buffer);
        while (bytesRead > 0)
        {
          totalBytesRead += bytesRead;

          if (mac != null)
          {
            mac.update(buffer, 0, bytesRead);
          }

          if (digest != null)
          {
            digest.update(buffer, 0, bytesRead);
          }

          if (outputStream != null)
          {
            outputStream.write(buffer, 0, bytesRead);
          }

          bytesRead = zipStream.read(buffer);
        }

        if (outputStream != null)
        {
          outputStream.close();

          int msgID = MSGID_JEB_BACKUP_RESTORED_FILE;
          String message = getMessage(msgID, zipEntry.getName(),
                                      totalBytesRead);
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
                   message, msgID);
        }
      }

      zipEntry = zipStream.getNextEntry();
    }

    zipStream.close();

    // Check the hash.
    if (digest != null)
    {
      if (!Arrays.equals(digest.digest(), hash))
      {
        int    msgID   = MSGID_JEB_BACKUP_UNSIGNED_HASH_ERROR;
        String message = getMessage(msgID, backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }
    }

    if (mac != null)
    {
      byte[] computedSignHash = mac.doFinal();

      if (!Arrays.equals(computedSignHash, signHash))
      {
        int    msgID   = MSGID_JEB_BACKUP_SIGNED_HASH_ERROR;
        String message = getMessage(msgID, backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }
    }
  }



  /**
   * Writes a file to an entry in the archive file.
   * @param zipStream The zip output stream to which the file is to be
   *                  written.
   * @param mac A message authentication code to be updated, if not null.
   * @param digest A message digest to be updated, if not null.
   * @param file The file to be written.
   * @return The number of bytes written from the file.
   * @throws FileNotFoundException If the file to be archived does not exist.
   * @throws IOException If an I/O error occurs while archiving the file.
   */
  private long archiveFile(ZipOutputStream zipStream,
                           Mac mac, MessageDigest digest, File file)
       throws IOException, FileNotFoundException
  {
    ZipEntry zipEntry = new ZipEntry(file.getName());

    // Open the file for reading.
    InputStream inputStream = new FileInputStream(file);

    // Start the zip entry.
    zipStream.putNextEntry(zipEntry);

    // Put the name in the hash.
    if (mac != null)
    {
      mac.update(getBytes(file.getName()));
    }

    if (digest != null)
    {
      digest.update(getBytes(file.getName()));
    }

    // Write the file.
    long totalBytesRead = 0;
    byte[] buffer = new byte[8192];
    int bytesRead = inputStream.read(buffer);
    while (bytesRead > 0)
    {
      if (mac != null)
      {
        mac.update(buffer, 0, bytesRead);
      }

      if (digest != null)
      {
        digest.update(buffer, 0, bytesRead);
      }

      zipStream.write(buffer, 0, bytesRead);
      totalBytesRead += bytesRead;
      bytesRead = inputStream.read(buffer);
    }
    inputStream.close();

    // Finish the zip entry.
    zipStream.closeEntry();

    int msgID = MSGID_JEB_BACKUP_ARCHIVED_FILE;
    String message = getMessage(msgID, zipEntry.getName());
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE,
             message, msgID);

    return totalBytesRead;
  }

  /**
   * Write a list of strings to an entry in the archive file.
   * @param zipStream The zip output stream to which the entry is to be
   *                  written.
   * @param mac An optional MAC to be updated.
   * @param digest An optional message digest to be updated.
   * @param fileName The name of the zip entry to be written.
   * @param list A list of strings to be written.  The strings must not
   *             contain newlines.
   * @throws IOException If an I/O error occurs while writing the archive entry.
   */
  private void archiveList(ZipOutputStream zipStream,
                           Mac mac, MessageDigest digest, String fileName,
                           List<String> list)
       throws IOException
  {
    ZipEntry zipEntry = new ZipEntry(fileName);

    // Start the zip entry.
    zipStream.putNextEntry(zipEntry);

    // Put the name in the hash.
    if (mac != null)
    {
      mac.update(getBytes(fileName));
    }

    if (digest != null)
    {
      digest.update(getBytes(fileName));
    }

    Writer writer = new OutputStreamWriter(zipStream);
    for (String s : list)
    {
      if (mac != null)
      {
        mac.update(getBytes(s));
      }

      if (digest != null)
      {
        digest.update(getBytes(s));
      }

      writer.write(s);
      writer.write(EOL);
    }
    writer.flush();

    // Finish the zip entry.
    zipStream.closeEntry();
  }

  /**
   * Obtains the set of files in a backup that are unchanged from its
   * dependent backup or backups.  This list is stored as the first entry
   * in the archive file.
   * @param backupDir The backup directory.
   * @param backupInfo The backup info.
   * @return The set of files that were unchanged.
   * @throws DirectoryException If an error occurs while trying to get the
   * appropriate cipher algorithm for an encrypted backup.
   * @throws IOException If an I/O error occurs while reading the backup
   * archive file.
   */
  private Set<String> getUnchanged(BackupDirectory backupDir,
                                   BackupInfo backupInfo)
       throws DirectoryException, IOException
  {
    HashSet<String> hashSet = new HashSet<String>();

    boolean         encrypt         = backupInfo.isEncrypted();

    HashMap<String,String> backupProperties = backupInfo.getBackupProperties();

    String archiveFilename =
         backupProperties.get(BACKUP_PROPERTY_ARCHIVE_FILENAME);
    File archiveFile = new File(backupDir.getPath(), archiveFilename);

    InputStream inputStream = new FileInputStream(archiveFile);

    // Get the crypto manager and use it to obtain references to the message
    // digest and/or MAC to use for hashing and/or signing.
    CryptoManager cryptoManager   = DirectoryServer.getCryptoManager();

    // If the data is encrypted, then wrap the input stream in a cipher
    // input stream.
    if (encrypt)
    {
      String cipherAlgorithm =
           backupProperties.get(BACKUP_PROPERTY_CIPHER_ALGORITHM);

      Cipher cipher;
      try
      {
        cipher = cryptoManager.getCipher(cipherAlgorithm, Cipher.DECRYPT_MODE);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_JEB_BACKUP_CANNOT_GET_CIPHER;
        String message = getMessage(msgID, cipherAlgorithm,
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }

      inputStream = new CipherInputStream(inputStream, cipher);
    }


    // Wrap the file input stream in a zip input stream.
    ZipInputStream zipStream = new ZipInputStream(inputStream);

    // Iterate through the entries in the zip file.
    ZipEntry zipEntry = zipStream.getNextEntry();
    while (zipEntry != null)
    {
      // We are looking for the entry containing the list of unchanged files.
      if (zipEntry.getName().equals(ZIPENTRY_UNCHANGED_LOGFILES))
      {
        InputStreamReader reader = new InputStreamReader(zipStream);
        BufferedReader bufferedReader = new BufferedReader(reader);
        String line = bufferedReader.readLine();
        while (line != null)
        {
          hashSet.add(line);
          line = bufferedReader.readLine();
        }
        break;
      }

      zipEntry = zipStream.getNextEntry();
    }

    zipStream.close();
    return hashSet;
  }

  /**
   * Obtains a list of the dependencies of a given backup in order from
   * the oldest (the full backup), to the most recent.
   * @param backupDir The backup directory.
   * @param backupInfo The backup for which dependencies are required.
   * @return A list of dependent backups.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  private ArrayList<BackupInfo> getDependents(BackupDirectory backupDir,
                                              BackupInfo backupInfo)
       throws DirectoryException
  {
    ArrayList<BackupInfo> dependents = new ArrayList<BackupInfo>();
    while (backupInfo != null && !backupInfo.getDependencies().isEmpty())
    {
      String backupID = backupInfo.getDependencies().iterator().next();
      backupInfo = getBackupInfo(backupDir, backupID);
      if (backupInfo != null)
      {
        dependents.add(backupInfo);
      }
    }
    Collections.reverse(dependents);
    return dependents;
  }

  /**
   * Get the information for a given backup ID from the backup directory.
   * @param backupDir The backup directory.
   * @param backupID The backup ID.
   * @return The backup information, never null.
   * @throws DirectoryException If the backup information cannot be found.
   */
  private BackupInfo getBackupInfo(BackupDirectory backupDir,
                                   String backupID) throws DirectoryException
  {
    BackupInfo backupInfo = backupDir.getBackupInfo(backupID);
    if (backupInfo == null)
    {
      int    msgID   = MSGID_JEB_BACKUP_MISSING_BACKUPID;
      String message = getMessage(msgID, backupDir.getPath(), backupID);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }
    return backupInfo;
  }
}
