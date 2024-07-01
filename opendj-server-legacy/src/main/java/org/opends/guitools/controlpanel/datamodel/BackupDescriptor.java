/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.datamodel;

import java.io.File;
import java.util.Date;

import org.opends.server.types.BackupInfo;

/** Class used to describe a backup. */
public class BackupDescriptor
{
  /** The different types of backups. */
  public enum Type
  {
    /** Full backup. */
    FULL,
    /** Incremental backup. */
    INCREMENTAL
  }

  private Type type;
  private Date creationDate;
  private File path;
  private String id;
  private BackupInfo info;

  /**
   * The BackupDescriptor constructor.
   * @param path the directory where the backup is located.
   * @param creationDate the date of creation of the backup.
   * @param type the type of backup.
   * @param id the backup id.
   */
  public BackupDescriptor(File path, Date creationDate, Type type, String id)
  {
   this.path = path;
   this.creationDate = creationDate;
   this.type = type;
   this.id = id;
  }

  /**
   * The BackupDescriptor generated using a BackupInfo object.
   * @param info the BackupInfo object that contains all the information about
   * the backup.
   */
  public BackupDescriptor(BackupInfo info)
  {
   this.path = new File(info.getBackupDirectory().getPath());
   this.creationDate = info.getBackupDate();
   this.type = info.isIncremental() ? Type.INCREMENTAL : Type.FULL;
   this.id = info.getBackupID();
   this.info = info;
  }

  /**
   * Returns the creation date of the backup.
   * @return the creation date of the backup.
   */
  public Date getCreationDate()
  {
    return creationDate;
  }

  /**
   * Returns the directory where the backup is located.
   * @return the directory where the backup is located.
   */
  public File getPath()
  {
    return path;
  }

  /**
   * Returns the type of the backup.
   * @return the type of the backup.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the backup ID.
   * @return the backup ID.
   */
  public String getID()
  {
    return id;
  }

  /**
   * Returns the BackupInfo object associated with this backup.  It might be
   * <CODE>null</CODE>.
   * @return the BackupInfo object associated with this backup.
   */
  public BackupInfo getBackupInfo()
  {
    return info;
  }
}
