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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.util;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backupable;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.RestoreConfig;
import org.testng.Reporter;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = "precommit", sequential = true)
public class BackupManagerTestCase extends DirectoryServerTestCase
{
  private static final String ENTRY_DN = "dc=example,dc=com";

  private static final String FILE_NAME_PREFIX = "file_";
  private static final String BACKEND_ID = "backendID";
  private static final String BACKUP_ID = "backupID";

  @BeforeClass
  public void setUp() throws Exception
  {
    // Need the schema to be available, so make sure the server is started.
    // startFakeServer() is insufficient because we also need the CryptoManager to be initialized
    TestCaseUtils.startServer();
  }

  @DataProvider
  Object[][] backupData() throws Exception {
    // For each case is provided
    // - a label identifying the case (not used in method but allow to identify easily the case in IDE)
    // - a mock of a backupable (building the mock also involves creating directory and files to backup)
    // - a backup config
    // - a restore config

    String label0 = "nohash";
    Backupable backupable0 = buildBackupable(createSourceDirectory(label0), 3);
    BackupDirectory backupDir0 = buildBackupDir(label0);
    BackupConfig backupConfig0 = new BackupConfig(backupDir0, BACKUP_ID, false);
    RestoreConfig restoreConfig0 = new RestoreConfig(backupDir0, BACKUP_ID, false);

    String label1 = "unsignedhash";
    Backupable backupable1 = buildBackupable(createSourceDirectory(label1), 3);
    BackupDirectory backupDir1 = buildBackupDir(label1);
    BackupConfig backupConfig1 = new BackupConfig(backupDir1, BACKUP_ID, false);
    backupConfig1.setHashData(true);
    RestoreConfig restoreConfig1 = new RestoreConfig(backupDir1, BACKUP_ID, false);

    String label2 = "signedhash";
    Backupable backupable2 = buildBackupable(createSourceDirectory(label2), 3);
    BackupDirectory backupDir2 = buildBackupDir(label2);
    BackupConfig backupConfig2 = new BackupConfig(backupDir2, BACKUP_ID, false);
    backupConfig2.setHashData(true);
    backupConfig2.setSignHash(true);
    RestoreConfig restoreConfig2 = new RestoreConfig(backupDir2, BACKUP_ID, false);

    String label3 = "encrypted_compressed";
    Backupable backupable3 = buildBackupable(createSourceDirectory(label3), 3);
    BackupDirectory backupDir3 = buildBackupDir(label3);
    BackupConfig backupConfig3 = new BackupConfig(backupDir3, BACKUP_ID, false);
    backupConfig3.setEncryptData(true);
    backupConfig3.setCompressData(true);
    RestoreConfig restoreConfig3 = new RestoreConfig(backupDir3, BACKUP_ID, false);

    // should perform a normal backup in absence of incremental base ID
    String label4 = "incremental_without_incrementalBaseID";
    Backupable backupable4 = buildBackupable(createSourceDirectory(label4), 3);
    BackupDirectory backupDir4 = buildBackupDir(label4);
    BackupConfig backupConfig4 = new BackupConfig(backupDir4, BACKUP_ID, true);
    backupConfig4.setHashData(true);
    RestoreConfig restoreConfig4 = new RestoreConfig(backupDir4, BACKUP_ID, false);

    String label5 = "noFiles";
    Backupable backupable5 = buildBackupable(createSourceDirectory(label5), 0);
    BackupDirectory backupDir5 = buildBackupDir(label5);
    BackupConfig backupConfig5 = new BackupConfig(backupDir5, BACKUP_ID, false);
    RestoreConfig restoreConfig5 = new RestoreConfig(backupDir5, BACKUP_ID, false);

    String label6 = "multiple_directories";
    Backupable backupable6 = buildBackupableForMultipleDirectoriesCase(createSourceDirectory(label6), 3);
    BackupDirectory backupDir6 = buildBackupDir(label6);
    BackupConfig backupConfig6 = new BackupConfig(backupDir6, BACKUP_ID, false);
    RestoreConfig restoreConfig6 = new RestoreConfig(backupDir6, BACKUP_ID, false);

    return new Object[][] {
      { label0, backupable0, backupConfig0, restoreConfig0 },
      { label1, backupable1, backupConfig1, restoreConfig1 },
      { label2, backupable2, backupConfig2, restoreConfig2 },
      { label3, backupable3, backupConfig3, restoreConfig3 },
      { label4, backupable4, backupConfig4, restoreConfig4 },
      { label5, backupable5, backupConfig5, restoreConfig5 },
      { label6, backupable6, backupConfig6, restoreConfig6 },
    };
  }

  /**
   * This test encompasses creation, restore and remove of a backup.
   *
   * It allows to ensure that a backup can actually be restored.
   */
  @Test(dataProvider="backupData")
  public void testCreateBackupThenRestoreThenRemove(String label, Backupable backupable, BackupConfig backupConfig,
      RestoreConfig restoreConfig) throws Exception
  {
    BackupManager backupManager = new BackupManager(BACKEND_ID);

    // create and check archive files
    backupManager.createBackup(backupable, backupConfig);

    String backupPath = backupConfig.getBackupDirectory().getPath();
    assertThat(new File(backupPath, getArchiveFileName(BACKUP_ID))).exists();
    assertThat(new File(backupPath, "backup.info")).exists();

    // change content of directory to later check that backup is recovering everything
    removeBackedUpFiles(backupable);

    // restore and check list of files
    backupManager.restoreBackup(backupable, restoreConfig);

    assertAllFilesAreRestoredCorrectly(backupable);

    // remove the backup archive and check
    backupManager.removeBackup(backupConfig.getBackupDirectory(), BACKUP_ID);
    assertThat(new File(backupPath, getArchiveFileName(BACKUP_ID))).doesNotExist();

    //cleanDirectories(sourceDirectory, backupPath);
  }

  /**
   * This test encompasses creation, restore and remove of an incremental backup.
   *
   * It allows to ensure that a backup can actually be restored.
   */
  @Test
  public void testCreateIncrementalBackupThenRestoreThenRemove() throws Exception
  {
    Path sourceDirectory = createSourceDirectory("incremental");
    BackupDirectory backupDir = buildBackupDir("incremental");
    BackupManager backupManager = new BackupManager(BACKEND_ID);

    // perform first backup with 2 files
    Backupable backupable0 = buildBackupable(sourceDirectory, 2);
    String initialBackupId = BACKUP_ID + "_0";
    BackupConfig backupConfig0 = new BackupConfig(backupDir, initialBackupId, true);

    backupManager.createBackup(backupable0, backupConfig0);

    // check archive and info file
    String backupPath = backupDir.getPath();
    assertThat(new File(backupPath, getArchiveFileName(initialBackupId))).exists();
    assertThat(new File(backupPath, "backup.info")).exists();

    // perform second backup with 4 files (2 initial files plus 2 new files)
    // now backup with id "backupID" should depend on backup with id "backupID_0"
    Backupable backupable1 = buildBackupable(sourceDirectory, 4);
    BackupConfig backupConfig1 = new BackupConfig(backupDir, BACKUP_ID, true);

    backupManager.createBackup(backupable1, backupConfig1);

    assertThat(new File(backupPath, getArchiveFileName(initialBackupId))).exists();
    assertThat(new File(backupPath, getArchiveFileName(BACKUP_ID))).exists();
    assertThat(new File(backupPath, "backup.info")).exists();
    assertThat(new File(backupPath, "backup.info.save")).exists();

    // change content of directory to later check that backup is recovering everything
    removeBackedUpFiles(backupable1);

    // restore and check list of files
    RestoreConfig restoreConfig = new RestoreConfig(backupDir, BACKUP_ID, false);
    backupManager.restoreBackup(backupable1, restoreConfig);

    assertAllFilesAreRestoredCorrectly(backupable1);

    // remove the backup archive and check
    backupManager.removeBackup(backupDir, BACKUP_ID);
    assertThat(new File(backupPath, getArchiveFileName(BACKUP_ID))).doesNotExist();
    backupManager.removeBackup(backupDir, initialBackupId);
    assertThat(new File(backupPath, getArchiveFileName(initialBackupId))).doesNotExist();

    cleanDirectories(sourceDirectory, backupPath);
  }

  @Test
  public void testCreateDirectoryWithNumericSuffix() throws Exception
  {
    File directory = TestCaseUtils.createTemporaryDirectory("createDirectory-");
    String dirPath = directory.getAbsolutePath();
    // delete the directory to ensure creation works fine when there is no directory
    directory.delete();

    Path dir = BackupManager.createDirectoryWithNumericSuffix(dirPath, BACKUP_ID);
    assertThat(dir.toString()).isEqualTo(dirPath + "1");

    Path dir2 = BackupManager.createDirectoryWithNumericSuffix(dirPath, BACKUP_ID);
    assertThat(dir2.toString()).isEqualTo(dirPath + "2");

    recursiveDelete(dir.toFile());
    recursiveDelete(dir2.toFile());
  }

  @Test
  public void testSaveFilesToDirectory() throws Exception
  {
    Backupable backupable = buildBackupableForMultipleDirectoriesCase(createSourceDirectory("saveFiles-root"), 2);
    File rootDir = backupable.getDirectory();
    File targetDir = TestCaseUtils.createTemporaryDirectory("saveFiles-target");

    File actualTargetDir = BackupManager.saveFilesToDirectory(rootDir.toPath(), backupable.getFilesToBackup(),
        targetDir.getCanonicalPath(), BACKUP_ID).toFile();

    // all files should have been saved  in the target directory, with correct sub-path
    assertThat(new File(actualTargetDir, FILE_NAME_PREFIX+0)).exists();
    assertThat(new File(actualTargetDir, FILE_NAME_PREFIX+1)).exists();
    File subdir = new File(actualTargetDir, "subdir");
    assertThat(new File(subdir, FILE_NAME_PREFIX+0)).exists();
    assertThat(new File(subdir, FILE_NAME_PREFIX+1)).exists();

    recursiveDelete(rootDir);
    recursiveDelete(targetDir);
    recursiveDelete(actualTargetDir);
  }

  private void cleanDirectories(Path sourceDirectory, String backupPath)
  {
    StaticUtils.recursiveDelete(sourceDirectory.toFile());
    StaticUtils.recursiveDelete(new File(backupPath));
  }

  private String getArchiveFileName(String backupId)
  {
    return "backup-" + BACKEND_ID + "-" + backupId;
  }

  private void assertAllFilesAreRestoredCorrectly(Backupable backupable) throws Exception
  {
    ListIterator<Path> files = backupable.getFilesToBackup();
    while (files.hasNext())
    {
      Path file = files.next();
      assertThat(file.toFile()).exists();
      assertThat(file.toFile()).hasContent(file.getFileName().toString());
    }
  }

  private void removeBackedUpFiles(Backupable backupable) throws Exception
  {
    ListIterator<Path> it = backupable.getFilesToBackup();
    while (it.hasNext())
    {
      Path file = it.next();
      Files.deleteIfExists(file);
    }
  }

  private BackupDirectory buildBackupDir(String label) throws Exception
  {
    File backupDirectory = TestCaseUtils.createTemporaryDirectory("backupDirectory-" + label + "-");
    Reporter.log("Create backup directory:" + backupDirectory, true);
    return new BackupDirectory(backupDirectory.getAbsolutePath(), DN.valueOf(ENTRY_DN));
  }

  private Backupable buildBackupable(Path sourceDirectory, int numberOfFiles) throws Exception
  {
    List<Path> files = createFilesInDirectoryToBackup(sourceDirectory, numberOfFiles);

    Backupable backupable = mock(Backupable.class);
    when(backupable.getDirectory()).thenReturn(sourceDirectory.toFile());
    when(backupable.getFilesToBackup()).thenReturn(files.listIterator());
    when(backupable.isDirectRestore()).thenReturn(true);

    return backupable;
  }

  /**
   * Create files in source directory + additional files under a subdirectory of source directory.
   */
  private Backupable buildBackupableForMultipleDirectoriesCase(Path sourceDirectory, int numberOfFiles)
      throws Exception
  {
    List<Path> files = createFilesInDirectoryToBackup(sourceDirectory, numberOfFiles);

    // create an additional subdirectory with files
    Path subdir = sourceDirectory.resolve("subdir");
    Files.createDirectory(subdir);
    List<Path> subdirFiles = createFilesInDirectoryToBackup(subdir, numberOfFiles);
    files.addAll(subdirFiles);

    Backupable backupable = mock(Backupable.class);
    when(backupable.getDirectory()).thenReturn(sourceDirectory.toFile());
    when(backupable.getFilesToBackup()).thenReturn(files.listIterator());

    return backupable;
  }

  private Path createSourceDirectory(String label) throws IOException
  {
    File sourceDirectory = TestCaseUtils.createTemporaryDirectory("dirToBackup-" + label + "-");
    Reporter.log("Create directory to backup:" + sourceDirectory, true);
    return sourceDirectory.toPath();
  }

  private List<Path> createFilesInDirectoryToBackup(Path directory, int numberOfFiles)
      throws Exception
  {
    List<Path> files = new ArrayList<>();
    for (int i = 0; i < numberOfFiles; i++)
    {
      String filename = FILE_NAME_PREFIX + i;
      Path file = directory.resolve(filename);
      createFile(file, StaticUtils.getBytes(filename));
      files.add(file);
    }
    return files;
  }

  private void createFile(Path file, byte[] content) throws Exception {
    OutputStream output = new FileOutputStream(file.toFile(), false);
    try
    {
      output.write(content);
    }
    finally {
      close(output);
    }
  }
}
