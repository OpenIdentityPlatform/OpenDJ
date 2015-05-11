/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.schema.BooleanSyntax.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.BackupBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.*;

/**
 * This class defines a backend used to present information about Directory
 * Server backups.  It will not actually store anything, but upon request will
 * retrieve information about the backups that it knows about.  The backups will
 * be arranged in a hierarchy based on the directory that contains them, and
 * it may be possible to dynamically discover new backups if a previously
 * unknown backup directory is included in the base DN.
 */
public class BackupBackend
       extends Backend<BackupBackendCfg>
       implements ConfigurationChangeListener<BackupBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /** The current configuration state. */
  private BackupBackendCfg currentConfig;

  /** The DN for the base backup entry. */
  private DN backupBaseDN;

  /** The set of base DNs for this backend. */
  private DN[] baseDNs;

  /** The backup base entry. */
  private Entry backupBaseEntry;

  /** A cache of BackupDirectories. */
  private HashMap<File,CachedBackupDirectory> backupDirectories;

  /**
   * To avoid parsing and reparsing the contents of backup.info files, we
   * cache the BackupDirectory for each directory using this class.
   */
  private class CachedBackupDirectory
  {
    /** The path to the 'bak' directory. */
    private final String directoryPath;

    /** The 'backup.info' file. */
    private final File backupInfo;

    /** The last modify time of the backupInfo file. */
    private long lastModified;

    /** The BackupDirectory parsed at lastModified time. */
    private BackupDirectory backupDirectory;

    /**
     * A BackupDirectory that is cached based on the backup descriptor file.
     *
     * @param directory Path to the backup directory itself.
     */
    public CachedBackupDirectory(File directory)
    {
      directoryPath = directory.getPath();
      backupInfo = new File(directoryPath + File.separator + BACKUP_DIRECTORY_DESCRIPTOR_FILE);
      lastModified = -1;
      backupDirectory = null;
    }

    /**
     * Return a BackupDirectory. This will be recomputed every time the underlying descriptor (backup.info) file
     * changes.
     *
     * @return An up-to-date BackupDirectory
     * @throws IOException If a problem occurs while trying to read the contents of the descriptor file.
     * @throws ConfigException If the contents of the descriptor file cannot be parsed to create a backup directory
     *                         structure.
     */
    public synchronized BackupDirectory getBackupDirectory()
            throws IOException, ConfigException
    {
      long currentModified = backupInfo.lastModified();
      if (backupDirectory == null || currentModified != lastModified)
      {
        backupDirectory = BackupDirectory.readBackupDirectoryDescriptor(directoryPath);
        lastModified = currentModified;
      }
      return backupDirectory;
    }
  }


  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public BackupBackend()
  {
    super();

    // Perform all initialization in initializeBackend.
  }



  /** {@inheritDoc} */
  @Override
  public void configureBackend(BackupBackendCfg config, ServerContext serverContext) throws ConfigException
  {
    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (config == null)
    {
      throw new ConfigException(ERR_BACKEND_CONFIG_ENTRY_NULL.get(getBackendID()));
    }
    currentConfig = config;
  }



  /** {@inheritDoc} */
  @Override
  public void openBackend()
         throws ConfigException, InitializationException
  {
    // Create the set of base DNs that we will handle.  In this case, it's just
    // the DN of the base backup entry.
    try
    {
      backupBaseDN = DN.valueOf(DN_BACKUP_ROOT);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_BACKEND_CANNOT_DECODE_BACKEND_ROOT_DN.get(getExceptionMessage(e), getBackendID());
      throw new InitializationException(message, e);
    }

    // FIXME -- Deal with this more correctly.
    this.baseDNs = new DN[] { backupBaseDN };


    // Determine the set of backup directories that we will use by default.
    Set<String> values = currentConfig.getBackupDirectory();
    backupDirectories = new LinkedHashMap<File,CachedBackupDirectory>(values.size());
    for (String s : values)
    {
      File dir = getFileForPath(s);
      backupDirectories.put(dir, new CachedBackupDirectory(dir));
    }


    // Construct the backup base entry.
    LinkedHashMap<ObjectClass,String> objectClasses =
         new LinkedHashMap<ObjectClass,String>(2);
    objectClasses.put(DirectoryServer.getTopObjectClass(), OC_TOP);

    ObjectClass untypedOC =
         DirectoryServer.getObjectClass(OC_UNTYPED_OBJECT_LC, true);
    objectClasses.put(untypedOC, OC_UNTYPED_OBJECT);

    LinkedHashMap<AttributeType,List<Attribute>> opAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>(0);
    LinkedHashMap<AttributeType,List<Attribute>> userAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>(1);

    RDN rdn = backupBaseDN.rdn();
    int numAVAs = rdn.getNumValues();
    for (int i=0; i < numAVAs; i++)
    {
      AttributeType attrType = rdn.getAttributeType(i);
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(Attributes.create(attrType, rdn
          .getAttributeValue(i)));

      userAttrs.put(attrType, attrList);
    }

    backupBaseEntry = new Entry(backupBaseDN, objectClasses, userAttrs,
                                opAttrs);

    currentConfig.addBackupChangeListener(this);

    // Register the backup base as a private suffix.
    try
    {
      DirectoryServer.registerBaseDN(backupBaseDN, this, true);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
          backupBaseDN, getExceptionMessage(e));
      throw new InitializationException(message, e);
    }
  }



  /** {@inheritDoc} */
  @Override
  public void closeBackend()
  {
    currentConfig.removeBackupChangeListener(this);

    try
    {
      DirectoryServer.deregisterBaseDN(backupBaseDN);
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
  }



  /** {@inheritDoc} */
  @Override
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }



  /** {@inheritDoc} */
  @Override
  public long getEntryCount()
  {
    int numEntries = 1;

    AttributeType backupPathType =
         DirectoryServer.getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);

    for (File dir : backupDirectories.keySet())
    {
      try
      {
        // Check to see if the descriptor file exists.  If not, then skip this
        // backup directory.
        File descriptorFile = new File(dir, BACKUP_DIRECTORY_DESCRIPTOR_FILE);
        if (! descriptorFile.exists())
        {
          continue;
        }

        DN backupDirDN = makeChildDN(backupBaseDN, backupPathType,
                                     dir.getAbsolutePath());
        getBackupDirectoryEntry(backupDirDN);
        numEntries++;
      }
      catch (Exception e) {}
    }

    return numEntries;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
  {
    long ret = getNumberOfSubordinates(entryDN, false);
    if(ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    return ConditionResult.valueOf(ret != 0);
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException {
    checkNotNull(baseDN, "baseDN must not be null");
    return getNumberOfSubordinates(baseDN, true) + 1;
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException {
    checkNotNull(parentDN, "parentDN must not be null");
    return getNumberOfSubordinates(parentDN, false);
  }

  private long getNumberOfSubordinates(DN entryDN, boolean includeSubtree) throws DirectoryException
  {
    // If the requested entry was the backend base entry, then return
    // the number of backup directories.
    if (backupBaseDN.equals(entryDN))
    {
      long count = 0;
      for (File dir : backupDirectories.keySet())
      {
        // Check to see if the descriptor file exists.  If not, then skip this
        // backup directory.
        File descriptorFile = new File(dir, BACKUP_DIRECTORY_DESCRIPTOR_FILE);
        if (! descriptorFile.exists())
        {
          continue;
        }

        // If subtree is included, count the number of entries for each
        // backup directory.
        if (includeSubtree)
        {
          count++;
          try
          {
            BackupDirectory backupDirectory = backupDirectories.get(dir).getBackupDirectory();
            count += backupDirectory.getBackups().keySet().size();
          }
          catch (Exception e)
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_BACKUP_INVALID_BACKUP_DIRECTORY.get(
                entryDN, e.getMessage()));
          }
        }

        count ++;
      }
      return count;
    }

    // See if the requested entry was one level below the backend base entry.
    // If so, then it must point to a backup directory.  Otherwise, it must be
    // two levels below the backup base entry and must point to a specific
    // backup.
    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null)
    {
      return -1;
    }
    else if (backupBaseDN.equals(parentDN))
    {
      long count = 0;
      Entry backupDirEntry = getBackupDirectoryEntry(entryDN);

      AttributeType t =
          DirectoryServer.getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);
      List<Attribute> attrList = backupDirEntry.getAttribute(t);
      if (attrList != null && !attrList.isEmpty())
      {
        for (ByteString v : attrList.get(0))
        {
          try
          {
            File dir = new File(v.toString());
            BackupDirectory backupDirectory = backupDirectories.get(dir).getBackupDirectory();
            count += backupDirectory.getBackups().keySet().size();
          }
          catch (Exception e)
          {
            return -1;
          }
        }
      }
      return count;
    }
    else if (backupBaseDN.equals(parentDN.getParentDNInSuffix()))
    {
      return 0;
    }
    else
    {
      return -1;
    }
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    // If the requested entry was null, then throw an exception.
    if (entryDN == null)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_BACKEND_GET_ENTRY_NULL.get(getBackendID()));
    }


    // If the requested entry was the backend base entry, then retrieve it.
    if (entryDN.equals(backupBaseDN))
    {
      return backupBaseEntry.duplicate(true);
    }


    // See if the requested entry was one level below the backend base entry.
    // If so, then it must point to a backup directory.  Otherwise, it must be
    // two levels below the backup base entry and must point to a specific
    // backup.
    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null)
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          ERR_BACKUP_INVALID_BASE.get(entryDN));
    }
    else if (parentDN.equals(backupBaseDN))
    {
      return getBackupDirectoryEntry(entryDN);
    }
    else if (backupBaseDN.equals(parentDN.getParentDNInSuffix()))
    {
      return getBackupEntry(entryDN);
    }
    else
    {
      LocalizableMessage message = ERR_BACKUP_INVALID_BASE.get(entryDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
              message, backupBaseDN, null);
    }
  }



  /**
   * Generates an entry for a backup directory based on the provided DN.  The
   * DN must contain an RDN component that specifies the path to the backup
   * directory, and that directory must exist and be a valid backup directory.
   *
   * @param  entryDN  The DN of the backup directory entry to retrieve.
   *
   * @return  The requested backup directory entry.
   *
   * @throws  DirectoryException  If the specified directory does not exist or
   *                              is not a valid backup directory, or if the DN
   *                              does not specify any backup directory.
   */
  private Entry getBackupDirectoryEntry(DN entryDN)
         throws DirectoryException
  {
    // Make sure that the DN specifies a backup directory.
    AttributeType t =
         DirectoryServer.getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);
    ByteString v = entryDN.rdn().getAttributeValue(t);
    if (v == null)
    {
      LocalizableMessage message =
          ERR_BACKUP_DN_DOES_NOT_SPECIFY_DIRECTORY.get(entryDN);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   backupBaseDN, null);
    }


    // Get a handle to the backup directory and the information that it
    // contains.
    BackupDirectory backupDirectory;
    try
    {
      File dir = new File(v.toString());
      backupDirectory = backupDirectories.get(dir).getBackupDirectory();
    }
    catch (ConfigException ce)
    {
      logger.traceException(ce);

      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_BACKUP_INVALID_BACKUP_DIRECTORY.get(entryDN, ce.getMessage()));
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_BACKUP_ERROR_GETTING_BACKUP_DIRECTORY.get(getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }


    // Construct the backup directory entry to return.
    LinkedHashMap<ObjectClass,String> ocMap =
        new LinkedHashMap<ObjectClass,String>(2);
    ocMap.put(DirectoryServer.getTopObjectClass(), OC_TOP);

    ObjectClass backupDirOC =
         DirectoryServer.getObjectClass(OC_BACKUP_DIRECTORY, true);
    ocMap.put(backupDirOC, OC_BACKUP_DIRECTORY);

    LinkedHashMap<AttributeType,List<Attribute>> opAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>(0);
    LinkedHashMap<AttributeType,List<Attribute>> userAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>(3);

    userAttrs.put(t, asList(t, v));

    t = DirectoryServer.getAttributeType(ATTR_BACKUP_BACKEND_DN, true);
    userAttrs.put(t, asList(t, ByteString.valueOf(backupDirectory.getConfigEntryDN().toString())));

    Entry e = new Entry(entryDN, ocMap, userAttrs, opAttrs);
    e.processVirtualAttributes();
    return e;
  }



  /**
   * Generates an entry for a backup based on the provided DN.  The DN must
   * have an RDN component that specifies the backup ID, and the parent DN must
   * have an RDN component that specifies the backup directory.
   *
   * @param  entryDN  The DN of the backup entry to retrieve.
   *
   * @return  The requested backup entry.
   *
   * @throws  DirectoryException  If the specified backup does not exist or is
   *                              invalid.
   */
  private Entry getBackupEntry(DN entryDN)
          throws DirectoryException
  {
    // First, get the backup ID from the entry DN.
    AttributeType idType = DirectoryServer.getAttributeType(ATTR_BACKUP_ID, true);
    ByteString idValue = entryDN.rdn().getAttributeValue(idType);
    if (idValue == null) {
      throw newConstraintViolation(ERR_BACKUP_NO_BACKUP_ID_IN_DN.get(entryDN));
    }
    String backupID = idValue.toString();

    // Next, get the backup directory from the parent DN.
    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null) {
      throw newConstraintViolation(ERR_BACKUP_NO_BACKUP_PARENT_DN.get(entryDN));
    }

    AttributeType t = DirectoryServer.getAttributeType(
        ATTR_BACKUP_DIRECTORY_PATH, true);
    ByteString v = parentDN.rdn().getAttributeValue(t);
    if (v == null) {
      throw newConstraintViolation(ERR_BACKUP_NO_BACKUP_DIR_IN_DN.get(entryDN));
    }

    BackupDirectory backupDirectory;
    try {
      backupDirectory = backupDirectories.get(new File(v.toString())).getBackupDirectory();
    } catch (ConfigException ce) {
      logger.traceException(ce);

      throw newConstraintViolation(ERR_BACKUP_INVALID_BACKUP_DIRECTORY.get(entryDN, ce.getMessageObject()));
    } catch (Exception e) {
      logger.traceException(e);

      LocalizableMessage message = ERR_BACKUP_ERROR_GETTING_BACKUP_DIRECTORY
          .get(getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message);
    }

    BackupInfo backupInfo = backupDirectory.getBackupInfo(backupID);
    if (backupInfo == null) {
      LocalizableMessage message = ERR_BACKUP_NO_SUCH_BACKUP.get(backupID, backupDirectory
          .getPath());
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
          parentDN, null);
    }

    // Construct the backup entry to return.
    LinkedHashMap<ObjectClass, String> ocMap =
      new LinkedHashMap<ObjectClass, String>(3);
    ocMap.put(DirectoryServer.getTopObjectClass(), OC_TOP);

    ObjectClass oc = DirectoryServer.getObjectClass(OC_BACKUP_INFO, true);
    ocMap.put(oc, OC_BACKUP_INFO);

    oc = DirectoryServer.getObjectClass(OC_EXTENSIBLE_OBJECT_LC, true);
    ocMap.put(oc, OC_EXTENSIBLE_OBJECT);

    LinkedHashMap<AttributeType, List<Attribute>> opAttrs =
      new LinkedHashMap<AttributeType, List<Attribute>>(0);
    LinkedHashMap<AttributeType, List<Attribute>> userAttrs =
      new LinkedHashMap<AttributeType, List<Attribute>>();

    userAttrs.put(idType, asList(idType, idValue));

    backupInfo.getBackupDirectory();
    userAttrs.put(t, asList(t, v));

    Date backupDate = backupInfo.getBackupDate();
    if (backupDate != null) {
      t = DirectoryServer.getAttributeType(ATTR_BACKUP_DATE, true);
      userAttrs.put(t,
          asList(t, ByteString.valueOf(GeneralizedTimeSyntax.format(backupDate))));
    }

    putBoolean(userAttrs, ATTR_BACKUP_COMPRESSED, backupInfo.isCompressed());
    putBoolean(userAttrs, ATTR_BACKUP_ENCRYPTED, backupInfo.isEncrypted());
    putBoolean(userAttrs, ATTR_BACKUP_INCREMENTAL, backupInfo.isIncremental());

    HashSet<String> dependencies = backupInfo.getDependencies();
    if (dependencies != null && !dependencies.isEmpty()) {
      t = DirectoryServer.getAttributeType(ATTR_BACKUP_DEPENDENCY, true);
      AttributeBuilder builder = new AttributeBuilder(t);
      for (String s : dependencies) {
        builder.add(s);
      }
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(builder.toAttribute());
      userAttrs.put(t, attrList);
    }

    byte[] signedHash = backupInfo.getSignedHash();
    if (signedHash != null) {
      putByteString(userAttrs, ATTR_BACKUP_SIGNED_HASH, signedHash);
    }

    byte[] unsignedHash = backupInfo.getUnsignedHash();
    if (unsignedHash != null) {
      putByteString(userAttrs, ATTR_BACKUP_UNSIGNED_HASH, unsignedHash);
    }

    HashMap<String, String> properties = backupInfo.getBackupProperties();
    if (properties != null && !properties.isEmpty()) {
      for (Map.Entry<String, String> e : properties.entrySet()) {
        t = DirectoryServer.getAttributeType(toLowerCase(e.getKey()), true);
        userAttrs.put(t, asList(t, ByteString.valueOf(e.getValue())));
      }
    }

    Entry e = new Entry(entryDN, ocMap, userAttrs, opAttrs);
    e.processVirtualAttributes();
    return e;
  }

  private void putByteString(LinkedHashMap<AttributeType, List<Attribute>> userAttrs, String attrName, byte[] value)
  {
    AttributeType t;
    t = DirectoryServer.getAttributeType(attrName, true);
    userAttrs.put(t, asList(t, ByteString.wrap(value)));
  }

  private void putBoolean(LinkedHashMap<AttributeType, List<Attribute>> attrsMap, String attrName, boolean value)
  {
    AttributeType t = DirectoryServer.getAttributeType(attrName, true);
    attrsMap.put(t, asList(t, createBooleanValue(value)));
  }

  private ArrayList<Attribute> asList(AttributeType attrType, ByteString value)
  {
    final ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(Attributes.create(attrType, value));
    return attrList;
  }

  private DirectoryException newConstraintViolation(LocalizableMessage message)
  {
    return new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
  }

  /** {@inheritDoc} */
  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_ADD_NOT_SUPPORTED.get(entry.getName(), getBackendID()));
  }



  /** {@inheritDoc} */
  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_DELETE_NOT_SUPPORTED.get(entryDN, getBackendID()));
  }



  /** {@inheritDoc} */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_NOT_SUPPORTED.get(oldEntry.getName(), getBackendID()));
  }



  /** {@inheritDoc} */
  @Override
  public void renameEntry(DN currentDN, Entry entry,
                                   ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_DN_NOT_SUPPORTED.get(currentDN, getBackendID()));
  }



  /** {@inheritDoc} */
  @Override
  public void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    // Get the base entry for the search, if possible.  If it doesn't exist,
    // then this will throw an exception.
    DN    baseDN    = searchOperation.getBaseDN();
    Entry baseEntry = getEntry(baseDN);


    // Look at the base DN and see if it's the backup base DN, a backup
    // directory entry DN, or a backup entry DN.
    DN parentDN;
    SearchScope  scope  = searchOperation.getScope();
    SearchFilter filter = searchOperation.getFilter();
    if (backupBaseDN.equals(baseDN))
    {
      if ((scope == SearchScope.BASE_OBJECT || scope == SearchScope.WHOLE_SUBTREE)
          && filter.matchesEntry(baseEntry))
      {
        searchOperation.returnEntry(baseEntry, null);
      }

      if (scope != SearchScope.BASE_OBJECT && !backupDirectories.isEmpty())
      {
        AttributeType backupPathType =
             DirectoryServer.getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);
        for (File dir : backupDirectories.keySet())
        {
          // Check to see if the descriptor file exists.  If not, then skip this
          // backup directory.
          File descriptorFile = new File(dir, BACKUP_DIRECTORY_DESCRIPTOR_FILE);
          if (! descriptorFile.exists())
          {
            continue;
          }


          DN backupDirDN = makeChildDN(backupBaseDN, backupPathType,
                                       dir.getAbsolutePath());

          Entry backupDirEntry;
          try
          {
            backupDirEntry = getBackupDirectoryEntry(backupDirDN);
          }
          catch (Exception e)
          {
            logger.traceException(e);

            continue;
          }

          if (filter.matchesEntry(backupDirEntry))
          {
            searchOperation.returnEntry(backupDirEntry, null);
          }

          if (scope != SearchScope.SINGLE_LEVEL)
          {
            List<Attribute> attrList = backupDirEntry.getAttribute(backupPathType);
            returnEntries(searchOperation, backupDirDN, filter, attrList);
          }
        }
      }
    }
    else if (backupBaseDN.equals(parentDN = baseDN.getParentDNInSuffix()))
    {
      Entry backupDirEntry = getBackupDirectoryEntry(baseDN);

      if ((scope == SearchScope.BASE_OBJECT || scope == SearchScope.WHOLE_SUBTREE)
          && filter.matchesEntry(backupDirEntry))
      {
        searchOperation.returnEntry(backupDirEntry, null);
      }


      if (scope != SearchScope.BASE_OBJECT)
      {
        AttributeType t =
             DirectoryServer.getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);
        List<Attribute> attrList = backupDirEntry.getAttribute(t);
        returnEntries(searchOperation, baseDN, filter, attrList);
      }
    }
    else
    {
      if (parentDN == null
          || !backupBaseDN.equals(parentDN.getParentDNInSuffix()))
      {
        LocalizableMessage message = ERR_BACKUP_NO_SUCH_ENTRY.get(backupBaseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }

      if (scope == SearchScope.BASE_OBJECT ||
          scope == SearchScope.WHOLE_SUBTREE)
      {
        Entry backupEntry = getBackupEntry(baseDN);
        if (backupEntry == null)
        {
          LocalizableMessage message = ERR_BACKUP_NO_SUCH_ENTRY.get(backupBaseDN);
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
        }

        if (filter.matchesEntry(backupEntry))
        {
          searchOperation.returnEntry(backupEntry, null);
        }
      }
    }
  }

  private void returnEntries(SearchOperation searchOperation, DN baseDN, SearchFilter filter, List<Attribute> attrList)
  {
    if (attrList != null && !attrList.isEmpty())
    {
      for (ByteString v : attrList.get(0))
      {
        try
        {
          File dir = new File(v.toString());
          BackupDirectory backupDirectory = backupDirectories.get(dir).getBackupDirectory();
          AttributeType idType = DirectoryServer.getAttributeType(ATTR_BACKUP_ID, true);

          for (String backupID : backupDirectory.getBackups().keySet())
          {
            DN backupEntryDN = makeChildDN(baseDN, idType, backupID);
            Entry backupEntry = getBackupEntry(backupEntryDN);
            if (filter.matchesEntry(backupEntry))
            {
              searchOperation.returnEntry(backupEntry, null);
            }
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          continue;
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedControls()
  {
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(BackendOperation backendOperation)
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void createBackup(BackupConfig backupConfig)
  throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
       BackupBackendCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    // We'll accept anything here.  The only configurable attribute is the
    // default set of backup directories, but that doesn't require any
    // validation at this point.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(BackupBackendCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    Set<String> values = cfg.getBackupDirectory();
    backupDirectories = new LinkedHashMap<File,CachedBackupDirectory>(values.size());
    for (String s : values)
    {
      File dir = getFileForPath(s);
      backupDirectories.put(dir, new CachedBackupDirectory(dir));
    }

    currentConfig = cfg;
    return ccr;
  }

  /**
   * Create a new child DN from a given parent DN.  The child RDN is formed
   * from a given attribute type and string value.
   * @param parentDN The DN of the parent.
   * @param rdnAttrType The attribute type of the RDN.
   * @param rdnStringValue The string value of the RDN.
   * @return A new child DN.
   */
  public static DN makeChildDN(DN parentDN, AttributeType rdnAttrType,
                               String rdnStringValue)
  {
    ByteString attrValue = ByteString.valueOf(rdnStringValue);
    return parentDN.child(RDN.create(rdnAttrType, attrValue));
  }
}
