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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.api;

import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.RestoreConfig;

/**
 * This class provides a very simple implementation of an import, export,
 * backup, and restore task listener.  It will simply increment a counter each
 * time one of the associated methods is invoked.
 */
public class TestTaskListener
       implements BackupTaskListener, RestoreTaskListener, ImportTaskListener,
                  ExportTaskListener
{
  private static final TestTaskListener instance = new TestTaskListener();
  public static final AtomicInteger backupBeginCount  = new AtomicInteger(0);
  public static final AtomicInteger backupEndCount    = new AtomicInteger(0);
  public static final AtomicInteger exportBeginCount  = new AtomicInteger(0);
  public static final AtomicInteger exportEndCount    = new AtomicInteger(0);
  public static final AtomicInteger importBeginCount  = new AtomicInteger(0);
  public static final AtomicInteger importEndCount    = new AtomicInteger(0);
  public static final AtomicInteger restoreBeginCount = new AtomicInteger(0);
  public static final AtomicInteger restoreEndCount   = new AtomicInteger(0);

  /** Registers the task listeners with the Directory Server. */
  public static void registerListeners()
  {
    DirectoryServer.registerBackupTaskListener(instance);
    DirectoryServer.registerRestoreTaskListener(instance);
    DirectoryServer.registerExportTaskListener(instance);
    DirectoryServer.registerImportTaskListener(instance);
  }

  /** Deregisters the task listeners with the Directory Server. */
  public static void deregisterListeners()
  {
    DirectoryServer.deregisterBackupTaskListener(instance);
    DirectoryServer.deregisterRestoreTaskListener(instance);
    DirectoryServer.deregisterExportTaskListener(instance);
    DirectoryServer.deregisterImportTaskListener(instance);
  }

  @Override
  public void processBackupBegin(Backend<?> backend, BackupConfig config)
  {
    backupBeginCount.incrementAndGet();
  }

  @Override
  public void processBackupEnd(Backend<?> backend, BackupConfig config, boolean successful)
  {
    backupEndCount.incrementAndGet();
  }

  @Override
  public void processRestoreBegin(Backend<?> backend, RestoreConfig config)
  {
    restoreBeginCount.incrementAndGet();
  }

  @Override
  public void processRestoreEnd(Backend<?> backend, RestoreConfig config, boolean successful)
  {
    restoreEndCount.incrementAndGet();
  }

  @Override
  public void processExportBegin(Backend<?> backend, LDIFExportConfig config)
  {
    exportBeginCount.incrementAndGet();
  }

  @Override
  public void processExportEnd(Backend<?> backend, LDIFExportConfig config, boolean successful)
  {
    exportEndCount.incrementAndGet();
  }

  @Override
  public void processImportBegin(Backend<?> backend, LDIFImportConfig config)
  {
    importBeginCount.incrementAndGet();
  }

  @Override
  public void processImportEnd(Backend<?> backend, LDIFImportConfig config, boolean successful)
  {
    importEndCount.incrementAndGet();
  }
}
