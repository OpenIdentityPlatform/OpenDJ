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



import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TimeZone;

import org.opends.server.config.ConfigException;
import org.opends.server.util.Base64;

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
 * backup that is available in a backup directory.
 */
public class BackupInfo
{



  /**
   * The name of the property that holds the date that the backup was
   * created.
   */
  public static final String PROPERTY_BACKUP_DATE = "backup_date";



  /**
   * The name of the property that holds the backup ID in encoded
   * representations.
   */
  public static final String PROPERTY_BACKUP_ID = "backup_id";



  /**
   * The name of the property that holds the incremental flag in
   * encoded representations.
   */
  public static final String PROPERTY_IS_INCREMENTAL = "incremental";



  /**
   * The name of the property that holds the compressed flag in
   * encoded representations.
   */
  public static final String PROPERTY_IS_COMPRESSED = "compressed";



  /**
   * The name of the property that holds the encrypted flag in encoded
   * representations.
   */
  public static final String PROPERTY_IS_ENCRYPTED = "encrypted";



  /**
   * The name of the property that holds the unsigned hash in encoded
   * representations.
   */
  public static final String PROPERTY_UNSIGNED_HASH = "hash";



  /**
   * The name of the property that holds the signed hash in encoded
   * representations.
   */
  public static final String PROPERTY_SIGNED_HASH = "signed_hash";



  /**
   * The name of the property that holds the set of dependencies in
   * encoded representations (one dependency per instance).
   */
  public static final String PROPERTY_DEPENDENCY = "dependency";



  /**
   * The prefix to use with custom backup properties.  The name of the
   * property will be appended to this prefix.
   */
  public static final String PROPERTY_CUSTOM_PREFIX = "property.";



  // The backup directory with which this backup info structure is
  // associated.
  private BackupDirectory backupDirectory;

  // Indicates whether this backup is compressed.
  private boolean isCompressed;

  // Indicates whether this backup is encrypted.
  private boolean isEncrypted;

  // Indicates whether this is an incremental backup.
  private boolean isIncremental;

  // The signed hash for this backup, if appropriate.
  private byte[] signedHash;

  // The unsigned hash for this backup, if appropriate.
  private byte[] unsignedHash;

  // The time that this backup was created.
  private Date backupDate;

  // The set of backup ID(s) on which this backup is dependent.
  private HashSet<String> dependencies;

  // The set of additional properties associated with this backup.
  // This is intended for use by the backend for storing any kind of
  // state information that it might need to associated with the
  // backup.  The mapping will be between a name and a value, where
  // the name must not contain an equal sign and neither the name nor
  // the value may contain line breaks;
  private HashMap<String,String> backupProperties;

  // The unique ID for this backup.
  private String backupID;



  /**
   * Creates a new backup info structure with the provided
   * information.
   *
   * @param  backupDirectory   A reference to the backup directory in
   *                           which this backup is stored.
   * @param  backupID          The unique ID for this backup.
   * @param  backupDate        The time that this backup was created.
   * @param  isIncremental     Indicates whether this is an
   *                           incremental or a full backup.
   * @param  isCompressed      Indicates whether the backup is
   *                           compressed.
   * @param  isEncrypted       Indicates whether the backup is
   *                           encrypted.
   * @param  unsignedHash      The unsigned hash for this backup, if
   *                           appropriate.
   * @param  signedHash        The signed hash for this backup, if
   *                           appropriate.
   * @param  dependencies      The backup IDs of the previous backups
   *                           on which this backup is dependent.
   * @param  backupProperties  The set of additional backend-specific
   *                           properties that should be stored with
   *                           this backup information.  It should be
   *                           a mapping between property names and
   *                           values, where the names do not contain
   *                           any equal signs and neither the names
   *                           nor the values contain line breaks.
   */
  public BackupInfo(BackupDirectory backupDirectory, String backupID,
                    Date backupDate, boolean isIncremental,
                    boolean isCompressed, boolean isEncrypted,
                    byte[] unsignedHash, byte[] signedHash,
                    HashSet<String> dependencies,
                    HashMap<String,String> backupProperties)
  {
    this.backupDirectory = backupDirectory;
    this.backupID        = backupID;
    this.backupDate      = backupDate;
    this.isIncremental   = isIncremental;
    this.isCompressed    = isCompressed;
    this.isEncrypted     = isEncrypted;
    this.unsignedHash    = unsignedHash;
    this.signedHash      = signedHash;

    if (dependencies == null)
    {
      this.dependencies = new HashSet<String>();
    }
    else
    {
      this.dependencies = dependencies;
    }

    if (backupProperties == null)
    {
      this.backupProperties = new HashMap<String,String>();
    }
    else
    {
      this.backupProperties = backupProperties;
    }
  }



  /**
   * Retrieves the reference to the backup directory in which this
   * backup is stored.
   *
   * @return  A reference to the backup directory in which this backup
   *          is stored.
   */
  public BackupDirectory getBackupDirectory()
  {
    return backupDirectory;
  }



  /**
   * Retrieves the unique ID for this backup.
   *
   * @return  The unique ID for this backup.
   */
  public String getBackupID()
  {
    return backupID;
  }



  /**
   * Retrieves the date that this backup was created.
   *
   * @return  The date that this backup was created.
   */
  public Date getBackupDate()
  {
    return backupDate;
  }



  /**
   * Indicates whether this is an incremental or a full backup.
   *
   * @return  <CODE>true</CODE> if this is an incremental backup, or
   *          <CODE>false</CODE> if it is a full backup.
   */
  public boolean isIncremental()
  {
    return isIncremental;
  }



  /**
   * Indicates whether this backup is compressed.
   *
   * @return  <CODE>true</CODE> if this backup is compressed, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean isCompressed()
  {
    return isCompressed;
  }



  /**
   * Indicates whether this backup is encrypted.
   *
   * @return  <CODE>true</CODE> if this backup is encrypted, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean isEncrypted()
  {
    return isEncrypted;
  }



  /**
   * Retrieves the data for the unsigned hash for this backup, if
   * appropriate.
   *
   * @return  The data for the unsigned hash for this backup, or
   *          <CODE>null</CODE> if there is none.
   */
  public byte[] getUnsignedHash()
  {
    return unsignedHash;
  }



  /**
   * Retrieves the data for the signed hash for this backup, if
   * appropriate.
   *
   * @return  The data for the signed hash for this backup, or
   *          <CODE>null</CODE> if there is none.
   */
  public byte[] getSignedHash()
  {
    return signedHash;
  }



  /**
   * Retrieves the set of the backup IDs for the backups on which this
   * backup is dependent.  This is primarily intended for use with
   * incremental backups (which should be dependent on at least a full
   * backup and possibly one or more other incremental backups).  The
   * contents of this hash should not be directly updated by the
   * caller.
   *
   * @return  The set of the backup IDs for the backups on which this
   *          backup is dependent.
   */
  public HashSet<String> getDependencies()
  {
    return dependencies;
  }



  /**
   * Indicates whether this backup has a dependency on the backup with
   * the provided ID.
   *
   * @param  backupID  The backup ID for which to make the
   *                   determination.
   *
   * @return  <CODE>true</CODE> if this backup has a dependency on the
   *          backup with the provided ID, or <CODE>false</CODE> if
   *          not.
   */
  public boolean dependsOn(String backupID)
  {
    return dependencies.contains(backupID);
  }



  /**
   * Retrieves a set of additional properties that should be
   * associated with this backup.  This may be used by the backend to
   * store arbitrary information that may be needed later to restore
   * the backup or perform an incremental backup based on this backup.
   * The mapping will be between property names and values, where the
   * names are not allowed to contain equal signs, and neither the
   * names nor the values may have line breaks.  The contents of the
   * mapping should not be altered by the caller.
   *
   * @return  A set of additional properties that should be associated
   *          with this backup.
   */
  public HashMap<String,String> getBackupProperties()
  {
    return backupProperties;
  }



  /**
   * Retrieves the value of the backup property with the specified
   * name.
   *
   * @param  name  The name of the backup property to retrieve.
   *
   * @return  The value of the backup property with the specified
   *          name, or <CODE>null</CODE> if there is no such property.
   */
  public String getBackupProperty(String name)
  {
    return backupProperties.get(name);
  }



  /**
   * Encodes this backup info structure to a multi-line string
   * representation.  This representation may be parsed by the
   * <CODE>decode</CODE> method to reconstruct the structure.
   *
   * @return  A multi-line string representation of this backup info
   *          structure.
   */
  public LinkedList<String> encode()
  {
    LinkedList<String> list       = new LinkedList<String>();
    SimpleDateFormat   dateFormat =
         new SimpleDateFormat(DATE_FORMAT_UTC_TIME);

    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    list.add(PROPERTY_BACKUP_ID + "=" + backupID);
    list.add(PROPERTY_BACKUP_DATE + "=" +
             dateFormat.format(backupDate));
    list.add(PROPERTY_IS_INCREMENTAL + "=" +
             String.valueOf(isIncremental));
    list.add(PROPERTY_IS_COMPRESSED + "=" +
             String.valueOf(isCompressed));
    list.add(PROPERTY_IS_ENCRYPTED + "=" +
             String.valueOf(isEncrypted));

    if (unsignedHash != null)
    {
      list.add(PROPERTY_UNSIGNED_HASH + "=" +
               Base64.encode(unsignedHash));
    }

    if (signedHash != null)
    {
      list.add(PROPERTY_SIGNED_HASH + "=" +
               Base64.encode(signedHash));
    }

    if (! dependencies.isEmpty())
    {
      for (String dependency : dependencies)
      {
        list.add(PROPERTY_DEPENDENCY + "=" + dependency);
      }
    }

    if (! backupProperties.isEmpty())
    {
      for (String name : backupProperties.keySet())
      {
        String value = backupProperties.get(name);
        if (value == null)
        {
          value = "";
        }

        list.add(PROPERTY_CUSTOM_PREFIX + name + "=" + value);
      }
    }

    return list;
  }



  /**
   * Decodes the provided list of strings as the representation of a
   * backup info structure.
   *
   * @param  backupDirectory  The reference to the backup directory
   *                          with which the backup info is
   *                          associated.
   * @param  encodedInfo      The list of strings that comprise the
   *                          string representation of the backup info
   *                          structure.
   *
   * @return  The decoded backup info structure.
   *
   * @throws  ConfigException  If a problem occurs while attempting to
   *                           decode the backup info data.
   */
  public static BackupInfo decode(BackupDirectory backupDirectory,
                                  LinkedList<String> encodedInfo)
         throws ConfigException
  {
    String                 backupID         = null;
    Date                   backupDate       = null;
    boolean                isIncremental    = false;
    boolean                isCompressed     = false;
    boolean                isEncrypted      = false;
    byte[]                 unsignedHash     = null;
    byte[]                 signedHash       = null;
    HashSet<String>        dependencies     = new HashSet<String>();
    HashMap<String,String> backupProperties =
         new HashMap<String,String>();

    String backupPath = backupDirectory.getPath();
    try
    {
      for (String line : encodedInfo)
      {
        int equalPos = line.indexOf('=');
        if (equalPos < 0)
        {
          int    msgID   = MSGID_BACKUPINFO_NO_DELIMITER;
          String message = getMessage(msgID, line, backupPath);
          throw new ConfigException(msgID, message);
        }
        else if (equalPos == 0)
        {
          int    msgID   = MSGID_BACKUPINFO_NO_NAME;
          String message = getMessage(msgID, line, backupPath);
          throw new ConfigException(msgID, message);
        }

        String name  = line.substring(0, equalPos);
        String value = line.substring(equalPos+1);

        if (name.equals(PROPERTY_BACKUP_ID))
        {
          if (backupID == null)
          {
            backupID = value;
          }
          else
          {
            int    msgID   = MSGID_BACKUPINFO_MULTIPLE_BACKUP_IDS;
            String message = getMessage(msgID, backupPath, backupID,
                                        value);
            throw new ConfigException(msgID, message);
          }
        }
        else if (name.equals(PROPERTY_BACKUP_DATE))
        {
          SimpleDateFormat dateFormat =
               new SimpleDateFormat(DATE_FORMAT_UTC_TIME);
          dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
          backupDate = dateFormat.parse(value);
        }
        else if (name.equals(PROPERTY_IS_INCREMENTAL))
        {
          isIncremental = Boolean.valueOf(value);
        }
        else if (name.equals(PROPERTY_IS_COMPRESSED))
        {
          isCompressed = Boolean.valueOf(value);
        }
        else if (name.equals(PROPERTY_IS_ENCRYPTED))
        {
          isEncrypted = Boolean.valueOf(value);
        }
        else if (name.equals(PROPERTY_UNSIGNED_HASH))
        {
          unsignedHash = Base64.decode(value);
        }
        else if (name.equals(PROPERTY_SIGNED_HASH))
        {
          signedHash = Base64.decode(value);
        }
        else if (name.equals(PROPERTY_DEPENDENCY))
        {
          dependencies.add(value);
        }
        else if (name.startsWith(PROPERTY_CUSTOM_PREFIX))
        {
          String propertyName =
               name.substring(PROPERTY_CUSTOM_PREFIX.length());
          backupProperties.put(propertyName, value);
        }
        else
        {
          int    msgID   = MSGID_BACKUPINFO_UNKNOWN_PROPERTY;
          String message = getMessage(msgID, backupPath, name, value);
          throw new ConfigException(msgID, message);
        }
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_BACKUPINFO_CANNOT_DECODE;
      String message = getMessage(msgID, backupPath,
                                  stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }


    // There must have been at least a backup ID and backup date
    // specified.
    if (backupID == null)
    {
      int    msgID   = MSGID_BACKUPINFO_NO_BACKUP_ID;
      String message = getMessage(msgID, backupPath);
      throw new ConfigException(msgID, message);
    }

    if (backupDate == null)
    {
      int    msgID   = MSGID_BACKUPINFO_NO_BACKUP_DATE;
      String message = getMessage(msgID, backupID, backupPath);
      throw new ConfigException(msgID, message);
    }


    return new BackupInfo(backupDirectory, backupID, backupDate,
                          isIncremental, isCompressed, isEncrypted,
                          unsignedHash, signedHash, dependencies,
                          backupProperties);
  }



  /**
   * Retrieves a multi-line string representation of this backup info
   * structure.
   *
   * @return  A multi-line string representation of this backup info
   *          structure.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a multi-line string representation of this backup info
   * structure to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 written.
   */
  public void toString(StringBuilder buffer)
  {
    LinkedList<String> lines = encode();
    for (String line : lines)
    {
      buffer.append(line);
      buffer.append(EOL);
    }
  }
}

