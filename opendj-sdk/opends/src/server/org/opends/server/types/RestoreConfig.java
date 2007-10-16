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


/**
 * This class defines a data structure for holding configuration
 * information to use when restoring a backup of a Directory Server
 * backend.  It is assumed that the only information necessary to
 * restore a backup is the path to the directory containing the backup
 * file(s) and the backup ID of the backup to restore.  Any other
 * information that may be needed to restore a given backup must be
 * saved in some way by the backup mechanism.  Note that if the
 * associated backend supports taking incremental backups, it must be
 * possible to restore the original full backup or any individual
 * incremental backup taken since that full backup (i.e., an
 * incremental backup must not prevent restoring an earlier
 * incremental backup or the original full backup with which the
 * incremental backups are associated).
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class RestoreConfig extends OperationConfig
{
  // The reference to the directory containing the backup file(s) to
  // restore.
  private BackupDirectory backupDirectory;

  // Indicates whether the "restore" should be verify-only but not
  // actually move or restore any files.
  private boolean verifyOnly;

  // The unique ID assigned to the backup that is to be restored.
  private String backupID;



  /**
   * Creates a new restore configuration with the provided
   * information.
   *
   * @param  backupDirectory  The reference to the directory
   *                          containing the backup file(s) to
   *                          restore.
   * @param  backupID         The unique ID assigned to the backup
   *                          that is to be restored.
   * @param  verifyOnly       Indicates whether the specified backup
   *                          should be verified only and not actually
   *                          restored.
   */
  public RestoreConfig(BackupDirectory backupDirectory,
                       String backupID, boolean verifyOnly)
  {
    this.backupDirectory = backupDirectory;
    this.backupID        = backupID;
    this.verifyOnly      = verifyOnly;
  }



  /**
   * Retrieves a reference to the directory containing the backup
   * file(s) to restore.
   *
   * @return  A reference to the directory containing the backup
   *          file(s) to restore.
   */
  public BackupDirectory getBackupDirectory()
  {
    return backupDirectory;
  }



  /**
   * Retrieves the identifier of the backup to be restored.  This ID
   * must be unique among all backups (both full and incremental) at
   * least within the specified backup directory.
   *
   * @return  The identifier of the backup to be restored.
   */
  public String getBackupID()
  {
    return backupID;
  }



  /**
   * Indicates whether the restore process should only attempt to
   * verify the validity and/or integrity of the backup files to the
   * best of its ability rather than actually trying to restore.  Note
   * that in some cases, the ability to verify a backup files will not
   * be able to guarantee that they may be used, but will it must at
   * least verify that the appropriate file(s) exist, that any hashes
   * or signatures are valid, and that any encryption can be
   * decrypted.
   *
   * @return  <CODE>true</CODE> if this restore process should only
   *          attempt to verify the validity and/or integrity of the
   *          backup files, or <CODE>false</CODE> if it should
   *          actually attempt to restore the backup.
   */
  public boolean verifyOnly()
  {
    return verifyOnly;
  }
}

