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
package org.opends.server.extensions;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;

import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.JMXMBean;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.tools.LDIFModify;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.admin.Configuration;


/**
 * This class defines a simple configuration handler for the Directory Server
 * that will read the server configuration from an LDIF file.
 */
public class ConfigFileHandler
       extends ConfigHandler
       implements AlertGenerator
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.ConfigFileHandler";



  /**
   * The privilege array containing both the CONFIG_READ and CONFIG_WRITE
   * privileges.
   */
  private static final Privilege[] CONFIG_READ_AND_WRITE =
  {
    Privilege.CONFIG_READ,
    Privilege.CONFIG_WRITE
  };



  // A SHA-1 digest of the last known configuration.  This should only be
  // incorrect if the server configuration file has been manually edited with
  // the server online, which is a bad thing.
  private byte[] configurationDigest;

  // The mapping that holds all of the configuration entries that have been read
  // from the LDIF file.
  private ConcurrentHashMap<DN,ConfigEntry> configEntries;

  // The reference to the configuration root entry.
  private ConfigEntry configRootEntry;

  // The set of base DNs for this config handler backend.
  private DN[] baseDNs;

  // The write lock used to ensure that only one thread can apply a
  // configuration update at any given time.
  private ReentrantLock configLock;

  // The path to the configuration file.
  private String configFile;

  // The instance root directory for the Directory Server.
  private String serverRoot;



  /**
   * Creates a new instance of this config file handler.  No initialization
   * should be performed here, as all of that work should be done in the
   * <CODE>initializeConfigHandler</CODE> method.
   */
  public ConfigFileHandler()
  {
    super();
  }



  /**
   * Bootstraps this configuration handler using the information in the provided
   * configuration file.  Depending on this configuration handler
   * implementation, the provided file may contain either the entire server
   * configuration or information that is needed to access the configuration in
   * some other location or repository.
   *
   * @param  configFile   The path to the file to use to initialize this
   *                      configuration handler.
   * @param  checkSchema  Indicates whether to perform schema checking on the
   *                      configuration data.
   *
   * @throws  InitializationException  If a problem occurs while attempting to
   *                                   initialize this configuration handler.
   */
  public void initializeConfigHandler(String configFile, boolean checkSchema)
         throws InitializationException
  {
    // Initialize the config lock.
    configLock = new ReentrantLock();


    // Make sure that the configuration file exists.
    this.configFile = configFile;
    File f = new File(configFile);

    try
    {
      if (! f.exists())
      {
        int    msgID   = MSGID_CONFIG_FILE_DOES_NOT_EXIST;
        String message = getMessage(msgID, configFile);
        throw new InitializationException(msgID, message);
      }
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }

      throw ie;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_FILE_CANNOT_VERIFY_EXISTENCE;
      String message = getMessage(msgID, configFile, String.valueOf(e));
      throw new InitializationException(msgID, message);
    }


    // Check to see if a configuration archive exists.  If not, then create one.
    // If so, then check whether the current configuration matches the last
    // configuration in the archive.  If it doesn't, then archive it.
    try
    {
      configurationDigest = calculateConfigDigest();
    }
    catch (DirectoryException de)
    {
      throw new InitializationException(de.getMessageID(),
                                        de.getErrorMessage(), de.getCause());
    }

    File archiveDirectory = new File(f.getParent(), CONFIG_ARCHIVE_DIR_NAME);
    if (archiveDirectory.exists())
    {
      try
      {
        byte[] lastDigest = getLastConfigDigest(archiveDirectory);
        if (! Arrays.equals(configurationDigest, lastDigest))
        {
          writeConfigArchive();
        }
      } catch (Exception e) {}
    }
    else
    {
      writeConfigArchive();
    }



    // Fixme -- Should we add a hash or signature check here?


    // See if there is a config changes file.  If there is, then try to apply
    // the changes contained in it.
    File changesFile = new File(f.getParent(), CONFIG_CHANGES_NAME);
    try
    {
      if (changesFile.exists())
      {
        applyChangesFile(f, changesFile);
        configurationDigest = calculateConfigDigest();
        writeConfigArchive();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_UNABLE_TO_APPLY_STARTUP_CHANGES;
      String message = getMessage(msgID, changesFile.getAbsolutePath(),
                                  String.valueOf(e));
      throw new InitializationException(msgID, message, e);
    }


    // We will use the LDIF reader to read the configuration file.  Create an
    // LDIF import configuration to do this and then get the reader.
    LDIFReader reader;
    try
    {
      LDIFImportConfig importConfig = new LDIFImportConfig(configFile);

      // FIXME -- Should we support encryption or compression for the config?

      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_FILE_CANNOT_OPEN_FOR_READ;
      String message = getMessage(msgID, configFile, String.valueOf(e));
      throw new InitializationException(msgID, message, e);
    }


    // Read the first entry from the configuration file.
    Entry entry;
    try
    {
      entry = reader.readEntry(checkSchema);
    }
    catch (LDIFException le)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, le);
      }

      try
      {
        reader.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      int    msgID   = MSGID_CONFIG_FILE_INVALID_LDIF_ENTRY;
      String message = getMessage(msgID, le.getLineNumber(), configFile,
                                  String.valueOf(le));
      throw new InitializationException(msgID, message, le);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      try
      {
        reader.close();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }
      }

      int    msgID   = MSGID_CONFIG_FILE_READ_ERROR;
      String message = getMessage(msgID, configFile, String.valueOf(e));
      throw new InitializationException(msgID, message, e);
    }


    // Make sure that the provide LDIF file is not empty.
    if (entry == null)
    {
      try
      {
        reader.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      int    msgID   = MSGID_CONFIG_FILE_EMPTY;
      String message = getMessage(msgID, configFile);
      throw new InitializationException(msgID, message);
    }


    // Make sure that the DN of this entry is equal to the config root DN.
    try
    {
      DN configRootDN = DN.decode(DN_CONFIG_ROOT);
      if (! entry.getDN().equals(configRootDN))
      {
        int    msgID   = MSGID_CONFIG_FILE_INVALID_BASE_DN;
        String message = getMessage(msgID, configFile, entry.getDN().toString(),
                                    DN_CONFIG_ROOT);
        throw new InitializationException(msgID, message);
      }
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }

      try
      {
        reader.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      throw ie;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      try
      {
        reader.close();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }
      }

      // This should not happen, so we can use a generic error here.
      int    msgID   = MSGID_CONFIG_FILE_GENERIC_ERROR;
      String message = getMessage(msgID, configFile, String.valueOf(e));
      throw new InitializationException(msgID, message, e);
    }


    // Convert the entry to a configuration entry and put it in the config
    // hash.
    configEntries   = new ConcurrentHashMap<DN,ConfigEntry>();
    configRootEntry = new ConfigEntry(entry, null);
    configEntries.put(entry.getDN(), configRootEntry);


    // Iterate through the rest of the configuration file and process the
    // remaining entries.
    while (true)
    {
      // Read the next entry from the configuration.
      try
      {
        entry = reader.readEntry(checkSchema);
      }
      catch (LDIFException le)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, le);
        }

        try
        {
          reader.close();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        int    msgID   = MSGID_CONFIG_FILE_INVALID_LDIF_ENTRY;
        String message = getMessage(msgID, le.getLineNumber(), configFile,
                                    String.valueOf(le));
        throw new InitializationException(msgID, message, le);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        try
        {
          reader.close();
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e2);
          }
        }

        int    msgID   = MSGID_CONFIG_FILE_READ_ERROR;
        String message = getMessage(msgID, configFile, String.valueOf(e));
        throw new InitializationException(msgID, message, e);
      }


      // If the entry is null, then we have reached the end of the configuration
      // file.
      if (entry == null)
      {
        try
        {
          reader.close();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        break;
      }


      // Make sure that the DN of the entry read doesn't already exist.
      DN entryDN = entry.getDN();
      if (configEntries.containsKey(entryDN))
      {
        try
        {
          reader.close();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        int    msgID   = MSGID_CONFIG_FILE_DUPLICATE_ENTRY;
        String message = getMessage(msgID, entryDN.toString(),
                                    reader.getLastEntryLineNumber(),
                                    configFile);
        throw new InitializationException(msgID, message);
      }


      // Make sure that the parent DN of the entry read does exist.
      DN parentDN = entryDN.getParent();
      if (parentDN == null)
      {
        try
        {
          reader.close();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        int    msgID   = MSGID_CONFIG_FILE_UNKNOWN_PARENT;
        String message = getMessage(msgID, entryDN.toString(),
                                    reader.getLastEntryLineNumber(),
                                    configFile);
        throw new InitializationException(msgID, message);
      }

      ConfigEntry parentEntry = configEntries.get(parentDN);
      if (parentEntry == null)
      {
        try
        {
          reader.close();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        int    msgID   = MSGID_CONFIG_FILE_NO_PARENT;
        String message = getMessage(msgID, entryDN.toString(),
                                    reader.getLastEntryLineNumber(),
                                    configFile, parentDN.toString());
        throw new InitializationException(msgID, message);
      }


      // Create the new configuration entry, add it as a child of the provided
      // parent entry, and put it into the entry has.
      try
      {
        ConfigEntry configEntry = new ConfigEntry(entry, parentEntry);
        parentEntry.addChild(configEntry);
        configEntries.put(entryDN, configEntry);
      }
      catch (Exception e)
      {
        // This should not happen.
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        try
        {
          reader.close();
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e2);
          }
        }

        int    msgID   = MSGID_CONFIG_FILE_GENERIC_ERROR;
        String message = getMessage(msgID, configFile, String.valueOf(e));
        throw new InitializationException(msgID, message, e);
      }
    }


    // Determine the appropriate server root for the Directory Server.  First,
    // do this by looking for a Java property.  If that isn't specified, then
    // look for an environment variable, and if all else fails then try to
    // figure it out from the location of the configuration file.
    String rootDirStr = System.getProperty(PROPERTY_SERVER_ROOT);
    if (rootDirStr == null)
    {
      rootDirStr = System.getenv(ENV_VAR_INSTANCE_ROOT);
    }

    if (rootDirStr != null)
    {
      try
      {
        File serverRootFile = new File(rootDirStr);
        serverRoot = serverRootFile.getAbsolutePath();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_CONFIG_CANNOT_DETERMINE_SERVER_ROOT;
        String message = getMessage(msgID, ENV_VAR_INSTANCE_ROOT);
        throw new InitializationException(msgID, message);
      }
    }


    if (serverRoot == null)
    {
      try
      {
        File configDirFile = new File(configFile).getParentFile();
        if ((configDirFile != null) &&
            configDirFile.getName().equals(CONFIG_DIR_NAME))
        {
          serverRoot = configDirFile.getParentFile().getAbsolutePath();
        }

        if (serverRoot == null)
        {
          int    msgID   = MSGID_CONFIG_CANNOT_DETERMINE_SERVER_ROOT;
          String message = getMessage(msgID, ENV_VAR_INSTANCE_ROOT);
          throw new InitializationException(msgID, message);
        }
      }
      catch (InitializationException ie)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ie);
        }

        throw ie;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_CONFIG_CANNOT_DETERMINE_SERVER_ROOT;
        String message = getMessage(msgID, ENV_VAR_INSTANCE_ROOT);
        throw new InitializationException(msgID, message);
      }
    }


    // Register with the Directory Server as an alert generator.
    DirectoryServer.registerAlertGenerator(this);


    // Register with the Directory Server as the backend that should be used
    // when accessing the configuration.
    baseDNs = new DN[] { configRootEntry.getDN() };

    try
    {
      DirectoryServer.registerBaseDN(configRootEntry.getDN(), this, true,
                                     false);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_CANNOT_REGISTER_AS_PRIVATE_SUFFIX;
      String message = getMessage(msgID, configRootEntry.getDN(),
                                  getExceptionMessage(e));
      throw new InitializationException(msgID, message, e);
    }
  }



  /**
   * Calculates a SHA-1 digest of the current configuration file.
   *
   * @return  The calculated configuration digest.
   *
   * @throws  DirectoryException  If a problem occurs while calculating the
   *                              digest.
   */
  private byte[] calculateConfigDigest()
          throws DirectoryException
  {
    InputStream inputStream = null;
    try
    {
      MessageDigest sha1Digest =
           MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM_SHA_1);
      inputStream = new FileInputStream(configFile);
      byte[] buffer = new byte[8192];
      while (true)
      {
        int bytesRead = inputStream.read(buffer);
        if (bytesRead < 0)
        {
          break;
        }

        sha1Digest.update(buffer, 0, bytesRead);
      }
      return sha1Digest.digest();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIG_CANNOT_CALCULATE_DIGEST;
      String message = getMessage(msgID, configFile,
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
    finally
    {
      if (inputStream != null)
      {
        try
        {
          inputStream.close();
        }
        catch (IOException e) {
          // ignore;
        }
      }
    }
  }



  /**
   * Looks at the existing archive directory, finds the latest archive file,
   * and calculates a SHA-1 digest of that file.
   *
   * @return  The calculated digest of the most recent archived configuration
   *          file.
   *
   * @throws  DirectoryException  If a problem occurs while calculating the
   *                              digest.
   */
  private byte[] getLastConfigDigest(File archiveDirectory)
          throws DirectoryException
  {
    int    latestCounter   = 0;
    long   latestTimestamp = -1;
    String latestFileName  = null;
    for (String name : archiveDirectory.list())
    {
      if (! name.startsWith("config-"))
      {
        continue;
      }

      int dotPos = name.indexOf('.', 7);
      if (dotPos < 0)
      {
        continue;
      }

      int dashPos = name.indexOf('-', 7);
      if (dashPos < 0)
      {
        try
        {
          ASN1OctetString ts = new ASN1OctetString(name.substring(7, dotPos));
          long timestamp = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(ts);
          if (timestamp > latestTimestamp)
          {
            latestFileName  = name;
            latestTimestamp = timestamp;
            latestCounter   = 0;
            continue;
          }
        }
        catch (Exception e)
        {
          continue;
        }
      }
      else
      {
        try
        {
          ASN1OctetString ts = new ASN1OctetString(name.substring(7, dashPos));
          long timestamp = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(ts);
          int counter = Integer.parseInt(name.substring(dashPos+1, dotPos));

          if (timestamp > latestTimestamp)
          {
            latestFileName  = name;
            latestTimestamp = timestamp;
            latestCounter   = counter;
            continue;
          }
          else if ((timestamp == latestTimestamp) && (counter > latestCounter))
          {
            latestFileName  = name;
            latestTimestamp = timestamp;
            latestCounter   = counter;
            continue;
          }
        }
        catch (Exception e)
        {
          continue;
        }
      }
    }

    if (latestFileName == null)
    {
      return null;
    }
    File latestFile = new File(archiveDirectory, latestFileName);

    try
    {
      MessageDigest sha1Digest =
           MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM_SHA_1);
      GZIPInputStream inputStream =
           new GZIPInputStream(new FileInputStream(latestFile));
      byte[] buffer = new byte[8192];
      while (true)
      {
        int bytesRead = inputStream.read(buffer);
        if (bytesRead < 0)
        {
          break;
        }

        sha1Digest.update(buffer, 0, bytesRead);
      }

      return sha1Digest.digest();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIG_CANNOT_CALCULATE_DIGEST;
      String message = getMessage(msgID, latestFile.getAbsolutePath(),
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
  }



  /**
   * Applies the updates in the provided changes file to the content in the
   * specified source file.  The result will be written to a temporary file, the
   * current source file will be moved out of place, and then the updated file
   * will be moved into the place of the original file.  The changes file will
   * also be renamed so it won't be applied again.
   * <BR><BR>
   * If any problems are encountered, then the config initialization process
   * will be aborted.
   *
   * @param  sourceFile   The LDIF file containing the source data.
   * @param  changesFile  The LDIF file containing the changes to apply.
   *
   * @throws  IOException  If a problem occurs while performing disk I/O.
   *
   * @throws  LDIFException  If a problem occurs while trying to interpret the
   *                         data.
   */
  private void applyChangesFile(File sourceFile, File changesFile)
          throws IOException, LDIFException
  {
    // Create the appropriate LDIF readers and writer.
    LDIFImportConfig importConfig =
         new LDIFImportConfig(sourceFile.getAbsolutePath());
    importConfig.setValidateSchema(false);
    LDIFReader sourceReader = new LDIFReader(importConfig);

    importConfig = new LDIFImportConfig(changesFile.getAbsolutePath());
    importConfig.setValidateSchema(false);
    LDIFReader changesReader = new LDIFReader(importConfig);

    String tempFile = changesFile.getAbsolutePath() + ".tmp";
    LDIFExportConfig exportConfig =
         new LDIFExportConfig(tempFile, ExistingFileBehavior.OVERWRITE);
    LDIFWriter targetWriter = new LDIFWriter(exportConfig);


    // Apply the changes and make sure there were no errors.
    LinkedList<String> errorList = new LinkedList<String>();
    boolean successful = LDIFModify.modifyLDIF(sourceReader, changesReader,
                                               targetWriter, errorList);

    try
    {
      sourceReader.close();
    } catch (Exception e) {}

    try
    {
      changesReader.close();
    } catch (Exception e) {}

    try
    {
      targetWriter.close();
    } catch (Exception e) {}

    if (! successful)
    {
      // FIXME -- Log each error message and throw an exception.
      for (String s : errorList)
      {
        int    msgID   = MSGID_CONFIG_ERROR_APPLYING_STARTUP_CHANGE;
        String message = getMessage(msgID, s);
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 msgID, message);
      }

      int    msgID   = MSGID_CONFIG_UNABLE_TO_APPLY_CHANGES_FILE;
      String message = getMessage(msgID);
      throw new LDIFException(msgID, message);
    }


    // Move the current config file out of the way and replace it with the
    // updated version.
    File oldSource = new File(sourceFile.getAbsolutePath() + ".prechanges");
    if (oldSource.exists())
    {
      oldSource.delete();
    }
    sourceFile.renameTo(oldSource);
    new File(tempFile).renameTo(sourceFile);

    // Move the changes file out of the way so it doesn't get applied again.
    File newChanges = new File(changesFile.getAbsolutePath() + ".applied");
    if (newChanges.exists())
    {
      newChanges.delete();
    }
    changesFile.renameTo(newChanges);
  }



  /**
   * Finalizes this configuration handler so that it will release any resources
   * associated with it so that it will no longer be used.  This will be called
   * when the Directory Server is shutting down, as well as in the startup
   * process once the schema has been read so that the configuration can be
   * re-read using the updated schema.
   */
  public void finalizeConfigHandler()
  {
    try
    {
      DirectoryServer.deregisterBaseDN(configRootEntry.getDN(), false);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Performs any necessary work to finalize this backend, including closing any
   * underlying databases or connections and deregistering any suffixes that it
   * manages with the Directory Server.  This may be called during the
   * Directory Server shutdown process or if a backend is disabled with the
   * server online.  It must not return until the backend is closed.
   * <BR><BR>
   * This method may not throw any exceptions.  If any problems are encountered,
   * then they may be logged but the closure should progress as completely as
   * possible.
   */
  public void finalizeBackend()
  {
    // NYI
  }



  /**
   * Retrieves the entry that is at the root of the Directory Server
   * configuration.
   *
   * @return  The entry that is at the root of the Directory Server
   *          configuration.
   *
   * @throws  ConfigException  If a problem occurs while interacting with the
   *                           configuration.
   */
  public ConfigEntry getConfigRootEntry()
         throws ConfigException
  {
    return configRootEntry;
  }



  /**
   * Retrieves the requested entry from the configuration.
   *
   * @param  entryDN  The distinguished name of the configuration entry to
   *                  retrieve.
   *
   * @return  The requested configuration entry.
   *
   * @throws  ConfigException  If a problem occurs while interacting with the
   *                           configuration.
   */
  public ConfigEntry getConfigEntry(DN entryDN)
         throws ConfigException
  {
    return configEntries.get(entryDN);
  }



  /**
   * Retrieves the absolute path of the Directory Server instance root.
   *
   * @return  The absolute path of the Directory Server instance root.
   */
  public String getServerRoot()
  {
    return serverRoot;
  }


  /**
   * {@inheritDoc}
   */
  public void configureBackend(Configuration cfg) throws ConfigException
  {
    // No action is required.
  }

  /**
   * {@inheritDoc}
   */
  public void initializeBackend()
         throws ConfigException, InitializationException
  {
    // No action is required, since all initialization was performed in the
    // initializeConfigHandler method.
  }



  /**
   * Retrieves the set of base-level DNs that may be used within this backend.
   *
   * @return  The set of base-level DNs that may be used within this backend.
   */
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }



  /**
   * {@inheritDoc}
   */
  public long getEntryCount()
  {
    return configEntries.size();
  }



  /**
   * Indicates whether the data associated with this backend may be considered
   * local (i.e., in a repository managed by the Directory Server) rather than
   * remote (i.e., in an external repository accessed by the Directory Server
   * but managed through some other means).
   *
   * @return  <CODE>true</CODE> if the data associated with this backend may be
   *          considered local, or <CODE>false</CODE> if it is remote.
   */
  public boolean isLocal()
  {
    // The configuration information will always be local.
    return true;
  }



  /**
   * Retrieves the requested entry from this backend.
   *
   * @param  entryDN  The distinguished name of the entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if the entry does not
   *          exist.
   *
   * @throws  DirectoryException  If a problem occurs while trying to retrieve
   *                              the entry.
   */
  public Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    ConfigEntry configEntry = configEntries.get(entryDN);
    if (configEntry == null)
    {
      return null;
    }

    return configEntry.getEntry().duplicate(true);
  }



  /**
   * Indicates whether an entry with the specified DN exists in the backend.
   * The default implementation obtains a read lock and calls
   * <CODE>getEntry</CODE>, but backend implementations may override this with a
   * more efficient version that does not require a lock.  The caller is not
   * required to hold any locks on the specified DN.
   *
   * @param  entryDN  The DN of the entry for which to determine existence.
   *
   * @return  <CODE>true</CODE> if the specified entry exists in this backend,
   *          or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while trying to make the
   *                              determination.
   */
  public boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    return configEntries.containsKey(entryDN);
  }



  /**
   * Adds the provided entry to this backend.  This method must ensure that the
   * entry is appropriate for the backend and that no entry already exists with
   * the same DN.
   *
   * @param  entry         The entry to add to this backend.
   * @param  addOperation  The add operation with which the new entry is
   *                       associated.  This may be <CODE>null</CODE> for adds
   *                       performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to add the
   *                              entry.
   */
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    Entry e = entry.duplicate(false);

    // If there is an add operation, then make sure that the associated user has
    // both the CONFIG_READ and CONFIG_WRITE privileges.
    if (addOperation != null)
    {
      ClientConnection clientConnection = addOperation.getClientConnection();
      if (! (clientConnection.hasAllPrivileges(CONFIG_READ_AND_WRITE,
                                               addOperation)))
      {
        int    msgID   = MSGID_CONFIG_FILE_ADD_INSUFFICIENT_PRIVILEGES;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message, msgID);
      }
    }


    // Grab the config lock to ensure that only one config update may be in
    // progress at any given time.
    configLock.lock();

    try
    {
      // Make sure that the target DN does not already exist.  If it does, then
      // fail.
      DN entryDN = e.getDN();
      if (configEntries.containsKey(entryDN))
      {
        int    msgID   = MSGID_CONFIG_FILE_ADD_ALREADY_EXISTS;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, message,
                                     msgID);
      }


      // Make sure that the entry's parent exists.  If it does not, then fail.
      DN parentDN = entryDN.getParent();
      if (parentDN == null)
      {
        // The entry DN doesn't have a parent.  This is not allowed.
        int    msgID   = MSGID_CONFIG_FILE_ADD_NO_PARENT_DN;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
      }

      ConfigEntry parentEntry = configEntries.get(parentDN);
      if (parentEntry == null)
      {
        // The parent entry does not exist.  This is not allowed.
        int msgID = MSGID_CONFIG_FILE_ADD_NO_PARENT;
        String message = getMessage(msgID, String.valueOf(entryDN),
                                    String.valueOf(parentDN));

        // Get the matched DN, if possible.
        DN matchedDN = null;
        parentDN = parentDN.getParent();
        while (parentDN != null)
        {
          if (configEntries.containsKey(parentDN))
          {
            matchedDN = parentDN;
            break;
          }

          parentDN = parentDN.getParent();
        }

        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID,
                                     matchedDN, null);
      }


      // Encapsulate the provided entry in a config entry.
      ConfigEntry newEntry = new ConfigEntry(e, parentEntry);


      // See if the parent entry has any add listeners.  If so, then iterate
      // through them and make sure the new entry is acceptable.
      CopyOnWriteArrayList<ConfigAddListener> addListeners =
           parentEntry.getAddListeners();
      StringBuilder unacceptableReason = new StringBuilder();
      for (ConfigAddListener l : addListeners)
      {
        if (! l.configAddIsAcceptable(newEntry, unacceptableReason))
        {
          int msgID = MSGID_CONFIG_FILE_ADD_REJECTED_BY_LISTENER;
          String message = getMessage(msgID, String.valueOf(entryDN),
                                      String.valueOf(parentDN),
                                      String.valueOf(unacceptableReason));
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                       msgID);

        }
      }


      // At this point, we will assume that everything is OK and proceed with
      // the add.
      try
      {
        parentEntry.addChild(newEntry);
        configEntries.put(entryDN, newEntry);
        writeUpdatedConfig();
      }
      catch (ConfigException ce)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ce);
        }

        int    msgID   = MSGID_CONFIG_FILE_ADD_FAILED;
        String message = getMessage(msgID, String.valueOf(entryDN),
                                    String.valueOf(parentDN),
                                    getExceptionMessage(ce));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }


      // Notify all the add listeners that the entry has been added.
      for (ConfigAddListener l : addListeners)
      {
        handleConfigChangeResult(l.applyConfigurationAdd(newEntry),
                                 newEntry.getDN(), l.getClass().getName(),
                                 "applyConfigurationAdd");
      }
    }
    finally
    {
      configLock.unlock();
    }
  }



  /**
   * Removes the specified entry from this backend.  This method must ensure
   * that the entry exists and that it does not have any subordinate entries
   * (unless the backend supports a subtree delete operation and the client
   * included the appropriate information in the request).
   *
   * @param  entryDN          The DN of the entry to remove from this backend.
   * @param  deleteOperation  The delete operation with which this action is
   *                          associated.  This may be <CODE>null</CODE> for
   *                          deletes performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to remove the
   *                              entry.
   */
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    // If there is a delete operation, then make sure that the associated user
    // has both the CONFIG_READ and CONFIG_WRITE privileges.
    if (deleteOperation != null)
    {
      ClientConnection clientConnection = deleteOperation.getClientConnection();
      if (! (clientConnection.hasAllPrivileges(CONFIG_READ_AND_WRITE,
                                               deleteOperation)))
      {
        int    msgID   = MSGID_CONFIG_FILE_DELETE_INSUFFICIENT_PRIVILEGES;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message, msgID);
      }
    }


    // Grab the config lock to ensure that only one config update may be in
    // progress at any given time.
    configLock.lock();

    try
    {
      // Get the target entry.  If it does not exist, then fail.
      ConfigEntry entry = configEntries.get(entryDN);
      if (entry == null)
      {
        // Try to find the matched DN if possible.
        DN matchedDN = null;
        if (entryDN.isDescendantOf(configRootEntry.getDN()))
        {
          DN parentDN = entryDN.getParent();
          while (parentDN != null)
          {
            if (configEntries.containsKey(parentDN))
            {
              matchedDN = parentDN;
              break;
            }

            parentDN = parentDN.getParent();
          }
        }

        int    msgID   = MSGID_CONFIG_FILE_DELETE_NO_SUCH_ENTRY;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID,
                                     matchedDN, null);
      }


      // If the entry has children, then fail.
      if (entry.hasChildren())
      {
        int    msgID   = MSGID_CONFIG_FILE_DELETE_HAS_CHILDREN;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF, message,
                                     msgID);
      }


      // Get the parent entry.  If there isn't one, then it must be the config
      // root, which we won't allow.
      ConfigEntry parentEntry = entry.getParent();
      if (parentEntry == null)
      {
        int    msgID   = MSGID_CONFIG_FILE_DELETE_NO_PARENT;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }


      // Get the delete listeners from the parent and make sure that they are
      // all OK with the delete.
      CopyOnWriteArrayList<ConfigDeleteListener> deleteListeners =
           parentEntry.getDeleteListeners();
      StringBuilder unacceptableReason = new StringBuilder();
      for (ConfigDeleteListener l : deleteListeners)
      {
        if (! l.configDeleteIsAcceptable(entry, unacceptableReason))
        {
          int    msgID   = MSGID_CONFIG_FILE_DELETE_REJECTED;
          String message = getMessage(msgID, String.valueOf(entryDN),
                                      String.valueOf(parentEntry.getDN()),
                                      String.valueOf(unacceptableReason));
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                       msgID);
        }
      }


      // At this point, we will assume that everything is OK and proceed with
      // the delete.
      try
      {
        parentEntry.removeChild(entryDN);
        configEntries.remove(entryDN);
        writeUpdatedConfig();
      }
      catch (ConfigException ce)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ce);
        }

        int    msgID   = MSGID_CONFIG_FILE_DELETE_FAILED;
        String message = getMessage(msgID, String.valueOf(entryDN),
                                    String.valueOf(parentEntry.getDN()),
                                    getExceptionMessage(ce));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }


      // Notify all the delete listeners that the entry has been removed.
      for (ConfigDeleteListener l : deleteListeners)
      {
        handleConfigChangeResult(l.applyConfigurationDelete(entry),
                                 entry.getDN(), l.getClass().getName(),
                                 "applyConfigurationDelete");
      }
    }
    finally
    {
      configLock.unlock();
    }
  }



  /**
   * Replaces the specified entry with the provided entry in this backend.  The
   * backend must ensure that an entry already exists with the same DN as the
   * provided entry.
   *
   * @param  entry            The new entry to use in place of the existing
   *                          entry with the same DN.
   * @param  modifyOperation  The modify operation with which this action is
   *                          associated.  This may be <CODE>null</CODE> for
   *                          modifications performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to replace
   *                              the entry.
   */
  public void replaceEntry(Entry entry, ModifyOperation modifyOperation)
         throws DirectoryException
  {
    Entry e = entry.duplicate(false);

    // If there is a modify operation, then make sure that the associated user
    // has both the CONFIG_READ and CONFIG_WRITE privileges.  Also, if the
    // operation targets the set of root privileges then make sure the user has
    // the PRIVILEGE_CHANGE privilege.
    if (modifyOperation != null)
    {
      ClientConnection clientConnection = modifyOperation.getClientConnection();
      if (! (clientConnection.hasAllPrivileges(CONFIG_READ_AND_WRITE,
                                               modifyOperation)))
      {
        int    msgID   = MSGID_CONFIG_FILE_MODIFY_INSUFFICIENT_PRIVILEGES;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message, msgID);
      }

      AttributeType privType =
           DirectoryServer.getAttributeType(ATTR_DEFAULT_ROOT_PRIVILEGE_NAME,
                                            true);
      for (Modification m : modifyOperation.getModifications())
      {
        if (m.getAttribute().getAttributeType().equals(privType))
        {
          if (! clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE,
                                              modifyOperation))
          {
            int msgID = MSGID_CONFIG_FILE_MODIFY_PRIVS_INSUFFICIENT_PRIVILEGES;
            String message = getMessage(msgID);
            throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                         message, msgID);
          }

          break;
        }
      }
    }


    // Grab the config lock to ensure that only one config update may be in
    // progress at any given time.
    configLock.lock();


    try
    {
      // Get the DN of the target entry for future reference.
      DN entryDN = e.getDN();


      // Get the target entry.  If it does not exist, then fail.
      ConfigEntry currentEntry = configEntries.get(entryDN);
      if (currentEntry == null)
      {
        // Try to find the matched DN if possible.
        DN matchedDN = null;
        if (entryDN.isDescendantOf(configRootEntry.getDN()))
        {
          DN parentDN = entryDN.getParent();
          while (parentDN != null)
          {
            if (configEntries.containsKey(parentDN))
            {
              matchedDN = parentDN;
              break;
            }

            parentDN = parentDN.getParent();
          }
        }

        int    msgID   = MSGID_CONFIG_FILE_MODIFY_NO_SUCH_ENTRY;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID,
                                     matchedDN, null);
      }


      // If the structural class is different between the current entry and the
      // new entry, then reject the change.
      if (! currentEntry.getEntry().getStructuralObjectClass().equals(
                 entry.getStructuralObjectClass()))
      {
        int    msgID   = MSGID_CONFIG_FILE_MODIFY_STRUCTURAL_CHANGE_NOT_ALLOWED;
        String message = getMessage(msgID, String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
      }


      // Create a new config entry to use for the validation testing.
      ConfigEntry newEntry = new ConfigEntry(e, currentEntry.getParent());


      // See if there are any config change listeners registered for this entry.
      // If there are, then make sure they are all OK with the change.
      CopyOnWriteArrayList<ConfigChangeListener> changeListeners =
           currentEntry.getChangeListeners();
      StringBuilder unacceptableReason = new StringBuilder();
      for (ConfigChangeListener l : changeListeners)
      {
        if (! l.configChangeIsAcceptable(newEntry, unacceptableReason))
        {
          int    msgID   = MSGID_CONFIG_FILE_MODIFY_REJECTED_BY_CHANGE_LISTENER;
          String message = getMessage(msgID, String.valueOf(entryDN),
                                      String.valueOf(unacceptableReason));
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                       msgID);
        }
      }


      // See if there are any configurable components associated with this
      // entry.  If there are, then make sure they are all OK with the change.
      JMXMBean mBean = DirectoryServer.getJMXMBean(entryDN);
      CopyOnWriteArrayList<ConfigurableComponent> configurableComponents = null;
      if (mBean != null)
      {
        configurableComponents = mBean.getConfigurableComponents();
        LinkedList<String> unacceptableReasons = new LinkedList<String>();

        for (ConfigurableComponent c : configurableComponents)
        {
          if (! c.hasAcceptableConfiguration(newEntry, unacceptableReasons))
          {
            if (! unacceptableReasons.isEmpty())
            {
              Iterator<String> iterator = unacceptableReasons.iterator();
              unacceptableReason.append(iterator.next());

              while (iterator.hasNext())
              {
                unacceptableReason.append("  ");
                unacceptableReason.append(iterator.next());
              }
            }

            int msgID = MSGID_CONFIG_FILE_MODIFY_REJECTED_BY_COMPONENT;
            String message = getMessage(msgID, String.valueOf(entryDN),
                                        String.valueOf(unacceptableReason));
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message, msgID);
          }
        }
      }


      // At this point, it looks like the change is acceptable, so apply it.
      // We'll just overwrite the core entry in the current config entry so that
      // we keep all the registered listeners, references to the parent and
      // children, and other metadata.
      currentEntry.setEntry(e);
      writeUpdatedConfig();


      // Notify all the change listeners of the update.
      for (ConfigChangeListener l : changeListeners)
      {
        handleConfigChangeResult(l.applyConfigurationChange(currentEntry),
                                 currentEntry.getDN(), l.getClass().getName(),
                                 "applyConfigurationChange");
      }


      // Notify all the configurable components of the update.
      if (configurableComponents != null)
      {
        for (ConfigurableComponent c : configurableComponents)
        {
          handleConfigChangeResult(c.applyNewConfiguration(currentEntry,
                                          DynamicConstants.DEBUG_BUILD),
                                   currentEntry.getDN(), c.getClass().getName(),
                                   "applyNewConfiguration");
        }
      }
    }
    finally
    {
      configLock.unlock();
    }
  }



  /**
   * Moves and/or renames the provided entry in this backend, altering any
   * subordinate entries as necessary.  This must ensure that an entry already
   * exists with the provided current DN, and that no entry exists with the
   * target DN of the provided entry.
   *
   * @param  currentDN          The current DN of the entry to be replaced.
   * @param  entry              The new content to use for the entry.
   * @param  modifyDNOperation  The modify DN operation with which this action
   *                            is associated.  This may be <CODE>null</CODE>
   *                            for modify DN operations performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to perform
   *                              the rename.
   */
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    // If there is a modify DN operation, then make sure that the associated
    // user has both the CONFIG_READ and CONFIG_WRITE privileges.
    if (modifyDNOperation != null)
    {
      ClientConnection clientConnection =
           modifyDNOperation.getClientConnection();
      if (! (clientConnection.hasAllPrivileges(CONFIG_READ_AND_WRITE,
                                               modifyDNOperation)))
      {
        int    msgID   = MSGID_CONFIG_FILE_MODDN_INSUFFICIENT_PRIVILEGES;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message, msgID);
      }
    }


    // Modify DN operations will not be allowed in the configuration, so this
    // will always throw an exception.
    int msgID = MSGID_CONFIG_FILE_MODDN_NOT_ALLOWED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Processes the specified search in this backend.  Matching entries should be
   * provided back to the core server using the
   * <CODE>SearchOperation.returnEntry</CODE> method.
   *
   * @param  searchOperation  The search operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs while processing the
   *                              search.
   */
  public void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    // Make sure that the associated user has the CONFIG_READ privilege.
    ClientConnection clientConnection = searchOperation.getClientConnection();
    if (! clientConnection.hasPrivilege(Privilege.CONFIG_READ, searchOperation))
    {
      int    msgID   = MSGID_CONFIG_FILE_SEARCH_INSUFFICIENT_PRIVILEGES;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                   message, msgID);
    }


    // First, get the base DN for the search and make sure that it exists.
    DN          baseDN    = searchOperation.getBaseDN();
    ConfigEntry baseEntry = configEntries.get(baseDN);
    if (baseEntry == null)
    {
      int    msgID   = MSGID_CONFIG_FILE_SEARCH_NO_SUCH_BASE;
      String message = getMessage(msgID, String.valueOf(baseDN));

      DN matchedDN = null;
      if (baseDN.isDescendantOf(configRootEntry.getDN()))
      {
        DN parentDN = baseDN.getParent();
        while (parentDN != null)
        {
          if (configEntries.containsKey(parentDN))
          {
            matchedDN = parentDN;
            break;
          }

          parentDN = parentDN.getParent();
        }
      }

      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID,
                                   matchedDN, null);
    }


    // Get the scope for the search and perform the remainder of the processing
    // accordingly.  Also get the filter since we will need it in all cases.
    SearchScope  scope  = searchOperation.getScope();
    SearchFilter filter = searchOperation.getFilter();
    switch (scope)
    {
      case BASE_OBJECT:
        // We are only interested in the base entry itself.  See if it matches
        // and if so then return the entry.
        Entry e = baseEntry.getEntry().duplicate(true);
        if (filter.matchesEntry(e))
        {
          searchOperation.returnEntry(e, null);
        }
        break;


      case SINGLE_LEVEL:
        // We are only interested in entries immediately below the base entry.
        // Iterate through them and return the ones that match the filter.
        for (ConfigEntry child : baseEntry.getChildren().values())
        {
          e = child.getEntry().duplicate(true);
          if (filter.matchesEntry(e))
          {
            if (! searchOperation.returnEntry(e, null))
            {
              break;
            }
          }
        }
        break;


      case WHOLE_SUBTREE:
        // We are interested in the base entry and all its children.  Use a
        // recursive process to achieve this.
        searchSubtree(baseEntry, filter, searchOperation);
        break;


      case SUBORDINATE_SUBTREE:
        // We are not interested in the base entry, but we want to check out all
        // of its children.  Use a recursive process to achieve this.
        for (ConfigEntry child : baseEntry.getChildren().values())
        {
          if (! searchSubtree(child, filter, searchOperation))
          {
            break;
          }
        }
        break;


      default:
        // The user provided an invalid scope.
        int    msgID   = MSGID_CONFIG_FILE_SEARCH_INVALID_SCOPE;
        String message = getMessage(msgID, String.valueOf(scope));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, msgID);
    }
  }



  /**
   * Performs a subtree search starting at the provided base entry, returning
   * all entries anywhere in that subtree that match the provided filter.
   *
   * @param  baseEntry        The base entry below which to perform the search.
   * @param  filter           The filter to use to identify matching entries.
   * @param  searchOperation  The search operation to use to return entries to
   *                          the client.
   *
   * @return  <CODE>true</CODE> if the search should continue, or
   *          <CODE>false</CODE> if it should stop for some reason (e.g., the
   *          time limit or size limit has been reached).
   *
   * @throws  DirectoryException  If a problem occurs during processing.
   */
  private boolean searchSubtree(ConfigEntry baseEntry, SearchFilter filter,
                                SearchOperation searchOperation)
          throws DirectoryException
  {
    Entry e = baseEntry.getEntry().duplicate(true);
    if (filter.matchesEntry(e))
    {
      if (! searchOperation.returnEntry(e, null))
      {
        return false;
      }
    }

    for (ConfigEntry child : baseEntry.getChildren().values())
    {
      if (! searchSubtree(child, filter, searchOperation))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Writes an updated version of the configuration file to disk.  This will
   * archive the previous configuration in a ZIP file before overwriting the
   * main config file.
   *
   * @throws  DirectoryException  If a problem is encountered while writing the
   *                              updated configuration.
   */
  public void writeUpdatedConfig()
         throws DirectoryException
  {
    // FIXME -- This needs support for encryption.


    // Calculate an archive for the current server configuration file and see if
    // it matches what we expect.  If not, then the file has been manually
    // edited with the server online which is a bad thing.  In that case, we'll
    // copy the current config off to the side before writing the new config
    // so that the manual changes don't get lost but also don't get applied.
    // Also, send an admin alert notifying administrators about the problem.
    try
    {
      byte[] currentDigest = calculateConfigDigest();
      if (! Arrays.equals(configurationDigest, currentDigest))
      {
        File existingCfg   = new File(configFile);
        File newConfigFile = new File(existingCfg.getParent(),
                                      "config.manualedit-" +
                                           TimeThread.getGMTTime() + ".ldif");
        int counter = 2;
        while (newConfigFile.exists())
        {
          newConfigFile = new File(newConfigFile.getAbsolutePath() + "." +
                                   counter++);
        }

        FileInputStream  inputStream  = new FileInputStream(existingCfg);
        FileOutputStream outputStream = new FileOutputStream(newConfigFile);
        byte[] buffer = new byte[8192];
        while (true)
        {
          int bytesRead = inputStream.read(buffer);
          if (bytesRead < 0)
          {
            break;
          }

          outputStream.write(buffer, 0, bytesRead);
        }

        inputStream.close();
        outputStream.close();

        int    msgID   = MSGID_CONFIG_MANUAL_CHANGES_DETECTED;
        String message = getMessage(msgID, configFile,
                                    newConfigFile.getAbsolutePath());

        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);

        DirectoryServer.sendAlertNotification(this,
             ALERT_TYPE_MANUAL_CONFIG_EDIT_HANDLED, msgID, message);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_MANUAL_CHANGES_LOST;
      String message = getMessage(msgID, configFile,
                                  stackTraceToSingleLineString(e));

      logError(ErrorLogCategory.CONFIGURATION,
               ErrorLogSeverity.SEVERE_ERROR, message, msgID);

      DirectoryServer.sendAlertNotification(this,
           ALERT_TYPE_MANUAL_CONFIG_EDIT_HANDLED, msgID, message);
    }


    // Write the new configuration to a temporary file.
    String tempConfig = configFile + ".tmp";
    try
    {
      LDIFExportConfig exportConfig =
           new LDIFExportConfig(tempConfig, ExistingFileBehavior.OVERWRITE);

      // FIXME -- Add all the appropriate configuration options.
      writeLDIF(exportConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_FILE_WRITE_CANNOT_EXPORT_NEW_CONFIG;
      String message = getMessage(msgID, String.valueOf(tempConfig),
                                  stackTraceToSingleLineString(e));

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);

      DirectoryServer.sendAlertNotification(this,
           ALERT_TYPE_CANNOT_WRITE_CONFIGURATION, msgID, message);
      return;
    }


    // Delete the previous version of the configuration and rename the new one.
    try
    {
      File actualConfig = new File(configFile);
      File tmpConfig = new File(tempConfig);
      renameFile(tmpConfig, actualConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_FILE_WRITE_CANNOT_RENAME_NEW_CONFIG;
      String message = getMessage(msgID, String.valueOf(tempConfig),
                                  String.valueOf(configFile),
                                  stackTraceToSingleLineString(e));

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);

      DirectoryServer.sendAlertNotification(this,
           ALERT_TYPE_CANNOT_WRITE_CONFIGURATION, msgID, message);
      return;
    }

    configurationDigest = calculateConfigDigest();


    // Try to write the archive for the new configuration.
    writeConfigArchive();
  }



  /**
   * Writes the current configuration to the configuration archive.  This will
   * be a best-effort attempt.
   */
  private void writeConfigArchive()
  {
    // Determine the path to the directory that will hold the archived
    // configuration files.
    File configDirectory  = new File(configFile).getParentFile();
    File archiveDirectory = new File(configDirectory, CONFIG_ARCHIVE_DIR_NAME);


    // If the archive directory doesn't exist, then create it.
    if (! archiveDirectory.exists())
    {
      try
      {
        if (! archiveDirectory.mkdirs())
        {
          int msgID = MSGID_CONFIG_FILE_CANNOT_CREATE_ARCHIVE_DIR_NO_REASON;
          String message = getMessage(msgID,
                                      archiveDirectory.getAbsolutePath());

          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR, message, msgID);

          DirectoryServer.sendAlertNotification(this,
               ALERT_TYPE_CANNOT_WRITE_CONFIGURATION, msgID, message);
          return;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_CONFIG_FILE_CANNOT_CREATE_ARCHIVE_DIR;
        String message = getMessage(msgID, archiveDirectory.getAbsolutePath(),
                                    stackTraceToSingleLineString(e));

        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_ERROR, message, msgID);

        DirectoryServer.sendAlertNotification(this,
             ALERT_TYPE_CANNOT_WRITE_CONFIGURATION, msgID, message);
        return;
      }
    }


    // Determine the appropriate name to use for the current configuration.
    File archiveFile;
    try
    {
      String timestamp = TimeThread.getGMTTime();
      archiveFile = new File(archiveDirectory, "config-" + timestamp + ".gz");
      if (archiveFile.exists())
      {
        int counter = 2;
        archiveFile = new File(archiveDirectory,
                               "config-" + timestamp + "-" + counter + ".gz");

        while (archiveFile.exists())
        {
          counter++;
          archiveFile = new File(archiveDirectory,
                                 "config-" + timestamp + "-" + counter + ".gz");
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_FILE_CANNOT_WRITE_CONFIG_ARCHIVE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));

      logError(ErrorLogCategory.CONFIGURATION,
               ErrorLogSeverity.SEVERE_ERROR, message, msgID);

      DirectoryServer.sendAlertNotification(this,
           ALERT_TYPE_CANNOT_WRITE_CONFIGURATION, msgID, message);
      return;
    }


    // Copy the current configuration to the new configuration file.
    byte[]           buffer       = new byte[8192];
    FileInputStream  inputStream  = null;
    GZIPOutputStream outputStream = null;
    try
    {
      inputStream  = new FileInputStream(configFile);
      outputStream = new GZIPOutputStream(new FileOutputStream(archiveFile));

      int bytesRead = inputStream.read(buffer);
      while (bytesRead > 0)
      {
        outputStream.write(buffer, 0, bytesRead);
        bytesRead = inputStream.read(buffer);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_FILE_CANNOT_WRITE_CONFIG_ARCHIVE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));

      logError(ErrorLogCategory.CONFIGURATION,
               ErrorLogSeverity.SEVERE_ERROR, message, msgID);

      DirectoryServer.sendAlertNotification(this,
           ALERT_TYPE_CANNOT_WRITE_CONFIGURATION, msgID, message);
      return;
    }
    finally
    {
      try
      {
        inputStream.close();
      } catch (Exception e) {}

      try
      {
        outputStream.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Retrieves the OIDs of the controls that may be supported by this backend.
   *
   * @return  The OIDs of the controls that may be supported by this backend.
   */
  public HashSet<String> getSupportedControls()
  {
    // NYI
    return null;
  }



  /**
   * Retrieves the OIDs of the features that may be supported by this backend.
   *
   * @return  The OIDs of the features that may be supported by this backend.
   */
  public HashSet<String> getSupportedFeatures()
  {
    // NYI
    return null;
  }



  /**
   * Indicates whether this backend provides a mechanism to export the data it
   * contains to an LDIF file.
   *
   * @return  <CODE>true</CODE> if this backend provides an LDIF export
   *          mechanism, or <CODE>false</CODE> if not.
   */
  public boolean supportsLDIFExport()
  {
    // TODO We would need export-ldif to initialize this backend.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    // TODO We would need export-ldif to initialize this backend.
    writeLDIF(exportConfig);
  }



  private void writeLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    LDIFWriter writer;
    try
    {
      writer = new LDIFWriter(exportConfig);
      writer.writeComment(getMessage(MSGID_CONFIG_FILE_HEADER), 80);
      writeEntryAndChildren(writer, configRootEntry);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_LDIF_WRITE_ERROR;
      String message = getMessage(msgID, String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }

    try
    {
      writer.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_FILE_CLOSE_ERROR;
      String message = getMessage(msgID, String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
  }



  /**
   * Writes the provided entry and any children that it may have to the provided
   * LDIF writer.
   *
   * @param  writer       The LDIF writer to use to write the entry and its
   *                      children.
   * @param  configEntry  The configuration entry to write, along with its
   *                      children.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to write
   *                              the entry or one of its children.
   */
  private void writeEntryAndChildren(LDIFWriter writer, ConfigEntry configEntry)
          throws DirectoryException
  {
    try
    {
      // Write the entry itself to LDIF.
      writer.writeEntry(configEntry.getEntry());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_FILE_WRITE_ERROR;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }


    // See if the entry has any children.  If so, then iterate through them and
    // write them and their children.  We'll copy the entries into a tree map
    // so that we have a sensible order in the resulting LDIF.
    TreeMap<DN,ConfigEntry> childMap =
         new TreeMap<DN,ConfigEntry>(configEntry.getChildren());
    for (ConfigEntry childEntry : childMap.values())
    {
      writeEntryAndChildren(writer, childEntry);
    }
  }



  /**
   * Indicates whether this backend provides a mechanism to import its data from
   * an LDIF file.
   *
   * @return  <CODE>true</CODE> if this backend provides an LDIF import
   *          mechanism, or <CODE>false</CODE> if not.
   */
  public boolean supportsLDIFImport()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    int msgID     =  MSGID_CONFIG_FILE_UNWILLING_TO_IMPORT;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Indicates whether this backend provides a backup mechanism of any kind.
   * This method is used by the backup process when backing up all backends to
   * determine whether this backend is one that should be skipped.  It should
   * only return <CODE>true</CODE> for backends that it is not possible to
   * archive directly (e.g., those that don't store their data locally, but
   * rather pass through requests to some other repository).
   *
   * @return  <CODE>true</CODE> if this backend provides any kind of backup
   *          mechanism, or <CODE>false</CODE> if it does not.
   */
  public boolean supportsBackup()
  {
    // We do support an online backup mechanism for the configuration.
    return true;
  }



  /**
   * Indicates whether this backend provides a mechanism to perform a backup of
   * its contents in a form that can be restored later, based on the provided
   * configuration.
   *
   * @param  backupConfig       The configuration of the backup for which to
   *                            make the determination.
   * @param  unsupportedReason  A buffer to which a message can be appended
   *                            explaining why the requested backup is not
   *                            supported.
   *
   * @return  <CODE>true</CODE> if this backend provides a mechanism for
   *          performing backups with the provided configuration, or
   *          <CODE>false</CODE> if not.
   */
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    // We should support online backup for the configuration in any form.  This
    // implementation does not support incremental backups, but in this case
    // even if we're asked to do an incremental we'll just do a full backup
    // instead.  So the answer to this should always be "true".
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    // Get the properties to use for the backup.  We don't care whether or not
    // it's incremental, so there's no need to get that.
    String          backupID        = backupConfig.getBackupID();
    BackupDirectory backupDirectory = backupConfig.getBackupDirectory();
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
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_CONFIG_BACKUP_CANNOT_GET_MAC;
          String message = getMessage(msgID, macAlgorithm,
                                      stackTraceToSingleLineString(e));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), message,
                         msgID, e);
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
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_CONFIG_BACKUP_CANNOT_GET_DIGEST;
          String message = getMessage(msgID, digestAlgorithm,
                                      stackTraceToSingleLineString(e));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), message,
                         msgID, e);
        }
      }
    }


    // Create an output stream that will be used to write the archive file.  At
    // its core, it will be a file output stream to put a file on the disk.  If
    // we are to encrypt the data, then that file output stream will be wrapped
    // in a cipher output stream.  The resulting output stream will then be
    // wrapped by a zip output stream (which may or may not actually use
    // compression).
    String filename = null;
    OutputStream outputStream;
    try
    {
      filename = CONFIG_BACKUP_BASE_FILENAME + backupID;
      File archiveFile = new File(backupDirectory.getPath() + File.separator +
                                  filename);
      if (archiveFile.exists())
      {
        int i=1;
        while (true)
        {
          archiveFile = new File(backupDirectory.getPath() + File.separator +
                                 filename  + "." + i);
          if (archiveFile.exists())
          {
            i++;
          }
          else
          {
            filename = filename + "." + i;
            break;
          }
        }
      }

      outputStream = new FileOutputStream(archiveFile, false);
      backupProperties.put(BACKUP_PROPERTY_ARCHIVE_FILENAME, filename);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_BACKUP_CANNOT_CREATE_ARCHIVE_FILE;
      String message = getMessage(msgID, String.valueOf(filename),
                                  backupDirectory.getPath(),
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
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_CONFIG_BACKUP_CANNOT_GET_CIPHER;
        String message = getMessage(msgID, cipherAlgorithm,
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }

      outputStream = new CipherOutputStream(outputStream, cipher);
    }


    // Wrap the file output stream in a zip output stream.
    ZipOutputStream zipStream = new ZipOutputStream(outputStream);

    int    msgID   = MSGID_CONFIG_BACKUP_ZIP_COMMENT;
    String message = getMessage(msgID, DynamicConstants.PRODUCT_NAME,
                                backupID);
    zipStream.setComment(message);

    if (compress)
    {
      zipStream.setLevel(Deflater.DEFAULT_COMPRESSION);
    }
    else
    {
      zipStream.setLevel(Deflater.NO_COMPRESSION);
    }


    // This may seem a little weird, but in this context, we only have access to
    // this class as a backend and not as the config handler.  We need it as a
    // config handler to determine the path to the config file, so we can get
    // that from the Directory Server object.
    String configFile = null;
    try
    {
      configFile =
           ((ConfigFileHandler) DirectoryServer.getConfigHandler()).configFile;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID   = MSGID_CONFIG_BACKUP_CANNOT_DETERMINE_CONFIG_FILE_LOCATION;
      message = getMessage(msgID, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }


    // Read the Directory Server configuration file and put it in the archive.
    byte[] buffer = new byte[8192];
    FileInputStream inputStream = null;
    try
    {
      File f = new File(configFile);

      ZipEntry zipEntry = new ZipEntry(f.getName());
      zipStream.putNextEntry(zipEntry);

      inputStream = new FileInputStream(f);
      while (true)
      {
        int bytesRead = inputStream.read(buffer);
        if (bytesRead < 0)
        {
          break;
        }

        if (hash)
        {
          if (signHash)
          {
            mac.update(buffer, 0, bytesRead);
          }
          else
          {
            digest.update(buffer, 0, bytesRead);
          }
        }

        zipStream.write(buffer, 0, bytesRead);
      }

      inputStream.close();
      zipStream.closeEntry();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      try
      {
        inputStream.close();
      } catch (Exception e2) {}

      try
      {
        zipStream.close();
      } catch (Exception e2) {}

      msgID   = MSGID_CONFIG_BACKUP_CANNOT_BACKUP_CONFIG_FILE;
      message = getMessage(msgID, configFile, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }


    // If an archive directory exists, then add its contents as well.
    try
    {
      File archiveDirectory = new File(new File(configFile).getParent(),
                                       CONFIG_ARCHIVE_DIR_NAME);
      if (archiveDirectory.exists())
      {
        for (File archiveFile : archiveDirectory.listFiles())
        {
          ZipEntry zipEntry = new ZipEntry(CONFIG_ARCHIVE_DIR_NAME +
                                           File.separator +
                                           archiveFile.getName());
          zipStream.putNextEntry(zipEntry);
          inputStream = new FileInputStream(archiveFile);
          while (true)
          {
            int bytesRead = inputStream.read(buffer);
            if (bytesRead < 0)
            {
              break;
            }

            if (hash)
            {
              if (signHash)
              {
                mac.update(buffer, 0, bytesRead);
              }
              else
              {
                digest.update(buffer, 0, bytesRead);
              }
            }

            zipStream.write(buffer, 0, bytesRead);
          }

          inputStream.close();
          zipStream.closeEntry();
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      try
      {
        inputStream.close();
      } catch (Exception e2) {}

      try
      {
        zipStream.close();
      } catch (Exception e2) {}

      msgID   = MSGID_CONFIG_BACKUP_CANNOT_BACKUP_ARCHIVED_CONFIGS;
      message = getMessage(msgID, configFile, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID   = MSGID_CONFIG_BACKUP_CANNOT_CLOSE_ZIP_STREAM;
      message = getMessage(msgID, filename, backupDirectory.getPath(),
                           getExceptionMessage(e));
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


    // Create the backup info structure for this backup and add it to the backup
    // directory.
    // FIXME -- Should I use the date from when I started or finished?
    BackupInfo backupInfo = new BackupInfo(backupDirectory, backupID,
                                           new Date(), false, compress,
                                           encrypt, digestBytes, macBytes,
                                           null, backupProperties);

    try
    {
      backupDirectory.addBackup(backupInfo);
      backupDirectory.writeBackupDirectoryDescriptor();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR;
      message = getMessage(msgID, backupDirectory.getDescriptorPath(),
                           stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
  }



  /**
   * Removes the specified backup if it is possible to do so.
   *
   * @param  backupDirectory  The backup directory structure with which the
   *                          specified backup is associated.
   * @param  backupID         The backup ID for the backup to be removed.
   *
   * @throws  DirectoryException  If it is not possible to remove the specified
   *                              backup for some reason (e.g., no such backup
   *                              exists or there are other backups that are
   *                              dependent upon it).
   */
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    // NYI
  }



  /**
   * Indicates whether this backend provides a mechanism to restore a backup.
   *
   * @return  <CODE>true</CODE> if this backend provides a mechanism for
   *          restoring backups, or <CODE>false</CODE> if not.
   */
  public boolean supportsRestore()
  {
    // We will provide a restore, but only for offline operations.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    // First, make sure that the requested backup exists.
    BackupDirectory backupDirectory = restoreConfig.getBackupDirectory();
    String          backupPath      = backupDirectory.getPath();
    String          backupID        = restoreConfig.getBackupID();
    BackupInfo      backupInfo      = backupDirectory.getBackupInfo(backupID);
    if (backupInfo == null)
    {
      int    msgID   = MSGID_CONFIG_RESTORE_NO_SUCH_BACKUP;
      String message = getMessage(msgID, backupID, backupPath);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }


    // Read the backup info structure to determine the name of the file that
    // contains the archive.  Then make sure that file exists.
    String backupFilename =
         backupInfo.getBackupProperty(BACKUP_PROPERTY_ARCHIVE_FILENAME);
    if (backupFilename == null)
    {
      int    msgID   = MSGID_CONFIG_RESTORE_NO_BACKUP_FILE;
      String message = getMessage(msgID, backupID, backupPath);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }

    File backupFile = new File(backupPath + File.separator + backupFilename);
    try
    {
      if (! backupFile.exists())
      {
        int    msgID   = MSGID_CONFIG_RESTORE_NO_SUCH_FILE;
        String message = getMessage(msgID, backupID, backupFile.getPath());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }
    }
    catch (DirectoryException de)
    {
      throw de;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIG_RESTORE_CANNOT_CHECK_FOR_ARCHIVE;
      String message = getMessage(msgID, backupID, backupFile.getPath(),
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }


    // If the backup is hashed, then we need to get the message digest to use
    // to verify it.
    byte[] unsignedHash = backupInfo.getUnsignedHash();
    MessageDigest digest = null;
    if (unsignedHash != null)
    {
      String digestAlgorithm =
           backupInfo.getBackupProperty(BACKUP_PROPERTY_DIGEST_ALGORITHM);
      if (digestAlgorithm == null)
      {
        int    msgID   = MSGID_CONFIG_RESTORE_UNKNOWN_DIGEST;
        String message = getMessage(msgID, backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }

      try
      {
        digest = DirectoryServer.getCryptoManager().getMessageDigest(
                                                         digestAlgorithm);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CONFIG_RESTORE_CANNOT_GET_DIGEST;
        String message = getMessage(msgID, backupID, digestAlgorithm);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
    }


    // If the backup is signed, then we need to get the MAC to use to verify it.
    byte[] signedHash = backupInfo.getSignedHash();
    Mac mac = null;
    if (signedHash != null)
    {
      String macAlgorithm =
           backupInfo.getBackupProperty(BACKUP_PROPERTY_MAC_ALGORITHM);
      if (macAlgorithm == null)
      {
        int    msgID   = MSGID_CONFIG_RESTORE_UNKNOWN_MAC;
        String message = getMessage(msgID, backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }

      try
      {
        mac = DirectoryServer.getCryptoManager().getMACProvider(macAlgorithm);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CONFIG_RESTORE_CANNOT_GET_MAC;
        String message = getMessage(msgID, backupID, macAlgorithm,
                                    backupFile.getPath());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
    }


    // Create the input stream that will be used to read the backup file.  At
    // its core, it will be a file input stream.
    InputStream inputStream;
    try
    {
      inputStream = new FileInputStream(backupFile);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIG_RESTORE_CANNOT_OPEN_BACKUP_FILE;
      String message = getMessage(msgID, backupID, backupFile.getPath(),
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }

    // If the backup is encrypted, then we need to wrap the file input stream
    // in a cipher input stream.
    if (backupInfo.isEncrypted())
    {
      String cipherAlgorithm =
           backupInfo.getBackupProperty(BACKUP_PROPERTY_CIPHER_ALGORITHM);
      if (cipherAlgorithm == null)
      {
        int    msgID   = MSGID_CONFIG_RESTORE_UNKNOWN_CIPHER;
        String message = getMessage(msgID, backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }

      Cipher cipher;
      try
      {
        cipher = DirectoryServer.getCryptoManager().getCipher(cipherAlgorithm,
                                                         Cipher.DECRYPT_MODE);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CONFIG_RESTORE_CANNOT_GET_CIPHER;
        String message = getMessage(msgID, cipherAlgorithm,
                                    backupFile.getPath(),
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }

      inputStream = new CipherInputStream(inputStream, cipher);
    }

    // Now wrap the resulting input stream in a zip stream so that we can read
    // its contents.  We don't need to worry about whether to use compression or
    // not because it will be handled automatically.
    ZipInputStream zipStream = new ZipInputStream(inputStream);


    // Determine whether we should actually do the restore, or if we should just
    // try to verify the archive.  If we are going to actually do the restore,
    // then create a directory and move the existing config files there so that
    // they can be restored in case something goes wrong.
    String configFilePath  =
         ((ConfigFileHandler) DirectoryServer.getConfigHandler()).configFile;
    File   configFile      = new File(configFilePath);
    File   configDir       = configFile.getParentFile();
    String configDirPath   = configDir.getPath();
    String backupDirPath   = null;
    File   configBackupDir = null;
    boolean verifyOnly     = restoreConfig.verifyOnly();
    if (! verifyOnly)
    {
      // Create a new directory to hold the current config files.
      try
      {
        if (configDir.exists())
        {
          String configBackupDirPath = configDirPath + ".save";
          backupDirPath = configBackupDirPath;
          configBackupDir = new File(backupDirPath);
          if (configBackupDir.exists())
          {
            int i=2;
            while (true)
            {
              backupDirPath = configBackupDirPath + i;
              configBackupDir = new File(backupDirPath);
              if (configBackupDir.exists())
              {
                i++;
              }
              else
              {
                break;
              }
            }
          }

          configBackupDir.mkdirs();
          moveFile(configFile, configBackupDir);

          File archiveDirectory = new File(configDir, CONFIG_ARCHIVE_DIR_NAME);
          if (archiveDirectory.exists())
          {
            File archiveBackupPath = new File(configBackupDir,
                                              CONFIG_ARCHIVE_DIR_NAME);
            archiveDirectory.renameTo(archiveBackupPath);
          }
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CONFIG_RESTORE_CANNOT_BACKUP_EXISTING_CONFIG;
        String message = getMessage(msgID, backupID, configDirPath,
                                    String.valueOf(backupDirPath),
                                    getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }


      // Create a new directory to hold the restored config files.
      try
      {
        configDir.mkdirs();
      }
      catch (Exception e)
      {
        // Try to restore the previous config directory if possible.  This will
        // probably fail in this case, but try anyway.
        if (configBackupDir != null)
        {
          try
          {
            configBackupDir.renameTo(configDir);
            int    msgID   = MSGID_CONFIG_RESTORE_RESTORED_OLD_CONFIG;
            String message = getMessage(msgID, configDirPath);
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                     msgID);
          }
          catch (Exception e2)
          {
            int msgID = MSGID_CONFIG_RESTORE_CANNOT_RESTORE_OLD_CONFIG;
            String message = getMessage(msgID, configBackupDir.getPath());
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
          }
        }


        int    msgID   = MSGID_CONFIG_RESTORE_CANNOT_CREATE_CONFIG_DIRECTORY;
        String message = getMessage(msgID, backupID, configDirPath,
                                    getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
    }


    // Read through the archive file an entry at a time.  For each entry, update
    // the digest or MAC if necessary, and if we're actually doing the restore,
    // then write the files out into the config directory.
    byte[] buffer = new byte[8192];
    while (true)
    {
      ZipEntry zipEntry;
      try
      {
        zipEntry = zipStream.getNextEntry();
      }
      catch (Exception e)
      {
        // Tell the user where the previous config was archived.
        if (configBackupDir != null)
        {
          int    msgID   = MSGID_CONFIG_RESTORE_OLD_CONFIG_SAVED;
          String message = getMessage(msgID, configBackupDir.getPath());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                   msgID);
        }

        int    msgID   = MSGID_CONFIG_RESTORE_CANNOT_GET_ZIP_ENTRY;
        String message = getMessage(msgID, backupID, backupFile.getPath(),
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }

      if (zipEntry == null)
      {
        break;
      }


      // Get the filename for the zip entry and update the digest or MAC as
      // necessary.
      String fileName = zipEntry.getName();
      if (digest != null)
      {
        digest.update(getBytes(fileName));
      }
      if (mac != null)
      {
        mac.update(getBytes(fileName));
      }


      // If we're doing the restore, then create the output stream to write the
      // file.
      OutputStream outputStream = null;
      if (! verifyOnly)
      {
        File restoreFile = new File(configDirPath + File.separator + fileName);
        File parentDir   = restoreFile.getParentFile();

        try
        {
          if (! parentDir.exists())
          {
            parentDir.mkdirs();
          }

          outputStream = new FileOutputStream(restoreFile);
        }
        catch (Exception e)
        {
          // Tell the user where the previous config was archived.
          if (configBackupDir != null)
          {
            int    msgID   = MSGID_CONFIG_RESTORE_OLD_CONFIG_SAVED;
            String message = getMessage(msgID, configBackupDir.getPath());
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                     msgID);
          }

          int    msgID   = MSGID_CONFIG_RESTORE_CANNOT_CREATE_FILE;
          String message = getMessage(msgID, backupID,
                                      restoreFile.getAbsolutePath(),
                                      stackTraceToSingleLineString(e));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), message,
                         msgID, e);
        }
      }


      // Read the contents of the file and update the digest or MAC as
      // necessary.  If we're actually restoring it, then write it into the
      // new config directory.
      try
      {
        while (true)
        {
          int bytesRead = zipStream.read(buffer);
          if (bytesRead < 0)
          {
            // We've reached the end of the entry.
            break;
          }


          // Update the digest or MAC if appropriate.
          if (digest != null)
          {
            digest.update(buffer, 0, bytesRead);
          }

          if (mac != null)
          {
            mac.update(buffer, 0, bytesRead);
          }


          //  Write the data to the output stream if appropriate.
          if (outputStream != null)
          {
            outputStream.write(buffer, 0, bytesRead);
          }
        }


        // We're at the end of the file so close the output stream if we're
        // writing it.
        if (outputStream != null)
        {
          outputStream.close();
        }
      }
      catch (Exception e)
      {
        // Tell the user where the previous config was archived.
        if (configBackupDir != null)
        {
          int    msgID   = MSGID_CONFIG_RESTORE_OLD_CONFIG_SAVED;
          String message = getMessage(msgID, configBackupDir.getPath());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                   msgID);
        }

        int msgID = MSGID_CONFIG_RESTORE_CANNOT_PROCESS_ARCHIVE_FILE;
        String message = getMessage(msgID, backupID, fileName,
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
    }


    // Close the zip stream since we don't need it anymore.
    try
    {
      zipStream.close();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIG_RESTORE_ERROR_ON_ZIP_STREAM_CLOSE;
      String message = getMessage(msgID, backupID, backupFile.getPath(),
                                  getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }


    // At this point, we should be done with the contents of the ZIP file and
    // the restore should be complete.  If we were generating a digest or MAC,
    // then make sure it checks out.
    if (digest != null)
    {
      byte[] calculatedHash = digest.digest();
      if (Arrays.equals(calculatedHash, unsignedHash))
      {
        int    msgID = MSGID_CONFIG_RESTORE_UNSIGNED_HASH_VALID;
        String message = getMessage(msgID);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                 msgID);
      }
      else
      {
        // Tell the user where the previous config was archived.
        if (configBackupDir != null)
        {
          int    msgID   = MSGID_CONFIG_RESTORE_OLD_CONFIG_SAVED;
          String message = getMessage(msgID, configBackupDir.getPath());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                   msgID);
        }

        int    msgID = MSGID_CONFIG_RESTORE_UNSIGNED_HASH_INVALID;
        String message = getMessage(msgID, backupID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }
    }

    if (mac != null)
    {
      byte[] calculatedSignature = mac.doFinal();
      if (Arrays.equals(calculatedSignature, signedHash))
      {
        int    msgID = MSGID_CONFIG_RESTORE_SIGNED_HASH_VALID;
        String message = getMessage(msgID);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                 msgID);
      }
      else
      {
        // Tell the user where the previous config was archived.
        if (configBackupDir != null)
        {
          int    msgID   = MSGID_CONFIG_RESTORE_OLD_CONFIG_SAVED;
          String message = getMessage(msgID, configBackupDir.getPath());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                   msgID);
        }

        int    msgID = MSGID_CONFIG_RESTORE_SIGNED_HASH_INVALID;
        String message = getMessage(msgID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }
    }


    // If we are just verifying the archive, then we're done.
    if (verifyOnly)
    {
      int    msgID   = MSGID_CONFIG_RESTORE_VERIFY_SUCCESSFUL;
      String message = getMessage(msgID, backupID, backupPath);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
               msgID);
      return;
    }


    // If we've gotten here, then the archive was restored successfully.  Get
    // rid of the temporary copy we made of the previous config directory and
    // exit.
    if (configBackupDir != null)
    {
      recursiveDelete(configBackupDir);
    }

    int    msgID   = MSGID_CONFIG_RESTORE_SUCCESSFUL;
    String message = getMessage(msgID, backupID, backupPath);
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
             msgID);
  }



  /**
   * Retrieves the DN of the configuration entry with which this alert generator
   * is associated.
   *
   * @return  The DN of the configuration entry with which this alert generator
   *          is associated.
   */
  public DN getComponentEntryDN()
  {
    return configRootEntry.getDN();
  }



  /**
   * Retrieves the fully-qualified name of the Java class for this alert
   * generator implementation.
   *
   * @return  The fully-qualified name of the Java class for this alert
   *          generator implementation.
   */
  public String getClassName()
  {
    return CLASS_NAME;
  }



  /**
   * Retrieves information about the set of alerts that this generator may
   * produce.  The map returned should be between the notification type for a
   * particular notification and the human-readable description for that
   * notification.  This alert generator must not generate any alerts with types
   * that are not contained in this list.
   *
   * @return  Information about the set of alerts that this generator may
   *          produce.
   */
  public LinkedHashMap<String,String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(ALERT_TYPE_CANNOT_WRITE_CONFIGURATION,
               ALERT_DESCRIPTION_CANNOT_WRITE_CONFIGURATION);
    alerts.put(ALERT_TYPE_MANUAL_CONFIG_EDIT_HANDLED,
               ALERT_DESCRIPTION_MANUAL_CONFIG_EDIT_HANDLED);
    alerts.put(ALERT_TYPE_MANUAL_CONFIG_EDIT_LOST,
               ALERT_DESCRIPTION_MANUAL_CONFIG_EDIT_LOST);

    return alerts;
  }



  /**
   * Examines the provided result and logs a message if appropriate.  If the
   * result code is anything other than {@code SUCCESS}, then it will log an
   * error message.  If the operation was successful but admin action is
   * required, then it will log a warning message.  If no action is required but
   * messages were generated, then it will log an informational message.
   *
   * @param  result      The config change result object that
   * @param  entryDN     The DN of the entry that was added, deleted, or
   *                     modified.
   * @param  className   The name of the class for the object that generated the
   *                     provided result.
   * @param  methodName  The name of the method that generated the provided
   *                     result.
   */
  public void handleConfigChangeResult(ConfigChangeResult result, DN entryDN,
                                       String className, String methodName)
  {
    if (result == null)
    {
      int    msgID   = MSGID_CONFIG_CHANGE_NO_RESULT;
      String message = getMessage(msgID, String.valueOf(className),
                                  String.valueOf(methodName),
                                  String.valueOf(entryDN));
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return;
    }

    ResultCode   resultCode          = result.getResultCode();
    boolean      adminActionRequired = result.adminActionRequired();
    List<String> messages            = result.getMessages();

    StringBuilder messageBuffer = new StringBuilder();
    if (messages != null)
    {
      for (String s : messages)
      {
        if (messageBuffer.length() > 0)
        {
          messageBuffer.append("  ");
        }
        messageBuffer.append(s);
      }
    }


    if (resultCode != ResultCode.SUCCESS)
    {
      int    msgID   = MSGID_CONFIG_CHANGE_RESULT_ERROR;
      String message = getMessage(msgID, String.valueOf(className),
                                  String.valueOf(methodName),
                                  String.valueOf(entryDN),
                                  String.valueOf(resultCode),
                                  adminActionRequired,
                                  messageBuffer.toString());
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    }
    else if (adminActionRequired)
    {
      int    msgID   = MSGID_CONFIG_CHANGE_RESULT_ACTION_REQUIRED;
      String message = getMessage(msgID, String.valueOf(className),
                                  String.valueOf(methodName),
                                  String.valueOf(entryDN),
                                  messageBuffer.toString());
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }
    else if (messageBuffer.length() > 0)
    {
      int    msgID   = MSGID_CONFIG_CHANGE_RESULT_MESSAGES;
      String message = getMessage(msgID, String.valueOf(className),
                                  String.valueOf(methodName),
                                  String.valueOf(entryDN),
                                  messageBuffer.toString());
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.INFORMATIONAL,
               message, msgID);
    }
  }
}

