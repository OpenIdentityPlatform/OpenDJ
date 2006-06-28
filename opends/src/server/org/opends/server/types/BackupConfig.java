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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a data structure for holding configuration
 * information to use when performing a backup of a Directory Server
 * backend.  This configuration may specify a full backup (in which
 * the entire contents of the backend repository is to be archived),
 * or incremental (in which only a small set of data containing
 * changes since the last incremental or full backup need be
 * preserved).  Note that some backends may not support incremental
 * backups, and those that do may require that incremental backups use
 * the same settings as the full backup with regard to compression,
 * encryption, hashing, signing, etc.  Also note that if the
 * incremental backups are supported, it must be possible to restore
 * the original full backup or any individual incremental backup taken
 * since that full backup (i.e., an incremental backup must not
 * prevent restoring an earlier incremental backup or the original
 * full backup with which the incremental backups are associated).
 */
public class BackupConfig
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.BackupConfig";



  // The path to the directory in which the backup file(s) should be
  // created.
  private BackupDirectory backupDirectory;

  // Indicates whether the data should be compressed as it is written.
  private boolean compressData;

  // Indicates whether the data should be encrypted as it is written.
  private boolean encryptData;

  // Indicates whether to generate a cryptographic hash of the data as
  // it is written.
  private boolean hashData;

  // Indicates whether to attempt an incremental backup.
  private boolean isIncremental;

  // Indicates whether to digitally sign the hash when the backup is
  // complete.
  private boolean signHash;

  // The unique identifier assigned to this backup operation (which
  // may be used to indicate which version to restore if multiple
  // backups are in the same directory).
  private String backupID;

  // The unique ID for the existing full or incremental backup against
  // which the incremental backup should be based.
  private String incrementalBaseID;



  /**
   * Creates a new backup configuration that will create a full or
   * incremental backup of a backend using the provided information.
   *
   * @param  backupDirectory  The backup directory structure that
   *                          indicates where the files should be
   *                          written.
   * @param  backupID         The unique identifier assigned to this
   *                          backup.
   * @param  isIncremental    Indicates whether this is to be an
   *                          incremental or a full backup.
   */
  public BackupConfig(BackupDirectory backupDirectory,
                      String backupID, boolean isIncremental)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(backupDirectory),
                            String.valueOf(backupID),
                            String.valueOf(isIncremental));

    this.backupDirectory = backupDirectory;
    this.backupID        = backupID;
    this.isIncremental   = isIncremental;
  }



  /**
   * Retrieves the backup directory structure for this backup
   * configuration.
   *
   * @return  The backup directory structure for this backup
   *          configuration.
   */
  public BackupDirectory getBackupDirectory()
  {
    assert debugEnter(CLASS_NAME, "getBackupDirectory");

    return backupDirectory;
  }



  /**
   * Retrieves the identifier associated with this backup
   * configuration, which can be used later to indicate which backup
   * should be restored if multiple backups are stored in the same
   * location.
   *
   * @return  The identifier associated with this backup
   *          configuration.
   */
  public String getBackupID()
  {
    assert debugEnter(CLASS_NAME, "getBackupID");

    return backupID;
  }



  /**
   * Indicates whether the backend should attempt to perform an
   * incremental backup containing only the changes since the last
   * incremental or full backup.
   *
   * @return  <CODE>true</CODE> if this should be an incremental
   *          backup, or <CODE>false</CODE> if it should be a full
   *          backup.
   */
  public boolean isIncremental()
  {
    assert debugEnter(CLASS_NAME, "isIncremental");

    return isIncremental;
  }



  /**
   * Retrieves the backup ID for the backup on which this incremental
   * backup should be based.  If it is <CODE>null</CODE>, then the
   * backend is free to choose the appropriate existing backup on
   * which to base this incremental backup.
   *
   * @return  The backup ID for the backup on which this incremental
   *          backup should be based, or <CODE>null</CODE> if none was
   *          specified.
   */
  public String getIncrementalBaseID()
  {
    assert debugEnter(CLASS_NAME, "getIncrementalBaseID");

    return incrementalBaseID;
  }



  /**
   * Specifies the backup ID for the backup on which this incremental
   * backup should be based.
   *
   * @param  incrementalBaseID  The backup ID for the backup on which
   *                            this incremental backup should be
   *                            based.
   */
  public void setIncrementalBaseID(String incrementalBaseID)
  {
    assert debugEnter(CLASS_NAME, "setIncrementalBaseID",
                      String.valueOf(incrementalBaseID));

    this.incrementalBaseID = incrementalBaseID;
  }



  /**
   * Indicates whether the backup process should compress the data as
   * it is archived.
   *
   * @return  <CODE>true</CODE> if the backup process should compress
   *          the data as it is archived, or <CODE>false</CODE> if
   *          not.
   */
  public boolean compressData()
  {
    assert debugEnter(CLASS_NAME, "compressData");

    return compressData;
  }



  /**
   * Specifies whether the backup process should compress the data as
   * it is archived.
   *
   * @param  compressData  Specifies whether the backup process should
   *                       compress the data as it is archived.
   */
  public void setCompressData(boolean compressData)
  {
    assert debugEnter(CLASS_NAME, "setCompressData",
                      String.valueOf(compressData));

    this.compressData = compressData;
  }



  /**
   * Indicates whether the backup process should encrypt the data as
   * it is archived.
   *
   * @return  <CODE>true</CODE> if the backup process should encrypt
   *          the data as it is archived, or <CODE>false</CODE> if
   *          not.
   */
  public boolean encryptData()
  {
    assert debugEnter(CLASS_NAME, "encryptData");

    return encryptData;
  }



  /**
   * Specifies whether the backup process should encrypt the data as
   * it is archived.
   *
   * @param  encryptData  Specifies whether the backup process should
   *                      encrypt the data as it is archived.
   */
  public void setEncryptData(boolean encryptData)
  {
    assert debugEnter(CLASS_NAME, "setEncryptData",
                      String.valueOf(encryptData));

    this.encryptData = encryptData;
  }



  /**
   * Indicates whether the backup process should generate a hash of
   * the data as it is archived that may be validated as part of the
   * restore process.
   *
   * @return  <CODE>true</CODE> if the backup process should generate
   *          a hash of the data as it is archived, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hashData()
  {
    assert debugEnter(CLASS_NAME, "hashData");

    return hashData;
  }



  /**
   * Specifies whether the backup process should generate a hash of
   * the data as it is archived.
   *
   * @param  hashData  Specifies whether the backup process should
   *                   generate a hash of the data as it is archived.
   */
  public void setHashData(boolean hashData)
  {
    assert debugEnter(CLASS_NAME, "setHashData",
                      String.valueOf(hashData));

    this.hashData = hashData;
  }



  /**
   * Indicates whether the backup process should digitally sign the
   * hash of the data when it is archived.  Signing the hash offers a
   * means of protection against tampering by an unauthorized party.
   * Note that this option is only applicable if the backup is to
   * include a hash of the archived data.
   *
   * @return  <CODE>true</CODE> if the backup process should digitally
   *          sign the generated hash, or <CODE>false</CODE> if not.
   */
  public boolean signHash()
  {
    assert debugEnter(CLASS_NAME, "signHash");

    return signHash;
  }



  /**
   * Specifies whether the backup process should digitally sign the
   * hash of the data when it is archived.
   *
   * @param  signHash  Specifies whether the backup process should
   *                   digitally sign the data when it is archived.
   */
  public void setSignHash(boolean signHash)
  {
    assert debugEnter(CLASS_NAME, "setSignHash",
                      String.valueOf(signHash));

    this.signHash = signHash;
  }
}

