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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.types.CommonSchemaElements.*;
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
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.ObjectClassType;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.SchemaBackendCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.api.Backupable;
import org.opends.server.api.ClientConnection;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.AttributeTypeSyntax;
import org.opends.server.schema.DITContentRuleSyntax;
import org.opends.server.schema.DITStructureRuleSyntax;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.schema.LDAPSyntaxDescriptionSyntax;
import org.opends.server.schema.MatchingRuleUseSyntax;
import org.opends.server.schema.NameFormSyntax;
import org.opends.server.schema.ObjectClassSyntax;
import org.opends.server.types.*;
import org.opends.server.util.BackupManager;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.StaticUtils;

/**
 * This class defines a backend to hold the Directory Server schema information.
 * It is a kind of meta-backend in that it doesn't actually hold any data but
 * rather dynamically generates the schema entry whenever it is requested.
 */
public class SchemaBackend extends Backend<SchemaBackendCfg>
     implements ConfigurationChangeListener<SchemaBackendCfg>, AlertGenerator, Backupable
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.SchemaBackend";


  private static final String CONFIG_SCHEMA_ELEMENTS_FILE = "02-config.ldif";
  private static final String CORE_SCHEMA_ELEMENTS_FILE = "00-core.ldif";



  /**
   * The set of user-defined attributes that will be included in the schema
   * entry.
   */
  private ArrayList<Attribute> userDefinedAttributes;

  /**
   * The attribute type that will be used to include the defined attribute
   * types.
   */
  private AttributeType attributeTypesType;

  /**
   * The attribute type that will be used to hold the schema creation timestamp.
   */
  private AttributeType createTimestampType;

  /** The attribute type that will be used to hold the schema creator's name. */
  private AttributeType creatorsNameType;

  /**
   * The attribute type that will be used to include the defined DIT content
   * rules.
   */
  private AttributeType ditContentRulesType;

  /**
   * The attribute type that will be used to include the defined DIT structure
   * rules.
   */
  private AttributeType ditStructureRulesType;

  /**
   * The attribute type that will be used to include the defined attribute
   * syntaxes.
   */
  private AttributeType ldapSyntaxesType;

  /**
   * The attribute type that will be used to include the defined matching rules.
   */
  private AttributeType matchingRulesType;

  /**
   * The attribute type that will be used to include the defined matching rule
   * uses.
   */
  private AttributeType matchingRuleUsesType;

  /** The attribute that will be used to hold the schema modifier's name. */
  private AttributeType modifiersNameType;

  /**
   * The attribute type that will be used to hold the schema modification
   * timestamp.
   */
  private AttributeType modifyTimestampType;

  /**
   * The attribute type that will be used to include the defined object classes.
   */
  private AttributeType objectClassesType;

  /** The attribute type that will be used to include the defined name forms. */
  private AttributeType nameFormsType;

  /**
   * The value containing DN of the user we'll say created the configuration.
   */
  private ByteString creatorsName;

  /**
   * The value containing the DN of the last user to modify the configuration.
   */
  private ByteString modifiersName;

  /** The timestamp that will be used for the schema creation time. */
  private ByteString createTimestamp;

  /**
   * The timestamp that will be used for the latest schema modification time.
   */
  private ByteString modifyTimestamp;

  /**
   * Indicates whether the attributes of the schema entry should always be
   * treated as user attributes even if they are defined as operational.
   */
  private boolean showAllAttributes;

  /** The DN of the configuration entry for this backend. */
  private DN configEntryDN;

  /** The current configuration state. */
  private SchemaBackendCfg currentConfig;

  /** The set of base DNs for this backend. */
  private DN[] baseDNs;

  /** The set of objectclasses that will be used in the schema entry. */
  private HashMap<ObjectClass,String> schemaObjectClasses;

  /** The time that the schema was last modified. */
  private long modifyTime;

  /**
   * Regular expression used to strip minimum upper bound value from syntax
   * Attribute Type Description. The value looks like: {count}.
   */
  private String stripMinUpperBoundRegEx = "\\{\\d+\\}";



  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public SchemaBackend()
  {
    super();

    // Perform all initialization in initializeBackend.
  }

  /** {@inheritDoc} */
  @Override
  public void configureBackend(SchemaBackendCfg cfg, ServerContext serverContext) throws ConfigException
  {
    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (cfg == null)
    {
      LocalizableMessage message = ERR_SCHEMA_CONFIG_ENTRY_NULL.get();
      throw new ConfigException(message);
    }

    ConfigEntry configEntry = DirectoryServer.getConfigEntry(cfg.dn());

    configEntryDN = configEntry.getDN();

    // Get all of the attribute types that we will use for schema elements.
    attributeTypesType =
         DirectoryServer.getAttributeType(ATTR_ATTRIBUTE_TYPES_LC, true);
    objectClassesType =
         DirectoryServer.getAttributeType(ATTR_OBJECTCLASSES_LC, true);
    matchingRulesType =
         DirectoryServer.getAttributeType(ATTR_MATCHING_RULES_LC, true);
    ldapSyntaxesType =
         DirectoryServer.getAttributeType(ATTR_LDAP_SYNTAXES_LC, true);
    ditContentRulesType =
         DirectoryServer.getAttributeType(ATTR_DIT_CONTENT_RULES_LC, true);
    ditStructureRulesType =
         DirectoryServer.getAttributeType(ATTR_DIT_STRUCTURE_RULES_LC, true);
    matchingRuleUsesType =
         DirectoryServer.getAttributeType(ATTR_MATCHING_RULE_USE_LC, true);
    nameFormsType = DirectoryServer.getAttributeType(ATTR_NAME_FORMS_LC, true);

    // Initialize the lastmod attributes.
    creatorsNameType =
         DirectoryServer.getAttributeType(OP_ATTR_CREATORS_NAME_LC, true);
    createTimestampType =
         DirectoryServer.getAttributeType(OP_ATTR_CREATE_TIMESTAMP_LC, true);
    modifiersNameType =
         DirectoryServer.getAttributeType(OP_ATTR_MODIFIERS_NAME_LC, true);
    modifyTimestampType =
         DirectoryServer.getAttributeType(OP_ATTR_MODIFY_TIMESTAMP_LC, true);

    // Construct the set of objectclasses to include in the schema entry.
    schemaObjectClasses = new LinkedHashMap<ObjectClass,String>(3);
    schemaObjectClasses.put(DirectoryServer.getTopObjectClass(), OC_TOP);

    ObjectClass subentryOC = DirectoryServer.getObjectClass(OC_LDAP_SUBENTRY_LC,
                                                            true);
    schemaObjectClasses.put(subentryOC, OC_LDAP_SUBENTRY);

    ObjectClass subschemaOC = DirectoryServer.getObjectClass(OC_SUBSCHEMA,
                                                             true);
    schemaObjectClasses.put(subschemaOC, OC_SUBSCHEMA);


    configEntryDN = configEntry.getDN();

    DN[] newBaseDNs = new DN[cfg.getBaseDN().size()];
    cfg.getBaseDN().toArray(newBaseDNs);
    this.baseDNs = newBaseDNs;

    creatorsName  = ByteString.valueOf(newBaseDNs[0].toString());
    modifiersName = ByteString.valueOf(newBaseDNs[0].toString());

    long createTime = DirectoryServer.getSchema().getOldestModificationTime();
    createTimestamp =
         GeneralizedTimeSyntax.createGeneralizedTimeValue(createTime);

    long newModifyTime =
        DirectoryServer.getSchema().getYoungestModificationTime();
    modifyTimestamp =
         GeneralizedTimeSyntax.createGeneralizedTimeValue(newModifyTime);


    // Get the set of user-defined attributes for the configuration entry.  Any
    // attributes that we don't recognize will be included directly in the
    // schema entry.
    userDefinedAttributes = new ArrayList<Attribute>();
    addAll(configEntry.getEntry().getUserAttributes().values());
    addAll(configEntry.getEntry().getOperationalAttributes().values());

    showAllAttributes = cfg.isShowAllAttributes();

    currentConfig = cfg;
  }

  private void addAll(Collection<List<Attribute>> attrsList)
  {
    for (List<Attribute> attrs : attrsList)
    {
      for (Attribute a : attrs)
      {
        if (! isSchemaConfigAttribute(a))
        {
          userDefinedAttributes.add(a);
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void openBackend() throws ConfigException, InitializationException
  {
    // Register each of the suffixes with the Directory Server.  Also, register
    // the first one as the schema base.
    DirectoryServer.setSchemaDN(baseDNs[0]);
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


    // Identify any differences that may exist between the concatenated schema
    // file from the last online modification and the current schema files.  If
    // there are any differences, then they should be from making changes to the
    // schema files with the server offline.
    try
    {
      // First, generate lists of elements from the current schema.
      Set<String> newATs  = new LinkedHashSet<String>();
      Set<String> newOCs  = new LinkedHashSet<String>();
      Set<String> newNFs  = new LinkedHashSet<String>();
      Set<String> newDCRs = new LinkedHashSet<String>();
      Set<String> newDSRs = new LinkedHashSet<String>();
      Set<String> newMRUs = new LinkedHashSet<String>();
      Set<String> newLSDs = new LinkedHashSet<String>();
      Schema.genConcatenatedSchema(newATs, newOCs, newNFs, newDCRs, newDSRs,
                                   newMRUs,newLSDs);

      // Next, generate lists of elements from the previous concatenated schema.
      // If there isn't a previous concatenated schema, then use the base
      // schema for the current revision.
      String concatFilePath;
      File configFile       = new File(DirectoryServer.getConfigFile());
      File configDirectory  = configFile.getParentFile();
      File upgradeDirectory = new File(configDirectory, "upgrade");
      File concatFile       = new File(upgradeDirectory,
                                       SCHEMA_CONCAT_FILE_NAME);
      if (concatFile.exists())
      {
        concatFilePath = concatFile.getAbsolutePath();
      }
      else
      {
        concatFile = new File(upgradeDirectory,
                              SCHEMA_BASE_FILE_NAME_WITHOUT_REVISION +
                              DynamicConstants.REVISION_NUMBER);
        if (concatFile.exists())
        {
          concatFilePath = concatFile.getAbsolutePath();
        }
        else
        {
          String runningUnitTestsStr =
               System.getProperty(PROPERTY_RUNNING_UNIT_TESTS);
          if ("true".equalsIgnoreCase(runningUnitTestsStr))
          {
            Schema.writeConcatenatedSchema();
            concatFile = new File(upgradeDirectory, SCHEMA_CONCAT_FILE_NAME);
            concatFilePath = concatFile.getAbsolutePath();
          }
          else
          {
            LocalizableMessage message = ERR_SCHEMA_CANNOT_FIND_CONCAT_FILE.
                get(upgradeDirectory.getAbsolutePath(), SCHEMA_CONCAT_FILE_NAME,
                    concatFile.getName());
            throw new InitializationException(message);
          }
        }
      }

      Set<String> oldATs  = new LinkedHashSet<String>();
      Set<String> oldOCs  = new LinkedHashSet<String>();
      Set<String> oldNFs  = new LinkedHashSet<String>();
      Set<String> oldDCRs = new LinkedHashSet<String>();
      Set<String> oldDSRs = new LinkedHashSet<String>();
      Set<String> oldMRUs = new LinkedHashSet<String>();
      Set<String> oldLSDs = new LinkedHashSet<String>();
      Schema.readConcatenatedSchema(concatFilePath, oldATs, oldOCs, oldNFs,
                                    oldDCRs, oldDSRs, oldMRUs,oldLSDs);

      // Create a list of modifications and add any differences between the old
      // and new schema into them.
      List<Modification> mods = new LinkedList<Modification>();
      Schema.compareConcatenatedSchema(oldATs, newATs, attributeTypesType, mods);
      Schema.compareConcatenatedSchema(oldOCs, newOCs, objectClassesType, mods);
      Schema.compareConcatenatedSchema(oldNFs, newNFs, nameFormsType, mods);
      Schema.compareConcatenatedSchema(oldDCRs, newDCRs, ditContentRulesType, mods);
      Schema.compareConcatenatedSchema(oldDSRs, newDSRs, ditStructureRulesType, mods);
      Schema.compareConcatenatedSchema(oldMRUs, newMRUs, matchingRuleUsesType, mods);
      Schema.compareConcatenatedSchema(oldLSDs, newLSDs, ldapSyntaxesType, mods);
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


    // Register with the Directory Server as a configurable component.
    currentConfig.addSchemaChangeListener(this);
  }

  /** {@inheritDoc} */
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
    AttributeType attrType = attribute.getAttributeType();
    return attrType.hasName(ATTR_SCHEMA_ENTRY_DN.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_ENABLED.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_CLASS.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_ID.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_BASE_DN.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_WRITABILITY_MODE.toLowerCase()) ||
        attrType.hasName(ATTR_SCHEMA_SHOW_ALL_ATTRIBUTES.toLowerCase()) ||
        attrType.hasName(ATTR_COMMON_NAME) ||
        attrType.hasName(OP_ATTR_CREATORS_NAME_LC) ||
        attrType.hasName(OP_ATTR_CREATE_TIMESTAMP_LC) ||
        attrType.hasName(OP_ATTR_MODIFIERS_NAME_LC) ||
        attrType.hasName(OP_ATTR_MODIFY_TIMESTAMP_LC);

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
    // There is always only a single entry in this backend.
    return 1;
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
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    return ConditionResult.FALSE;
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
  {
    checkNotNull(baseDN, "baseDN must not be null");
    return 1L;
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException
  {
    checkNotNull(parentDN, "parentDN must not be null");
    return 0L;
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    // If the requested entry was one of the schema entries, then create and
    // return it.
    DN[] dnArray = baseDNs;
    for (DN baseDN : dnArray)
    {
      if (entryDN.equals(baseDN))
      {
        return getSchemaEntry(entryDN, false, true);
      }
    }


    // There is never anything below the schema entries, so we will return null.
    return null;
  }


  /**
   * Generates and returns a schema entry for the Directory Server.
   *
   * @param  entryDN            The DN to use for the generated entry.
   * @param  includeSchemaFile  A boolean indicating if the X-SCHEMA-FILE
   *                            extension should be used when generating
   *                            the entry.
   *
   * @return  The schema entry that was generated.
   */
  public Entry getSchemaEntry(DN entryDN, boolean includeSchemaFile)
  {
    return getSchemaEntry(entryDN, includeSchemaFile, false);
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
    Map<AttributeType, List<Attribute>> userAttrs =
      new LinkedHashMap<AttributeType, List<Attribute>>();

    Map<AttributeType, List<Attribute>> operationalAttrs =
      new LinkedHashMap<AttributeType, List<Attribute>>();

    // Add the RDN attribute(s) for the provided entry.
    RDN rdn = entryDN.rdn();
    if (rdn != null)
    {
      int numAVAs = rdn.getNumValues();
      for (int i = 0; i < numAVAs; i++)
      {
        AttributeType attrType = rdn.getAttributeType(i);
        Attribute attribute = Attributes.create(attrType, rdn.getAttributeValue(i));
        addAttributeToSchemaEntry(attribute, userAttrs, operationalAttrs);
      }
    }

    /*
     * Add the schema definition attributes.
     */
    Schema schema = DirectoryServer.getSchema();
    buildSchemaAttribute(schema.getAttributeTypes().values(), userAttrs,
        operationalAttrs, attributeTypesType, includeSchemaFile,
        AttributeTypeSyntax.isStripSyntaxMinimumUpperBound(),
        ignoreShowAllOption);
    buildSchemaAttribute(schema.getObjectClasses().values(), userAttrs,
        operationalAttrs, objectClassesType, includeSchemaFile, false,
        ignoreShowAllOption);
    buildSchemaAttribute(schema.getMatchingRules().values(), userAttrs,
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
    buildSchemaAttribute(schema.getSyntaxes().values(), userAttrs,
        operationalAttrs, ldapSyntaxesType, includeSchemaFile, false, true);
    buildSchemaAttribute(schema.getNameFormsByNameOrOID().values(), userAttrs,
        operationalAttrs, nameFormsType, includeSchemaFile, false, true);
    buildSchemaAttribute(schema.getDITContentRules().values(), userAttrs,
        operationalAttrs, ditContentRulesType, includeSchemaFile, false, true);
    buildSchemaAttribute(schema.getDITStructureRulesByID().values(), userAttrs,
        operationalAttrs, ditStructureRulesType, includeSchemaFile, false, true);
    buildSchemaAttribute(schema.getMatchingRuleUses().values(), userAttrs,
        operationalAttrs, matchingRuleUsesType, includeSchemaFile, false, true);

    // Add the lastmod attributes.
    if (DirectoryServer.getSchema().getYoungestModificationTime() != modifyTime)
    {
      synchronized (this)
      {
        modifyTime = DirectoryServer.getSchema().getYoungestModificationTime();
        modifyTimestamp = GeneralizedTimeSyntax
            .createGeneralizedTimeValue(modifyTime);
      }
    }
    addAttributeToSchemaEntry(
        Attributes.create(creatorsNameType, creatorsName), userAttrs, operationalAttrs);
    addAttributeToSchemaEntry(
        Attributes.create(createTimestampType, createTimestamp), userAttrs, operationalAttrs);
    addAttributeToSchemaEntry(
        Attributes.create(modifiersNameType, modifiersName), userAttrs, operationalAttrs);
    addAttributeToSchemaEntry(
        Attributes.create(modifyTimestampType, modifyTimestamp), userAttrs, operationalAttrs);

    // Add the extra attributes.
    for (Attribute attribute : DirectoryServer.getSchema().getExtraAttributes().values())
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
    AttributeType type = attribute.getAttributeType();
    Map<AttributeType, List<Attribute>> attrsMap = type.isOperational() ? operationalAttrs : userAttrs;
    List<Attribute> attrs = attrsMap.get(type);
    if (attrs == null)
    {
      attrs = new ArrayList<Attribute>(1);
      attrsMap.put(type, attrs);
    }
    attrs.add(attribute);
  }



  private void buildSchemaAttribute(Collection<?> elements,
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
    builder.setInitialCapacity(elements.size());
    for (Object element : elements)
    {
      /*
       * Add the file name to the description of the element if this was
       * requested by the caller.
       */
      String value;
      if (includeSchemaFile && element instanceof CommonSchemaElements)
      {
        value = getDefinitionWithFileName((CommonSchemaElements) element);
      }
      else
      {
        value = element.toString();
      }
      if (stripSyntaxMinimumUpperBound && value.indexOf('{') != -1)
      {
        // Strip the minimum upper bound value from the attribute value.
        value = value.replaceFirst(stripMinUpperBoundRegEx, "");
      }
      builder.add(value);
    }

    Attribute attribute = builder.toAttribute();
    ArrayList<Attribute> attrList = newArrayList(attribute);
    if (attribute.getAttributeType().isOperational()
        && (ignoreShowAllOption || !showAllAttributes))
    {
      operationalAttrs.put(attribute.getAttributeType(), attrList);
    }
    else
    {
      userAttrs.put(attribute.getAttributeType(), attrList);
    }
  }

  private ArrayList<Attribute> newArrayList(Attribute a)
  {
    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    return attrList;
  }

  /** {@inheritDoc} */
  @Override
  public boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    // The specified DN must be one of the specified schema DNs.
    DN[] baseArray = baseDNs;
    for (DN baseDN : baseArray)
    {
      if (entryDN.equals(baseDN))
      {
        return true;
      }
    }

    return false;
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
    // Make sure that the authenticated user has the necessary UPDATE_SCHEMA
    // privilege.
    ClientConnection clientConnection = modifyOperation.getClientConnection();
    if (! clientConnection.hasPrivilege(Privilege.UPDATE_SCHEMA,
                                        modifyOperation))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_INSUFFICIENT_PRIVILEGES.get();
      throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                   message);
    }


    ArrayList<Modification> mods =
         new ArrayList<Modification>(modifyOperation.getModifications());
    if (mods.isEmpty())
    {
      // There aren't any modifications, so we don't need to do anything.
      return;
    }

    Schema newSchema = DirectoryServer.getSchema().duplicate();
    TreeSet<String> modifiedSchemaFiles = new TreeSet<String>();

    int pos = -1;
    for (Modification m : mods)
    {
      pos++;

      // Determine the type of modification to perform.  We will support add and
      // delete operations in the schema, and we will also support the ability
      // to add a schema element that already exists and treat it as a
      // replacement of that existing element.
      Attribute a = m.getAttribute();
      AttributeType at = a.getAttributeType();
      switch (m.getModificationType().asEnum())
      {
        case ADD:
          if (at.equals(attributeTypesType))
          {
            for (ByteString v : a)
            {
              AttributeType type;
              try
              {
                type = AttributeTypeSyntax.decodeAttributeType(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_ATTRTYPE.get(
                    v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              addAttributeType(type, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(objectClassesType))
          {
            for (ByteString v : a)
            {
              ObjectClass oc;
              try
              {
                oc = ObjectClassSyntax.decodeObjectClass(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_OBJECTCLASS.
                    get(v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              addObjectClass(oc, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(nameFormsType))
          {
            for (ByteString v : a)
            {
              NameForm nf;
              try
              {
                nf = NameFormSyntax.decodeNameForm(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_NAME_FORM.get(
                    v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              addNameForm(nf, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(ditContentRulesType))
          {
            for (ByteString v : a)
            {
              DITContentRule dcr;
              try
              {
                dcr = DITContentRuleSyntax.decodeDITContentRule(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_DCR.get(
                    v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              addDITContentRule(dcr, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(ditStructureRulesType))
          {
            for (ByteString v : a)
            {
              DITStructureRule dsr;
              try
              {
                dsr = DITStructureRuleSyntax.decodeDITStructureRule(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_DSR.get(
                    v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              addDITStructureRule(dsr, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(matchingRuleUsesType))
          {
            for (ByteString v : a)
            {
              MatchingRuleUse mru;
              try
              {
                mru = MatchingRuleUseSyntax.decodeMatchingRuleUse(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_MR_USE.get(
                    v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              addMatchingRuleUse(mru, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(ldapSyntaxesType))
          {
            for (ByteString v : a)
            {
              LDAPSyntaxDescription lsd;
              try
              {
                lsd = LDAPSyntaxDescriptionSyntax.decodeLDAPSyntax(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message =
                    ERR_SCHEMA_MODIFY_CANNOT_DECODE_LDAP_SYNTAX.get(v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }
              addLdapSyntaxDescription(lsd, newSchema, modifiedSchemaFiles);
            }
          }
          else
          {
            LocalizableMessage message =
                ERR_SCHEMA_MODIFY_UNSUPPORTED_ATTRIBUTE_TYPE.get(a.getName());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                message);
          }

          break;


        case DELETE:
          if (a.isEmpty())
          {
            LocalizableMessage message =
                ERR_SCHEMA_MODIFY_DELETE_NO_VALUES.get(a.getName());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                message);
          }

          if (at.equals(attributeTypesType))
          {
            for (ByteString v : a)
            {
              AttributeType type;
              try
              {
                type = AttributeTypeSyntax.decodeAttributeType(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_ATTRTYPE.get(
                    v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              removeAttributeType(type, newSchema, mods, pos,
                  modifiedSchemaFiles);
            }
          }
          else if (at.equals(objectClassesType))
          {
            for (ByteString v : a)
            {
              ObjectClass oc;
              try
              {
                oc = ObjectClassSyntax.decodeObjectClass(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_OBJECTCLASS.
                    get(v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              removeObjectClass(oc, newSchema, mods, pos, modifiedSchemaFiles);
            }
          }
          else if (at.equals(nameFormsType))
          {
            for (ByteString v : a)
            {
              NameForm nf;
              try
              {
                nf = NameFormSyntax.decodeNameForm(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_NAME_FORM.get(
                    v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              removeNameForm(nf, newSchema, mods, pos, modifiedSchemaFiles);
            }
          }
          else if (at.equals(ditContentRulesType))
          {
            for (ByteString v : a)
            {
              DITContentRule dcr;
              try
              {
                dcr = DITContentRuleSyntax.decodeDITContentRule(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_DCR.get(
                    v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              removeDITContentRule(dcr, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(ditStructureRulesType))
          {
            for (ByteString v : a)
            {
              DITStructureRule dsr;
              try
              {
                dsr = DITStructureRuleSyntax.decodeDITStructureRule(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_DSR.get(
                    v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              removeDITStructureRule(dsr, newSchema, mods, pos,
                  modifiedSchemaFiles);
            }
          }
          else if (at.equals(matchingRuleUsesType))
          {
            for (ByteString v : a)
            {
              MatchingRuleUse mru;
              try
              {
                mru = MatchingRuleUseSyntax.decodeMatchingRuleUse(v, newSchema, false);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);

                LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_MR_USE.get(
                    v, de.getMessageObject());
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
              }

              removeMatchingRuleUse(mru, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(ldapSyntaxesType))
          {
            for (ByteString v : a)
            {
              LDAPSyntaxDescription lsd;
              try
              {
                lsd = LDAPSyntaxDescriptionSyntax.decodeLDAPSyntax(v, newSchema, false);
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
              removeLdapSyntaxDescription(lsd, newSchema, modifiedSchemaFiles);
            }
          }
          else
          {
            LocalizableMessage message =
                ERR_SCHEMA_MODIFY_UNSUPPORTED_ATTRIBUTE_TYPE.get(a.getName());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                message);
          }

          break;


        case REPLACE:
          if (!m.isInternal()
              && !modifyOperation.isSynchronizationOperation())
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                ERR_SCHEMA_INVALID_MODIFICATION_TYPE.get(m.getModificationType()));
          }
          else  if (SchemaConfigManager.isSchemaAttribute(a))
          {
            logger.error(ERR_SCHEMA_INVALID_REPLACE_MODIFICATION, a.getNameWithOptions());
          }
          else
          {
            // If this is not a Schema attribute, we put it
            // in the extraAttribute map. This in fact acts as a replace.
            newSchema.addExtraAttribute(at.getNameOrOID(), a);
            modifiedSchemaFiles.add(FILE_USER_SCHEMA_ELEMENTS);
          }
          break;

        default:
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
              ERR_SCHEMA_INVALID_MODIFICATION_TYPE.get(m.getModificationType()));
      }
    }


    // If we've gotten here, then everything looks OK, re-write all the
    // modified Schema Files.
    updateSchemaFiles(newSchema, modifiedSchemaFiles);

    // Finally set DirectoryServer to use the new Schema.
    DirectoryServer.setSchema(newSchema);


    DN authzDN = modifyOperation.getAuthorizationDN();
    if (authzDN == null)
    {
      authzDN = DN.rootDN();
    }

    modifiersName = ByteString.valueOf(authzDN.toString());
    modifyTimestamp = GeneralizedTimeSyntax.createGeneralizedTimeValue(
                           System.currentTimeMillis());
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
  private void updateSchemaFiles(
               Schema newSchema, TreeSet<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // We'll re-write all
    // impacted schema files by first creating them in a temporary location
    // and then replacing the existing schema files with the new versions.
    // If all that goes successfully, then activate the new schema.
    HashMap<String,File> tempSchemaFiles = new HashMap<String,File>();
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
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
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
   * @param  schema               The schema to which the attribute type should
   *                              be added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided attribute type to the server
   *                              schema.
   */
  private void addAttributeType(AttributeType attributeType, Schema schema,
                                Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // First, see if the specified attribute type already exists.  We'll check
    // the OID and all of the names, which means that it's possible there could
    // be more than one match (although if there is, then we'll refuse the
    // operation).
    AttributeType existingType =
         schema.getAttributeType(attributeType.getOID());
    for (String name : attributeType.getNormalizedNames())
    {
      AttributeType t = schema.getAttributeType(name);
      if (t == null)
      {
        continue;
      }
      else if (existingType == null)
      {
        existingType = t;
      }
      else if (existingType != t)
      {
        // NOTE:  We really do want to use "!=" instead of "! t.equals()"
        // because we want to check whether it's the same object instance, not
        // just a logical equivalent.
        LocalizableMessage message = ERR_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_ATTRTYPE.
            get(attributeType.getNameOrOID(), existingType.getNameOrOID(),
                t.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }


    // Make sure that the new attribute type doesn't reference an undefined
    // or OBSOLETE superior attribute type.
    AttributeType superiorType = attributeType.getSuperiorType();
    if (superiorType != null)
    {
      if (! schema.hasAttributeType(superiorType.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_UNDEFINED_SUPERIOR_ATTRIBUTE_TYPE.
            get(attributeType.getNameOrOID(), superiorType.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (superiorType.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_OBSOLETE_SUPERIOR_ATTRIBUTE_TYPE.
            get(attributeType.getNameOrOID(), superiorType.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }


    // Make sure that none of the associated matching rules are marked OBSOLETE.
    MatchingRule mr = attributeType.getEqualityMatchingRule();
    if (mr != null && mr.isObsolete())
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_ATTRTYPE_OBSOLETE_MR.get(
          attributeType.getNameOrOID(), mr.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    mr = attributeType.getOrderingMatchingRule();
    if (mr != null && mr.isObsolete())
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_ATTRTYPE_OBSOLETE_MR.get(
          attributeType.getNameOrOID(), mr.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    mr = attributeType.getSubstringMatchingRule();
    if (mr != null && mr.isObsolete())
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_ATTRTYPE_OBSOLETE_MR.get(
          attributeType.getNameOrOID(), mr.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    mr = attributeType.getApproximateMatchingRule();
    if (mr != null && mr.isObsolete())
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_ATTRTYPE_OBSOLETE_MR.get(
          attributeType.getNameOrOID(), mr.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }


    // If there is no existing type, then we're adding a new attribute.
    // Otherwise, we're replacing an existing one.
    if (existingType == null)
    {
      schema.registerAttributeType(attributeType, false);
      addNewSchemaElement(modifiedSchemaFiles, attributeType);
    }
    else
    {
      schema.deregisterAttributeType(existingType);
      schema.registerAttributeType(attributeType, false);
      schema.rebuildDependentElements(existingType);
      replaceExistingSchemaElement(modifiedSchemaFiles, attributeType,
          existingType);
    }
  }



  private void addNewSchemaElement(Set<String> modifiedSchemaFiles,
      SchemaFileElement elem)
  {
    String schemaFile = getSchemaFile(elem);
    if (schemaFile == null || schemaFile.length() == 0)
    {
      schemaFile = FILE_USER_SCHEMA_ELEMENTS;
      setSchemaFile(elem, schemaFile);
    }

    modifiedSchemaFiles.add(schemaFile);
  }



  private <T extends SchemaFileElement> void replaceExistingSchemaElement(
      Set<String> modifiedSchemaFiles, T newElem, T existingElem)
  {
    String newSchemaFile = getSchemaFile(newElem);
    String oldSchemaFile = getSchemaFile(existingElem);
    if (newSchemaFile == null || newSchemaFile.length() == 0)
    {
      if (oldSchemaFile == null || oldSchemaFile.length() == 0)
      {
        oldSchemaFile = FILE_USER_SCHEMA_ELEMENTS;
      }

      setSchemaFile(newElem, oldSchemaFile);
      modifiedSchemaFiles.add(oldSchemaFile);
    }
    else if (oldSchemaFile == null || oldSchemaFile.equals(newSchemaFile))
    {
      modifiedSchemaFiles.add(newSchemaFile);
    }
    else
    {
      modifiedSchemaFiles.add(newSchemaFile);
      modifiedSchemaFiles.add(oldSchemaFile);
    }
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
   * @param  attributeType        The attribute type to remove from the server
   *                              schema.
   * @param  schema               The schema from which the attribute type
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
  private void removeAttributeType(AttributeType attributeType, Schema schema,
                                   ArrayList<Modification> modifications,
                                   int currentPosition,
                                   Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // See if the specified attribute type is actually defined in the server
    // schema.  If not, then fail.
    AttributeType removeType = schema.getAttributeType(attributeType.getOID());
    if (removeType == null || !removeType.equals(attributeType))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_ATTRIBUTE_TYPE.get(
          attributeType.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // See if there is another modification later to add the attribute type back
    // into the schema.  If so, then it's a replace and we should ignore the
    // remove because adding it back will handle the replace.
    for (int i=currentPosition+1; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute    a = m.getAttribute();

      if (m.getModificationType() != ModificationType.ADD
          || !a.getAttributeType().equals(attributeTypesType))
      {
        continue;
      }

      for (ByteString v : a)
      {
        AttributeType at;
        try
        {
          at = AttributeTypeSyntax.decodeAttributeType(v, schema, true);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_ATTRTYPE.get(
              v, de.getMessageObject());
          throw new DirectoryException(
                         ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
        }

        if (attributeType.getOID().equals(at.getOID()))
        {
          // We found a match where the attribute type is added back later, so
          // we don't need to do anything else here.
          return;
        }
      }
    }


    // Make sure that the attribute type isn't used as the superior type for
    // any other attributes.
    for (AttributeType at : schema.getAttributeTypes().values())
    {
      AttributeType superiorType = at.getSuperiorType();
      if (superiorType != null && superiorType.equals(removeType))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_AT_SUPERIOR_TYPE.get(
            removeType.getNameOrOID(), superiorType.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }


    // Make sure that the attribute type isn't used as a required or optional
    // attribute type in any objectclass.
    for (ObjectClass oc : schema.getObjectClasses().values())
    {
      if (oc.getRequiredAttributes().contains(removeType) ||
          oc.getOptionalAttributes().contains(removeType))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_AT_IN_OC.get(
            removeType.getNameOrOID(), oc.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }


    // Make sure that the attribute type isn't used as a required or optional
    // attribute type in any name form.
    for (List<NameForm> mappedForms :
                      schema.getNameFormsByObjectClass().values())
    {
      for(NameForm nf : mappedForms)
      {
        if (nf.getRequiredAttributes().contains(removeType) ||
            nf.getOptionalAttributes().contains(removeType))
        {
          LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_AT_IN_NF.get(
              removeType.getNameOrOID(), nf.getNameOrOID());
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                  message);
        }
      }
    }


    // Make sure that the attribute type isn't used as a required, optional, or
    // prohibited attribute type in any DIT content rule.
    for (DITContentRule dcr : schema.getDITContentRules().values())
    {
      if (dcr.getRequiredAttributes().contains(removeType) ||
          dcr.getOptionalAttributes().contains(removeType) ||
          dcr.getProhibitedAttributes().contains(removeType))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_AT_IN_DCR.get(
            removeType.getNameOrOID(), dcr.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }


    // Make sure that the attribute type isn't referenced by any matching rule
    // use.
    for (MatchingRuleUse mru : schema.getMatchingRuleUses().values())
    {
      if (mru.getAttributes().contains(removeType))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_AT_IN_MR_USE.get(
            removeType.getNameOrOID(), mru.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }


    // If we've gotten here, then it's OK to remove the attribute type from
    // the schema.
    schema.deregisterAttributeType(removeType);
    String schemaFile = getSchemaFile(removeType);
    if (schemaFile != null)
    {
      modifiedSchemaFiles.add(schemaFile);
    }
  }



  /**
   * Handles all processing required for adding the provided objectclass to the
   * given schema, replacing an existing class if necessary, and ensuring
   * all other metadata is properly updated.
   *
   * @param  objectClass          The objectclass to add or replace in the
   *                              server schema.
   * @param  schema               The schema to which the objectclass should be
   *                              added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided objectclass to the server schema.
   */
  private void addObjectClass(ObjectClass objectClass, Schema schema,
                              Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // First, see if the specified objectclass already exists.  We'll check the
    // OID and all of the names, which means that it's possible there could be
    // more than one match (although if there is, then we'll refuse the
    // operation).
    ObjectClass existingClass =
         schema.getObjectClass(objectClass.getOID());
    for (String name : objectClass.getNormalizedNames())
    {
      ObjectClass oc = schema.getObjectClass(name);
      if (oc == null)
      {
        continue;
      }
      else if (existingClass == null)
      {
        existingClass = oc;
      }
      else if (existingClass != oc)
      {
        // NOTE:  We really do want to use "!=" instead of "! t.equals()"
        // because we want to check whether it's the same object instance, not
        // just a logical equivalent.
        LocalizableMessage message =
                ERR_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_OBJECTCLASS
                        .get(objectClass.getNameOrOID(),
                                existingClass.getNameOrOID(),
                                oc.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }


    // Make sure that the new objectclass doesn't reference an undefined
    // superior class, or an undefined required or optional attribute type,
    // and that none of them are OBSOLETE.
    for(ObjectClass superiorClass : objectClass.getSuperiorClasses())
    {
      if (! schema.hasObjectClass(superiorClass.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_UNDEFINED_SUPERIOR_OBJECTCLASS.get(
            objectClass.getNameOrOID(), superiorClass.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (superiorClass.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_OBSOLETE_SUPERIOR_OBJECTCLASS.get(
            objectClass.getNameOrOID(), superiorClass.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }

    for (AttributeType at : objectClass.getRequiredAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_OC_UNDEFINED_REQUIRED_ATTR.get(
            objectClass.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (at.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_OC_OBSOLETE_REQUIRED_ATTR.get(
            objectClass.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }

    for (AttributeType at : objectClass.getOptionalAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_OC_UNDEFINED_OPTIONAL_ATTR.get(
            objectClass.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (at.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_OC_OBSOLETE_OPTIONAL_ATTR.get(
            objectClass.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }


    // If there is no existing class, then we're adding a new objectclass.
    // Otherwise, we're replacing an existing one.
    if (existingClass == null)
    {
      schema.registerObjectClass(objectClass, false);
      addNewSchemaElement(modifiedSchemaFiles, objectClass);
    }
    else
    {
      schema.deregisterObjectClass(existingClass);
      schema.registerObjectClass(objectClass, false);
      schema.rebuildDependentElements(existingClass);
      replaceExistingSchemaElement(modifiedSchemaFiles, objectClass,
          existingClass);
    }
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
   * @param  objectClass          The objectclass to remove from the server
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
  private void removeObjectClass(ObjectClass objectClass, Schema schema,
                                 ArrayList<Modification> modifications,
                                 int currentPosition,
                                 Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // See if the specified objectclass is actually defined in the server
    // schema.  If not, then fail.
    ObjectClass removeClass = schema.getObjectClass(objectClass.getOID());
    if (removeClass == null || !removeClass.equals(objectClass))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_OBJECTCLASS.get(
          objectClass.getNameOrOID());
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
          !a.getAttributeType().equals(objectClassesType))
      {
        continue;
      }

      for (ByteString v : a)
      {
        ObjectClass oc;
        try
        {
          oc = ObjectClassSyntax.decodeObjectClass(v, schema, true);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_OBJECTCLASS.get(
              v, de.getMessageObject());
          throw new DirectoryException(
                         ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
        }

        if (objectClass.getOID().equals(oc.getOID()))
        {
          // We found a match where the objectClass is added back later, so we
          // don't need to do anything else here.
          return;
        }
      }
    }


    // Make sure that the objectclass isn't used as the superior class for any
    // other objectclass.
    for (ObjectClass oc : schema.getObjectClasses().values())
    {
      for(ObjectClass superiorClass : oc.getSuperiorClasses())
      {
        if (superiorClass.equals(removeClass))
        {
          LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_OC_SUPERIOR_CLASS.get(
              removeClass.getNameOrOID(), superiorClass.getNameOrOID());
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                  message);
        }
      }
    }


    // Make sure that the objectclass isn't used as the structural class for
    // any name form.
    List<NameForm> mappedForms = schema.getNameForm(removeClass);
    if (mappedForms != null)
    {
      StringBuilder buffer = new StringBuilder();
      for(NameForm nf : mappedForms)
      {
        buffer.append(nf.getNameOrOID());
        buffer.append("\t");
      }
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_OC_IN_NF.get(
          removeClass.getNameOrOID(), buffer);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Make sure that the objectclass isn't used as a structural or auxiliary
    // class for any DIT content rule.
    for (DITContentRule dcr : schema.getDITContentRules().values())
    {
      if (dcr.getStructuralClass().equals(removeClass) ||
          dcr.getAuxiliaryClasses().contains(removeClass))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_OC_IN_DCR.get(
            removeClass.getNameOrOID(), dcr.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }


    // If we've gotten here, then it's OK to remove the objectclass from the
    // schema.
    schema.deregisterObjectClass(removeClass);
    String schemaFile = getSchemaFile(removeClass);
    if (schemaFile != null)
    {
      modifiedSchemaFiles.add(schemaFile);
    }
  }



  /**
   * Handles all processing required for adding the provided name form to the
   * the given schema, replacing an existing name form if necessary, and
   * ensuring all other metadata is properly updated.
   *
   * @param  nameForm             The name form to add or replace in the server
   *                              schema.
   * @param  schema               The schema to which the name form should be
   *                              added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided name form to the server schema.
   */
  private void addNameForm(NameForm nameForm, Schema schema,
                           Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // First, see if the specified name form already exists.  We'll check the
    // OID and all of the names, which means that it's possible there could be
    // more than one match (although if there is, then we'll refuse the
    // operation).
    NameForm existingNF =
         schema.getNameForm(nameForm.getOID());
    for (String name : nameForm.getNames().keySet())
    {
      NameForm nf = schema.getNameForm(name);
      if (nf == null)
      {
        continue;
      }
      else if (existingNF == null)
      {
        existingNF = nf;
      }
      else if (existingNF != nf)
      {
        // NOTE:  We really do want to use "!=" instead of "! t.equals()"
        // because we want to check whether it's the same object instance, not
        // just a logical equivalent.
        LocalizableMessage message =
                ERR_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_NAME_FORM
                        .get(nameForm.getNameOrOID(), existingNF.getNameOrOID(),
                  nf.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }


    // Make sure that the new name form doesn't reference an undefined
    // structural class, or an undefined required or optional attribute type, or
    // that any of them are marked OBSOLETE.
    ObjectClass structuralClass = nameForm.getStructuralClass();
    if (! schema.hasObjectClass(structuralClass.getOID()))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_NF_UNDEFINED_STRUCTURAL_OC.get(
          nameForm.getNameOrOID(), structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }
    if (structuralClass.getObjectClassType() != ObjectClassType.STRUCTURAL)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_NF_OC_NOT_STRUCTURAL.get(
          nameForm.getNameOrOID(), structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }
    if (structuralClass.isObsolete())
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_NF_OC_OBSOLETE.get(
          nameForm.getNameOrOID(), structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    for (AttributeType at : nameForm.getRequiredAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_NF_UNDEFINED_REQUIRED_ATTR.get(
            nameForm.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (at.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_NF_OBSOLETE_REQUIRED_ATTR.get(
            nameForm.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }

    for (AttributeType at : nameForm.getOptionalAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_NF_UNDEFINED_OPTIONAL_ATTR.get(
            nameForm.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (at.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_NF_OBSOLETE_OPTIONAL_ATTR.get(
            nameForm.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }


    // If there is no existing class, then we're adding a new name form.
    // Otherwise, we're replacing an existing one.
    if (existingNF == null)
    {
      schema.registerNameForm(nameForm, false);
      addNewSchemaElement(modifiedSchemaFiles, nameForm);
    }
    else
    {
      schema.deregisterNameForm(existingNF);
      schema.registerNameForm(nameForm, false);
      schema.rebuildDependentElements(existingNF);
      replaceExistingSchemaElement(modifiedSchemaFiles, nameForm, existingNF);
    }
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
   * @param  nameForm             The name form to remove from the server
   *                              schema.
   * @param  schema               The schema from which the name form should be
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
  private void removeNameForm(NameForm nameForm, Schema schema,
                              ArrayList<Modification> modifications,
                              int currentPosition,
                              Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // See if the specified name form is actually defined in the server schema.
    // If not, then fail.
    NameForm removeNF = schema.getNameForm(nameForm.getOID());
    if (removeNF == null || !removeNF.equals(nameForm))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_NAME_FORM.get(
          nameForm.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // See if there is another modification later to add the name form back
    // into the schema.  If so, then it's a replace and we should ignore the
    // remove because adding it back will handle the replace.
    for (int i=currentPosition+1; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute    a = m.getAttribute();

      if (m.getModificationType() != ModificationType.ADD ||
          !a.getAttributeType().equals(nameFormsType))
      {
        continue;
      }

      for (ByteString v : a)
      {
        NameForm nf;
        try
        {
          nf = NameFormSyntax.decodeNameForm(v, schema, true);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_NAME_FORM.get(
              v, de.getMessageObject());
          throw new DirectoryException(
                         ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
        }

        if (nameForm.getOID().equals(nf.getOID()))
        {
          // We found a match where the name form is added back later, so we
          // don't need to do anything else here.
          return;
        }
      }
    }


    // Make sure that the name form isn't referenced by any DIT structure
    // rule.
    DITStructureRule dsr = schema.getDITStructureRule(removeNF);
    if (dsr != null)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NF_IN_DSR.get(
          removeNF.getNameOrOID(), dsr.getNameOrRuleID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // If we've gotten here, then it's OK to remove the name form from the
    // schema.
    schema.deregisterNameForm(removeNF);
    String schemaFile = getSchemaFile(removeNF);
    if (schemaFile != null)
    {
      modifiedSchemaFiles.add(schemaFile);
    }
  }



  /**
   * Handles all processing required for adding the provided DIT content rule to
   * the given schema, replacing an existing rule if necessary, and ensuring
   * all other metadata is properly updated.
   *
   * @param  ditContentRule       The DIT content rule to add or replace in the
   *                              server schema.
   * @param  schema               The schema to which the DIT content rule
   *                              should be added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided DIT content rule to the server
   *                              schema.
   */
  private void addDITContentRule(DITContentRule ditContentRule, Schema schema,
                                 Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // First, see if the specified DIT content rule already exists.  We'll check
    // all of the names, which means that it's possible there could be more than
    // one match (although if there is, then we'll refuse the operation).
    DITContentRule existingDCR = null;
    for (DITContentRule dcr : schema.getDITContentRules().values())
    {
      for (String name : ditContentRule.getNames().keySet())
      {
        if (dcr.hasName(name))
        {
          if (existingDCR == null)
          {
            existingDCR = dcr;
            break;
          }
          else
          {
            LocalizableMessage message = ERR_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_DCR.
                get(ditContentRule.getNameOrOID(), existingDCR.getNameOrOID(),
                    dcr.getNameOrOID());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }
        }
      }
    }


    // Get the structural class for the new DIT content rule and see if there's
    // already an existing rule that is associated with that class.  If there
    // is, then it will only be acceptable if it's the DIT content rule that we
    // are replacing (in which case we really do want to use the "!=" operator).
    ObjectClass structuralClass = ditContentRule.getStructuralClass();
    DITContentRule existingRuleForClass =
         schema.getDITContentRule(structuralClass);
    if (existingRuleForClass != null && existingRuleForClass != existingDCR)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_STRUCTURAL_OC_CONFLICT_FOR_ADD_DCR.
          get(ditContentRule.getNameOrOID(), structuralClass.getNameOrOID(),
              existingRuleForClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Make sure that the new DIT content rule doesn't reference an undefined
    // structural or auxiliary class, or an undefined required, optional, or
    // prohibited attribute type.
    if (! schema.hasObjectClass(structuralClass.getOID()))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_UNDEFINED_STRUCTURAL_OC.get(
          ditContentRule.getNameOrOID(), structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (structuralClass.getObjectClassType() != ObjectClassType.STRUCTURAL)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_OC_NOT_STRUCTURAL.get(
          ditContentRule.getNameOrOID(), structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (structuralClass.isObsolete())
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_STRUCTURAL_OC_OBSOLETE.get(
          ditContentRule.getNameOrOID(), structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    for (ObjectClass oc : ditContentRule.getAuxiliaryClasses())
    {
      if (! schema.hasObjectClass(oc.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_UNDEFINED_AUXILIARY_OC.get(
            ditContentRule.getNameOrOID(), oc.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      if (oc.getObjectClassType() != ObjectClassType.AUXILIARY)
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_OC_NOT_AUXILIARY.get(
            ditContentRule.getNameOrOID(), oc.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      if (oc.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_OBSOLETE_AUXILIARY_OC.get(
            ditContentRule.getNameOrOID(), oc.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }

    for (AttributeType at : ditContentRule.getRequiredAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_UNDEFINED_REQUIRED_ATTR.get(
            ditContentRule.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (at.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_OBSOLETE_REQUIRED_ATTR.get(
            ditContentRule.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }

    for (AttributeType at : ditContentRule.getOptionalAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_UNDEFINED_OPTIONAL_ATTR.get(
            ditContentRule.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (at.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_OBSOLETE_OPTIONAL_ATTR.get(
            ditContentRule.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }

    for (AttributeType at : ditContentRule.getProhibitedAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_UNDEFINED_PROHIBITED_ATTR.get(
            ditContentRule.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (at.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_DCR_OBSOLETE_PROHIBITED_ATTR.get(
            ditContentRule.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }


    // If there is no existing rule, then we're adding a new DIT content rule.
    // Otherwise, we're replacing an existing one.
    if (existingDCR == null)
    {
      schema.registerDITContentRule(ditContentRule, false);
      addNewSchemaElement(modifiedSchemaFiles, ditContentRule);
    }
    else
    {
      schema.deregisterDITContentRule(existingDCR);
      schema.registerDITContentRule(ditContentRule, false);
      schema.rebuildDependentElements(existingDCR);
      replaceExistingSchemaElement(modifiedSchemaFiles, ditContentRule,
          existingDCR);
    }
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
   * @param  ditContentRule       The DIT content rule to remove from the server
   *                              schema.
   * @param  schema               The schema from which the DIT content rule
   *                              should be removed.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to remove
   *                              the provided DIT content rule from the server
   *                              schema.
   */
  private void removeDITContentRule(DITContentRule ditContentRule,
      Schema schema, Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    // See if the specified DIT content rule is actually defined in the server
    // schema.  If not, then fail.
    DITContentRule removeDCR =
         schema.getDITContentRule(ditContentRule.getStructuralClass());
    if (removeDCR == null || !removeDCR.equals(ditContentRule))
    {
      LocalizableMessage message =
          ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_DCR.get(ditContentRule.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Since DIT content rules don't have any dependencies, then we don't need
    // to worry about the difference between a remove or a replace.  We can
    // just remove the DIT content rule now, and if it is added back later then
    // there still won't be any conflict.
    schema.deregisterDITContentRule(removeDCR);
    String schemaFile = getSchemaFile(removeDCR);
    if (schemaFile != null)
    {
      modifiedSchemaFiles.add(schemaFile);
    }
  }



  /**
   * Handles all processing required for adding the provided DIT structure rule
   * to the given schema, replacing an existing rule if necessary, and ensuring
   * all other metadata is properly updated.
   *
   * @param  ditStructureRule     The DIT structure rule to add or replace in
   *                              the server schema.
   * @param  schema               The schema to which the DIT structure rule
   *                              should be added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided DIT structure rule to the server
   *                              schema.
   */
  private void addDITStructureRule(DITStructureRule ditStructureRule,
                                   Schema schema,
                                   Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // First, see if the specified DIT structure rule already exists.  We'll
    // check the rule ID and all of the names, which means that it's possible
    // there could be more than one match (although if there is, then we'll
    // refuse the operation).
    DITStructureRule existingDSR =
         schema.getDITStructureRule(ditStructureRule.getRuleID());
    //Boolean to check if the new rule is in use or not.
    boolean inUse = false;
    for (DITStructureRule dsr : schema.getDITStructureRulesByID().values())
    {
      for (String name : ditStructureRule.getNames().keySet())
      {
        if (dsr.hasName(name))
        {
          // We really do want to use the "!=" operator here because it's
          // acceptable if we find match for the same object instance.
          if (existingDSR != null && existingDSR != dsr)
          {
            LocalizableMessage message = ERR_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_DSR.
                get(ditStructureRule.getNameOrRuleID(),
                    existingDSR.getNameOrRuleID(), dsr.getNameOrRuleID());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }
          inUse = true;
        }
      }
    }

    if(existingDSR != null && !inUse)
    {
      //We have an existing DSR with the same rule id but we couldn't find
      //any existing rules sharing this name. It means that it is a
      //new rule with a conflicting rule id.Raise an Exception as the
      //rule id should be unique.
      LocalizableMessage message = ERR_SCHEMA_MODIFY_RULEID_CONFLICTS_FOR_ADD_DSR.
                get(ditStructureRule.getNameOrRuleID(),
                    existingDSR.getNameOrRuleID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
    }

    // Get the name form for the new DIT structure rule and see if there's
    // already an existing rule that is associated with that name form.  If
    // there is, then it will only be acceptable if it's the DIT structure rule
    // that we are replacing (in which case we really do want to use the "!="
    // operator).
    NameForm nameForm = ditStructureRule.getNameForm();
    DITStructureRule existingRuleForNameForm =
         schema.getDITStructureRule(nameForm);
    if (existingRuleForNameForm != null &&
        existingRuleForNameForm != existingDSR)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_NAME_FORM_CONFLICT_FOR_ADD_DSR.
          get(ditStructureRule.getNameOrRuleID(), nameForm.getNameOrOID(),
              existingRuleForNameForm.getNameOrRuleID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Make sure that the new DIT structure rule doesn't reference an undefined
    // name form or superior DIT structure rule.
    if (! schema.hasNameForm(nameForm.getOID()))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_DSR_UNDEFINED_NAME_FORM.get(
          ditStructureRule.getNameOrRuleID(), nameForm.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }
    if (nameForm.isObsolete())
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_DSR_OBSOLETE_NAME_FORM.get(
          ditStructureRule.getNameOrRuleID(), nameForm.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }


    // If there are any superior rules, then make sure none of them are marked
    // OBSOLETE.
    for (DITStructureRule dsr : ditStructureRule.getSuperiorRules())
    {
      if (dsr.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_DSR_OBSOLETE_SUPERIOR_RULE.get(
            ditStructureRule.getNameOrRuleID(), dsr.getNameOrRuleID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }


    // If there is no existing rule, then we're adding a new DIT structure rule.
    // Otherwise, we're replacing an existing one.
    if (existingDSR == null)
    {
      schema.registerDITStructureRule(ditStructureRule, false);
      addNewSchemaElement(modifiedSchemaFiles, ditStructureRule);
    }
    else
    {
      schema.deregisterDITStructureRule(existingDSR);
      schema.registerDITStructureRule(ditStructureRule, false);
      schema.rebuildDependentElements(existingDSR);
      replaceExistingSchemaElement(modifiedSchemaFiles, ditStructureRule,
          existingDSR);
    }
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
   * @param  ditStructureRule     The DIT structure rule to remove from the
   *                              server schema.
   * @param  schema               The schema from which the DIT structure rule
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
  private void removeDITStructureRule(DITStructureRule ditStructureRule,
                                      Schema schema,
                                      ArrayList<Modification> modifications,
                                      int currentPosition,
                                      Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // See if the specified DIT structure rule is actually defined in the server
    // schema.  If not, then fail.
    DITStructureRule removeDSR =
         schema.getDITStructureRule(ditStructureRule.getRuleID());
    if (removeDSR == null || !removeDSR.equals(ditStructureRule))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_DSR.get(
          ditStructureRule.getNameOrRuleID());
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
          !a.getAttributeType().equals(ditStructureRulesType))
      {
        continue;
      }

      for (ByteString v : a)
      {
        DITStructureRule dsr;
        try
        {
          dsr = DITStructureRuleSyntax.decodeDITStructureRule(v, schema, true);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          LocalizableMessage message = ERR_SCHEMA_MODIFY_CANNOT_DECODE_DSR.get(
              v, de.getMessageObject());
          throw new DirectoryException(
                         ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, de);
        }

        if (ditStructureRule.getRuleID() == dsr.getRuleID())
        {
          // We found a match where the DIT structure rule is added back later,
          // so we don't need to do anything else here.
          return;
        }
      }
    }


    // Make sure that the DIT structure rule isn't the superior for any other
    // DIT structure rule.
    for (DITStructureRule dsr : schema.getDITStructureRulesByID().values())
    {
      if (dsr.getSuperiorRules().contains(removeDSR))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_DSR_SUPERIOR_RULE.get(
            removeDSR.getNameOrRuleID(), dsr.getNameOrRuleID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }


    // If we've gotten here, then it's OK to remove the DIT structure rule from
    // the schema.
    schema.deregisterDITStructureRule(removeDSR);
    String schemaFile = getSchemaFile(removeDSR);
    if (schemaFile != null)
    {
      modifiedSchemaFiles.add(schemaFile);
    }
  }



  /**
   * Handles all processing required for adding the provided matching rule use
   * to the given schema, replacing an existing use if necessary, and ensuring
   * all other metadata is properly updated.
   *
   * @param  matchingRuleUse      The matching rule use to add or replace in the
   *                              server schema.
   * @param  schema               The schema to which the matching rule use
   *                              should be added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided matching rule use to the server
   *                              schema.
   */
  private void addMatchingRuleUse(MatchingRuleUse matchingRuleUse,
                                  Schema schema,
                                  Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // First, see if the specified matching rule use already exists.  We'll
    // check all of the names, which means that it's possible that there could
    // be more than one match (although if there is, then we'll refuse the
    // operation).
    MatchingRuleUse existingMRU = null;
    for (MatchingRuleUse mru : schema.getMatchingRuleUses().values())
    {
      for (String name : matchingRuleUse.getNames().keySet())
      {
        if (mru.hasName(name))
        {
          if (existingMRU == null)
          {
            existingMRU = mru;
            break;
          }
          else
          {
            LocalizableMessage message =
                    ERR_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_MR_USE.get(
                            matchingRuleUse.getNameOrOID(),
                            existingMRU.getNameOrOID(),
                            mru.getNameOrOID());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }
        }
      }
    }


    // Get the matching rule for the new matching rule use and see if there's
    // already an existing matching rule use that is associated with that
    // matching rule.  If there is, then it will only be acceptable if it's the
    // matching rule use that we are replacing (in which case we really do want
    // to use the "!=" operator).
    MatchingRule matchingRule = matchingRuleUse.getMatchingRule();
    MatchingRuleUse existingMRUForRule =
         schema.getMatchingRuleUse(matchingRule);
    if (existingMRUForRule != null && existingMRUForRule != existingMRU)
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_MR_CONFLICT_FOR_ADD_MR_USE.
          get(matchingRuleUse.getNameOrOID(), matchingRule.getNameOrOID(),
              existingMRUForRule.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (matchingRule.isObsolete())
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_MRU_OBSOLETE_MR.get(
          matchingRuleUse.getNameOrOID(), matchingRule.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }


    // Make sure that the new matching rule use doesn't reference an undefined
    // attribute type.
    for (AttributeType at : matchingRuleUse.getAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_MRU_UNDEFINED_ATTR.get(
            matchingRuleUse.getNameOrOID(), at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (at.isObsolete())
      {
        LocalizableMessage message = ERR_SCHEMA_MODIFY_MRU_OBSOLETE_ATTR.get(
            matchingRuleUse.getNameOrOID(), matchingRule.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
    }


    // If there is no existing matching rule use, then we're adding a new one.
    // Otherwise, we're replacing an existing matching rule use.
    if (existingMRU == null)
    {
      schema.registerMatchingRuleUse(matchingRuleUse, false);
      addNewSchemaElement(modifiedSchemaFiles, matchingRuleUse);
    }
    else
    {
      schema.deregisterMatchingRuleUse(existingMRU);
      schema.registerMatchingRuleUse(matchingRuleUse, false);
      schema.rebuildDependentElements(existingMRU);
      replaceExistingSchemaElement(modifiedSchemaFiles, matchingRuleUse,
          existingMRU);
    }
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
   * @param  matchingRuleUse      The matching rule use to remove from the
   *                              server schema.
   * @param  schema               The schema from which the matching rule use
   *                              should be removed.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   * @throws  DirectoryException  If a problem occurs while attempting to remove
   *                              the provided matching rule use from the server
   *                              schema.
   */
  private void removeMatchingRuleUse(MatchingRuleUse matchingRuleUse,
                                     Schema schema,
                                     Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // See if the specified DIT content rule is actually defined in the server
    // schema.  If not, then fail.
    MatchingRuleUse removeMRU =
         schema.getMatchingRuleUse(matchingRuleUse.getMatchingRule());
    if (removeMRU == null || !removeMRU.equals(matchingRuleUse))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_MR_USE.get(
          matchingRuleUse.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Since matching rule uses don't have any dependencies, then we don't need
    // to worry about the difference between a remove or a replace.  We can
    // just remove the DIT content rule now, and if it is added back later then
    // there still won't be any conflict.
    schema.deregisterMatchingRuleUse(removeMRU);
    String schemaFile = getSchemaFile(removeMRU);
    if (schemaFile != null)
    {
      modifiedSchemaFiles.add(schemaFile);
    }
  }



  /**
   * Handles all processing required for adding the provided ldap syntax
   * description to the given schema, replacing an existing ldap syntax
   * description if necessary, and ensuring all other metadata is properly
   * updated.
   *
   * @param  ldapSyntaxDesc   The ldap syntax description to add or replace in
   *                               the server schema.
   * @param  schema               The schema to which the name form should be
   *                              added.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to add
   *                              the provided ldap syntax description to the
   *                              server schema.
   */
  private void addLdapSyntaxDescription(LDAPSyntaxDescription ldapSyntaxDesc,
                           Schema schema,
                           Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
       //Check if there is an existing syntax with this oid.
    String oid = ldapSyntaxDesc.getLdapSyntaxDescriptionSyntax().getOID();

    // We allow only unimplemented syntaxes to be substituted.
    if(schema.getSyntax(oid) !=null)
    {
      LocalizableMessage message =
          ERR_ATTR_SYNTAX_INVALID_LDAP_SYNTAX.get(ldapSyntaxDesc, oid);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                     message);
    }

    LDAPSyntaxDescription existingLSD =
         schema.getLdapSyntaxDescription(oid);

    // If there is no existing lsd, then we're adding a new ldapsyntax.
    // Otherwise, we're replacing an existing one.
    if (existingLSD == null)
    {
      schema.registerLdapSyntaxDescription(ldapSyntaxDesc, false);
      addNewSchemaElement(modifiedSchemaFiles, ldapSyntaxDesc);
    }
    else
    {
      schema.deregisterLdapSyntaxDescription(existingLSD);
      schema.registerLdapSyntaxDescription(ldapSyntaxDesc, false);
      schema.rebuildDependentElements(existingLSD);
      replaceExistingSchemaElement(modifiedSchemaFiles, ldapSyntaxDesc,
          existingLSD);
    }
  }



  /** Gets rid of the ldap syntax description. */
  private void removeLdapSyntaxDescription(LDAPSyntaxDescription ldapSyntaxDesc,
                                    Schema schema,
                                    Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    //See if the specified ldap syntax description is actually defined in the
    //server schema.  If not, then fail. Note that we are checking only the
     //real part of the ldapsyntaxes attribute. A virtual value is not searched
      // and hence never deleted.
    String oid = ldapSyntaxDesc.getLdapSyntaxDescriptionSyntax().getOID();
    LDAPSyntaxDescription removeLSD = schema.getLdapSyntaxDescription(oid);

    if (removeLSD == null || !removeLSD.equals(ldapSyntaxDesc))
    {
      LocalizableMessage message =
          ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_LSD.get(oid);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    schema.deregisterLdapSyntaxDescription(removeLSD);
    String schemaFile = getSchemaFile(removeLSD);
    if (schemaFile != null)
    {
      modifiedSchemaFiles.add(schemaFile);
    }
  }



  /**
   * Creates an empty entry that may be used as the basis for a new schema file.
   *
   * @return  An empty entry that may be used as the basis for a new schema
   *          file.
   */
  private Entry createEmptySchemaEntry()
  {
    Map<ObjectClass,String> objectClasses =
         new LinkedHashMap<ObjectClass,String>();
    objectClasses.put(DirectoryServer.getTopObjectClass(), OC_TOP);
    objectClasses.put(DirectoryServer.getObjectClass(OC_LDAP_SUBENTRY_LC, true),
                      OC_LDAP_SUBENTRY);
    objectClasses.put(DirectoryServer.getObjectClass(OC_SUBSCHEMA, true),
                      OC_SUBSCHEMA);

    Map<AttributeType,List<Attribute>> userAttributes =
         new LinkedHashMap<AttributeType,List<Attribute>>();

    Map<AttributeType,List<Attribute>> operationalAttributes =
         new LinkedHashMap<AttributeType,List<Attribute>>();

    DN  dn  = DirectoryServer.getSchemaDN();
    RDN rdn = dn.rdn();
    for (int i=0; i < rdn.getNumValues(); i++)
    {
      AttributeType type = rdn.getAttributeType(i);
      List<Attribute> attrList = new LinkedList<Attribute>();
      attrList.add(Attributes.create(type, rdn.getAttributeValue(i)));
      if (type.isOperational())
      {
        operationalAttributes.put(type, attrList);
      }
      else
      {
        userAttributes.put(type, attrList);
      }
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
  private File writeTempSchemaFile(Schema schema, String schemaFile)
          throws DirectoryException, IOException, LDIFException
  {
    // Start with an empty schema entry.
    Entry schemaEntry = createEmptySchemaEntry();

     /*
     * Add all of the ldap syntax descriptions to the schema entry. We do
     * this only for the real part of the ldapsyntaxes attribute. The real part
     * is read and write to/from the schema files.
     */
    Set<ByteString> values = new LinkedHashSet<ByteString>();
    for (LDAPSyntaxDescription ldapSyntax :
                                   schema.getLdapSyntaxDescriptions().values())
    {
      if (schemaFile.equals(getSchemaFile(ldapSyntax)))
      {
        values.add(ByteString.valueOf(ldapSyntax.toString()));
      }
    }

   if (! values.isEmpty())
   {
     AttributeBuilder builder = new AttributeBuilder(ldapSyntaxesType);
     builder.addAll(values);
     schemaEntry.putAttribute(ldapSyntaxesType, newArrayList(builder.toAttribute()));
   }

    // Add all of the appropriate attribute types to the schema entry.  We need
    // to be careful of the ordering to ensure that any superior types in the
    // same file are written before the subordinate types.
    Set<AttributeType> addedTypes = new HashSet<AttributeType>();
    values = new LinkedHashSet<ByteString>();
    for (AttributeType at : schema.getAttributeTypes().values())
    {
      if (schemaFile.equals(getSchemaFile(at)))
      {
        addAttrTypeToSchemaFile(schema, schemaFile, at, values, addedTypes, 0);
      }
    }

    if (! values.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(attributeTypesType);
      builder.addAll(values);
      schemaEntry.putAttribute(attributeTypesType, newArrayList(builder.toAttribute()));
    }


    // Add all of the appropriate objectclasses to the schema entry.  We need
    // to be careful of the ordering to ensure that any superior classes in the
    // same file are written before the subordinate classes.
    Set<ObjectClass> addedClasses = new HashSet<ObjectClass>();
    values = new LinkedHashSet<ByteString>();
    for (ObjectClass oc : schema.getObjectClasses().values())
    {
      if (schemaFile.equals(getSchemaFile(oc)))
      {
        addObjectClassToSchemaFile(schema, schemaFile, oc, values, addedClasses,
                                   0);
      }
    }

    if (! values.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(objectClassesType);
      builder.addAll(values);
      schemaEntry.putAttribute(objectClassesType, newArrayList(builder.toAttribute()));
    }


    // Add all of the appropriate name forms to the schema entry.  Since there
    // is no hierarchical relationship between name forms, we don't need to
    // worry about ordering.
    values = new LinkedHashSet<ByteString>();
    for (List<NameForm> forms : schema.getNameFormsByObjectClass().values())
    {
      for(NameForm nf : forms)
      {
        if (schemaFile.equals(getSchemaFile(nf)))
        {
          values.add(ByteString.valueOf(nf.toString()));
        }
      }
    }

    if (! values.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(nameFormsType);
      builder.addAll(values);
      schemaEntry.putAttribute(nameFormsType, newArrayList(builder.toAttribute()));
    }


    // Add all of the appropriate DIT content rules to the schema entry.  Since
    // there is no hierarchical relationship between DIT content rules, we don't
    // need to worry about ordering.
    values = new LinkedHashSet<ByteString>();
    for (DITContentRule dcr : schema.getDITContentRules().values())
    {
      if (schemaFile.equals(getSchemaFile(dcr)))
      {
        values.add(ByteString.valueOf(dcr.toString()));
      }
    }

    if (! values.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(ditContentRulesType);
      builder.addAll(values);
      schemaEntry.putAttribute(ditContentRulesType, newArrayList(builder.toAttribute()));
    }


    // Add all of the appropriate DIT structure rules to the schema entry.  We
    // need to be careful of the ordering to ensure that any superior rules in
    // the same file are written before the subordinate rules.
    Set<DITStructureRule> addedDSRs = new HashSet<DITStructureRule>();
    values = new LinkedHashSet<ByteString>();
    for (DITStructureRule dsr : schema.getDITStructureRulesByID().values())
    {
      if (schemaFile.equals(getSchemaFile(dsr)))
      {
        addDITStructureRuleToSchemaFile(schema, schemaFile, dsr, values,
                                        addedDSRs, 0);
      }
    }

    if (! values.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(ditStructureRulesType);
      builder.addAll(values);
      schemaEntry.putAttribute(ditStructureRulesType, newArrayList(builder.toAttribute()));
    }


    // Add all of the appropriate matching rule uses to the schema entry.  Since
    // there is no hierarchical relationship between matching rule uses, we
    // don't need to worry about ordering.
    values = new LinkedHashSet<ByteString>();
    for (MatchingRuleUse mru : schema.getMatchingRuleUses().values())
    {
      if (schemaFile.equals(getSchemaFile(mru)))
      {
        values.add(ByteString.valueOf(mru.toString()));
      }
    }

    if (! values.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(matchingRuleUsesType);
      builder.addAll(values);
      schemaEntry.putAttribute(matchingRuleUsesType, newArrayList(builder.toAttribute()));
    }


    if (FILE_USER_SCHEMA_ELEMENTS.equals(schemaFile))
    {
      Map<String, Attribute> attributes = schema.getExtraAttributes();
      for (Attribute attribute : attributes.values())
      {
        ArrayList<Attribute> attrList = newArrayList(attribute);
        schemaEntry.putAttribute(attribute.getAttributeType(), attrList);
      }
    }

    // Create a temporary file to which we can write the schema entry.
    File tempFile = File.createTempFile(schemaFile, "temp");
    LDIFExportConfig exportConfig =
         new LDIFExportConfig(tempFile.getAbsolutePath(),
                              ExistingFileBehavior.OVERWRITE);
    LDIFWriter ldifWriter = new LDIFWriter(exportConfig);
    ldifWriter.writeEntry(schemaEntry);
    ldifWriter.close();

    return tempFile;
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
  private void addAttrTypeToSchemaFile(Schema schema, String schemaFile,
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
        schemaFile.equals(getSchemaFile(superiorType)) &&
        !addedTypes.contains(superiorType))
    {
      addAttrTypeToSchemaFile(schema, schemaFile, superiorType, values,
                              addedTypes, depth+1);
    }

    values.add(ByteString.valueOf(attributeType.toString()));
    addedTypes.add(attributeType);
  }



  /**
   * Adds the definition for the specified objectclass to the provided set of
   * attribute values, recursively adding superior classes as appropriate.
   *
   * @param  schema        The schema containing the objectclass.
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
  private void addObjectClassToSchemaFile(Schema schema, String schemaFile,
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
      if (schemaFile.equals(getSchemaFile(superiorClass)) &&
          !addedClasses.contains(superiorClass))
      {
        addObjectClassToSchemaFile(schema, schemaFile, superiorClass, values,
                                   addedClasses, depth+1);
      }
    }
    values.add(ByteString.valueOf(objectClass.toString()));
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
  private void addDITStructureRuleToSchemaFile(Schema schema, String schemaFile,
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
      if (schemaFile.equals(getSchemaFile(dsr)) && !addedDSRs.contains(dsr))
      {
        addDITStructureRuleToSchemaFile(schema, schemaFile, dsr, values,
                                        addedDSRs, depth+1);
      }
    }

    values.add(ByteString.valueOf(ditStructureRule.toString()));
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
    ArrayList<File> installedFileList = new ArrayList<File>();
    ArrayList<File> tempFileList      = new ArrayList<File>();
    ArrayList<File> origFileList      = new ArrayList<File>();

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
  private void copyFile(File from, File to)
          throws IOException
  {
    byte[]           buffer        = new byte[4096];
    FileInputStream  inputStream   = null;
    FileOutputStream outputStream  = null;
    try
    {
      inputStream  = new FileInputStream(from);
      outputStream = new FileOutputStream(to, false);

      int bytesRead = inputStream.read(buffer);
      while (bytesRead > 0)
      {
        outputStream.write(buffer, 0, bytesRead);
        bytesRead = inputStream.read(buffer);
      }
    }
    finally
    {
      close(inputStream, outputStream);
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
    DN baseDN = searchOperation.getBaseDN();

    boolean found = false;
    DN[] dnArray = baseDNs;
    DN matchedDN = null;
    for (DN dn : dnArray)
    {
      if (dn.equals(baseDN))
      {
        found = true;
        break;
      }
      else if (dn.isAncestorOf(baseDN))
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
    Entry schemaEntry = getSchemaEntry(baseDN, false);
    SearchFilter filter = searchOperation.getFilter();
    if (filter.matchesEntry(schemaEntry))
    {
      searchOperation.returnEntry(schemaEntry, null);
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
      ldifWriter.writeEntry(getSchemaEntry(baseDNs[0], true, true));
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

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    LDIFReader reader;
    try
    {
      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_MEMORYBACKEND_CANNOT_CREATE_LDIF_READER.get(e), e);
    }


    try
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
          else
          {
            continue;
          }
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
    finally
    {
      close(reader);
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
    Schema schema = DirectoryServer.getSchema();
    Schema newSchema = DirectoryServer.getSchema().duplicate();
    TreeSet<String> modifiedSchemaFiles = new TreeSet<String>();

    // Get the attributeTypes attribute from the entry.
    AttributeTypeSyntax attrTypeSyntax;
    try
    {
      attrTypeSyntax = (AttributeTypeSyntax)
                       schema.getSyntax(SYNTAX_ATTRIBUTE_TYPE_OID);
      if (attrTypeSyntax == null)
      {
        attrTypeSyntax = new AttributeTypeSyntax();
        attrTypeSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      attrTypeSyntax = new AttributeTypeSyntax();
    }

    AttributeType attributeAttrType =
         schema.getAttributeType(ATTR_ATTRIBUTE_TYPES_LC);
    if (attributeAttrType == null)
    {
      attributeAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_ATTRIBUTE_TYPES,
                                                   attrTypeSyntax);
    }

    // loop on the attribute types in the entry just received
    // and add them in the existing schema.
    List<Attribute> attrList = newSchemaEntry.getAttribute(attributeAttrType);
    Set<String> oidList = new HashSet<String>(1000);
    if (attrList != null && !attrList.isEmpty())
    {
      for (Attribute a : attrList)
      {
        // Look for attributetypes that could have been added to the schema
        // or modified in the schema
        for (ByteString v : a)
        {
          // Parse the attribute type.
          AttributeType attrType = AttributeTypeSyntax.decodeAttributeType(v, schema, false);
          String schemaFile = getSchemaFile(attrType);
          if (CONFIG_SCHEMA_ELEMENTS_FILE.equals(schemaFile))
          {
            // Don't import the file containing the definitions of the
            // Schema elements used for configuration because these
            // definitions may vary between versions of OpenDJ.
            continue;
          }

          oidList.add(attrType.getOID());
          try
          {
            // Register this attribute type in the new schema
            // unless it is already defined with the same syntax.
            AttributeType oldAttrType =
              schema.getAttributeType(attrType.getOID());
            if (oldAttrType == null ||
                !oldAttrType.toString().equals(attrType.toString()))
            {
              newSchema.registerAttributeType(attrType, true);

              if (schemaFile != null)
              {
                modifiedSchemaFiles.add(schemaFile);
              }
            }
          }
          catch (Exception e)
          {
            logger.info(NOTE_SCHEMA_IMPORT_FAILED, attrType, e.getMessage());
          }
        }
      }
    }

    // loop on all the attribute types in the current schema and delete
    // them from the new schema if they are not in the imported schema entry.
    ConcurrentHashMap<String, AttributeType> currentAttrTypes =
      newSchema.getAttributeTypes();

    for (AttributeType removeType : currentAttrTypes.values())
    {
      String schemaFile = getSchemaFile(removeType);
      if (CONFIG_SCHEMA_ELEMENTS_FILE.equals(schemaFile)
          || CORE_SCHEMA_ELEMENTS_FILE.equals(schemaFile))
      {
        // Don't import the file containing the definitions of the
        // Schema elements used for configuration because these
        // definitions may vary between versions of OpenDJ.
        // Also never delete anything from the core schema file.
        continue;
      }
      if (!oidList.contains(removeType.getOID()))
      {
        newSchema.deregisterAttributeType(removeType);
        if (schemaFile != null)
        {
              modifiedSchemaFiles.add(schemaFile);
        }
      }
    }

    // loop on the objectClasses from the entry, search if they are
    // already in the current schema, add them if not.
    ObjectClassSyntax ocSyntax;
    try
    {
      ocSyntax = (ObjectClassSyntax) schema.getSyntax(SYNTAX_OBJECTCLASS_OID);
      if (ocSyntax == null)
      {
        ocSyntax = new ObjectClassSyntax();
        ocSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ocSyntax = new ObjectClassSyntax();
    }

    AttributeType objectclassAttrType =
      schema.getAttributeType(ATTR_OBJECTCLASSES_LC);
    if (objectclassAttrType == null)
    {
      objectclassAttrType =
        DirectoryServer.getDefaultAttributeType(ATTR_OBJECTCLASSES,
                                                ocSyntax);
    }

    oidList.clear();
    List<Attribute> ocList = newSchemaEntry.getAttribute(objectclassAttrType);
    if (ocList != null && !ocList.isEmpty())
    {
      for (Attribute a : ocList)
      {
        for (ByteString v : a)
        {
          // It IS important here to allow the unknown elements that could
          // appear in the new config schema.
          ObjectClass newObjectClass = ObjectClassSyntax.decodeObjectClass(v, newSchema, true);
          String schemaFile = getSchemaFile(newObjectClass);
          if (CONFIG_SCHEMA_ELEMENTS_FILE.equals(schemaFile))
          {
            // Don't import the file containing the definitions of the
            // Schema elements used for configuration because these
            // definitions may vary between versions of OpenDJ.
            continue;
          }

          // Now we know we are not in the config schema, let's check
          // the unknown elements ... sadly but simply by redoing the
          // whole decoding.
          newObjectClass = ObjectClassSyntax.decodeObjectClass(v, newSchema, false);
          oidList.add(newObjectClass.getOID());
          try
          {
            // Register this ObjectClass in the new schema
            // unless it is already defined with the same syntax.
            ObjectClass oldObjectClass =
              schema.getObjectClass(newObjectClass.getOID());
            if (oldObjectClass == null ||
                !oldObjectClass.toString().equals(newObjectClass.toString()))
            {
              newSchema.registerObjectClass(newObjectClass, true);

              if (schemaFile != null)
              {
                modifiedSchemaFiles.add(schemaFile);
              }
            }
          }
          catch (Exception e)
          {
            logger.info(NOTE_SCHEMA_IMPORT_FAILED, newObjectClass, e.getMessage());
          }
        }
      }
    }

    // loop on all the attribute types in the current schema and delete
    // them from the new schema if they are not in the imported schema entry.
    ConcurrentHashMap<String, ObjectClass> currentObjectClasses =
      newSchema.getObjectClasses();

    for (ObjectClass removeClass : currentObjectClasses.values())
    {
      String schemaFile = getSchemaFile(removeClass);
      if (CONFIG_SCHEMA_ELEMENTS_FILE.equals(schemaFile))
      {
        // Don't import the file containing the definition of the
        // Schema elements used for configuration because these
        // definitions may vary between versions of OpenDJ.
        continue;
      }
      if (!oidList.contains(removeClass.getOID()))
      {
        newSchema.deregisterObjectClass(removeClass);

        if (schemaFile != null)
        {
          modifiedSchemaFiles.add(schemaFile);
        }
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

  /** {@inheritDoc} */
  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    new BackupManager(getBackendID()).createBackup(this, backupConfig);
  }

  /** {@inheritDoc} */
  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
    new BackupManager(getBackendID()).removeBackup(backupDirectory, backupID);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    new BackupManager(getBackendID()).restoreBackup(this, restoreConfig);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
       SchemaBackendCfg configEntry,
       List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
       SchemaBackendCfg backendCfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();


    // Check to see if we should apply a new set of base DNs.
    Set<DN> newBaseDNs;
    try
    {
      newBaseDNs = new HashSet<DN>(backendCfg.getSchemaEntryDN());
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


    // Check to see if we should change the behavior regarding whether to show
    // all schema attributes.
    boolean newShowAllAttributes = backendCfg.isShowAllAttributes();


    // Check to see if there is a new set of user-defined attributes.
    ArrayList<Attribute> newUserAttrs = new ArrayList<Attribute>();
    try
    {
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(configEntryDN);
      for (List<Attribute> attrs :
           configEntry.getEntry().getUserAttributes().values())
      {
        for (Attribute a : attrs)
        {
          if (! isSchemaConfigAttribute(a))
          {
            newUserAttrs.add(a);
          }
        }
      }
      for (List<Attribute> attrs :
           configEntry.getEntry().getOperationalAttributes().values())
      {
        for (Attribute a : attrs)
        {
          if (! isSchemaConfigAttribute(a))
          {
            newUserAttrs.add(a);
          }
        }
      }
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
      // Get an array containing the new base DNs to use.
      DN[] dnArray = new DN[newBaseDNs.size()];
      newBaseDNs.toArray(dnArray);


      // Determine the set of DNs to add and delete.  When this is done, the
      // deleteBaseDNs will contain the set of DNs that should no longer be used
      // and should be deregistered from the server, and the newBaseDNs set will
      // just contain the set of DNs to add.
      Set<DN> deleteBaseDNs = new HashSet<DN>(baseDNs.length);
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

      baseDNs = dnArray;
      for (DN dn : newBaseDNs)
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


      showAllAttributes = newShowAllAttributes;


      userDefinedAttributes = newUserAttrs;
      LocalizableMessage message = INFO_SCHEMA_USING_NEW_USER_ATTRS.get();
      ccr.addMessage(message);
    }


    currentConfig = backendCfg;
    return ccr;
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
    return showAllAttributes;
  }



  /**
   * Specifies whether to treat common schema attributes like user attributes
   * rather than operational attributes.
   *
   * @param  showAllAttributes  Specifies whether to treat common schema
   *                            attributes like user attributes rather than
   *                            operational attributes.
   */
  void setShowAllAttributes(boolean showAllAttributes)
  {
    this.showAllAttributes = showAllAttributes;
  }

  /** {@inheritDoc} */
  @Override
  public DN getComponentEntryDN()
  {
    return configEntryDN;
  }

  /** {@inheritDoc} */
  @Override
  public String getClassName()
  {
    return CLASS_NAME;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, String> getAlerts()
  {
    Map<String, String> alerts = new LinkedHashMap<String, String>();

    alerts.put(ALERT_TYPE_CANNOT_COPY_SCHEMA_FILES,
               ALERT_DESCRIPTION_CANNOT_COPY_SCHEMA_FILES);
    alerts.put(ALERT_TYPE_CANNOT_WRITE_NEW_SCHEMA_FILES,
               ALERT_DESCRIPTION_CANNOT_WRITE_NEW_SCHEMA_FILES);

    return alerts;
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public ListIterator<Path> getFilesToBackup() throws DirectoryException
  {
    return BackupManager.getFiles(getDirectory(), BACKUP_FILES_FILTER, getBackendID()).listIterator();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isDirectRestore()
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public Path beforeRestore() throws DirectoryException
  {
    // save current schema files in save directory
    return BackupManager.saveCurrentFilesToDirectory(this, getBackendID());
  }

  /** {@inheritDoc} */
  @Override
  public void afterRestore(Path restoreDirectory, Path saveDirectory) throws DirectoryException
  {
    // restore was successful, delete save directory
    StaticUtils.recursiveDelete(saveDirectory.toFile());
  }

}

