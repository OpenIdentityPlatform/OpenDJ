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
package org.opends.server.types;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import org.opends.server.config.ConfigException;

import static
    org.opends.server.loggers.debug.DebugLogger.debugCought;
import static
    org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure for holding information about a
 * filesystem directory that contains data for one or more backups
 * associated with a backend.  Only backups for a single backend may
 * be placed in any given directory.
 */
public class BackupDirectory
{



  /**
   * The name of the property that will be used to provide the DN of
   * the configuration entry for the backend associated with the
   * backups in this directory.
   */
  public static final String PROPERTY_BACKEND_CONFIG_DN =
       "backend_dn";



  // The DN of the configuration entry for the backend with which this
  // backup directory is associated.
  private DN configEntryDN;

  // The set of backups in the specified directory.  The iteration
  // order will be the order in which the backups were created.
  private LinkedHashMap<String,BackupInfo> backups;

  // The filesystem path to the backup directory.
  private String path;



  /**
   * Creates a new backup directory object with the provided
   * information.
   *
   * @param  path           The path to the directory containing the
   *                        backup file(s).
   * @param  configEntryDN  The DN of the configuration entry for the
   *                        backend with which this backup directory
   *                        is associated.
   */
  public BackupDirectory(String path, DN configEntryDN)
  {
    this.path          = path;
    this.configEntryDN = configEntryDN;

    backups = new LinkedHashMap<String,BackupInfo>();
  }



  /**
   * Creates a new backup directory object with the provided
   * information.
   *
   * @param  path           The path to the directory containing the
   *                        backup file(s).
   * @param  configEntryDN  The DN of the configuration entry for the
   *                        backend with which this backup directory
   *                        is associated.
   * @param  backups        Information about the set of backups
   *                        available within the specified directory.
   */
  public BackupDirectory(String path, DN configEntryDN,
                         LinkedHashMap<String,BackupInfo> backups)
  {
    this.path          = path;
    this.configEntryDN = configEntryDN;

    if (backups == null)
    {
      this.backups = new LinkedHashMap<String,BackupInfo>();
    }
    else
    {
      this.backups = backups;
    }
  }



  /**
   * Retrieves the path to the directory containing the backup
   * file(s).
   *
   * @return  The path to the directory containing the backup file(s).
   */
  public String getPath()
  {
    return path;
  }



  /**
   * Retrieves the DN of the configuration entry for the backend with
   * which this backup directory is associated.
   *
   * @return  The DN of the configuration entry for the backend with
   *          which this backup directory is associated.
   */
  public DN getConfigEntryDN()
  {
    return configEntryDN;
  }



  /**
   * Retrieves the set of backups in this backup directory, as a
   * mapping between the backup ID and the associated backup info.
   * The iteration order for the map will be the order in which the
   * backups were created.
   *
   * @return  The set of backups in this backup directory.
   */
  public LinkedHashMap<String,BackupInfo> getBackups()
  {
    return backups;
  }



  /**
   * Retrieves the backup info structure for the backup with the
   * specified ID.
   *
   * @param  backupID  The backup ID for the structure to retrieve.
   *
   * @return  The requested backup info structure, or
   *          <CODE>null</CODE> if no such structure exists.
   */
  public BackupInfo getBackupInfo(String backupID)
  {
    return backups.get(backupID);
  }



  /**
   * Retrieves the most recent backup for this backup directory,
   * according to the backup date.
   *
   * @return  The most recent backup for this backup directory,
   *          according to the backup date, or <CODE>null</CODE> if
   *          there are no backups in the backup directory.
   */
  public BackupInfo getLatestBackup()
  {
    BackupInfo latestBackup = null;
    for (BackupInfo backup : backups.values())
    {
      if (latestBackup == null)
      {
        latestBackup = backup;
      }
      else
      {
        if (backup.getBackupDate().getTime() >
            latestBackup.getBackupDate().getTime())
        {
          latestBackup = backup;
        }
      }
    }

    return latestBackup;
  }



  /**
   * Adds information about the provided backup to this backup
   * directory.
   *
   * @param  backupInfo  The backup info structure for the backup to
   *                     be added.
   *
   * @throws  ConfigException  If another backup already exists with
   *                           the same backup ID.
   */
  public void addBackup(BackupInfo backupInfo)
         throws ConfigException
  {
    String backupID = backupInfo.getBackupID();
    if (backups.containsKey(backupID))
    {
      int    msgID   = MSGID_BACKUPDIRECTORY_ADD_DUPLICATE_ID;
      String message = getMessage(msgID, backupID, path);
      throw new ConfigException(msgID, message);
    }

    backups.put(backupID, backupInfo);
  }



  /**
   * Removes the backup with the specified backup ID from this backup
   * directory.
   *
   * @param  backupID  The backup ID for the backup to remove from
   *                   this backup directory.
   *
   * @throws  ConfigException  If it is not possible to remove the
   *                           requested backup for some reason (e.g.,
   *                           no such backup exists, or another
   *                           backup is dependent on it).
   */
  public void removeBackup(String backupID)
         throws ConfigException
  {
    if (! backups.containsKey(backupID))
    {
      int    msgID   = MSGID_BACKUPDIRECTORY_NO_SUCH_BACKUP;
      String message = getMessage(msgID, backupID, path);
      throw new ConfigException(msgID, message);
    }

    for (BackupInfo backup : backups.values())
    {
      if (backup.dependsOn(backupID))
      {
        int    msgID   = MSGID_BACKUPDIRECTORY_UNRESOLVED_DEPENDENCY;
        String message = getMessage(msgID, backupID, path,
                                    backup.getBackupID());
        throw new ConfigException(msgID, message);
      }
    }

    backups.remove(backupID);
  }



  /**
   * Retrieves a path to the backup descriptor file that should be
   * used for this backup directory.
   *
   * @return  A path to the backup descriptor file that should be used
   *          for this backup directory.
   */
  public String getDescriptorPath()
  {
    return path + File.separator + BACKUP_DIRECTORY_DESCRIPTOR_FILE;
  }



  /**
   * Writes the descriptor with the information contained in this
   * structure to disk in the appropriate directory.
   *
   * @throws  IOException  If a problem occurs while writing to disk.
   */
  public void writeBackupDirectoryDescriptor()
         throws IOException
  {
    // First make sure that the target directory exists.  If it
    // doesn't, then try to create it.
    File dir = new File(path);
    if (! dir.exists())
    {
      try
      {
        dir.mkdirs();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_BACKUPDIRECTORY_CANNOT_CREATE_DIRECTORY;
        String message = getMessage(msgID, path,
                                    stackTraceToSingleLineString(e));
        throw new IOException(message);
      }
    }
    else if (! dir.isDirectory())
    {
      int    msgID   = MSGID_BACKUPDIRECTORY_NOT_DIRECTORY;
      String message = getMessage(msgID, path);
      throw new IOException(message);
    }


    // We'll write to a temporary file so that we won't destroy the
    // live copy if a problem occurs.
    String newDescriptorFilePath = path + File.separator +
                                   BACKUP_DIRECTORY_DESCRIPTOR_FILE +
                                   ".new";
    File newDescriptorFile = new File(newDescriptorFilePath);
    BufferedWriter writer =
         new BufferedWriter(new FileWriter(newDescriptorFile, false));


    // The first line in the file will only contain the DN of the
    // configuration entry for the associated backend.
    writer.write(PROPERTY_BACKEND_CONFIG_DN + "=" +
                 configEntryDN.toString());
    writer.newLine();
    writer.newLine();


    // Iterate through all of the backups and add them to the file.
    for (BackupInfo backup : backups.values())
    {
      LinkedList<String> backupLines = backup.encode();

      for (String line : backupLines)
      {
        writer.write(line);
        writer.newLine();
      }

      writer.newLine();
    }


    // At this point, the file should be complete so flush and close
    // it.
    writer.flush();
    writer.close();


    // If previous backup descriptor file exists, then rename it.
    String descriptorFilePath = path + File.separator +
                                BACKUP_DIRECTORY_DESCRIPTOR_FILE;
    File descriptorFile = new File(descriptorFilePath);
    if (descriptorFile.exists())
    {
      String savedDescriptorFilePath = descriptorFilePath + ".save";
      File savedDescriptorFile = new File(savedDescriptorFilePath);
      if (savedDescriptorFile.exists())
      {
        try
        {
          savedDescriptorFile.delete();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
          }

          int msgID =
              MSGID_BACKUPDIRECTORY_CANNOT_DELETE_SAVED_DESCRIPTOR;
          String message = getMessage(msgID, savedDescriptorFilePath,
                                      stackTraceToSingleLineString(e),
                                      newDescriptorFilePath,
                                      descriptorFilePath);
          throw new IOException(message);
        }
      }

      try
      {
        descriptorFile.renameTo(savedDescriptorFile);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int msgID =
            MSGID_BACKUPDIRECTORY_CANNOT_RENAME_CURRENT_DESCRIPTOR;
        String message = getMessage(msgID, descriptorFilePath,
                                    savedDescriptorFilePath,
                                    stackTraceToSingleLineString(e),
                                    newDescriptorFilePath);
        throw new IOException(message);
      }
    }


    // Rename the new descriptor file to match the previous one.
    try
    {
      newDescriptorFile.renameTo(descriptorFile);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_BACKUPDIRECTORY_CANNOT_RENAME_NEW_DESCRIPTOR;
      String message = getMessage(msgID, newDescriptorFilePath,
                                  descriptorFilePath,
                                  stackTraceToSingleLineString(e));
      throw new IOException(message);
    }
  }



  /**
   * Reads the backup descriptor file in the specified path and uses
   * the information it contains to create a new backup directory
   * structure.
   *
   * @param  path  The path to the directory containing the backup
   *               descriptor file to read.
   *
   * @return  The backup directory structure created from the contents
   *          of the descriptor file.
   *
   * @throws  IOException  If a problem occurs while trying to read
   *                       the contents of the descriptor file.
   *
   * @throws  ConfigException  If the contents of the descriptor file
   *                           cannot be parsed to create a backup
   *                           directory structure.
   */
  public static BackupDirectory
                     readBackupDirectoryDescriptor(String path)
         throws IOException, ConfigException
  {
    // Make sure that the descriptor file exists.
    String descriptorFilePath = path + File.separator +
                                BACKUP_DIRECTORY_DESCRIPTOR_FILE;
    File descriptorFile = new File(descriptorFilePath);
    if (! descriptorFile.exists())
    {
      int    msgID   = MSGID_BACKUPDIRECTORY_NO_DESCRIPTOR_FILE;
      String message = getMessage(msgID, descriptorFilePath);
      throw new ConfigException(msgID, message);
    }


    // Open the file for reading.  The first line should be the DN of
    // the associated configuration entry.
    BufferedReader reader =
         new BufferedReader(new FileReader(descriptorFile));
    String line = reader.readLine();
    if ((line == null) || (line.length() == 0))
    {
      int msgID = MSGID_BACKUPDIRECTORY_CANNOT_READ_CONFIG_ENTRY_DN;
      String message = getMessage(msgID, descriptorFilePath);
      throw new ConfigException(msgID, message);
    }
    else if (! line.startsWith(PROPERTY_BACKEND_CONFIG_DN))
    {
      int    msgID   = MSGID_BACKUPDIRECTORY_FIRST_LINE_NOT_DN;
      String message = getMessage(msgID, descriptorFilePath, line);
      throw new ConfigException(msgID, message);
    }

    String dnString =
         line.substring(PROPERTY_BACKEND_CONFIG_DN.length() + 1);
    DN configEntryDN;
    try
    {
      configEntryDN = DN.decode(dnString);
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_BACKUPDIRECTORY_CANNOT_DECODE_DN;
      String message = getMessage(msgID, dnString,
                                  de.getErrorMessage());
      throw new ConfigException(msgID, message, de);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_BACKUPDIRECTORY_CANNOT_DECODE_DN;
      String message = getMessage(msgID, dnString,
                                  stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }


    // Create the backup directory structure from what we know so far.
    BackupDirectory backupDirectory =
         new BackupDirectory(path, configEntryDN);


    // Iterate through the rest of the file and create the backup info
    // structures.  Blank lines will be considered delimiters.
    LinkedList<String> lines = new LinkedList<String>();
    while (true)
    {
      line = reader.readLine();
      if ((line == null) || (line.length() == 0))
      {
        // It's a blank line or the end of the file.  If we have lines
        // to process then do so.  Otherwise, move on.
        if (lines.isEmpty())
        {
          if (line == null)
          {
            break;
          }
          else
          {
            continue;
          }
        }


        // Parse the lines that we read and add the backup info to the
        // directory structure.
        BackupInfo backupInfo = BackupInfo.decode(backupDirectory,
                                                  lines);
        backupDirectory.addBackup(backupInfo);
        lines.clear();


        // If it was the end of the file, then break out of the loop.
        if (line == null)
        {
          break;
        }
      }
      else
      {
        lines.add(line);
      }
    }


    // Close the reader and return the backup directory structure.
    reader.close();
    return backupDirectory;
  }
}

