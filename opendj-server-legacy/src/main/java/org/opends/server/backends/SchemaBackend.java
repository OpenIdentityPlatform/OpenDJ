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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.backends;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.schema.GeneralizedTimeSyntax.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.SchemaUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.DITContentRule;
import org.forgerock.opendj.ldap.schema.DITStructureRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleUse;
import org.forgerock.opendj.ldap.schema.NameForm;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.server.config.server.SchemaBackendCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.api.Backupable;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.core.SchemaHandler;
import org.opends.server.core.SchemaHandler.SchemaUpdater;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.AttributeTypeSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.Modification;
import org.opends.server.types.Privilege;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.Schema;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.BackupManager;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.StaticUtils;

import sun.util.locale.ParseStatus;

/**
 * This class defines a backend to hold the Directory Server schema information.
 * It is a kind of meta-backend in that it doesn't actually hold any data but
 * rather dynamically generates the schema entry whenever it is requested.
 */
public class SchemaBackend extends Backend<SchemaBackendCfg>
     implements ConfigurationChangeListener<SchemaBackendCfg>, AlertGenerator, Backupable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME = "org.opends.server.backends.SchemaBackend";

  private static final String CONFIG_SCHEMA_ELEMENTS_FILE = "02-config.ldif";
  private static final String CORE_SCHEMA_ELEMENTS_FILE = "00-core.ldif";

  private static final AttributeType attributeTypesType = getAttributeTypesAttributeType();
  private static final AttributeType ditStructureRulesType = getDITStructureRulesAttributeType();
  private static final AttributeType ditContentRulesType = getDITContentRulesAttributeType();
  private static final AttributeType ldapSyntaxesType = getLDAPSyntaxesAttributeType();
  private static final AttributeType matchingRulesType = getMatchingRulesAttributeType();
  private static final AttributeType matchingRuleUsesType = getMatchingRuleUseAttributeType();
  private static final AttributeType nameFormsType = getNameFormsAttributeType();
  private static final AttributeType objectClassesType = getObjectClassesAttributeType();

  /** The value containing DN of the user we'll say created the configuration. */
  private ByteString creatorsName;
  /** The value containing the DN of the last user to modify the configuration. */
  private ByteString modifiersName;
  /** The timestamp that will be used for the schema creation time. */
  private ByteString createTimestamp;
  /** The timestamp that will be used for the latest schema modification time. */
  private ByteString modifyTimestamp;
  /** The time that the schema was last modified. */
  private long modifyTime;

  /** The DN of the configuration entry for this backend. */
  private DN configEntryDN;
  /** The current configuration state. */
  private SchemaBackendCfg currentConfig;
  /** The set of base DNs for this backend. */
  private Set<DN> baseDNs;

  /** The set of user-defined attributes that will be included in the schema entry. */
  private List<Attribute> userDefinedAttributes;
  /** The set of objectclasses that will be used in the schema entry. */
  private Map<ObjectClass, String> schemaObjectClasses;

  /**
   * Regular expression used to strip minimum upper bound value from syntax
   * Attribute Type Description. The value looks like: {count}.
   */
  private final Pattern stripMinUpperBoundRegEx = Pattern.compile("\\{\\d+\\}");

  private ServerContext serverContext;

  private SchemaHandler schemaHandler;

  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public SchemaBackend()
  {
    super();
  }

  @Override
  public void configureBackend(SchemaBackendCfg cfg, ServerContext serverContext) throws ConfigException
  {
    this.serverContext = serverContext;

    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (cfg == null)
    {
      throw new ConfigException(ERR_SCHEMA_CONFIG_ENTRY_NULL.get());
    }

    Entry configEntry = DirectoryServer.getConfigEntry(cfg.dn());

    configEntryDN = configEntry.getName();

    // Construct the set of objectclasses to include in the schema entry.
    schemaObjectClasses = new LinkedHashMap<>(3);
    schemaObjectClasses.put(CoreSchema.getTopObjectClass(), OC_TOP);
    schemaObjectClasses.put(DirectoryServer.getSchema().getObjectClass(OC_LDAP_SUBENTRY_LC), OC_LDAP_SUBENTRY);
    schemaObjectClasses.put(DirectoryServer.getSchema().getObjectClass(OC_SUBSCHEMA), OC_SUBSCHEMA);

    configEntryDN = configEntry.getName();
    baseDNs = cfg.getBaseDN();

    ByteString newBaseDN = ByteString.valueOfUtf8(baseDNs.iterator().next().toString());
    creatorsName = newBaseDN;
    modifiersName = newBaseDN;
    createTimestamp = createGeneralizedTimeValue(getSchema().getOldestModificationTime());
    modifyTimestamp = createGeneralizedTimeValue(getSchema().getYoungestModificationTime());

    // Get the set of user-defined attributes for the configuration entry.  Any
    // attributes that we don't recognize will be included directly in the
    // schema entry.
    userDefinedAttributes = new ArrayList<>();
    addAllNonSchemaConfigAttributes(userDefinedAttributes, configEntry.getAllAttributes());

    schemaHandler = serverContext.getSchemaHandler();

    currentConfig = cfg;
  }

  @Override
  public void openBackend() throws ConfigException, InitializationException
  {
    // Register each of the suffixes with the Directory Server.  Also, register
    // the first one as the schema base.
    DirectoryServer.setSchemaDN(baseDNs.iterator().next());
    for (DN baseDN : baseDNs) {
      try {
        DirectoryServer.registerBaseDN(baseDN, this, true);
      } catch (Exception e) {
        logger.traceException(e);

        LocalizableMessage message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
            baseDN, getExceptionMessage(e));
        throw new InitializationException(message, e);
      }
    }

    updateConcatenatedSchema();

    // Register with the Directory Server as a configurable component.
    currentConfig.addSchemaChangeListener(this);
  }

  /**
   * Updates the concatenated schema if changes are detected in the current schema files.
   * <p>
   * Identify any differences that may exist between the concatenated schema file from the last
   * online modification and the current schema files. If there are any differences, then they
   * should be from making changes to the schema files with the server offline.
   */
  private void updateConcatenatedSchema() throws InitializationException
  {
    try
    {
      // First, generate lists of elements from the current schema.
      Set<String> newATs  = new LinkedHashSet<>();
      Set<String> newOCs  = new LinkedHashSet<>();
      Set<String> newNFs  = new LinkedHashSet<>();
      Set<String> newDCRs = new LinkedHashSet<>();
      Set<String> newDSRs = new LinkedHashSet<>();
      Set<String> newMRUs = new LinkedHashSet<>();
      Set<String> newLSs = new LinkedHashSet<>();
      Schema.genConcatenatedSchema(newATs, newOCs, newNFs, newDCRs, newDSRs, newMRUs, newLSs);

      // Next, generate lists of elements from the previous concatenated schema.
      // If there isn't a previous concatenated schema, then use the base
      // schema for the current revision.
      File concatFile = getConcatFile();

      Set<String> oldATs  = new LinkedHashSet<>();
      Set<String> oldOCs  = new LinkedHashSet<>();
      Set<String> oldNFs  = new LinkedHashSet<>();
      Set<String> oldDCRs = new LinkedHashSet<>();
      Set<String> oldDSRs = new LinkedHashSet<>();
      Set<String> oldMRUs = new LinkedHashSet<>();
      Set<String> oldLSs = new LinkedHashSet<>();
      Schema.readConcatenatedSchema(concatFile, oldATs, oldOCs, oldNFs,
                                    oldDCRs, oldDSRs, oldMRUs,oldLSs);

      // Create a list of modifications and add any differences between the old
      // and new schema into them.
      List<Modification> mods = new LinkedList<>();
      Schema.compareConcatenatedSchema(oldATs, newATs, attributeTypesType, mods);
      Schema.compareConcatenatedSchema(oldOCs, newOCs, objectClassesType, mods);
      Schema.compareConcatenatedSchema(oldNFs, newNFs, nameFormsType, mods);
      Schema.compareConcatenatedSchema(oldDCRs, newDCRs, ditContentRulesType, mods);
      Schema.compareConcatenatedSchema(oldDSRs, newDSRs, ditStructureRulesType, mods);
      Schema.compareConcatenatedSchema(oldMRUs, newMRUs, matchingRuleUsesType, mods);
      Schema.compareConcatenatedSchema(oldLSs, newLSs, ldapSyntaxesType, mods);
      if (! mods.isEmpty())
      {
        // TODO : Raise an alert notification.

        DirectoryServer.setOfflineSchemaChanges(mods);

        // Write a new concatenated schema file with the most recent information
        // so we don't re-find these same changes on the next startup.
        Schema.writeConcatenatedSchema();
      }
    }
    catch (InitializationException ie)
    {
      throw ie;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      logger.error(ERR_SCHEMA_ERROR_DETERMINING_SCHEMA_CHANGES, getExceptionMessage(e));
    }
  }

  private File getConcatFile() throws InitializationException
  {
    File configDirectory  = new File(DirectoryServer.getConfigFile()).getParentFile();
    File upgradeDirectory = new File(configDirectory, "upgrade");
    File concatFile       = new File(upgradeDirectory, SCHEMA_CONCAT_FILE_NAME);
    if (concatFile.exists())
    {
      return concatFile.getAbsoluteFile();
    }

    String fileName = SCHEMA_BASE_FILE_NAME_WITHOUT_REVISION + BuildVersion.instanceVersion().getRevision();
    concatFile = new File(upgradeDirectory, fileName);
    if (concatFile.exists())
    {
      return concatFile.getAbsoluteFile();
    }

    String runningUnitTestsStr = System.getProperty(PROPERTY_RUNNING_UNIT_TESTS);
    if ("true".equalsIgnoreCase(runningUnitTestsStr))
    {
      Schema.writeConcatenatedSchema();
      concatFile = new File(upgradeDirectory, SCHEMA_CONCAT_FILE_NAME);
      return concatFile.getAbsoluteFile();
    }
    throw new InitializationException(ERR_SCHEMA_CANNOT_FIND_CONCAT_FILE.get(
        upgradeDirectory.getAbsolutePath(), SCHEMA_CONCAT_FILE_NAME, concatFile.getName()));
  }

  @Override
  public void closeBackend()
  {
    currentConfig.removeSchemaChangeListener(this);

    for (DN baseDN : baseDNs)
    {
      try
      {
        DirectoryServer.deregisterBaseDN(baseDN);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Indicates whether the provided attribute is one that is used in the
   * configuration of this backend.
   *
   * @param  attribute  The attribute for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute is one that is used in
   *          the configuration of this backend, <CODE>false</CODE> if not.
   */
  private boolean isSchemaConfigAttribute(Attribute attribute)
  {
    AttributeType attrType = attribute.getAttributeDescription().getAttributeType();
    return attrType.hasName(ATTR_SCHEMA_ENTRY_DN) ||
        attrType.hasName(ATTR_BACKEND_ENABLED) ||
        attrType.hasName(ATTR_BACKEND_CLASS) ||
        attrType.hasName(ATTR_BACKEND_ID) ||
        attrType.hasName(ATTR_BACKEND_BASE_DN) ||
        attrType.hasName(ATTR_BACKEND_WRITABILITY_MODE) ||
        attrType.hasName(ATTR_SCHEMA_SHOW_ALL_ATTRIBUTES) ||
        attrType.hasName(ATTR_COMMON_NAME) ||
        attrType.hasName(OP_ATTR_CREATORS_NAME_LC) ||
        attrType.hasName(OP_ATTR_CREATE_TIMESTAMP_LC) ||
        attrType.hasName(OP_ATTR_MODIFIERS_NAME_LC) ||
        attrType.hasName(OP_ATTR_MODIFY_TIMESTAMP_LC);
  }

  @Override
  public Set<DN> getBaseDNs()
  {
    return baseDNs;
  }

  @Override
  public long getEntryCount()
  {
    // There is always only a single entry in this backend.
    return 1;
  }

  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }

  @Override
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    return ConditionResult.FALSE;
  }

  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
  {
    checkNotNull(baseDN, "baseDN must not be null");
    return 1L;
  }

  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException
  {
    checkNotNull(parentDN, "parentDN must not be null");
    return 0L;
  }

  @Override
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    // If the requested entry was one of the schema entries, then create and return it.
    if (entryExists(entryDN))
    {
      return getSchemaEntry(entryDN, false, true);
    }

    // There is never anything below the schema entries, so we will return null.
    return null;
  }

  /**
   * Generates and returns a schema entry for the Directory Server.
   *
   * @param  entryDN            The DN to use for the generated entry.
   * @return  The schema entry that was generated.
   */
  public Entry getSchemaEntry(DN entryDN)
  {
    return getSchemaEntry(entryDN, false, false);
  }

  /**
   * Generates and returns a schema entry for the Directory Server.
   *
   * @param  entryDN            The DN to use for the generated entry.
   * @param  includeSchemaFile  A boolean indicating if the X-SCHEMA-FILE
   *                            extension should be used when generating
   *                            the entry.
   * @param ignoreShowAllOption A boolean indicating if the showAllAttributes
   *                            parameter should be ignored or not. It must
   *                            only considered for Search operation, and
   *                            definitely ignored for Modify operations, i.e.
   *                            when calling through getEntry().
   *
   * @return  The schema entry that was generated.
   */
  private Entry getSchemaEntry(DN entryDN, boolean includeSchemaFile,
                                          boolean ignoreShowAllOption)
  {
    Map<AttributeType, List<Attribute>> userAttrs = new LinkedHashMap<>();
    Map<AttributeType, List<Attribute>> operationalAttrs = new LinkedHashMap<>();

    // Add the RDN attribute(s) for the provided entry.
    RDN rdn = entryDN.rdn();
    if (rdn != null)
    {
      for (AVA ava : rdn)
      {
        Attribute attribute = Attributes.create(ava.getAttributeType(), ava.getAttributeValue());
        addAttributeToSchemaEntry(attribute, userAttrs, operationalAttrs);
      }
    }

    /* Add the schema definition attributes. */
    org.forgerock.opendj.ldap.schema.Schema schema = serverContext.getSchemaNG();
    buildSchemaAttribute(schema.getAttributeTypes(), userAttrs,
        operationalAttrs, attributeTypesType, includeSchemaFile,
        AttributeTypeSyntax.isStripSyntaxMinimumUpperBound(),
        ignoreShowAllOption);
    buildSchemaAttribute(schema.getObjectClasses(), userAttrs,
        operationalAttrs, objectClassesType, includeSchemaFile, false,
        ignoreShowAllOption);
    buildSchemaAttribute(schema.getMatchingRules(), userAttrs,
        operationalAttrs, matchingRulesType, includeSchemaFile, false,
        ignoreShowAllOption);

    /*
     * Note that we intentionally ignore showAllAttributes for attribute
     * syntaxes, name forms, matching rule uses, DIT content rules, and DIT
     * structure rules because those attributes aren't allowed in the subschema
     * objectclass, and treating them as user attributes would cause schema
     * updates to fail. This means that you'll always have to explicitly request
     * these attributes in order to be able to see them.
     */
    buildSchemaAttribute(schema.getSyntaxes(), userAttrs,
        operationalAttrs, ldapSyntaxesType, includeSchemaFile, false, true);
    buildSchemaAttribute(schema.getNameForms(), userAttrs,
        operationalAttrs, nameFormsType, includeSchemaFile, false, true);
    buildSchemaAttribute(schema.getDITContentRules(), userAttrs,
        operationalAttrs, ditContentRulesType, includeSchemaFile, false, true);
    buildSchemaAttribute(schema.getDITStuctureRules(), userAttrs,
        operationalAttrs, ditStructureRulesType, includeSchemaFile, false, true);
    buildSchemaAttribute(schema.getMatchingRuleUses(), userAttrs,
        operationalAttrs, matchingRuleUsesType, includeSchemaFile, false, true);

    // Add the lastmod attributes.
    if (DirectoryServer.getSchema().getYoungestModificationTime() != modifyTime)
    {
      synchronized (this)
      {
        modifyTime = DirectoryServer.getSchema().getYoungestModificationTime();
        modifyTimestamp = createGeneralizedTimeValue(modifyTime);
      }
    }
    addAttributeToSchemaEntry(
        Attributes.create(getCreatorsNameAttributeType(), creatorsName), userAttrs, operationalAttrs);
    addAttributeToSchemaEntry(
        Attributes.create(getCreateTimestampAttributeType(), createTimestamp), userAttrs, operationalAttrs);
    addAttributeToSchemaEntry(
        Attributes.create(getModifiersNameAttributeType(), modifiersName), userAttrs, operationalAttrs);
    addAttributeToSchemaEntry(
        Attributes.create(getModifyTimestampAttributeType(), modifyTimestamp), userAttrs, operationalAttrs);

    // Add the extra attributes.
    for (Attribute attribute : DirectoryServer.getSchema().getExtraAttributes())
    {
      addAttributeToSchemaEntry(attribute, userAttrs, operationalAttrs);
    }

    // Add all the user-defined attributes.
    for (Attribute attribute : userDefinedAttributes)
    {
      addAttributeToSchemaEntry(attribute, userAttrs, operationalAttrs);
    }

    // Construct and return the entry.
    Entry e = new Entry(entryDN, schemaObjectClasses, userAttrs, operationalAttrs);
    e.processVirtualAttributes();
    return e;
  }

  private void addAttributeToSchemaEntry(Attribute attribute,
      Map<AttributeType, List<Attribute>> userAttrs,
      Map<AttributeType, List<Attribute>> operationalAttrs)
  {
    AttributeType type = attribute.getAttributeDescription().getAttributeType();
    Map<AttributeType, List<Attribute>> attrsMap = type.isOperational() ? operationalAttrs : userAttrs;
    List<Attribute> attrs = attrsMap.get(type);
    if (attrs == null)
    {
      attrs = new ArrayList<>(1);
      attrsMap.put(type, attrs);
    }
    attrs.add(attribute);
  }

  private void buildSchemaAttribute(Collection<? extends SchemaElement> elements,
      Map<AttributeType, List<Attribute>> userAttrs,
      Map<AttributeType, List<Attribute>> operationalAttrs,
      AttributeType schemaAttributeType, boolean includeSchemaFile,
      final boolean stripSyntaxMinimumUpperBound, boolean ignoreShowAllOption)
  {
    // Skip the schema attribute if it is empty.
    if (elements.isEmpty())
    {
      return;
    }

    AttributeBuilder builder = new AttributeBuilder(schemaAttributeType);
    for (SchemaElement element : elements)
    {
      /* Add the file name to the description of the element if this was requested by the caller. */
      String value = includeSchemaFile ? getElementDefinitionWithFileName(element) : element.toString();
      if (stripSyntaxMinimumUpperBound && value.indexOf('{') != -1)
      {
        // Strip the minimum upper bound value from the attribute value.
        value = stripMinUpperBoundRegEx.matcher(value).replaceFirst("");
      }
      builder.add(value);
    }

    Attribute attribute = builder.toAttribute();
    AttributeType attrType = attribute.getAttributeDescription().getAttributeType();
    if (attrType.isOperational()
        && (ignoreShowAllOption || !showAllAttributes()))
    {
      operationalAttrs.put(attrType, newArrayList(attribute));
    }
    else
    {
      userAttrs.put(attrType, newArrayList(attribute));
    }
  }

  @Override
  public boolean entryExists(DN entryDN) throws DirectoryException
  {
    // The specified DN must be one of the specified schema DNs.
    return baseDNs.contains(entryDN);
  }

  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_ADD_NOT_SUPPORTED.get(entry.getName(), getBackendID()));
  }

  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_DELETE_NOT_SUPPORTED.get(entryDN, getBackendID()));
  }

  @Override
  public void replaceEntry(final Entry oldEntry, final Entry newEntry, final ModifyOperation modifyOperation)
      throws DirectoryException
  {
    // Make sure that the authenticated user has the necessary UPDATE_SCHEMA privilege.
    ClientConnection clientConnection = modifyOperation.getClientConnection();
    if (!clientConnection.hasPrivilege(Privilege.UPDATE_SCHEMA, modifyOperation))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_INSUFFICIENT_PRIVILEGES.get();
      throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, message);
    }

    final List<Modification> mods = new ArrayList<>(modifyOperation.getModifications());
    if (mods.isEmpty())
    {
      // There aren't any modifications, so we don't need to do anything.
      return;
    }
    final TreeSet<String> modifiedSchemaFiles = new TreeSet<>();

    SchemaBuilder schemaBuilder = new SchemaBuilder(schemaHandler.getSchema());
    applyModifications(schemaBuilder, mods, modifiedSchemaFiles, modifyOperation.isSynchronizationOperation());
    org.forgerock.opendj.ldap.schema.Schema newSchema = schemaBuilder.toSchema();
    schemaHandler.updateSchema(newSchema);

    updateSchemaFiles(newSchema, modifiedSchemaFiles);

    DN authzDN = modifyOperation.getAuthorizationDN();
    if (authzDN == null)
    {
      authzDN = DN.rootDN();
    }

    modifiersName = ByteString.valueOfUtf8(authzDN.toString());
    modifyTimestamp = createGeneralizedTimeValue(System.currentTimeMillis());
  }

  private void applyModifications(SchemaBuilder newSchemaBuilder, List<Modification> mods,
      Set<String> modifiedSchemaFiles, boolean isSynchronizationOperation) throws DirectoryException
  {
    int pos = -1;
    for (Modification m : mods)
    {
      pos++;

      // Determine the type of modification to perform.  We will support add and
      // delete operations in the schema, and we will also support the ability
      // to add a schema element that already exists and treat it as a
      // replacement of that existing element.
      Attribute a = m.getAttribute();
      AttributeType at = a.getAttributeDescription().getAttributeType();
      switch (m.getModificationType().asEnum())
      {
        case ADD:
          addAttribute(newSchemaBuilder, a, modifiedSchemaFiles);
          break;

        case DELETE:
          deleteAttribute(newSchemaBuilder, a, mods, pos, modifiedSchemaFiles);
          break;

        case REPLACE:
          if (!m.isInternal() && !isSynchronizationOperation)
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                ERR_SCHEMA_INVALID_MODIFICATION_TYPE.get(m.getModificationType()));
          }
          else  if (isSchemaAttribute(a))
          {
            logger.error(ERR_SCHEMA_INVALID_REPLACE_MODIFICATION, a.getAttributeDescription());
          }
          else
          {
            // If this is not a Schema attribute, we put it
            // in the extraAttribute map. This in fact acts as a replace.
            schemaHandler.putExtraAttribute(at.getNameOrOID(), a);
            modifiedSchemaFiles.add(FILE_USER_SCHEMA_ELEMENTS);
          }
          break;

        default:
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
              ERR_SCHEMA_INVALID_MODIFICATION_TYPE.get(m.getModificationType()));
      }
    }
  }

  private void addAttribute(SchemaBuilder newSchemaBuilder, Attribute a, Set<String> modifiedSchemaFiles)
      throws DirectoryException
  {
    AttributeType at = a.getAttributeDescription().getAttributeType();
    if (at.equals(attributeTypesType))
    {
      for (ByteString v : a)
      {
        addAttributeType(v.toString(), newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(objectClassesType))
    {
      for (ByteString v : a)
      {
        addObjectClass(v.toString(), newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(nameFormsType))
    {
      for (ByteString v : a)
      {
        addNameForm(v.toString(), newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(ditContentRulesType))
    {
      for (ByteString v : a)
      {
        addDITContentRule(v.toString(), newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(ditStructureRulesType))
    {
      for (ByteString v : a)
      {
        addDITStructureRule(v.toString(), newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(matchingRuleUsesType))
    {
      for (ByteString v : a)
      {
        addMatchingRuleUse(v.toString(), newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(ldapSyntaxesType))
    {
      for (ByteString v : a)
      {
        try
        {
          addLdapSyntaxDescription(v.toString(), newSchemaBuilder, modifiedSchemaFiles);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          LocalizableMessage message =
              ERR_SCHEMA_MODIFY_CANNOT_DECODE_LDAP_SYNTAX.get(v, de.getMessageObject());
          throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
        }
      }
    }
    else
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_UNSUPPORTED_ATTRIBUTE_TYPE.get(a.getAttributeDescription());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }
  }

  private void deleteAttribute(SchemaBuilder newSchema, Attribute attribute, List<Modification> mods, int pos,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    AttributeType at = attribute.getAttributeDescription().getAttributeType();
    if (attribute.isEmpty())
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_DELETE_NO_VALUES.get(attribute.getAttributeDescription());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (at.equals(attributeTypesType))
    {
      for (ByteString v : attribute)
      {
        removeAttributeType(v.toString(), newSchema, mods, pos, modifiedSchemaFiles);
      }
    }
    else if (at.equals(objectClassesType))
    {
      for (ByteString v : attribute)
      {
        removeObjectClass(v.toString(), newSchema, mods, pos, modifiedSchemaFiles);
      }
    }
    else if (at.equals(nameFormsType))
    {
      for (ByteString v : attribute)
      {
        removeNameForm(v.toString(), newSchema, mods, pos, modifiedSchemaFiles);
      }
    }
    else if (at.equals(ditContentRulesType))
    {
      for (ByteString v : attribute)
      {
        removeDITContentRule(v.toString(), newSchema, modifiedSchemaFiles);
      }
    }
    else if (at.equals(ditStructureRulesType))
    {
      for (ByteString v : attribute)
      {
        removeDITStructureRule(v.toString(), newSchema, mods, pos, modifiedSchemaFiles);
      }
    }
    else if (at.equals(matchingRuleUsesType))
    {
      for (ByteString v : attribute)
      {
        removeMatchingRuleUse(v.toString(), newSchema, modifiedSchemaFiles);
      }
    }
    else if (at.equals(ldapSyntaxesType))
    {
      for (ByteString v : attribute)
      {
        try
        {
          removeLdapSyntaxDescription(v.toString(), newSchema, modifiedSchemaFiles);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          LocalizableMessage message =
              ERR_SCHEMA_MODIFY_CANNOT_DECODE_LDAP_SYNTAX.get(
                  v, de.getMessageObject());
          throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
        }
      }
    }
    else
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_UNSUPPORTED_ATTRIBUTE_TYPE.get(attribute.getAttributeDescription());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }
  }

  /**
   * This method checks if a given attribute is an attribute that is used by the definition of the
   * schema.
   *
   * @param attribute
   *          The attribute to be checked.
   * @return true if the attribute is part of the schema definition, false if the attribute is not
   *         part of the schema definition.
   */
  private static boolean isSchemaAttribute(Attribute attribute)
  {
    String attributeOid = attribute.getAttributeDescription().getAttributeType().getOID();
    return attributeOid.equals("2.5.21.1") ||
        attributeOid.equals("2.5.21.2") ||
        attributeOid.equals("2.5.21.4") ||
        attributeOid.equals("2.5.21.5") ||
        attributeOid.equals("2.5.21.6") ||
        attributeOid.equals("2.5.21.7") ||
        attributeOid.equals("2.5.21.8") ||
        attributeOid.equals("2.5.4.3") ||
        attributeOid.equals("1.3.6.1.4.1.1466.101.120.16") ||
        attributeOid.equals("cn-oid") ||
        attributeOid.equals("attributetypes-oid") ||
        attributeOid.equals("objectclasses-oid") ||
        attributeOid.equals("matchingrules-oid") ||
        attributeOid.equals("matchingruleuse-oid") ||
        attributeOid.equals("nameformdescription-oid") ||
        attributeOid.equals("ditcontentrules-oid") ||
        attributeOid.equals("ditstructurerules-oid") ||
        attributeOid.equals("ldapsyntaxes-oid");
  }

  /**
   * Re-write all schema files using the provided new Schema and list of
   * modified files.
   *
   * @param newSchema            The new schema that should be used.
   *
   * @param modifiedSchemaFiles  The list of files that should be modified.
   *
   * @throws DirectoryException  When the new file cannot be written.
   */
  private void updateSchemaFiles(org.forgerock.opendj.ldap.schema.Schema newSchema, TreeSet<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // We'll re-write all
    // impacted schema files by first creating them in a temporary location
    // and then replacing the existing schema files with the new versions.
    // If all that goes successfully, then activate the new schema.
    HashMap<String, File> tempSchemaFiles = new HashMap<>();
    try
    {
      for (String schemaFile : modifiedSchemaFiles)
      {
        File tempSchemaFile = writeTempSchemaFile(newSchema, schemaFile);
        tempSchemaFiles.put(schemaFile, tempSchemaFile);
      }

      installSchemaFiles(tempSchemaFiles);
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      throw de;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_SCHEMA_MODIFY_CANNOT_WRITE_NEW_SCHEMA.get(getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
    }
    finally
    {
      cleanUpTempSchemaFiles(tempSchemaFiles);
    }

    // Create a single file with all of the concatenated schema information
    // that we can use on startup to detect whether the schema files have been
    // edited with the server offline.
    Schema.writeConcatenatedSchema();
  }

  /**
   * Handles all processing required for adding the provided attribute type to
   * the given schema, replacing an existing type if necessary, and ensuring all
   * other metadata is properly updated.
   *
   * @param  attributeType        The attribute type to add or replace in the
   *                              server schema.
   * @param  schemaBuilder        The schema builder to which the attribute type should
   *                              be added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided attribute type to the server
   *                              schema.
   */
  private void addAttributeType(String definition, SchemaBuilder schemaBuilder, Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = schemaHandler.getSchema();
    String oid = SchemaHandler.parseAttributeTypeOID(definition);
    final String finalDefinition;
    if (!currentSchema.hasAttributeType(oid))
    {
      finalDefinition = completeDefinitionWhenAddingSchemaElement(definition, modifiedSchemaFiles);
    }
    else
    {
      AttributeType existingAttributeType = currentSchema.getAttributeType(oid);
      finalDefinition = completeDefinitionWhenReplacingSchemaElement(definition, existingAttributeType,
          modifiedSchemaFiles);
    }
    schemaBuilder.addAttributeType(finalDefinition, true);
  }

  /**
   * Returns the updated definition of the schema element with the schema file added if necessary.
   */
  private String completeDefinitionWhenAddingSchemaElement(String definition, Set<String> modifiedSchemaFiles)
      throws DirectoryException
  {
    String givenSchemaFile = SchemaHandler.parseSchemaFileFromElementDefinition(definition);
    String finalSchemaFile = givenSchemaFile == null ? FILE_USER_SCHEMA_ELEMENTS : givenSchemaFile;
    modifiedSchemaFiles.add(finalSchemaFile);
    return SchemaHandler.addSchemaFileToElementDefinitionIfAbsent(definition, finalSchemaFile);
  }

  /**
   * Returns the updated definition of the schema element with the schema file added if necessary.
   *
   * @throws DirectoryException
   *            If an error occurs while parsing the schema element definition
   */
  private String completeDefinitionWhenReplacingSchemaElement(String definition, SchemaElement existingElement,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    String givenSchemaFile = SchemaHandler.parseSchemaFileFromElementDefinition(definition);
    String oldSchemaFile = getElementSchemaFile(existingElement);

    if (givenSchemaFile == null)
    {
      if (oldSchemaFile == null)
      {
        oldSchemaFile = FILE_USER_SCHEMA_ELEMENTS;
      }
      modifiedSchemaFiles.add(oldSchemaFile);
      return SchemaHandler.addSchemaFileToElementDefinitionIfAbsent(definition, oldSchemaFile);
    }
    else if (oldSchemaFile == null || oldSchemaFile.equals(givenSchemaFile))
    {
      modifiedSchemaFiles.add(givenSchemaFile);
    }
    else
    {
      modifiedSchemaFiles.add(givenSchemaFile);
      modifiedSchemaFiles.add(oldSchemaFile);
    }
    return definition;
  }

  /**
   * Handles all processing required to remove the provided attribute type from
   * the server schema, ensuring all other metadata is properly updated.  Note
   * that this method will first check to see whether the same attribute type
   * will be later added to the server schema with an updated definition, and if
   * so then the removal will be ignored because the later add will be handled
   * as a replace.  If the attribute type will not be replaced with a new
   * definition, then this method will ensure that there are no other schema
   * elements that depend on the attribute type before allowing it to be
   * removed.
   *
   * @param  definition           The definition of attribute type to remove from the server
   *                              schema.
   * @param  newSchemaBuilder     The schema builder from which the attribute type
   *                              should be removed.
   * @param  modifications        The full set of modifications to be processed
   *                              against the server schema.
   * @param  currentPosition      The position of the modification currently
   *                              being performed.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to remove
   *                              the provided attribute type from the server
   *                              schema.
   */
  private void removeAttributeType(String definition, SchemaBuilder newSchemaBuilder, List<Modification> modifications,
      int currentPosition, Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = newSchemaBuilder.toSchema();
    String atOID = SchemaHandler.parseAttributeTypeOID(definition);

    if (!currentSchema.hasAttributeType(atOID))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_ATTRIBUTE_TYPE.get(atOID);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    // See if there is another modification later to add the attribute type back
    // into the schema. If so, then it's a replace and we should ignore the
    // remove because adding it back will handle the replace.
    for (int i = currentPosition + 1; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute a = m.getAttribute();

      if (m.getModificationType() != ModificationType.ADD
          || !a.getAttributeDescription().getAttributeType().equals(attributeTypesType))
      {
        continue;
      }

      for (ByteString v : a)
      {
        try
        {
          String oid = SchemaHandler.parseAttributeTypeOID(v.toString());
          if (atOID.equals(oid))
          {
            // We found a match where the attribute type is added back later,
            // so we don't need to do anything else here.
            return;
          }
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);
          throw de;
        }
      }
    }

    // If we've gotten here, then it's OK to remove the attribute type from the schema.
    newSchemaBuilder.removeAttributeType(atOID);
    addElementIfNotNull(modifiedSchemaFiles, getElementSchemaFile(currentSchema.getAttributeType(atOID)));
  }

  /**
   * Handles all processing required for adding the provided objectclass definition to the
   * given schema, replacing an existing class if necessary, and ensuring
   * all other metadata is properly updated.
   *
   * @param  definition          The definition of objectclass to add or replace in the
   *                              server schema.
   * @param  schemaBuilder               The schema builder to which the objectclass should be
   *                              added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided objectclass to the server schema.
   */
  private void addObjectClass(String definition, SchemaBuilder schemaBuilder, Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = schemaHandler.getSchema();
    String oid = SchemaHandler.parseObjectClassOID(definition);
    final String finalDefinition;
    if (!currentSchema.hasObjectClass(oid))
    {
      finalDefinition = completeDefinitionWhenAddingSchemaElement(definition, modifiedSchemaFiles);
    }
    else
    {
      ObjectClass existingOC = currentSchema.getObjectClass(oid);
      finalDefinition = completeDefinitionWhenReplacingSchemaElement(definition, existingOC, modifiedSchemaFiles);
    }
    schemaBuilder.addObjectClass(finalDefinition, true);
  }

  /**
   * Handles all processing required to remove the provided objectclass from the
   * server schema, ensuring all other metadata is properly updated.  Note that
   * this method will first check to see whether the same objectclass will be
   * later added to the server schema with an updated definition, and if so then
   * the removal will be ignored because the later add will be handled as a
   * replace.  If the objectclass will not be replaced with a new definition,
   * then this method will ensure that there are no other schema elements that
   * depend on the objectclass before allowing it to be removed.
   *
   * @param  definition           The definition of objectclass to remove from the server
   *                              schema.
   * @param  schema               The schema from which the objectclass should
   *                              be removed.
   * @param  modifications        The full set of modifications to be processed
   *                              against the server schema.
   * @param  currentPosition      The position of the modification currently
   *                              being performed.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to remove
   *                              the provided objectclass from the server
   *                              schema.
   */
  private void removeObjectClass(String definition, SchemaBuilder newSchemaBuilder,
                                 List<Modification> modifications,
                                 int currentPosition,
                                 Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = newSchemaBuilder.toSchema();
    String ocOID = SchemaHandler.parseObjectClassOID(definition);

    if (!currentSchema.hasObjectClass(ocOID))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_OBJECTCLASS.get(ocOID);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    // See if there is another modification later to add the objectclass back
    // into the schema.  If so, then it's a replace and we should ignore the
    // remove because adding it back will handle the replace.
    for (int i=currentPosition+1; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute    a = m.getAttribute();

      if (m.getModificationType() != ModificationType.ADD ||
          !a.getAttributeDescription().getAttributeType().equals(objectClassesType))
      {
        continue;
      }

      for (ByteString v : a)
      {
        String oid;
        try
        {
          oid = SchemaHandler.parseObjectClassOID(v.toString());
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);
          throw de;
        }

        if (ocOID.equals(oid))
        {
          // We found a match where the objectClass is added back later, so we
          // don't need to do anything else here.
          return;
        }
      }
    }

    // If we've gotten here, then it's OK to remove the objectclass from the schema.
    newSchemaBuilder.removeObjectClass(ocOID);
    addElementIfNotNull(modifiedSchemaFiles, getElementSchemaFile(currentSchema.getObjectClass(ocOID)));
  }

  /**
   * Handles all processing required for adding the provided name form to the
   * the given schema, replacing an existing name form if necessary, and
   * ensuring all other metadata is properly updated.
   *
   * @param  nameForm             The name form to add or replace in the server
   *                              schema.
   * @param  schemaBuilder        The schema builder to which the name form should be
   *                              added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided name form to the server schema.
   */
  private void addNameForm(String definition, SchemaBuilder schemaBuilder, Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = schemaHandler.getSchema();
    String oid = SchemaHandler.parseNameFormOID(definition);
    final String finalDefinition;
    if (!currentSchema.hasNameForm(oid))
    {
      finalDefinition = completeDefinitionWhenAddingSchemaElement(definition, modifiedSchemaFiles);
    }
    else
    {
      NameForm existingNF = currentSchema.getNameForm(oid);
      finalDefinition = completeDefinitionWhenReplacingSchemaElement(definition, existingNF, modifiedSchemaFiles);
    }
    schemaBuilder.addNameForm(finalDefinition, true);
  }

  /**
   * Handles all processing required to remove the provided name form from the
   * server schema, ensuring all other metadata is properly updated.  Note that
   * this method will first check to see whether the same name form will be
   * later added to the server schema with an updated definition, and if so then
   * the removal will be ignored because the later add will be handled as a
   * replace.  If the name form will not be replaced with a new definition, then
   * this method will ensure that there are no other schema elements that depend
   * on the name form before allowing it to be removed.
   *
   * @param  definition           The definition of name form to remove from the server
   *                              schema.
   * @param  newSchemaBuilder     The schema builder from which the name form should be
   *                              be removed.
   * @param  modifications        The full set of modifications to be processed
   *                              against the server schema.
   * @param  currentPosition      The position of the modification currently
   *                              being performed.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to remove
   *                              the provided name form from the server schema.
   */
  private void removeNameForm(String definition, SchemaBuilder newSchemaBuilder,
                              List<Modification> modifications,
                              int currentPosition,
                              Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = newSchemaBuilder.toSchema();
    String nfOID = SchemaHandler.parseNameFormOID(definition);

    if (!currentSchema.hasNameForm(nfOID))
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_NAME_FORM.get(nfOID));
    }

    // See if there is another modification later to add the name form back
    // into the schema.  If so, then it's a replace and we should ignore the
    // remove because adding it back will handle the replace.
    for (int i=currentPosition+1; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute    a = m.getAttribute();

      if (m.getModificationType() != ModificationType.ADD ||
          !a.getAttributeDescription().getAttributeType().equals(nameFormsType))
      {
        continue;
      }

      for (ByteString v : a)
      {
        try
        {
          String oid = SchemaHandler.parseNameFormOID(v.toString());
          if (nfOID.equals(oid))
          {
            // We found a match where the name form is added back later, so we
            // don't need to do anything else here.
            return;
          }
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);
          throw de;
        }
      }
    }

    // Now remove the name form from the schema.
    newSchemaBuilder.removeNameForm(nfOID);
    addElementIfNotNull(modifiedSchemaFiles, getElementSchemaFile(currentSchema.getNameForm(nfOID)));
  }

  /**
   * Handles all processing required for adding the provided DIT content rule to
   * the given schema, replacing an existing rule if necessary, and ensuring
   * all other metadata is properly updated.
   *
   * @param  ditContentRule       The DIT content rule to add or replace in the
   *                              server schema.
   * @param  schemaBuilder               The schema to which the DIT content rule
   *                              should be added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided DIT content rule to the server
   *                              schema.
   */
  private void addDITContentRule(String definition, SchemaBuilder schemaBuilder,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = schemaHandler.getSchema();
    String oid = SchemaHandler.parseDITContentRuleOID(definition);
    final String finalDefinition;
    if (!currentSchema.hasDITContentRule(oid))
    {
      finalDefinition = completeDefinitionWhenAddingSchemaElement(definition, modifiedSchemaFiles);
    }
    else
    {
      DITContentRule existingRule = currentSchema.getDITContentRule(oid);
      finalDefinition = completeDefinitionWhenReplacingSchemaElement(definition, existingRule, modifiedSchemaFiles);
    }
    schemaBuilder.addDITContentRule(finalDefinition, true);
  }

  /**
   * Handles all processing required to remove the provided DIT content rule
   * from the server schema, ensuring all other metadata is properly updated.
   * Note that this method will first check to see whether the same rule will be
   * later added to the server schema with an updated definition, and if so then
   * the removal will be ignored because the later add will be handled as a
   * replace.  If the DIT content rule will not be replaced with a new
   * definition, then this method will ensure that there are no other schema
   * elements that depend on the rule before allowing it to be removed.
   *
   * @param  definition           The definition of DIT content rule to remove from the server
   *                              schema.
   * @param  newSchemaBuilder     The schema builder from which the DIT content rule
   *                              should be removed.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to remove
   *                              the provided DIT content rule from the server
   *                              schema.
   */
  private void removeDITContentRule(String definition,
      SchemaBuilder newSchemaBuilder, Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = newSchemaBuilder.toSchema();
    String ruleOid = SchemaHandler.parseDITContentRuleOID(definition);

    if (! currentSchema.hasDITContentRule(ruleOid))
    {
      LocalizableMessage message =
          ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_DCR.get(ruleOid);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    // Since DIT content rules don't have any dependencies, then we don't need
    // to worry about the difference between a remove or a replace.  We can
    // just remove the DIT content rule now, and if it is added back later then
    // there still won't be any conflict.
    newSchemaBuilder.removeDITContentRule(ruleOid);
    addElementIfNotNull(modifiedSchemaFiles, getElementSchemaFile(currentSchema.getDITContentRule(ruleOid)));
  }

  /**
   * Handles all processing required for adding the provided DIT structure rule
   * to the given schema, replacing an existing rule if necessary, and ensuring
   * all other metadata is properly updated.
   *
   * @param  definition           The DIT structure rule to add or replace in
   *                              the server schema.
   * @param  schemaBuilder        The schema builder to which the DIT structure rule
   *                              should be added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided DIT structure rule to the server
   *                              schema.
   */
  private void addDITStructureRule(String definition, SchemaBuilder schemaBuilder, Set<String> modifiedSchemaFiles)
      throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = schemaHandler.getSchema();
    int ruleId = SchemaHandler.parseRuleID(definition);
    final String finalDefinition;
    if (!currentSchema.hasDITStructureRule(ruleId))
    {
      finalDefinition = completeDefinitionWhenAddingSchemaElement(definition, modifiedSchemaFiles);
    }
    else
    {
      DITStructureRule existingRule = currentSchema.getDITStructureRule(ruleId);
      finalDefinition = completeDefinitionWhenReplacingSchemaElement(definition, existingRule, modifiedSchemaFiles);
    }
    schemaBuilder.addDITStructureRule(finalDefinition, true);
  }

  /**
   * Handles all processing required to remove the provided DIT structure rule
   * from the server schema, ensuring all other metadata is properly updated.
   * Note that this method will first check to see whether the same rule will be
   * later added to the server schema with an updated definition, and if so then
   * the removal will be ignored because the later add will be handled as a
   * replace.  If the DIT structure rule will not be replaced with a new
   * definition, then this method will ensure that there are no other schema
   * elements that depend on the rule before allowing it to be removed.
   *
   * @param  definition           The definition of DIT structure rule to remove from the
   *                              server schema.
   * @param  newSchemaBuilder     The schema builder from which the DIT structure rule
   *                              should be removed.
   * @param  modifications        The full set of modifications to be processed
   *                              against the server schema.
   * @param  currentPosition      The position of the modification currently
   *                              being performed.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to remove
   *                              the provided DIT structure rule from the
   *                              server schema.
   */
  private void removeDITStructureRule(String definition,
                                      SchemaBuilder newSchemaBuilder,
                                      List<Modification> modifications,
                                      int currentPosition,
                                      Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = newSchemaBuilder.toSchema();
    int ruleID = SchemaHandler.parseRuleID(definition);

    if (!currentSchema.hasDITStructureRule(ruleID))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_DSR.get(ruleID);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    // See if there is another modification later to add the DIT structure rule
    // back into the schema.  If so, then it's a replace and we should ignore
    // the remove because adding it back will handle the replace.
    for (int i=currentPosition+1; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute    a = m.getAttribute();

      if (m.getModificationType() != ModificationType.ADD ||
          !a.getAttributeDescription().getAttributeType().equals(ditStructureRulesType))
      {
        continue;
      }

      for (ByteString v : a)
      {
        int id = SchemaHandler.parseRuleID(v.toString());
        if (ruleID == id)
        {
          // We found a match where the DIT structure rule is added back later,
          // so we don't need to do anything else here.
          return;
        }
      }
    }

    // If we've gotten here, then it's OK to remove the DIT structure rule from the schema.
    newSchemaBuilder.removeDITStructureRule(ruleID);
    addElementIfNotNull(modifiedSchemaFiles, getElementSchemaFile(currentSchema.getDITStructureRule(ruleID)));
  }

  /**
   * Handles all processing required for adding the provided matching rule use
   * to the given schema, replacing an existing use if necessary, and ensuring
   * all other metadata is properly updated.
   *
   * @param  definition           The definition of matching rule use to add or replace in the
   *                              server schema.
   * @param  schemaBuilder        The schema to which the matching rule use
   *                              should be added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided matching rule use to the server
   *                              schema.
   */
  private void addMatchingRuleUse(String definition, SchemaBuilder schemaBuilder, Set<String> modifiedSchemaFiles)
      throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = schemaHandler.getSchema();
    String oid = SchemaHandler.parseMatchingRuleUseOID(definition);
    final String finalDefinition;
    if (!currentSchema.hasMatchingRuleUse(oid))
    {
      finalDefinition = completeDefinitionWhenAddingSchemaElement(definition, modifiedSchemaFiles);
    }
    else
    {
      MatchingRuleUse existingMRU = currentSchema.getMatchingRuleUse(oid);
      finalDefinition = completeDefinitionWhenReplacingSchemaElement(definition, existingMRU, modifiedSchemaFiles);
    }
    schemaBuilder.addMatchingRuleUse(finalDefinition, true);
  }

  /**
   * Handles all processing required to remove the provided matching rule use
   * from the server schema, ensuring all other metadata is properly updated.
   * Note that this method will first check to see whether the same matching
   * rule use will be later added to the server schema with an updated
   * definition, and if so then the removal will be ignored because the later
   * add will be handled as a replace.  If the matching rule use will not be
   * replaced with a new definition, then this method will ensure that there are
   * no other schema elements that depend on the matching rule use before
   * allowing it to be removed.
   *
   * @param  definition           The definition of matching rule use to remove from the
   *                              server schema.
   * @param  newSchemaBuilder     The schema builder from which the matching rule use
   *                              should be removed.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   * @throws  DirectoryException  If a problem occurs while attempting to remove
   *                              the provided matching rule use from the server
   *                              schema.
   */
  private void removeMatchingRuleUse(String definition,
                                     SchemaBuilder newSchemaBuilder,
                                     Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    org.forgerock.opendj.ldap.schema.Schema currentSchema = newSchemaBuilder.toSchema();
    String mruOid = SchemaHandler.parseMatchingRuleUseOID(definition);

    if (!currentSchema.hasMatchingRuleUse(mruOid))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_MR_USE.get(mruOid);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    // Since matching rule uses don't have any dependencies, then we don't need
    // to worry about the difference between a remove or a replace.  We can
    // just remove the DIT content rule now, and if it is added back later then
    // there still won't be any conflict.
    newSchemaBuilder.removeMatchingRuleUse(mruOid);
    addElementIfNotNull(modifiedSchemaFiles, getElementSchemaFile(currentSchema.getMatchingRuleUse(mruOid)));
  }

  /**
   * Handles all processing required for adding the provided ldap syntax description to the given
   * schema, replacing an existing ldap syntax description if necessary, and ensuring all other
   * metadata is properly updated.
   *
   * @param definition
   *          The definition of the ldap syntax description to add or replace in the server schema.
   * @param schemaBuilder
   *          The schema to which the LDAP syntax description should be added.
   * @param modifiedSchemaFiles
   *          The names of the schema files containing schema elements that have been updated as
   *          part of the schema modification.
   * @throws DirectoryException
   *           If a problem occurs while attempting to add the provided ldap syntax description to
   *           the server schema.
   */
  private void addLdapSyntaxDescription(String definition, SchemaBuilder schemaBuilder, Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // TODO: not sure of the correct implementation here. There was previously a check that would
    // reject a change if a syntax with oid already exists, but I don't understand why.
    // I kept an implementation that behave like other schema elements.
    org.forgerock.opendj.ldap.schema.Schema currentSchema = schemaHandler.getSchema();
    String oid = SchemaHandler.parseSyntaxOID(definition);
    final String finalDefinition;
    if (!currentSchema.hasSyntax(oid))
    {
      finalDefinition = completeDefinitionWhenAddingSchemaElement(definition, modifiedSchemaFiles);
    }
    else
    {
      Syntax existingSyntax = currentSchema.getSyntax(oid);
      finalDefinition = completeDefinitionWhenReplacingSchemaElement(definition, existingSyntax, modifiedSchemaFiles);
    }
    schemaBuilder.addSyntax(finalDefinition, true);
  }

  private void removeLdapSyntaxDescription(String definition, SchemaBuilder newSchemaBuilder,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    /*
     * See if the specified ldap syntax description is actually defined in the
     * server schema. If not, then fail. Note that we are checking only the real
     * part of the ldapsyntaxes attribute. A virtual value is not searched and
     * hence never deleted.
     */
    org.forgerock.opendj.ldap.schema.Schema currentSchema = newSchemaBuilder.toSchema();
    String oid = SchemaHandler.parseSyntaxOID(definition);

    if (!currentSchema.hasSyntax(oid))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_LSD.get(oid);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    newSchemaBuilder.removeSyntax(oid);
    addElementIfNotNull(modifiedSchemaFiles, getElementSchemaFile(currentSchema.getSyntax(oid)));
  }

  /**
   * Creates an empty entry that may be used as the basis for a new schema file.
   *
   * @return  An empty entry that may be used as the basis for a new schema
   *          file.
   */
  private Entry createEmptySchemaEntry()
  {
    Map<ObjectClass,String> objectClasses = new LinkedHashMap<>();
    objectClasses.put(CoreSchema.getTopObjectClass(), OC_TOP);
    objectClasses.put(DirectoryServer.getSchema().getObjectClass(OC_LDAP_SUBENTRY_LC), OC_LDAP_SUBENTRY);
    objectClasses.put(DirectoryServer.getSchema().getObjectClass(OC_SUBSCHEMA), OC_SUBSCHEMA);

    Map<AttributeType,List<Attribute>> userAttributes = new LinkedHashMap<>();
    Map<AttributeType,List<Attribute>> operationalAttributes = new LinkedHashMap<>();

    DN  dn  = DirectoryServer.getSchemaDN();
    for (AVA ava : dn.rdn())
    {
      AttributeType type = ava.getAttributeType();
      Map<AttributeType, List<Attribute>> attrs = type.isOperational() ? operationalAttributes : userAttributes;
      attrs.put(type, newLinkedList(Attributes.create(type, ava.getAttributeValue())));
    }

    return new Entry(dn, objectClasses,  userAttributes, operationalAttributes);
  }

  /**
   * Writes a temporary version of the specified schema file.
   *
   * @param  schema      The schema from which to take the definitions to be
   *                     written.
   * @param  schemaFile  The name of the schema file to be written.
   *
   * @throws  DirectoryException  If an unexpected problem occurs while
   *                              identifying the schema definitions to include
   *                              in the schema file.
   *
   * @throws  IOException  If an unexpected error occurs while attempting to
   *                       write the temporary schema file.
   *
   * @throws  LDIFException  If an unexpected problem occurs while generating
   *                         the LDIF representation of the schema entry.
   */
  private File writeTempSchemaFile(org.forgerock.opendj.ldap.schema.Schema schema, String schemaFile)
          throws DirectoryException, IOException, LDIFException
  {
    Entry schemaEntry = createEmptySchemaEntry();

     /*
     * Add all of the ldap syntax descriptions to the schema entry. We do
     * this only for the real part of the ldapsyntaxes attribute. The real part
     * is read and write to/from the schema files.
     */
    Set<ByteString> values = getValuesForSchemaFile(getCustomSyntaxes(schema), schemaFile);
    addAttribute(schemaEntry, ldapSyntaxesType, values);

    // Add all of the appropriate attribute types to the schema entry.  We need
    // to be careful of the ordering to ensure that any superior types in the
    // same file are written before the subordinate types.
    values = getAttributeTypeValuesForSchemaFile(schema, schemaFile);
    addAttribute(schemaEntry, attributeTypesType, values);

    // Add all of the appropriate objectclasses to the schema entry.  We need
    // to be careful of the ordering to ensure that any superior classes in the
    // same file are written before the subordinate classes.
    values = getObjectClassValuesForSchemaFile(schema, schemaFile);
    addAttribute(schemaEntry, objectClassesType, values);

    // Add all of the appropriate name forms to the schema entry.  Since there
    // is no hierarchical relationship between name forms, we don't need to
    // worry about ordering.
    values = getValuesForSchemaFile(schema.getNameForms(), schemaFile);
    addAttribute(schemaEntry, nameFormsType, values);

    // Add all of the appropriate DIT content rules to the schema entry.  Since
    // there is no hierarchical relationship between DIT content rules, we don't
    // need to worry about ordering.
    values = getValuesForSchemaFile(schema.getDITContentRules(), schemaFile);
    addAttribute(schemaEntry, ditContentRulesType, values);

    // Add all of the appropriate DIT structure rules to the schema entry.  We
    // need to be careful of the ordering to ensure that any superior rules in
    // the same file are written before the subordinate rules.
    values = getDITStructureRuleValuesForSchemaFile(schema, schemaFile);
    addAttribute(schemaEntry, ditStructureRulesType, values);

    // Add all of the appropriate matching rule uses to the schema entry.  Since
    // there is no hierarchical relationship between matching rule uses, we
    // don't need to worry about ordering.
    values = getValuesForSchemaFile(schema.getMatchingRuleUses(), schemaFile);
    addAttribute(schemaEntry, matchingRuleUsesType, values);

    if (FILE_USER_SCHEMA_ELEMENTS.equals(schemaFile))
    {
      for (Attribute attribute : schemaHandler.getExtraAttributes())
      {
        AttributeType attributeType = attribute.getAttributeDescription().getAttributeType();
        schemaEntry.putAttribute(attributeType, newArrayList(attribute));
      }
    }

    // Create a temporary file to which we can write the schema entry.
    File tempFile = File.createTempFile(schemaFile, "temp");
    LDIFExportConfig exportConfig =
         new LDIFExportConfig(tempFile.getAbsolutePath(),
                              ExistingFileBehavior.OVERWRITE);
    try (LDIFWriter ldifWriter = new LDIFWriter(exportConfig))
    {
      ldifWriter.writeEntry(schemaEntry);
    }

    return tempFile;
  }

  /**
   * Returns custom syntaxes defined by OpenDJ configuration or by users.
   * <p>
   * These are non-standard syntaxes.
   *
   * @param schema
   *          the schema where to extract custom syntaxes from
   * @return custom, non-standard syntaxes
   */
  private Collection<Syntax> getCustomSyntaxes(org.forgerock.opendj.ldap.schema.Schema schema)
  {
    List<Syntax> results = new ArrayList<>();
    for (Syntax syntax : schema.getSyntaxes())
    {
      for (String propertyName : syntax.getExtraProperties().keySet())
      {
        if ("x-subst".equalsIgnoreCase(propertyName)
            || "x-pattern".equalsIgnoreCase(propertyName)
            || "x-enum".equalsIgnoreCase(propertyName)
            || "x-schema-file".equalsIgnoreCase(propertyName))
        {
          results.add(syntax);
          break;
        }
      }
    }
    return results;
  }

  private Set<ByteString> getValuesForSchemaFile(Collection<? extends SchemaElement> schemaElements, String schemaFile)
  {
    Set<ByteString> values = new LinkedHashSet<>();
    for (SchemaElement schemaElement : schemaElements)
    {
      if (schemaFile.equals(getElementSchemaFile(schemaElement)))
      {
        values.add(ByteString.valueOfUtf8(schemaElement.toString()));
      }
    }
    return values;
  }

  private Set<ByteString> getAttributeTypeValuesForSchemaFile(org.forgerock.opendj.ldap.schema.Schema schema,
      String schemaFile) throws DirectoryException
  {
    Set<AttributeType> addedTypes = new HashSet<>();
    Set<ByteString> values = new LinkedHashSet<>();
    for (AttributeType at : schema.getAttributeTypes())
    {
      if (schemaFile.equals(getElementSchemaFile(at)))
      {
        addAttrTypeToSchemaFile(schemaFile, at, values, addedTypes, 0);
      }
    }
    return values;
  }

  private Set<ByteString> getObjectClassValuesForSchemaFile(org.forgerock.opendj.ldap.schema.Schema schema,
      String schemaFile) throws DirectoryException
  {
    Set<ObjectClass> addedClasses = new HashSet<>();
    Set<ByteString> values = new LinkedHashSet<>();
    for (ObjectClass oc : schema.getObjectClasses())
    {
      if (schemaFile.equals(getElementSchemaFile(oc)))
      {
        addObjectClassToSchemaFile(schemaFile, oc, values, addedClasses, 0);
      }
    }
    return values;
  }

  private Set<ByteString> getDITStructureRuleValuesForSchemaFile(org.forgerock.opendj.ldap.schema.Schema schema,
      String schemaFile) throws DirectoryException
  {
    Set<DITStructureRule> addedDSRs = new HashSet<>();
    Set<ByteString> values = new LinkedHashSet<>();
    for (DITStructureRule dsr : schema.getDITStuctureRules())
    {
      if (schemaFile.equals(getElementSchemaFile(dsr)))
      {
        addDITStructureRuleToSchemaFile(schemaFile, dsr, values, addedDSRs, 0);
      }
    }
    return values;
  }

  private void addAttribute(Entry schemaEntry, AttributeType attrType, Set<ByteString> values)
  {
    if (!values.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(attrType);
      builder.addAll(values);
      schemaEntry.putAttribute(attrType, newArrayList(builder.toAttribute()));
    }
  }

  /**
   * Adds the definition for the specified attribute type to the provided set of
   * attribute values, recursively adding superior types as appropriate.
   *
   * @param  schema         The schema containing the attribute type.
   * @param  schemaFile     The schema file with which the attribute type is
   *                        associated.
   * @param  attributeType  The attribute type whose definition should be added
   *                        to the value set.
   * @param  values         The set of values for attribute type definitions
   *                        already added.
   * @param  addedTypes     The set of attribute types whose definitions have
   *                        already been added to the set of values.
   * @param  depth          A depth counter to use in an attempt to detect
   *                        circular references.
   */
  private void addAttrTypeToSchemaFile(String schemaFile,
                                       AttributeType attributeType,
                                       Set<ByteString> values,
                                       Set<AttributeType> addedTypes,
                                       int depth)
          throws DirectoryException
  {
    if (depth > 20)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_CIRCULAR_REFERENCE_AT.get(
          attributeType.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (addedTypes.contains(attributeType))
    {
      return;
    }

    AttributeType superiorType = attributeType.getSuperiorType();
    if (superiorType != null &&
        schemaFile.equals(getElementSchemaFile(attributeType)) &&
        !addedTypes.contains(superiorType))
    {
      addAttrTypeToSchemaFile(schemaFile, superiorType, values, addedTypes, depth+1);
    }

    values.add(ByteString.valueOfUtf8(attributeType.toString()));
    addedTypes.add(attributeType);
  }

  /**
   * Adds the definition for the specified objectclass to the provided set of
   * attribute values, recursively adding superior classes as appropriate.
   *
   * @param  schemaFile    The schema file with which the objectclass is
   *                       associated.
   * @param  objectClass   The objectclass whose definition should be added to
   *                       the value set.
   * @param  values        The set of values for objectclass definitions
   *                       already added.
   * @param  addedClasses  The set of objectclasses whose definitions have
   *                       already been added to the set of values.
   * @param  depth         A depth counter to use in an attempt to detect
   *                       circular references.
   */
  private void addObjectClassToSchemaFile(String schemaFile,
                                          ObjectClass objectClass,
                                          Set<ByteString> values,
                                          Set<ObjectClass> addedClasses,
                                          int depth)
          throws DirectoryException
  {
    if (depth > 20)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_CIRCULAR_REFERENCE_OC.get(
          objectClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (addedClasses.contains(objectClass))
    {
      return;
    }

    for(ObjectClass superiorClass : objectClass.getSuperiorClasses())
    {
      if (schemaFile.equals(getElementSchemaFile(superiorClass)) &&
          !addedClasses.contains(superiorClass))
      {
        addObjectClassToSchemaFile(schemaFile, superiorClass, values,
                                   addedClasses, depth+1);
      }
    }
    values.add(ByteString.valueOfUtf8(objectClass.toString()));
    addedClasses.add(objectClass);
  }

  /**
   * Adds the definition for the specified DIT structure rule to the provided
   * set of attribute values, recursively adding superior rules as appropriate.
   *
   * @param  schema            The schema containing the DIT structure rule.
   * @param  schemaFile        The schema file with which the DIT structure rule
   *                           is associated.
   * @param  ditStructureRule  The DIT structure rule whose definition should be
   *                           added to the value set.
   * @param  values            The set of values for DIT structure rule
   *                           definitions already added.
   * @param  addedDSRs         The set of DIT structure rules whose definitions
   *                           have already been added added to the set of
   *                           values.
   * @param  depth             A depth counter to use in an attempt to detect
   *                           circular references.
   */
  private void addDITStructureRuleToSchemaFile(String schemaFile,
                    DITStructureRule ditStructureRule,
                    Set<ByteString> values,
                    Set<DITStructureRule> addedDSRs, int depth)
          throws DirectoryException
  {
    if (depth > 20)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_CIRCULAR_REFERENCE_DSR.get(
          ditStructureRule.getNameOrRuleID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (addedDSRs.contains(ditStructureRule))
    {
      return;
    }

    for (DITStructureRule dsr : ditStructureRule.getSuperiorRules())
    {
      if (schemaFile.equals(getElementSchemaFile(dsr)) && !addedDSRs.contains(dsr))
      {
        addDITStructureRuleToSchemaFile(schemaFile, dsr, values,
                                        addedDSRs, depth+1);
      }
    }

    values.add(ByteString.valueOfUtf8(ditStructureRule.toString()));
    addedDSRs.add(ditStructureRule);
  }

  /**
   * Moves the specified temporary schema files in place of the active versions.
   * If an error occurs in the process, then this method will attempt to restore
   * the original schema files if possible.
   *
   * @param  tempSchemaFiles  The set of temporary schema files to be activated.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              install the temporary schema files.
   */
  private void installSchemaFiles(HashMap<String,File> tempSchemaFiles)
          throws DirectoryException
  {
    // Create lists that will hold the three types of files we'll be dealing
    // with (the temporary files that will be installed, the installed schema
    // files, and the previously-installed schema files).
    ArrayList<File> installedFileList = new ArrayList<>();
    ArrayList<File> tempFileList      = new ArrayList<>();
    ArrayList<File> origFileList      = new ArrayList<>();

    File schemaInstanceDir =
      new File(SchemaConfigManager.getSchemaDirectoryPath());

    for (String name : tempSchemaFiles.keySet())
    {
      installedFileList.add(new File(schemaInstanceDir, name));
      tempFileList.add(tempSchemaFiles.get(name));
      origFileList.add(new File(schemaInstanceDir, name + ".orig"));
    }

    // If there are any old ".orig" files laying around from a previous
    // attempt, then try to clean them up.
    for (File f : origFileList)
    {
      if (f.exists())
      {
        f.delete();
      }
    }

    // Copy all of the currently-installed files with a ".orig" extension.  If
    // this fails, then try to clean up the copies.
    try
    {
      for (int i=0; i < installedFileList.size(); i++)
      {
        File installedFile = installedFileList.get(i);
        File origFile      = origFileList.get(i);

        if (installedFile.exists())
        {
          copyFile(installedFile, origFile);
        }
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      boolean allCleaned = true;
      for (File f : origFileList)
      {
        try
        {
          if (f.exists() && !f.delete())
          {
            allCleaned = false;
          }
        }
        catch (Exception e2)
        {
          logger.traceException(e2);

          allCleaned = false;
        }
      }

      LocalizableMessage message;
      if (allCleaned)
      {
        message = ERR_SCHEMA_MODIFY_CANNOT_WRITE_ORIG_FILES_CLEANED.get(getExceptionMessage(e));
      }
      else
      {
        message = ERR_SCHEMA_MODIFY_CANNOT_WRITE_ORIG_FILES_NOT_CLEANED.get(getExceptionMessage(e));

        DirectoryServer.sendAlertNotification(this,
                             ALERT_TYPE_CANNOT_COPY_SCHEMA_FILES,
                             message);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
    }

    // Try to copy all of the temporary files into place over the installed
    // files.  If this fails, then try to restore the originals.
    try
    {
      for (int i=0; i < installedFileList.size(); i++)
      {
        File installedFile = installedFileList.get(i);
        File tempFile      = tempFileList.get(i);
        copyFile(tempFile, installedFile);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      deleteFiles(installedFileList);

      boolean allRestored = true;
      for (int i=0; i < installedFileList.size(); i++)
      {
        File installedFile = installedFileList.get(i);
        File origFile      = origFileList.get(i);

        try
        {
          if (origFile.exists() && !origFile.renameTo(installedFile))
          {
            allRestored = false;
          }
        }
        catch (Exception e2)
        {
          logger.traceException(e2);

          allRestored = false;
        }
      }

      LocalizableMessage message;
      if (allRestored)
      {
        message = ERR_SCHEMA_MODIFY_CANNOT_WRITE_NEW_FILES_RESTORED.get(getExceptionMessage(e));
      }
      else
      {
        message = ERR_SCHEMA_MODIFY_CANNOT_WRITE_NEW_FILES_NOT_RESTORED.get(getExceptionMessage(e));

        DirectoryServer.sendAlertNotification(this,
                             ALERT_TYPE_CANNOT_WRITE_NEW_SCHEMA_FILES,
                             message);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
    }

    deleteFiles(origFileList);
    deleteFiles(tempFileList);
  }

  private void deleteFiles(Iterable<File> files)
  {
    if (files != null)
    {
      for (File f : files)
      {
        try
        {
          if (f.exists())
          {
            f.delete();
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);
        }
      }
    }
  }

  /**
   * Creates a copy of the specified file.
   *
   * @param  from  The source file to be copied.
   * @param  to    The destination file to be created.
   *
   * @throws  IOException  If a problem occurs.
   */
  private void copyFile(File from, File to) throws IOException
  {
    try (FileInputStream inputStream = new FileInputStream(from);
        FileOutputStream outputStream = new FileOutputStream(to, false))
    {
      byte[] buffer = new byte[4096];
      int bytesRead = inputStream.read(buffer);
      while (bytesRead > 0)
      {
        outputStream.write(buffer, 0, bytesRead);
        bytesRead = inputStream.read(buffer);
      }
    }
  }

  /**
   * Performs any necessary cleanup in an attempt to delete any temporary schema
   * files that may have been left over after trying to install the new schema.
   *
   * @param  tempSchemaFiles  The set of temporary schema files that have been
   *                          created and are candidates for cleanup.
   */
  private void cleanUpTempSchemaFiles(HashMap<String,File> tempSchemaFiles)
  {
    deleteFiles(tempSchemaFiles.values());
  }

  @Override
  public void renameEntry(DN currentDN, Entry entry,
                                   ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_DN_NOT_SUPPORTED.get(currentDN, getBackendID()));
  }

  @Override
  public void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    DN baseDN = searchOperation.getBaseDN();

    boolean found = false;
    DN matchedDN = null;
    for (DN dn : this.baseDNs)
    {
      if (dn.equals(baseDN))
      {
        found = true;
        break;
      }
      else if (dn.isSuperiorOrEqualTo(baseDN))
      {
        matchedDN = dn;
        break;
      }
    }

    if (! found)
    {
      LocalizableMessage message = ERR_SCHEMA_INVALID_BASE.get(baseDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
              matchedDN, null);
    }

    // If it's a onelevel or subordinate subtree search, then we will never
    // match anything since there isn't anything below the schema.
    SearchScope scope = searchOperation.getScope();
    if (scope == SearchScope.SINGLE_LEVEL ||
        scope == SearchScope.SUBORDINATES)
    {
      return;
    }

    // Get the schema entry and see if it matches the filter.  If so, then send
    // it to the client.
    Entry schemaEntry = getSchemaEntry(baseDN);
    SearchFilter filter = searchOperation.getFilter();
    if (filter.matchesEntry(schemaEntry))
    {
      searchOperation.returnEntry(schemaEntry, null);
    }
  }

  @Override
  public Set<String> getSupportedControls()
  {
    return Collections.emptySet();
  }

  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

  @Override
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_SCHEMA_UNABLE_TO_CREATE_LDIF_WRITER.get(
          stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    // Write the root schema entry to it.  Make sure to close the LDIF
    // writer when we're done.
    try
    {
      ldifWriter.writeEntry(getSchemaEntry(baseDNs.iterator().next(), true, true));
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_SCHEMA_UNABLE_TO_EXPORT_BASE.get(stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    finally
    {
      close(ldifWriter);
    }
  }

  @Override
  public boolean supports(BackendOperation backendOperation)
  {
    switch (backendOperation)
    {
    case LDIF_EXPORT:
    case LDIF_IMPORT:
    case RESTORE:
      // We will provide a restore, but only for offline operations.
    case BACKUP:
      // We do support an online backup mechanism for the schema.
      return true;

    default:
      return false;
    }
  }

  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    try (LDIFReader reader = newLDIFReader(importConfig))
    {
      while (true)
      {
        Entry e = null;
        try
        {
          e = reader.readEntry();
          if (e == null)
          {
            break;
          }
        }
        catch (LDIFException le)
        {
          if (! le.canContinueReading())
          {
            throw new DirectoryException(
                DirectoryServer.getServerErrorResultCode(),
                ERR_MEMORYBACKEND_ERROR_READING_LDIF.get(e), le);
          }
          continue;
        }

        importEntry(e);
      }

      return new LDIFImportResult(reader.getEntriesRead(),
                                  reader.getEntriesRejected(),
                                  reader.getEntriesIgnored());
    }
    catch (DirectoryException de)
    {
      throw de;
    }
    catch (Exception e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_MEMORYBACKEND_ERROR_DURING_IMPORT.get(e), e);
    }
  }

  private LDIFReader newLDIFReader(LDIFImportConfig importConfig) throws DirectoryException
  {
    try
    {
      return new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_MEMORYBACKEND_CANNOT_CREATE_LDIF_READER.get(e), e);
    }
  }

  /**
   * Import an entry in a new schema by :
   *   - duplicating the schema
   *   - iterating over each element of the newSchemaEntry and comparing
   *     with the existing schema
   *   - if the new schema element do not exist : add it
   *
   *   FIXME : attributeTypes and objectClasses are the only elements
   *   currently taken into account.
   *
   * @param newSchemaEntry   The entry to be imported.
   */
  private void importEntry(Entry newSchemaEntry)
          throws DirectoryException
  {
    Schema schema = serverContext.getSchema();
    Schema newSchema = schema.duplicate();
    TreeSet<String> modifiedSchemaFiles = new TreeSet<>();

    // loop on the attribute types in the entry just received
    // and add them in the existing schema.
    Set<String> oidList = new HashSet<>(1000);
    for (Attribute a : newSchemaEntry.getAllAttributes(attributeTypesType))
    {
      // Look for attribute types that could have been added to the schema
      // or modified in the schema
      for (ByteString v : a)
      {
        AttributeType attrType = schema.parseAttributeType(v.toString());
        String schemaFile = getElementSchemaFile(attrType);
        if (is02ConfigLdif(schemaFile))
        {
          continue;
        }

        oidList.add(attrType.getOID());
        try
        {
          // Register this attribute type in the new schema
          // unless it is already defined with the same syntax.
          if (hasDefinitionChanged(schema, attrType))
          {
            newSchema.registerAttributeType(attrType, schemaFile, true);
            addElementIfNotNull(modifiedSchemaFiles, schemaFile);
          }
        }
        catch (Exception e)
        {
          logger.info(NOTE_SCHEMA_IMPORT_FAILED, attrType, e.getMessage());
        }
      }
    }

    // loop on all the attribute types in the current schema and delete
    // them from the new schema if they are not in the imported schema entry.
    for (AttributeType removeType : newSchema.getAttributeTypes())
    {
      String schemaFile = getElementSchemaFile(removeType);
      if (is02ConfigLdif(schemaFile) || CORE_SCHEMA_ELEMENTS_FILE.equals(schemaFile))
      {
        // Also never delete anything from the core schema file.
        continue;
      }
      if (!oidList.contains(removeType.getOID()))
      {
        newSchema.deregisterAttributeType(removeType);
        addElementIfNotNull(modifiedSchemaFiles, schemaFile);
      }
    }

    // loop on the objectClasses from the entry, search if they are
    // already in the current schema, add them if not.
    oidList.clear();
    for (Attribute a : newSchemaEntry.getAllAttributes(objectClassesType))
    {
      for (ByteString v : a)
      {
        // It IS important here to allow the unknown elements that could
        // appear in the new config schema.
        ObjectClass newObjectClass = newSchema.parseObjectClass(v.toString());
        String schemaFile = getElementSchemaFile(newObjectClass);
        if (is02ConfigLdif(schemaFile))
        {
          continue;
        }

        oidList.add(newObjectClass.getOID());
        try
        {
          // Register this ObjectClass in the new schema
          // unless it is already defined with the same syntax.
          if (hasDefinitionChanged(schema, newObjectClass))
          {
            newSchema.registerObjectClass(newObjectClass, schemaFile, true);
            addElementIfNotNull(modifiedSchemaFiles, schemaFile);
          }
        }
        catch (Exception e)
        {
          logger.info(NOTE_SCHEMA_IMPORT_FAILED, newObjectClass, e.getMessage());
        }
      }
    }

    // loop on all the object classes in the current schema and delete
    // them from the new schema if they are not in the imported schema entry.
    for (ObjectClass removeClass : newSchema.getObjectClasses())
    {
      String schemaFile = getElementSchemaFile(removeClass);
      if (is02ConfigLdif(schemaFile))
      {
        continue;
      }
      if (!oidList.contains(removeClass.getOID()))
      {
        newSchema.deregisterObjectClass(removeClass);
        addElementIfNotNull(modifiedSchemaFiles, schemaFile);
      }
    }

    // Finally, if there were some modifications, save the new schema
    // in the Schema Files and update DirectoryServer.
    if (!modifiedSchemaFiles.isEmpty())
    {
      updateSchemaFiles(newSchema, modifiedSchemaFiles);
      DirectoryServer.setSchema(newSchema);
    }
  }

  /**
   * Do not import the file containing the definitions of the Schema elements used for configuration
   * because these definitions may vary between versions of OpenDJ.
   */
  private boolean is02ConfigLdif(String schemaFile)
  {
    return CONFIG_SCHEMA_ELEMENTS_FILE.equals(schemaFile);
  }

  private <T> void addElementIfNotNull(Collection<T> col, T element)
  {
    if (element != null)
    {
      col.add(element);
    }
  }

  private boolean hasDefinitionChanged(Schema schema, AttributeType newAttrType)
  {
    AttributeType oldAttrType = schema.getAttributeType(newAttrType.getOID());
    return oldAttrType.isPlaceHolder() || !oldAttrType.toString().equals(newAttrType.toString());
  }

  private boolean hasDefinitionChanged(Schema schema, ObjectClass newObjectClass)
  {
    ObjectClass oldObjectClass = schema.getObjectClass(newObjectClass.getOID());
    return oldObjectClass.isPlaceHolder() || !oldObjectClass.toString().equals(newObjectClass.toString());
  }

  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    new BackupManager(getBackendID()).createBackup(this, backupConfig);
  }

  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
    new BackupManager(getBackendID()).removeBackup(backupDirectory, backupID);
  }

  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    new BackupManager(getBackendID()).restoreBackup(this, restoreConfig);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
       SchemaBackendCfg configEntry,
       List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(SchemaBackendCfg backendCfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Check to see if we should apply a new set of base DNs.
    Set<DN> newBaseDNs;
    try
    {
      newBaseDNs = new HashSet<>(backendCfg.getSchemaEntryDN());
      if (newBaseDNs.isEmpty())
      {
        newBaseDNs.add(DN.valueOf(DN_DEFAULT_SCHEMA_ROOT));
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.addMessage(ERR_SCHEMA_CANNOT_DETERMINE_BASE_DN.get(
          configEntryDN, getExceptionMessage(e)));
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      newBaseDNs = null;
    }

    // Check to see if there is a new set of user-defined attributes.
    List<Attribute> newUserAttrs = new ArrayList<>();
    try
    {
      Entry configEntry = DirectoryServer.getConfigEntry(configEntryDN);
      addAllNonSchemaConfigAttributes(newUserAttrs, configEntry.getAllAttributes());
    }
    catch (ConfigException e)
    {
      logger.traceException(e);

      ccr.addMessage(ERR_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY.get(
          configEntryDN, stackTraceToSingleLineString(e)));
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      // Determine the set of DNs to add and delete.  When this is done, the
      // deleteBaseDNs will contain the set of DNs that should no longer be used
      // and should be deregistered from the server, and the newBaseDNs set will
      // just contain the set of DNs to add.
      Set<DN> deleteBaseDNs = new HashSet<>(baseDNs.size());
      for (DN baseDN : baseDNs)
      {
        if (! newBaseDNs.remove(baseDN))
        {
          deleteBaseDNs.add(baseDN);
        }
      }

      for (DN dn : deleteBaseDNs)
      {
        try
        {
          DirectoryServer.deregisterBaseDN(dn);
          ccr.addMessage(INFO_SCHEMA_DEREGISTERED_BASE_DN.get(dn));
        }
        catch (Exception e)
        {
          logger.traceException(e);

          ccr.addMessage(ERR_SCHEMA_CANNOT_DEREGISTER_BASE_DN.get(dn, getExceptionMessage(e)));
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        }
      }

      baseDNs = newBaseDNs;
      for (DN dn : baseDNs)
      {
        try
        {
          DirectoryServer.registerBaseDN(dn, this, true);
          ccr.addMessage(INFO_SCHEMA_REGISTERED_BASE_DN.get(dn));
        }
        catch (Exception e)
        {
          logger.traceException(e);

          ccr.addMessage(ERR_SCHEMA_CANNOT_REGISTER_BASE_DN.get(dn, getExceptionMessage(e)));
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        }
      }

      userDefinedAttributes = newUserAttrs;
      ccr.addMessage(INFO_SCHEMA_USING_NEW_USER_ATTRS.get());
    }

    currentConfig = backendCfg;
    return ccr;
  }

  private void addAllNonSchemaConfigAttributes(List<Attribute> newUserAttrs, Iterable<Attribute> attributes)
  {
    for (Attribute a : attributes)
    {
      if (!isSchemaConfigAttribute(a))
      {
        newUserAttrs.add(a);
      }
    }
  }

  /**
   * Indicates whether to treat common schema attributes like user attributes
   * rather than operational attributes.
   *
   * @return  {@code true} if common attributes should be treated like user
   *          attributes, or {@code false} if not.
   */
  boolean showAllAttributes()
  {
    return this.currentConfig.isShowAllAttributes();
  }

  @Override
  public DN getComponentEntryDN()
  {
    return configEntryDN;
  }

  @Override
  public String getClassName()
  {
    return CLASS_NAME;
  }

  @Override
  public Map<String, String> getAlerts()
  {
    Map<String, String> alerts = new LinkedHashMap<>();

    alerts.put(ALERT_TYPE_CANNOT_COPY_SCHEMA_FILES,
               ALERT_DESCRIPTION_CANNOT_COPY_SCHEMA_FILES);
    alerts.put(ALERT_TYPE_CANNOT_WRITE_NEW_SCHEMA_FILES,
               ALERT_DESCRIPTION_CANNOT_WRITE_NEW_SCHEMA_FILES);

    return alerts;
  }

  @Override
  public File getDirectory()
  {
    return new File(SchemaConfigManager.getSchemaDirectoryPath());
  }

  private static final FileFilter BACKUP_FILES_FILTER = new FileFilter()
  {
    @Override
    public boolean accept(File file)
    {
      return file.getName().endsWith(".ldif");
    }
  };

  @Override
  public ListIterator<Path> getFilesToBackup() throws DirectoryException
  {
    return BackupManager.getFiles(getDirectory(), BACKUP_FILES_FILTER, getBackendID()).listIterator();
  }

  @Override
  public boolean isDirectRestore()
  {
    return true;
  }

  @Override
  public Path beforeRestore() throws DirectoryException
  {
    // save current schema files in save directory
    return BackupManager.saveCurrentFilesToDirectory(this, getBackendID());
  }

  @Override
  public void afterRestore(Path restoreDirectory, Path saveDirectory) throws DirectoryException
  {
    // restore was successful, delete save directory
    StaticUtils.recursiveDelete(saveDirectory.toFile());
  }
}
