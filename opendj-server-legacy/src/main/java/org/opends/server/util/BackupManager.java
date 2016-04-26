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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.util;

import static java.util.Collections.*;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Mac;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Pair;
import org.opends.server.api.Backupable;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.CryptoManagerException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RestoreConfig;

/**
 * A backup manager for any entity that is backupable (backend, storage).
 *
 * @see Backupable
 */
public class BackupManager
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The common prefix for archive files. */
  private static final String BACKUP_BASE_FILENAME = "backup-";

  /**
   * The name of the property that holds the name of the latest log file
   * at the time the backup was created.
   */
  private static final String PROPERTY_LAST_LOGFILE_NAME = "last_logfile_name";

  /**
   * The name of the property that holds the size of the latest log file
   * at the time the backup was created.
   */
  private static final String PROPERTY_LAST_LOGFILE_SIZE = "last_logfile_size";

  /**
   * The name of the entry in an incremental backup archive file
   * containing a list of log files that are unchanged since the
   * previous backup.
   */
  private static final String ZIPENTRY_UNCHANGED_LOGFILES = "unchanged.txt";

  /**
   * The name of a dummy entry in the backup archive file that will act
   * as a placeholder in case a backup is done on an empty backend.
   */
  private static final String ZIPENTRY_EMPTY_PLACEHOLDER = "empty.placeholder";

  /** The backend ID. */
  private final String backendID;

  /**
   * Construct a backup manager for a backend.
   *
   * @param backendID
   *          The ID of the backend instance for which a backup manager is
   *          required.
   */
  public BackupManager(String backendID)
  {
    this.backendID = backendID;
  }

  /** A cryptographic engine to use for backup creation or restore. */
  private static abstract class CryptoEngine
  {
    final CryptoManager cryptoManager;
    final boolean shouldEncrypt;

    /** Creates a crypto engine for archive creation. */
    static CryptoEngine forCreation(BackupConfig backupConfig, NewBackupParams backupParams)
        throws DirectoryException {
      if (backupConfig.hashData())
      {
        if (backupConfig.signHash())
        {
          return new MacCryptoEngine(backupConfig, backupParams);
        }
        else
        {
          return new DigestCryptoEngine(backupConfig, backupParams);
        }
      }
      else
      {
        return new NoHashCryptoEngine(backupConfig.encryptData());
      }
    }

    /** Creates a crypto engine for archive restore. */
    static CryptoEngine forRestore(BackupInfo backupInfo)
        throws DirectoryException {
      boolean hasSignedHash = backupInfo.getSignedHash() != null;
      boolean hasHashData = hasSignedHash || backupInfo.getUnsignedHash() != null;
      if (hasHashData)
      {
        if (hasSignedHash)
        {
          return new MacCryptoEngine(backupInfo);
        }
        else
        {
          return new DigestCryptoEngine(backupInfo);
        }
      }
      else
      {
        return new NoHashCryptoEngine(backupInfo.isEncrypted());
      }
    }

    CryptoEngine(boolean shouldEncrypt)
    {
      cryptoManager = DirectoryServer.getCryptoManager();
      this.shouldEncrypt = shouldEncrypt;
    }

    /** Indicates if data is encrypted. */
    final boolean shouldEncrypt() {
      return shouldEncrypt;
    }

    /** Indicates if hashed data is signed. */
    boolean hasSignedHash() {
      return false;
    }

    /** Update the hash with the provided string. */
    abstract void updateHashWith(String s);

    /** Update the hash with the provided buffer. */
    abstract void updateHashWith(byte[] buffer, int offset, int len);

    /** Generates the hash bytes. */
    abstract byte[] generateBytes();

    /** Returns the error message to use in case of check failure. */
    abstract LocalizableMessage getErrorMessageForCheck(String backupID);

    /** Check that generated hash is equal to the provided hash. */
    final void check(byte[] hash, String backupID) throws DirectoryException
    {
      byte[] bytes = generateBytes();
      if (bytes != null && !Arrays.equals(bytes, hash))
      {
        LocalizableMessage message = getErrorMessageForCheck(backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
      }
    }

    /** Wraps an output stream in a cipher output stream if encryption is required. */
    final OutputStream encryptOutput(OutputStream output) throws DirectoryException
    {
      if (!shouldEncrypt())
      {
        return output;
      }
      try
      {
        return cryptoManager.getCipherOutputStream(output);
      }
      catch (CryptoManagerException e)
      {
        logger.traceException(e);
        StaticUtils.close(output);
        LocalizableMessage message = ERR_BACKUP_CANNOT_GET_CIPHER.get(stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
      }
    }

    /** Wraps an input stream in a cipher input stream if encryption is required. */
    final InputStream encryptInput(InputStream inputStream) throws DirectoryException
    {
      if (!shouldEncrypt)
      {
        return inputStream;
      }

      try
      {
        return cryptoManager.getCipherInputStream(inputStream);
      }
      catch (CryptoManagerException e)
      {
        logger.traceException(e);
        StaticUtils.close(inputStream);
        LocalizableMessage message = ERR_BACKUP_CANNOT_GET_CIPHER.get(stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
      }
    }
  }

  /** Represents the cryptographic engine with no hash used for a backup. */
  private static final class NoHashCryptoEngine extends CryptoEngine
  {
    NoHashCryptoEngine(boolean shouldEncrypt)
    {
      super(shouldEncrypt);
    }

    @Override
    void updateHashWith(String s)
    {
      // nothing to do
    }

    @Override
    void updateHashWith(byte[] buffer, int offset, int len)
    {
      // nothing to do
    }

    @Override
    byte[] generateBytes()
    {
      return null;
    }

    @Override
    LocalizableMessage getErrorMessageForCheck(String backupID)
    {
      // check never fails because bytes are always null
      return null;
    }
  }

  /** Represents the cryptographic engine with signed hash. */
  private static final class MacCryptoEngine extends CryptoEngine
  {
    private Mac mac;

    /** Constructor for backup creation. */
    private MacCryptoEngine(BackupConfig backupConfig, NewBackupParams backupParams) throws DirectoryException
    {
      super(backupConfig.encryptData());

      String macKeyID = null;
      try
      {
        macKeyID = cryptoManager.getMacEngineKeyEntryID();
        backupParams.putProperty(BACKUP_PROPERTY_MAC_KEY_ID, macKeyID);
      }
      catch (CryptoManagerException e)
      {
        LocalizableMessage message = ERR_BACKUP_CANNOT_GET_MAC_KEY_ID.get(backupParams.backupID,
            stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
      }
      retrieveMacEngine(macKeyID);
    }

    /** Constructor for backup restore. */
    private MacCryptoEngine(BackupInfo backupInfo) throws DirectoryException
    {
      super(backupInfo.isEncrypted());
      Map<String, String> backupProperties = backupInfo.getBackupProperties();
      String macKeyID = backupProperties.get(BACKUP_PROPERTY_MAC_KEY_ID);
      retrieveMacEngine(macKeyID);
    }

    private void retrieveMacEngine(String macKeyID) throws DirectoryException
    {
      try
      {
        mac = cryptoManager.getMacEngine(macKeyID);
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_BACKUP_CANNOT_GET_MAC.get(macKeyID, stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
      }
    }

    @Override
    void updateHashWith(String s)
    {
      mac.update(getBytes(s));
    }

    @Override
    void updateHashWith(byte[] buffer, int offset, int len)
    {
      mac.update(buffer, offset, len);
    }

    @Override
    byte[] generateBytes()
    {
      return mac.doFinal();
    }

    @Override
    boolean hasSignedHash()
    {
      return true;
    }

    @Override
    LocalizableMessage getErrorMessageForCheck(String backupID)
    {
      return ERR_BACKUP_SIGNED_HASH_ERROR.get(backupID);
    }

    @Override
    public String toString()
    {
      return "MacCryptoEngine [mac=" + mac + "]";
    }
  }

  /** Represents the cryptographic engine with unsigned hash used for a backup. */
  private static final class DigestCryptoEngine extends CryptoEngine
  {
    private final MessageDigest digest;

    /** Constructor for backup creation. */
    private DigestCryptoEngine(BackupConfig backupConfig, NewBackupParams backupParams) throws DirectoryException
    {
      super(backupConfig.encryptData());
      String digestAlgorithm = cryptoManager.getPreferredMessageDigestAlgorithm();
      backupParams.putProperty(BACKUP_PROPERTY_DIGEST_ALGORITHM, digestAlgorithm);
      digest = retrieveMessageDigest(digestAlgorithm);
    }

    /** Constructor for backup restore. */
    private DigestCryptoEngine(BackupInfo backupInfo) throws DirectoryException
    {
      super(backupInfo.isEncrypted());
      Map<String, String> backupProperties = backupInfo.getBackupProperties();
      String digestAlgorithm = backupProperties.get(BACKUP_PROPERTY_DIGEST_ALGORITHM);
      digest = retrieveMessageDigest(digestAlgorithm);
    }

    private MessageDigest retrieveMessageDigest(String digestAlgorithm) throws DirectoryException
    {
      try
      {
        return cryptoManager.getMessageDigest(digestAlgorithm);
      }
      catch (Exception e)
      {
        LocalizableMessage message =
            ERR_BACKUP_CANNOT_GET_DIGEST.get(digestAlgorithm, stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
      }
    }

    @Override
    public void updateHashWith(String s)
    {
      digest.update(getBytes(s));
    }

    @Override
    public void updateHashWith(byte[] buffer, int offset, int len)
    {
      digest.update(buffer, offset, len);
    }

    @Override
    public byte[] generateBytes()
    {
      return digest.digest();
    }

    @Override
    LocalizableMessage getErrorMessageForCheck(String backupID)
    {
      return ERR_BACKUP_UNSIGNED_HASH_ERROR.get(backupID);
    }

    @Override
    public String toString()
    {
      return "DigestCryptoEngine [digest=" + digest + "]";
    }
  }

  /** Contains all parameters for creation of a new backup. */
  private static final class NewBackupParams
  {
    final String backupID;
    final BackupDirectory backupDir;
    final HashMap<String,String> backupProperties;

    final boolean shouldCompress;

    final boolean isIncremental;
    final String incrementalBaseID;
    final BackupInfo baseBackupInfo;

    NewBackupParams(BackupConfig backupConfig) throws DirectoryException
    {
      backupID = backupConfig.getBackupID();
      backupDir = backupConfig.getBackupDirectory();
      backupProperties = new HashMap<>();
      shouldCompress = backupConfig.compressData();

      incrementalBaseID = retrieveIncrementalBaseID(backupConfig);
      isIncremental = incrementalBaseID != null;
      baseBackupInfo = isIncremental ? getBackupInfo(backupDir, incrementalBaseID) : null;
    }

    private String retrieveIncrementalBaseID(BackupConfig backupConfig)
    {
      String id = null;
      if (backupConfig.isIncremental())
      {
        if (backupConfig.getIncrementalBaseID() == null && backupDir.getLatestBackup() != null)
        {
          // The default is to use the latest backup as base.
          id = backupDir.getLatestBackup().getBackupID();
        }
        else
        {
          id = backupConfig.getIncrementalBaseID();
        }

        if (id == null)
        {
          // No incremental backup ID: log a message informing that a backup
          // could not be found and that a normal backup will be done.
          logger.warn(WARN_BACKUPDB_INCREMENTAL_NOT_FOUND_DOING_NORMAL, backupDir.getPath());
        }
      }
      return id;
    }

    void putProperty(String name, String value) {
      backupProperties.put(name,  value);
    }

    @Override
    public String toString()
    {
      return "BackupCreationParams [backupID=" + backupID + ", backupDir=" + backupDir.getPath() + "]";
    }
  }

  /** Represents a new backup archive. */
  private static final class NewBackupArchive {
    private final String archiveFilename;

    private String latestFileName;
    private long latestFileSize;

    private final HashSet<String> dependencies;

    private final String backendID;
    private final NewBackupParams newBackupParams;
    private final CryptoEngine cryptoEngine;

    NewBackupArchive(String backendID, NewBackupParams backupParams, CryptoEngine crypt)
    {
      this.backendID = backendID;
      this.newBackupParams = backupParams;
      this.cryptoEngine = crypt;
      dependencies = new HashSet<>();
      if (backupParams.isIncremental)
      {
        Map<String, String> properties = backupParams.baseBackupInfo.getBackupProperties();
        latestFileName = properties.get(PROPERTY_LAST_LOGFILE_NAME);
        latestFileSize = Long.parseLong(properties.get(PROPERTY_LAST_LOGFILE_SIZE));
      }
      archiveFilename = BACKUP_BASE_FILENAME + backendID + "-" +  backupParams.backupID;
    }

    String getArchiveFilename()
    {
      return archiveFilename;
    }

    String getBackendID()
    {
      return backendID;
    }

    String getBackupID()
    {
      return newBackupParams.backupID;
    }

    String getBackupPath() {
      return newBackupParams.backupDir.getPath();
    }

    void addBaseBackupAsDependency() {
      dependencies.add(newBackupParams.baseBackupInfo.getBackupID());
    }

    void updateBackupDirectory() throws DirectoryException
    {
      BackupInfo backupInfo = createDescriptorForBackup();
      try
      {
        newBackupParams.backupDir.addBackup(backupInfo);
        newBackupParams.backupDir.writeBackupDirectoryDescriptor();
      }
      catch (Exception e)
      {
        logger.traceException(e);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
            ERR_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR.get(
                newBackupParams.backupDir.getDescriptorPath(), stackTraceToSingleLineString(e)),
            e);
      }
    }

    /** Create a descriptor for the backup. */
    private BackupInfo createDescriptorForBackup()
    {
      byte[] bytes = cryptoEngine.generateBytes();
      byte[] digestBytes = cryptoEngine.hasSignedHash() ? null : bytes;
      byte[] macBytes = cryptoEngine.hasSignedHash() ? bytes : null;
      newBackupParams.putProperty(PROPERTY_LAST_LOGFILE_NAME, latestFileName);
      newBackupParams.putProperty(PROPERTY_LAST_LOGFILE_SIZE, String.valueOf(latestFileSize));
      return new BackupInfo(
          newBackupParams.backupDir, newBackupParams.backupID, new Date(), newBackupParams.isIncremental,
          newBackupParams.shouldCompress, cryptoEngine.shouldEncrypt(), digestBytes, macBytes,
          dependencies, newBackupParams.backupProperties);
    }

    @Override
    public String toString()
    {
      return "NewArchive [archive file=" + archiveFilename + ", latestFileName=" + latestFileName
          + ", backendID=" + backendID + "]";
    }
  }

  /** Represents an existing backup archive. */
  private static final class ExistingBackupArchive {
    private final String backupID;
    private final BackupDirectory backupDir;
    private final BackupInfo backupInfo;
    private final CryptoEngine cryptoEngine;
    private final File archiveFile;

    ExistingBackupArchive(String backupID, BackupDirectory backupDir) throws DirectoryException
    {
      this.backupID = backupID;
      this.backupDir = backupDir;
      this.backupInfo = BackupManager.getBackupInfo(backupDir, backupID);
      this.cryptoEngine = CryptoEngine.forRestore(backupInfo);
      this.archiveFile = BackupManager.retrieveArchiveFile(backupInfo, backupDir.getPath());
    }

    File getArchiveFile()
    {
      return archiveFile;
    }

    BackupInfo getBackupInfo() {
      return backupInfo;
    }

    CryptoEngine getCryptoEngine()
    {
      return cryptoEngine;
    }

    /**
     * Obtains a list of the dependencies of this backup in order from
     * the oldest (the full backup), to the most recent.
     *
     * @return A list of dependent backups.
     * @throws DirectoryException If a Directory Server error occurs.
     */
    List<BackupInfo> getBackupDependencies() throws DirectoryException
    {
      List<BackupInfo> dependencies = new ArrayList<>();
      BackupInfo currentBackupInfo = backupInfo;
      while (currentBackupInfo != null && !currentBackupInfo.getDependencies().isEmpty())
      {
        String backupID = currentBackupInfo.getDependencies().iterator().next();
        currentBackupInfo = backupDir.getBackupInfo(backupID);
        if (currentBackupInfo != null)
        {
          dependencies.add(currentBackupInfo);
        }
      }
      Collections.reverse(dependencies);
      return dependencies;
    }

    boolean hasDependencies()
    {
      return !backupInfo.getDependencies().isEmpty();
    }

    /** Removes the archive from file system. */
    boolean removeArchive() throws DirectoryException
    {
      try
      {
        backupDir.removeBackup(backupID);
        backupDir.writeBackupDirectoryDescriptor();
      }
      catch (ConfigException e)
      {
        logger.traceException(e);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), e.getMessageObject());
      }
      catch (Exception e)
      {
        logger.traceException(e);
        LocalizableMessage message = ERR_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR.get(
            backupDir.getDescriptorPath(), stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
      }

      return archiveFile.delete();
    }
  }

  /** Represents a writer of a backup archive. */
  private static final class BackupArchiveWriter implements Closeable {
    private final ZipOutputStream zipOutputStream;
    private final NewBackupArchive archive;
    private final CryptoEngine cryptoEngine;

    BackupArchiveWriter(NewBackupArchive archive) throws DirectoryException
    {
      this.archive = archive;
      this.cryptoEngine = archive.cryptoEngine;
      this.zipOutputStream = open(archive.getBackupPath(), archive.getArchiveFilename());
    }

    @Override
    public void close() throws IOException
    {
      StaticUtils.close(zipOutputStream);
    }

    /**
     * Writes the provided file to a new entry in the archive.
     *
     * @param file
     *          The file to be written.
     * @param cryptoMethod
     *          The cryptographic method for the written data.
     * @param backupConfig
     *          The configuration, used to know if operation is cancelled.
     *
     * @return The number of bytes written from the file.
     * @throws FileNotFoundException If the file to be archived does not exist.
     * @throws IOException If an I/O error occurs while archiving the file.
     */
    long writeFile(Path file, String relativePath, CryptoEngine cryptoMethod, BackupConfig backupConfig)
         throws IOException, FileNotFoundException
    {
      ZipEntry zipEntry = new ZipEntry(relativePath);
      zipOutputStream.putNextEntry(zipEntry);

      cryptoMethod.updateHashWith(relativePath);

      long totalBytesRead = 0;
      try (InputStream inputStream = new FileInputStream(file.toFile())) {
        byte[] buffer = new byte[8192];
        int bytesRead = inputStream.read(buffer);
        while (bytesRead > 0 && !backupConfig.isCancelled())
        {
          cryptoMethod.updateHashWith(buffer, 0, bytesRead);
          zipOutputStream.write(buffer, 0, bytesRead);
          totalBytesRead += bytesRead;
          bytesRead = inputStream.read(buffer);
        }
      }

      zipOutputStream.closeEntry();
      logger.info(NOTE_BACKUP_ARCHIVED_FILE, zipEntry.getName());
      return totalBytesRead;
    }

    /**
     * Write a list of strings to an entry in the archive.
     *
     * @param stringList
     *          A list of strings to be written.  The strings must not
     *          contain newlines.
     * @param fileName
     *          The name of the zip entry to be written.
     * @param cryptoMethod
     *          The cryptographic method for the written data.
     * @throws IOException
     *          If an I/O error occurs while writing the archive entry.
     */
    void writeStrings(List<String> stringList, String fileName, CryptoEngine cryptoMethod)
         throws IOException
    {
      ZipEntry zipEntry = new ZipEntry(fileName);
      zipOutputStream.putNextEntry(zipEntry);

      cryptoMethod.updateHashWith(fileName);

      Writer writer = new OutputStreamWriter(zipOutputStream);
      for (String s : stringList)
      {
        cryptoMethod.updateHashWith(s);
        writer.write(s);
        writer.write(EOL);
      }
      writer.flush();
      zipOutputStream.closeEntry();
    }

    /** Writes a empty placeholder entry into the archive. */
    void writeEmptyPlaceHolder() throws DirectoryException
    {
      try
      {
        ZipEntry emptyPlaceholder = new ZipEntry(ZIPENTRY_EMPTY_PLACEHOLDER);
        zipOutputStream.putNextEntry(emptyPlaceholder);
      }
      catch (IOException e)
      {
        logger.traceException(e);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
            ERR_BACKUP_CANNOT_WRITE_ARCHIVE_FILE.get(ZIPENTRY_EMPTY_PLACEHOLDER, archive.getBackupID(),
                stackTraceToSingleLineString(e)),
            e);
      }
    }

    /**
     * Writes the files that are unchanged from the base backup (for an
     * incremental backup only).
     * <p>
     * The unchanged files names are listed in the "unchanged.txt" file, which
     * is put in the archive.
     */
    void writeUnchangedFiles(Path rootDirectory, ListIterator<Path> files, BackupConfig backupConfig)
        throws DirectoryException
    {
      List<String> unchangedFilenames = new ArrayList<>();
      while (files.hasNext() && !backupConfig.isCancelled())
      {
        Path file = files.next();
        String relativePath = rootDirectory.relativize(file).toString();
        int cmp = relativePath.compareTo(archive.latestFileName);
        if (cmp > 0 || (cmp == 0 && file.toFile().length() != archive.latestFileSize))
        {
          files.previous();
          break;
        }
        logger.info(NOTE_BACKUP_FILE_UNCHANGED, relativePath);
        unchangedFilenames.add(relativePath);
      }

      if (!unchangedFilenames.isEmpty())
      {
        writeUnchangedFilenames(unchangedFilenames);
      }
    }

    /** Writes the list of unchanged files names in a file as new entry in the archive. */
    private void writeUnchangedFilenames(List<String> unchangedList) throws DirectoryException
    {
      String zipEntryName = ZIPENTRY_UNCHANGED_LOGFILES;
      try
      {
        writeStrings(unchangedList, zipEntryName, archive.cryptoEngine);
      }
      catch (IOException e)
      {
        logger.traceException(e);
        throw new DirectoryException(
             DirectoryServer.getServerErrorResultCode(),
             ERR_BACKUP_CANNOT_WRITE_ARCHIVE_FILE.get(zipEntryName, archive.getBackupID(),
                 stackTraceToSingleLineString(e)), e);
      }
      archive.addBaseBackupAsDependency();
    }

    /** Writes the new files in the archive. */
    void writeChangedFiles(Path rootDirectory, ListIterator<Path> files, BackupConfig backupConfig)
        throws DirectoryException
    {
        while (files.hasNext() && !backupConfig.isCancelled())
        {
          Path file = files.next();
          String relativePath = rootDirectory.relativize(file).toString();
          try
          {
            archive.latestFileSize = writeFile(file, relativePath, archive.cryptoEngine, backupConfig);
            archive.latestFileName = relativePath;
          }
          catch (FileNotFoundException e)
          {
            // The file may have been deleted by a cleaner (i.e. for JE storage) since we started.
            // The backupable entity is responsible for handling the changes through the files list iterator
            logger.traceException(e);
          }
          catch (IOException e)
          {
            logger.traceException(e);
            throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                 ERR_BACKUP_CANNOT_WRITE_ARCHIVE_FILE.get(relativePath, archive.getBackupID(),
                     stackTraceToSingleLineString(e)), e);
          }
        }
    }

    private ZipOutputStream open(String backupPath, String archiveFilename) throws DirectoryException
    {
      OutputStream output = openStream(backupPath, archiveFilename);
      output = cryptoEngine.encryptOutput(output);
      return openZipStream(output);
    }

    private OutputStream openStream(String backupPath, String archiveFilename) throws DirectoryException {
      OutputStream output = null;
      try
      {
        File archiveFile = new File(backupPath, archiveFilename);
        int i = 1;
        while (archiveFile.exists())
        {
          archiveFile = new File(backupPath, archiveFilename  + "." + i);
          i++;
        }
        output = new FileOutputStream(archiveFile, false);
        archive.newBackupParams.putProperty(BACKUP_PROPERTY_ARCHIVE_FILENAME, archiveFilename);
        return output;
      }
      catch (Exception e)
      {
        logger.traceException(e);
        StaticUtils.close(output);
        LocalizableMessage message = ERR_BACKUP_CANNOT_CREATE_ARCHIVE_FILE.
            get(archiveFilename, backupPath, archive.getBackupID(), stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
      }
    }

    /** Wraps the file output stream in a zip output stream. */
    private ZipOutputStream openZipStream(OutputStream outputStream)
    {
      ZipOutputStream zipStream = new ZipOutputStream(outputStream);

      zipStream.setComment(ERR_BACKUP_ZIP_COMMENT.get(DynamicConstants.PRODUCT_NAME, archive.getBackupID())
          .toString());

      if (archive.newBackupParams.shouldCompress)
      {
        zipStream.setLevel(Deflater.DEFAULT_COMPRESSION);
      }
      else
      {
        zipStream.setLevel(Deflater.NO_COMPRESSION);
      }
      return zipStream;
    }

    @Override
    public String toString()
    {
      return "BackupArchiveWriter [archive file=" + archive.getArchiveFilename() + ", backendId="
          + archive.getBackendID() + "]";
    }
  }

  /** Represents a reader of a backup archive. */
  private static final class BackupArchiveReader {
    private final CryptoEngine cryptoEngine;
    private final File archiveFile;
    private final String identifier;
    private final BackupInfo backupInfo;

    BackupArchiveReader(String identifier, ExistingBackupArchive archive)
    {
      this.identifier = identifier;
      this.backupInfo = archive.getBackupInfo();
      this.archiveFile = archive.getArchiveFile();
      this.cryptoEngine = archive.getCryptoEngine();
    }

    BackupArchiveReader(String identifier, BackupInfo backupInfo, String backupDirectoryPath) throws DirectoryException
    {
      this.identifier = identifier;
      this.backupInfo = backupInfo;
      this.archiveFile = BackupManager.retrieveArchiveFile(backupInfo, backupDirectoryPath);
      this.cryptoEngine = CryptoEngine.forRestore(backupInfo);
    }

    /**
     * Obtains the set of files in a backup that are unchanged from its
     * dependent backup or backups.
     * <p>
     * The file set is stored as as the first entry in the archive file.
     *
     * @return The set of files that are listed in "unchanged.txt" file
     *         of the archive.
     * @throws DirectoryException
     *          If an error occurs.
     */
    Set<String> readUnchangedDependentFiles() throws DirectoryException
    {
      Set<String> hashSet = new HashSet<>();
      try (ZipInputStream zipStream = openZipStream())
      {

        // Iterate through the entries in the zip file.
        ZipEntry zipEntry = zipStream.getNextEntry();
        while (zipEntry != null)
        {
          // We are looking for the entry containing the list of unchanged files.
          if (ZIPENTRY_UNCHANGED_LOGFILES.equals(zipEntry.getName()))
          {
            hashSet.addAll(readAllLines(zipStream));
            break;
          }
          zipEntry = zipStream.getNextEntry();
        }
        return hashSet;
      }
      catch (IOException e)
      {
        logger.traceException(e);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), ERR_BACKUP_CANNOT_RESTORE.get(
            identifier, stackTraceToSingleLineString(e)), e);
      }
    }

    /**
     * Restore the provided list of files from the provided restore directory.
     * @param restoreDir
     *          The target directory for restored files.
     * @param filesToRestore
     *          The set of files to restore. If empty, all files in the archive
     *          are restored.
     * @param restoreConfig
     *          The restore configuration, used to check for cancellation of
     *          this restore operation.
     * @throws DirectoryException
     *          If an error occurs.
     */
    void restoreArchive(Path restoreDir, Set<String> filesToRestore, RestoreConfig restoreConfig, Backupable backupable)
        throws DirectoryException
    {
      try
      {
        restoreArchive0(restoreDir, filesToRestore, restoreConfig);
      }
      catch (IOException e)
      {
        logger.traceException(e);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
            ERR_BACKUP_CANNOT_RESTORE.get(identifier, stackTraceToSingleLineString(e)), e);
      }

      // check the hash
      byte[] hash = backupInfo.getUnsignedHash() != null ? backupInfo.getUnsignedHash() : backupInfo.getSignedHash();
      cryptoEngine.check(hash, backupInfo.getBackupID());
    }

    private void restoreArchive0(Path restoreDir, Set<String> filesToRestore, RestoreConfig restoreConfig)
        throws DirectoryException, IOException
    {
      try (ZipInputStream zipStream = openZipStream())
      {
          ZipEntry zipEntry = zipStream.getNextEntry();
          while (zipEntry != null && !restoreConfig.isCancelled())
          {
            String zipEntryName = zipEntry.getName();

            Pair<Boolean, ZipEntry> result = handleSpecialEntries(zipStream, zipEntryName);
            if (result.getFirst()) {
              zipEntry = result.getSecond();
              continue;
            }

            boolean mustRestoreOnDisk = !restoreConfig.verifyOnly()
                && (filesToRestore.isEmpty() || filesToRestore.contains(zipEntryName));

            if (mustRestoreOnDisk)
            {
              restoreZipEntry(zipEntryName, zipStream, restoreDir, restoreConfig);
            }
            else
            {
              restoreZipEntryVirtual(zipEntryName, zipStream, restoreConfig);
            }

            zipEntry = zipStream.getNextEntry();
          }
      }
    }

    /**
     * Handle any special entry in the archive.
     *
     * @return the pair (true, zipEntry) if next entry was read, (false, null) otherwise
     */
    private Pair<Boolean, ZipEntry> handleSpecialEntries(ZipInputStream zipStream, String zipEntryName)
          throws IOException
    {
      if (ZIPENTRY_EMPTY_PLACEHOLDER.equals(zipEntryName))
      {
        // the backup contains no files
        return Pair.of(true, zipStream.getNextEntry());
      }

      if (ZIPENTRY_UNCHANGED_LOGFILES.equals(zipEntryName))
      {
        // This entry is treated specially. It is never restored,
        // and its hash is computed on the strings, not the bytes.
        cryptoEngine.updateHashWith(zipEntryName);
        List<String> lines = readAllLines(zipStream);
        for (String line : lines)
        {
          cryptoEngine.updateHashWith(line);
        }
        return Pair.of(true, zipStream.getNextEntry());
      }
      return Pair.of(false, null);
    }

    /** Restores a zip entry virtually (no actual write on disk). */
    private void restoreZipEntryVirtual(String zipEntryName, ZipInputStream zipStream, RestoreConfig restoreConfig)
            throws FileNotFoundException, IOException
    {
      if (restoreConfig.verifyOnly())
      {
        logger.info(NOTE_BACKUP_VERIFY_FILE, zipEntryName);
      }
      cryptoEngine.updateHashWith(zipEntryName);
      restoreFile(zipStream, null, restoreConfig);
    }

    /** Restores a zip entry with actual write on disk. */
    private void restoreZipEntry(String zipEntryName, ZipInputStream zipStream, Path restoreDir,
        RestoreConfig restoreConfig) throws IOException, DirectoryException
    {
      Path fileToRestore = restoreDir.resolve(zipEntryName);
      ensureFileCanBeRestored(fileToRestore);

      try (OutputStream outputStream = new FileOutputStream(fileToRestore.toFile()))
      {
        cryptoEngine.updateHashWith(zipEntryName);
        long totalBytesRead = restoreFile(zipStream, outputStream, restoreConfig);
        logger.info(NOTE_BACKUP_RESTORED_FILE, zipEntryName, totalBytesRead);
      }
    }

    private void ensureFileCanBeRestored(Path fileToRestore) throws DirectoryException
    {
      Path parent = fileToRestore.getParent();
      if (!Files.exists(parent))
      {
        try
        {
          Files.createDirectories(parent);
        }
        catch (IOException e)
        {
          throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              ERR_BACKUP_CANNOT_CREATE_DIRECTORY_TO_RESTORE_FILE.get(fileToRestore, identifier));
        }
      }
    }

    /**
     * Restores the file provided by the zip input stream.
     * <p>
     * The restore can be virtual: if the outputStream is {@code null}, the file
     * is not actually restored on disk.
     */
    private long restoreFile(ZipInputStream zipInputStream, OutputStream outputStream, RestoreConfig restoreConfig)
        throws IOException
    {
      long totalBytesRead = 0;
      byte[] buffer = new byte[8192];
      int bytesRead = zipInputStream.read(buffer);
      while (bytesRead > 0 && !restoreConfig.isCancelled())
      {
        totalBytesRead += bytesRead;

        cryptoEngine.updateHashWith(buffer, 0, bytesRead);

        if (outputStream != null)
        {
          outputStream.write(buffer, 0, bytesRead);
        }

        bytesRead = zipInputStream.read(buffer);
      }
      return totalBytesRead;
    }

    private InputStream openStream() throws DirectoryException
    {
      try
      {
        return new FileInputStream(archiveFile);
      }
      catch (FileNotFoundException e)
      {
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
            ERR_BACKUP_CANNOT_RESTORE.get(identifier, stackTraceToSingleLineString(e)), e);
      }
    }

    private ZipInputStream openZipStream() throws DirectoryException
    {
      InputStream inputStream = openStream();
      inputStream = cryptoEngine.encryptInput(inputStream);
      return new ZipInputStream(inputStream);
    }

    private List<String> readAllLines(ZipInputStream zipStream) throws IOException
    {
      final ArrayList<String> results = new ArrayList<>();
      String line;
      BufferedReader reader = new BufferedReader(new InputStreamReader(zipStream));
      while ((line = reader.readLine()) != null)
      {
        results.add(line);
      }
      return results;
    }
  }

  /**
   * Creates a backup of the provided backupable entity.
   * <p>
   * The backup is stored in a single zip file in the backup directory.
   * <p>
   * If the backup is incremental, then the first entry in the zip is a text
   * file containing a list of all the log files that are unchanged since the
   * previous backup. The remaining zip entries are the log files themselves,
   * which, for an incremental, only include those files that have changed.
   *
   * @param backupable
   *          The underlying entity (storage, backend) to be backed up.
   * @param backupConfig
   *          The configuration to use when performing the backup.
   * @throws DirectoryException
   *           If a Directory Server error occurs.
   */
  public void createBackup(final Backupable backupable, final BackupConfig backupConfig) throws DirectoryException
  {
    final NewBackupParams backupParams = new NewBackupParams(backupConfig);
    final CryptoEngine cryptoEngine = CryptoEngine.forCreation(backupConfig, backupParams);
    final NewBackupArchive newArchive = new NewBackupArchive(backendID, backupParams, cryptoEngine);

    final ListIterator<Path> files = backupable.getFilesToBackup();
    final Path rootDirectory = backupable.getDirectory().toPath();
    try (BackupArchiveWriter archiveWriter = new BackupArchiveWriter(newArchive))
    {
      if (files.hasNext())
      {
        if (backupParams.isIncremental) {
          archiveWriter.writeUnchangedFiles(rootDirectory, files, backupConfig);
        }
        archiveWriter.writeChangedFiles(rootDirectory, files, backupConfig);
      }
      else {
        archiveWriter.writeEmptyPlaceHolder();
      }
    }
    catch (IOException e)
    {
      logger.traceException(e);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), ERR_BACKUP_CANNOT_CLOSE_ZIP_STREAM.get(
          newArchive.getArchiveFilename(), backupParams.backupDir.getPath(), stackTraceToSingleLineString(e)), e);
    }

    newArchive.updateBackupDirectory();

    if (backupConfig.isCancelled())
    {
      // Remove the backup since it may be incomplete
      removeBackup(backupParams.backupDir, backupParams.backupID);
    }
  }

  /**
   * Restores a backupable entity from its backup, or verify the backup.
   *
   * @param backupable
   *          The underlying entity (storage, backend) to be backed up.
   * @param restoreConfig
   *          The configuration to use when performing the restore.
   * @throws DirectoryException
   *           If a Directory Server error occurs.
   */
  public void restoreBackup(Backupable backupable, RestoreConfig restoreConfig) throws DirectoryException
  {
    Path saveDirectory = null;
    if (!restoreConfig.verifyOnly())
    {
      saveDirectory = backupable.beforeRestore();
    }

    final String backupID = restoreConfig.getBackupID();
    final ExistingBackupArchive existingArchive =
        new ExistingBackupArchive(backupID, restoreConfig.getBackupDirectory());
    final Path restoreDirectory = getRestoreDirectory(backupable, backupID);

    if (existingArchive.hasDependencies())
    {
      final BackupArchiveReader zipArchiveReader = new BackupArchiveReader(backupID, existingArchive);
      final Set<String> unchangedFilesToRestore = zipArchiveReader.readUnchangedDependentFiles();
      final List<BackupInfo> dependencies = existingArchive.getBackupDependencies();
      for (BackupInfo dependencyBackupInfo : dependencies)
      {
        restoreArchive(restoreDirectory, unchangedFilesToRestore, restoreConfig, backupable, dependencyBackupInfo);
      }
    }

    // Restore the final archive file.
    Set<String> filesToRestore = emptySet();
    restoreArchive(restoreDirectory, filesToRestore, restoreConfig, backupable, existingArchive.getBackupInfo());

    if (!restoreConfig.verifyOnly())
    {
      backupable.afterRestore(restoreDirectory, saveDirectory);
    }
  }

  /**
   * Removes the specified backup if it is possible to do so.
   *
   * @param  backupDir  The backup directory structure with which the
   *                    specified backup is associated.
   * @param  backupID   The backup ID for the backup to be removed.
   *
   * @throws  DirectoryException  If it is not possible to remove the specified
   *                              backup for some reason (e.g., no such backup
   *                              exists or there are other backups that are
   *                              dependent upon it).
   */
  public void removeBackup(BackupDirectory backupDir, String backupID) throws DirectoryException
  {
    ExistingBackupArchive archive = new ExistingBackupArchive(backupID, backupDir);
    archive.removeArchive();
  }

  private Path getRestoreDirectory(Backupable backupable, String backupID)
  {
    File restoreDirectory = backupable.getDirectory();
    if (!backupable.isDirectRestore())
    {
      restoreDirectory = new File(restoreDirectory.getAbsoluteFile() + "-restore-" + backupID);
    }
    return restoreDirectory.toPath();
  }

  private void closeArchiveWriter(BackupArchiveWriter archiveWriter, String backupFile, String backupPath)
      throws DirectoryException
  {
    if (archiveWriter != null)
    {
      try
      {
        archiveWriter.close();
      }
      catch (Exception e)
      {
        logger.traceException(e);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
            ERR_BACKUP_CANNOT_CLOSE_ZIP_STREAM.get(backupFile, backupPath, stackTraceToSingleLineString(e)), e);
      }
    }
  }

  /**
   * Restores the content of an archive file.
   * <p>
   * If set of files is not empty, only the specified files are restored.
   * If set of files is empty, all files are restored.
   *
   * If the archive is being restored as a dependency, then only files in the
   * specified set are restored, and the restored files are removed from the
   * set. Otherwise all files from the archive are restored, and files that are
   * to be found in dependencies are added to the set.
   * @param restoreDir
   *          The directory in which files are to be restored.
   * @param filesToRestore
   *          The set of files to restore. If empty, then all files are
   *          restored.
   * @param restoreConfig
   *          The restore configuration.
   * @param backupInfo
   *          The backup containing the files to be restored.
   *
   * @throws DirectoryException
   *           If a Directory Server error occurs.
   * @throws IOException
   *           If an I/O exception occurs during the restore.
   */
  private void restoreArchive(Path restoreDir,
                              Set<String> filesToRestore,
                              RestoreConfig restoreConfig,
                              Backupable backupable,
                              BackupInfo backupInfo) throws DirectoryException
  {
    String backupID = backupInfo.getBackupID();
    String backupDirectoryPath = restoreConfig.getBackupDirectory().getPath();

    BackupArchiveReader zipArchiveReader = new BackupArchiveReader(backupID, backupInfo, backupDirectoryPath);
    zipArchiveReader.restoreArchive(restoreDir, filesToRestore, restoreConfig, backupable);
  }

  /** Retrieves the full path of the archive file. */
  private static File retrieveArchiveFile(BackupInfo backupInfo, String backupDirectoryPath)
  {
    Map<String,String> backupProperties = backupInfo.getBackupProperties();
    String archiveFilename = backupProperties.get(BACKUP_PROPERTY_ARCHIVE_FILENAME);
    return new File(backupDirectoryPath, archiveFilename);
  }

  /**
   * Get the information for a given backup ID from the backup directory.
   *
   * @param backupDir The backup directory.
   * @param backupID The backup ID.
   * @return The backup information, never null.
   * @throws DirectoryException If the backup information cannot be found.
   */
  private static BackupInfo getBackupInfo(BackupDirectory backupDir, String backupID) throws DirectoryException
  {
    BackupInfo backupInfo = backupDir.getBackupInfo(backupID);
    if (backupInfo == null)
    {
      LocalizableMessage message = ERR_BACKUP_MISSING_BACKUPID.get(backupID, backupDir.getPath());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
    }
    return backupInfo;
  }

  /**
   * Helper method to build a list of files to backup, in the simple case where all files are located
   * under the provided directory.
   *
   * @param directory
   *            The directory containing files to backup.
   * @param filter
   *            The filter to select files to backup.
   * @param identifier
   *            Identifier of the backed-up entity
   * @return the files to backup, which may be empty but never {@code null}
   * @throws DirectoryException
   *            if an error occurs.
   */
  public static List<Path> getFiles(File directory, FileFilter filter, String identifier)
      throws DirectoryException
  {
    File[] files = null;
    try
    {
      files = directory.listFiles(filter);
    }
    catch (Exception e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_BACKUP_CANNOT_LIST_LOG_FILES.get(directory.getAbsolutePath(), identifier), e);
    }
    if (files == null)
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          ERR_BACKUP_CANNOT_LIST_LOG_FILES.get(directory.getAbsolutePath(), identifier));
    }

    List<Path> paths = new ArrayList<>();
    for (File file : files)
    {
      paths.add(file.toPath());
    }
    return paths;
  }

  /**
   * Helper method to save all current files of the provided backupable entity, using
   * default behavior.
   *
   * @param backupable
   *          The entity to backup.
   * @param identifier
   *            Identifier of the backup
   * @return the directory where all files are saved.
   * @throws DirectoryException
   *           If a problem occurs.
   */
  public static Path saveCurrentFilesToDirectory(Backupable backupable, String identifier) throws DirectoryException
  {
     ListIterator<Path> filesToBackup = backupable.getFilesToBackup();
     File rootDirectory = backupable.getDirectory();
     String saveDirectory = rootDirectory.getAbsolutePath() + ".save";
     BackupManager.saveFilesToDirectory(rootDirectory.toPath(), filesToBackup, saveDirectory, identifier);
     return Paths.get(saveDirectory);
  }

  /**
   * Helper method to move all provided files in a target directory created from
   * provided target base path, keeping relative path information relative to
   * root directory.
   *
   * @param rootDirectory
   *          A directory which is an ancestor of all provided files.
   * @param files
   *          The files to move.
   * @param targetBasePath
   *          Base path of the target directory. Actual directory is built by
   *          adding ".save" and a number, always ensuring that the directory is new.
   * @param identifier
   *            Identifier of the backup
   * @return the actual directory where all files are saved.
   * @throws DirectoryException
   *           If a problem occurs.
   */
  public static Path saveFilesToDirectory(Path rootDirectory, ListIterator<Path> files, String targetBasePath,
      String identifier) throws DirectoryException
  {
    Path targetDirectory = null;
    try
    {
      targetDirectory = createDirectoryWithNumericSuffix(targetBasePath, identifier);
      while (files.hasNext())
      {
        Path file = files.next();
        Path relativeFilePath = rootDirectory.relativize(file);
        Path targetFile = targetDirectory.resolve(relativeFilePath);
        Files.createDirectories(targetFile.getParent());
        Files.move(file, targetFile);
      }
      return targetDirectory;
    }
    catch (IOException e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_BACKUP_CANNOT_SAVE_FILES_BEFORE_RESTORE.get(rootDirectory, targetDirectory, identifier,
              stackTraceToSingleLineString(e)), e);
    }
  }

  /**
   * Creates a new directory based on the provided directory path, by adding a
   * suffix number that is guaranteed to be the highest.
   */
  static Path createDirectoryWithNumericSuffix(final String baseDirectoryPath, String identifier)
      throws DirectoryException
  {
    try
    {
      int number = getHighestSuffixNumberForPath(baseDirectoryPath);
      String path = baseDirectoryPath + (number + 1);
      Path directory = Paths.get(path);
      Files.createDirectories(directory);
      return directory;
    }
    catch (IOException e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_BACKUP_CANNOT_CREATE_SAVE_DIRECTORY.get(baseDirectoryPath, identifier,
          stackTraceToSingleLineString(e)), e);
    }
  }

  /**
   * Returns a number that correspond to the highest suffix number existing for the provided base path.
   * <p>
   * Example: given the following directory structure
   * <pre>
   * +--- someDir
   * |   \--- directory
   * |   \--- directory1
   * |   \--- directory2
   * |   \--- directory10
   * </pre>
   * getHighestSuffixNumberForPath("directory") returns 10.
   *
   * @param basePath
   *            A base path to a file or directory, without any suffix number.
   * @return the highest suffix number, or 0 if no suffix number exists
   * @throws IOException
   *            if an error occurs.
   */
  private static int getHighestSuffixNumberForPath(final String basePath) throws IOException
  {
    final File baseFile = new File(basePath).getCanonicalFile();
    final File[] existingFiles = baseFile.getParentFile().listFiles();
    final Pattern pattern = Pattern.compile(baseFile + "\\d*");
    int highestNumber = 0;
    for (File file : existingFiles)
    {
      final String name = file.getCanonicalPath();
      if (pattern.matcher(name).matches())
      {
        String numberAsString = name.substring(baseFile.getPath().length());
        int number = numberAsString.isEmpty() ? 0 : Integer.valueOf(numberAsString);
        highestNumber = number > highestNumber ? number : highestNumber;
      }
    }
    return highestNumber;
  }
}
