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
package org.opends.server.backends;
import org.opends.messages.Message;



import java.io.File;
import java.util.*;

import org.opends.server.api.Backend;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.schema.BooleanSyntax;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.messages.BackendMessages.*;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.util.Validator;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.BackupBackendCfg;
import org.opends.server.admin.Configuration;


/**
 * This class defines a backend used to present information about Directory
 * Server backups.  It will not actually store anything, but upon request will
 * retrieve information about the backups that it knows about.  The backups will
 * be arranged in a hierarchy based on the directory that contains them, and
 * it may be possible to dynamically discover new backups if a previously
 * unknown backup directory is included in the base DN.
 */
public class BackupBackend
       extends Backend
       implements ConfigurationChangeListener<BackupBackendCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The current configuration state.
  private BackupBackendCfg currentConfig;

  // The DN for the base backup entry.
  private DN backupBaseDN;

  // The set of base DNs for this backend.
  private DN[] baseDNs;

  // The backup base entry.
  private Entry backupBaseEntry;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;

  // The set of predefined backup directories that we will use.
  private LinkedHashSet<File> backupDirectories;



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


  /**
   * {@inheritDoc}
   */
  public void configureBackend(Configuration config) throws ConfigException
  {
    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (config == null)
    {
      Message message = ERR_BACKUP_CONFIG_ENTRY_NULL.get();
      throw new ConfigException(message);
    }


    Validator.ensureTrue(config instanceof BackupBackendCfg);

    currentConfig = (BackupBackendCfg)config;
  }

  /**
   * {@inheritDoc}
   */
  public void initializeBackend()
         throws ConfigException, InitializationException
  {
    // Create the set of base DNs that we will handle.  In this case, it's just
    // the DN of the base backup entry.
    try
    {
      backupBaseDN = DN.decode(DN_BACKUP_ROOT);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_BACKUP_CANNOT_DECODE_BACKUP_ROOT_DN.get(getExceptionMessage(e));
      throw new InitializationException(message, e);
    }

    // FIXME -- Deal with this more correctly.
    this.baseDNs = new DN[] { backupBaseDN };


    // Determine the set of backup directories that we will use by default.
    Set<String> values = currentConfig.getBackupDirectory();
    backupDirectories = new LinkedHashSet<File>(values.size());
    for (String s : values)
    {
      backupDirectories.add(getFileForPath(s));
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

    RDN rdn = backupBaseDN.getRDN();
    int numAVAs = rdn.getNumValues();
    for (int i=0; i < numAVAs; i++)
    {
      LinkedHashSet<AttributeValue> valueSet =
           new LinkedHashSet<AttributeValue>(1);
      valueSet.add(rdn.getAttributeValue(i));

      AttributeType attrType = rdn.getAttributeType(i);
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(attrType, attrType.getNameOrOID(),
                                 valueSet));

      userAttrs.put(attrType, attrList);
    }

    backupBaseEntry = new Entry(backupBaseDN, objectClasses, userAttrs,
                                opAttrs);


    // Define an empty sets for the supported controls and features.
    supportedControls = new HashSet<String>(0);
    supportedFeatures = new HashSet<String>(0);


    // Register this as a change listener.
    currentConfig.addBackupChangeListener(this);


    // Register the backup base as a private suffix.
    try
    {
      DirectoryServer.registerBaseDN(backupBaseDN, this, true, false);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
          backupBaseDN.toString(), getExceptionMessage(e));
      throw new InitializationException(message, e);
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
    currentConfig.removeBackupChangeListener(this);

    try
    {
      DirectoryServer.deregisterBaseDN(backupBaseDN, false);
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
    int numEntries = 1;

    AttributeType backupPathType =
         DirectoryServer.getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);

    for (File f : backupDirectories)
    {
      try
      {
        // Check to see if the descriptor file exists.  If not, then skip this
        // backup directory.
        File descriptorFile = new File(f, BACKUP_DIRECTORY_DESCRIPTOR_FILE);
        if (! descriptorFile.exists())
        {
          continue;
        }

        DN backupDirDN = makeChildDN(backupBaseDN, backupPathType,
                                     f.getAbsolutePath());
        getBackupDirectoryEntry(backupDirDN);
        numEntries++;
      } catch (Exception e) {}
    }

    return numEntries;
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
    // For the purposes of this method, this is a local backend.
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
    // If the requested entry was null, then throw an exception.
    if (entryDN == null)
    {
      Message message = ERR_BACKUP_GET_ENTRY_NULL.get();
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
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
      Message message = ERR_BACKUP_INVALID_BASE.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
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
      Message message = ERR_BACKUP_INVALID_BASE.get(String.valueOf(entryDN));
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
    AttributeValue v = entryDN.getRDN().getAttributeValue(t);
    if (v == null)
    {
      Message message =
          ERR_BACKUP_DN_DOES_NOT_SPECIFY_DIRECTORY.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   backupBaseDN, null);
    }


    // Get a handle to the backup directory and the information that it
    // contains.
    BackupDirectory backupDirectory;
    try
    {
      backupDirectory =
           BackupDirectory.readBackupDirectoryDescriptor(v.getStringValue());
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
      }

      Message message = ERR_BACKUP_INVALID_BACKUP_DIRECTORY.get(
          String.valueOf(entryDN), ce.getMessage());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
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

    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(1);
    valueSet.add(v);

    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
    userAttrs.put(t, attrList);


    t = DirectoryServer.getAttributeType(ATTR_BACKUP_BACKEND_DN, true);
    valueSet = new LinkedHashSet<AttributeValue>(1);
    valueSet.add(new AttributeValue(t,
                          backupDirectory.getConfigEntryDN().toString()));
    attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
    userAttrs.put(t, attrList);


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
    AttributeType idType = DirectoryServer.getAttributeType(ATTR_BACKUP_ID,
                                                            true);
    AttributeValue idValue = entryDN.getRDN().getAttributeValue(idType);
    if (idValue == null)
    {
      Message message =
          ERR_BACKUP_NO_BACKUP_ID_IN_DN.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    String backupID = idValue.getStringValue();


    // Next, get the backup directory from the parent DN.
    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null)
    {
      Message message =
          ERR_BACKUP_NO_BACKUP_PARENT_DN.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    AttributeType t =
         DirectoryServer.getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);
    AttributeValue v = parentDN.getRDN().getAttributeValue(t);
    if (v == null)
    {
      Message message =
          ERR_BACKUP_NO_BACKUP_DIR_IN_DN.get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }


    BackupDirectory backupDirectory;
    try
    {
      backupDirectory =
           BackupDirectory.readBackupDirectoryDescriptor(v.getStringValue());
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
      }

      Message message =
          ERR_BACKUP_INVALID_BACKUP_DIRECTORY.get(
                  String.valueOf(entryDN), ce.getMessageObject());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_BACKUP_ERROR_GETTING_BACKUP_DIRECTORY.get(getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    BackupInfo backupInfo = backupDirectory.getBackupInfo(backupID);
    if (backupInfo == null)
    {
      Message message =
          ERR_BACKUP_NO_SUCH_BACKUP.get(backupID, backupDirectory.getPath());
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
              message, parentDN, null);
    }


    // Construct the backup entry to return.
    LinkedHashMap<ObjectClass,String> ocMap =
        new LinkedHashMap<ObjectClass,String>(3);
    ocMap.put(DirectoryServer.getTopObjectClass(), OC_TOP);

    ObjectClass oc = DirectoryServer.getObjectClass(OC_BACKUP_INFO, true);
    ocMap.put(oc, OC_BACKUP_INFO);

    oc = DirectoryServer.getObjectClass(OC_EXTENSIBLE_OBJECT_LC, true);
    ocMap.put(oc, OC_EXTENSIBLE_OBJECT);

    LinkedHashMap<AttributeType,List<Attribute>> opAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>(0);
    LinkedHashMap<AttributeType,List<Attribute>> userAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>();

    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(1);
    valueSet.add(idValue);

    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(idType, idType.getNameOrOID(), valueSet));
    userAttrs.put(idType, attrList);


    backupInfo.getBackupDirectory();
    valueSet = new LinkedHashSet<AttributeValue>(1);
    valueSet.add(v);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
    userAttrs.put(t, attrList);


    Date backupDate = backupInfo.getBackupDate();
    if (backupDate != null)
    {
      t = DirectoryServer.getAttributeType(ATTR_BACKUP_DATE, true);
      valueSet = new LinkedHashSet<AttributeValue>(1);
      valueSet.add(new AttributeValue(t,
                            GeneralizedTimeSyntax.format(backupDate)));
      attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
      userAttrs.put(t, attrList);
    }


    t = DirectoryServer.getAttributeType(ATTR_BACKUP_COMPRESSED, true);
    valueSet = new LinkedHashSet<AttributeValue>(1);
    valueSet.add(BooleanSyntax.createBooleanValue(backupInfo.isCompressed()));
    attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
    userAttrs.put(t, attrList);


    t = DirectoryServer.getAttributeType(ATTR_BACKUP_ENCRYPTED, true);
    valueSet = new LinkedHashSet<AttributeValue>(1);
    valueSet.add(BooleanSyntax.createBooleanValue(backupInfo.isEncrypted()));
    attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
    userAttrs.put(t, attrList);


    t = DirectoryServer.getAttributeType(ATTR_BACKUP_INCREMENTAL, true);
    valueSet = new LinkedHashSet<AttributeValue>(1);
    valueSet.add(BooleanSyntax.createBooleanValue(backupInfo.isIncremental()));
    attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
    userAttrs.put(t, attrList);


    HashSet<String> dependencies = backupInfo.getDependencies();
    if ((dependencies != null) && (! dependencies.isEmpty()))
    {
      t = DirectoryServer.getAttributeType(ATTR_BACKUP_DEPENDENCY, true);
      valueSet = new LinkedHashSet<AttributeValue>(dependencies.size());
      for (String s : dependencies)
      {
        valueSet.add(new AttributeValue(t, s));
      }
      attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
      userAttrs.put(t, attrList);
    }


    byte[] signedHash = backupInfo.getSignedHash();
    if (signedHash != null)
    {
      t = DirectoryServer.getAttributeType(ATTR_BACKUP_SIGNED_HASH, true);
      valueSet = new LinkedHashSet<AttributeValue>(1);
      valueSet.add(new AttributeValue(t, new ASN1OctetString(signedHash)));
      attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
      userAttrs.put(t, attrList);
    }


    byte[] unsignedHash = backupInfo.getUnsignedHash();
    if (unsignedHash != null)
    {
      t = DirectoryServer.getAttributeType(ATTR_BACKUP_UNSIGNED_HASH, true);
      valueSet = new LinkedHashSet<AttributeValue>(1);
      valueSet.add(new AttributeValue(t, new ASN1OctetString(unsignedHash)));
      attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
      userAttrs.put(t, attrList);
    }


    HashMap<String,String> properties = backupInfo.getBackupProperties();
    if ((properties != null) && (! properties.isEmpty()))
    {
      for (Map.Entry<String,String> e : properties.entrySet())
      {
        t = DirectoryServer.getAttributeType(toLowerCase(e.getKey()), true);
        valueSet = new LinkedHashSet<AttributeValue>(1);
        valueSet.add(new AttributeValue(t, e.getValue()));
        attrList = new ArrayList<Attribute>(1);
        attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
        userAttrs.put(t, attrList);
      }
    }


    Entry e = new Entry(entryDN, ocMap, userAttrs, opAttrs);
    e.processVirtualAttributes();
    return e;
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
    Message message = ERR_BACKUP_ADD_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
    Message message = ERR_BACKUP_DELETE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
    Message message = ERR_BACKUP_MODIFY_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
    Message message = ERR_BACKUP_MODIFY_DN_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
      if ((scope == SearchScope.BASE_OBJECT) ||
          (scope == SearchScope.WHOLE_SUBTREE))
      {
        if (filter.matchesEntry(baseEntry))
        {
          searchOperation.returnEntry(baseEntry, null);
        }
      }

      if ((scope != SearchScope.BASE_OBJECT) && (! backupDirectories.isEmpty()))
      {
        AttributeType backupPathType =
             DirectoryServer.getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);
        for (File f : backupDirectories)
        {
          // Check to see if the descriptor file exists.  If not, then skip this
          // backup directory.
          File descriptorFile = new File(f, BACKUP_DIRECTORY_DESCRIPTOR_FILE);
          if (! descriptorFile.exists())
          {
            continue;
          }


          DN backupDirDN = makeChildDN(backupBaseDN, backupPathType,
                                       f.getAbsolutePath());

          Entry backupDirEntry;
          try
          {
            backupDirEntry = getBackupDirectoryEntry(backupDirDN);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            continue;
          }

          if (filter.matchesEntry(backupDirEntry))
          {
            searchOperation.returnEntry(backupDirEntry, null);
          }

          if (scope != SearchScope.SINGLE_LEVEL)
          {
            List<Attribute> attrList =
                 backupDirEntry.getAttribute(backupPathType);
            if ((attrList != null) && (! attrList.isEmpty()))
            {
              for (AttributeValue v : attrList.get(0).getValues())
              {
                try
                {
                  BackupDirectory backupDirectory =
                       BackupDirectory.readBackupDirectoryDescriptor(
                            v.getStringValue());
                  AttributeType idType =
                       DirectoryServer.getAttributeType(ATTR_BACKUP_ID,
                                                        true);
                  for (String backupID : backupDirectory.getBackups().keySet())
                  {
                    DN backupEntryDN = makeChildDN(backupDirDN, idType,
                                                   backupID);
                    Entry backupEntry = getBackupEntry(backupEntryDN);
                    if (filter.matchesEntry(backupEntry))
                    {
                      searchOperation.returnEntry(backupEntry, null);
                    }
                  }
                }
                catch (Exception e)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                  }

                  continue;
                }
              }
            }
          }
        }
      }
    }
    else if (backupBaseDN.equals(parentDN = baseDN.getParentDNInSuffix()))
    {
      Entry backupDirEntry = getBackupDirectoryEntry(baseDN);

      if ((scope == SearchScope.BASE_OBJECT) ||
          (scope == SearchScope.WHOLE_SUBTREE))
      {
        if (filter.matchesEntry(backupDirEntry))
        {
          searchOperation.returnEntry(backupDirEntry, null);
        }
      }


      if (scope != SearchScope.BASE_OBJECT)
      {
        AttributeType t =
             DirectoryServer.getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);
        List<Attribute> attrList = backupDirEntry.getAttribute(t);
        if ((attrList != null) && (! attrList.isEmpty()))
        {
          for (AttributeValue v : attrList.get(0).getValues())
          {
            try
            {
              BackupDirectory backupDirectory =
                   BackupDirectory.readBackupDirectoryDescriptor(
                        v.getStringValue());
              AttributeType idType =
                   DirectoryServer.getAttributeType(ATTR_BACKUP_ID,
                                                    true);
              for (String backupID : backupDirectory.getBackups().keySet())
              {
                DN backupEntryDN = makeChildDN(baseDN, idType,
                                               backupID);
                Entry backupEntry = getBackupEntry(backupEntryDN);
                if (filter.matchesEntry(backupEntry))
                {
                  searchOperation.returnEntry(backupEntry, null);
                }
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              continue;
            }
          }
        }
      }
    }
    else
    {
      if ((parentDN == null)
          || (! backupBaseDN.equals(parentDN.getParentDNInSuffix())))
      {
        Message message = ERR_BACKUP_NO_SUCH_ENTRY.get(
                String.valueOf(backupBaseDN)
        );
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }

      if ((scope == SearchScope.BASE_OBJECT) ||
          (scope == SearchScope.WHOLE_SUBTREE))
      {
        Entry backupEntry = getBackupEntry(baseDN);
        if (backupEntry == null)
        {
          Message message = ERR_BACKUP_NO_SUCH_ENTRY.get(
                  String.valueOf(backupBaseDN));
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
        }

        if (filter.matchesEntry(backupEntry))
        {
          searchOperation.returnEntry(backupEntry, null);
        }
      }
    }
  }



  /**
   * Retrieves the OIDs of the controls that may be supported by this backend.
   *
   * @return  The OIDs of the controls that may be supported by this backend.
   */
  public HashSet<String> getSupportedControls()
  {
    return supportedControls;
  }



  /**
   * Retrieves the OIDs of the features that may be supported by this backend.
   *
   * @return  The OIDs of the features that may be supported by this backend.
   */
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
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
    // We do not support LDIF exports.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    Message message = ERR_BACKUP_EXPORT_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
    // This backend does not support LDIF imports.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    // This backend does not support LDIF imports.
    Message message = ERR_BACKUP_IMPORT_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
    // This backend does not provide a backup/restore mechanism.
    return false;
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
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void createBackup(BackupConfig backupConfig)
  throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_BACKUP_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_BACKUP_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * Indicates whether this backend provides a mechanism to restore a backup.
   *
   * @return  <CODE>true</CODE> if this backend provides a mechanism for
   *          restoring backups, or <CODE>false</CODE> if not.
   */
  public boolean supportsRestore()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_BACKUP_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
       BackupBackendCfg cfg, List<Message> unacceptableReasons)
  {
    // We'll accept anything here.  The only configurable attribute is the
    // default set of backup directories, but that doesn't require any
    // validation at this point.
    return true;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(BackupBackendCfg cfg)
  {
    ResultCode         resultCode          = ResultCode.SUCCESS;
    boolean            adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    Set<String> values = cfg.getBackupDirectory();
    backupDirectories = new LinkedHashSet<File>(values.size());
    for (String s : values)
    {
      backupDirectories.add(getFileForPath(s));
    }

    currentConfig = cfg;
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
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
    AttributeValue attrValue =
         new AttributeValue(rdnAttrType, rdnStringValue);
    return parentDN.concat(RDN.create(rdnAttrType, attrValue));
  }


}

