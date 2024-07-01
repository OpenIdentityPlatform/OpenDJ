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

import static org.forgerock.opendj.ldap.schema.SchemaOptions.STRIP_UPPER_BOUND_FOR_ATTRIBUTE_TYPE;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.schema.GeneralizedTimeSyntax.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.SchemaUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.server.config.server.SchemaBackendCfg;
import org.forgerock.util.Reject;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.Backupable;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.SchemaHandler;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.Modification;
import org.opends.server.types.Privilege;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.BackupManager;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.SchemaUtils;
import org.opends.server.util.StaticUtils;

/**
 * This class defines a backend to hold the Directory Server schema information.
 * It is a kind of meta-backend in that it doesn't actually hold any data but
 * rather dynamically generates the schema entry whenever it is requested.
 */
public class SchemaBackend extends LocalBackend<SchemaBackendCfg>
     implements ConfigurationChangeListener<SchemaBackendCfg>, AlertGenerator, Backupable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME = "org.opends.server.backends.SchemaBackend";

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

  /** Will be used in the future to remove static calls to DirectoryServer. */
  private ServerContext serverContext;

  /** Manages access to the schema. */
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
    Reject.ifNull(serverContext);
    this.serverContext = serverContext;
    this.schemaHandler = serverContext.getSchemaHandler();

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
    Schema schema = schemaHandler.getSchema();
    schemaObjectClasses.put(schema.getObjectClass(OC_LDAP_SUBENTRY_LC), OC_LDAP_SUBENTRY);
    schemaObjectClasses.put(schema.getObjectClass(OC_SUBSCHEMA), OC_SUBSCHEMA);

    configEntryDN = configEntry.getName();
    baseDNs = cfg.getBaseDN();

    ByteString newBaseDN = ByteString.valueOfUtf8(baseDNs.iterator().next().toString());
    creatorsName = newBaseDN;
    modifiersName = newBaseDN;

    // Get the set of user-defined attributes for the configuration entry.  Any
    // attributes that we don't recognize will be included directly in the
    // schema entry.
    userDefinedAttributes = new ArrayList<>();
    addAllNonSchemaConfigAttributes(userDefinedAttributes, configEntry.getAllAttributes());


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
        serverContext.getBackendConfigManager().registerBaseDN(baseDN, this, true);
      } catch (Exception e) {
        logger.traceException(e);

        LocalizableMessage message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
            baseDN, getExceptionMessage(e));
        throw new InitializationException(message, e);
      }
    }

    // Register with the Directory Server as a configurable component.
    currentConfig.addSchemaChangeListener(this);
  }

  @Override
  public void closeBackend()
  {
    currentConfig.removeSchemaChangeListener(this);

    for (DN baseDN : baseDNs)
    {
      try
      {
        serverContext.getBackendConfigManager().deregisterBaseDN(baseDN);
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
    Schema schema = schemaHandler.getSchema();
    buildSchemaAttribute(schema.getAttributeTypes(), userAttrs,
        operationalAttrs, attributeTypesType, includeSchemaFile,
        schema.getOption(STRIP_UPPER_BOUND_FOR_ATTRIBUTE_TYPE),
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
    addAttributeToSchemaEntry(
        Attributes.create(getCreatorsNameAttributeType(), creatorsName),
        userAttrs, operationalAttrs);
    addAttributeToSchemaEntry(
        Attributes.create(getCreateTimestampAttributeType(),
            createGeneralizedTimeValue(schemaHandler.getOldestModificationTime())),
        userAttrs, operationalAttrs);
    addAttributeToSchemaEntry(
        Attributes.create(getModifiersNameAttributeType(), modifiersName),
        userAttrs, operationalAttrs);
    addAttributeToSchemaEntry(
        Attributes.create(getModifyTimestampAttributeType(),
            createGeneralizedTimeValue(schemaHandler.getYoungestModificationTime())),
        userAttrs, operationalAttrs);

    // Add the extra attributes.
    for (Attribute attribute : schemaHandler.getExtraAttributes().values())
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

    final List<Modification> modifications = new ArrayList<>(modifyOperation.getModifications());
    if (modifications.isEmpty())
    {
      // There aren't any modifications, so we don't need to do anything.
      return;
    }
    final TreeSet<String> modifiedSchemaFiles = new TreeSet<>();

    Schema currentSchema = schemaHandler.getSchema();
    Map<String, Attribute> extraAttributes = schemaHandler.getExtraAttributes();
    SchemaBuilder newSchemaBuilder = new SchemaBuilder(currentSchema);
    applyModificationsToNewSchemaBuilder(currentSchema, newSchemaBuilder, extraAttributes, modifications,
        modifiedSchemaFiles, modifyOperation.isSynchronizationOperation());
    Schema newSchema = newSchemaBuilder.toSchema();
    schemaHandler.updateSchemaAndSchemaFiles(newSchema, extraAttributes, modifiedSchemaFiles, this);

    DN authzDN = modifyOperation.getAuthorizationDN();
    if (authzDN == null)
    {
      authzDN = DN.rootDN();
    }

    modifiersName = ByteString.valueOfUtf8(authzDN.toString());
  }

  private void applyModificationsToNewSchemaBuilder(Schema currentSchema, SchemaBuilder newSchemaBuilder,
      Map<String, Attribute> extraAttributes, List<Modification> mods, Set<String> modifiedSchemaFiles,
      boolean isSynchronizationOperation) throws DirectoryException
  {
    int pos = -1;
    for (Modification m : mods)
    {
      pos++;

      // Determine the type of modification to perform.  We will support add and
      // delete operations in the schema, and we will also support the ability
      // to add a schema element that already exists and treat it as a
      // replacement of that existing element.
      Attribute attribute = m.getAttribute();
      AttributeType attributeType = attribute.getAttributeDescription().getAttributeType();
      switch (m.getModificationType().asEnum())
      {
        case ADD:
          addAttribute(currentSchema, newSchemaBuilder, attribute, modifiedSchemaFiles);
          break;

        case DELETE:
          deleteAttribute(newSchemaBuilder, attribute, mods, pos, modifiedSchemaFiles);
          break;

        case REPLACE:
          if (!m.isInternal() && !isSynchronizationOperation)
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                ERR_SCHEMA_INVALID_MODIFICATION_TYPE.get(m.getModificationType()));
          }
          else  if (isSchemaAttribute(attribute))
          {
            logger.error(ERR_SCHEMA_INVALID_REPLACE_MODIFICATION, attribute.getAttributeDescription());
          }
          else
          {
            // If this is not a Schema attribute, we put it
            // in the extraAttribute map. This in fact acts as a replace.
            extraAttributes.put(attributeType.getNameOrOID(), attribute);
            modifiedSchemaFiles.add(FILE_USER_SCHEMA_ELEMENTS);
          }
          break;

        default:
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
              ERR_SCHEMA_INVALID_MODIFICATION_TYPE.get(m.getModificationType()));
      }
    }
  }

  private void addAttribute(Schema currentSchema, SchemaBuilder newSchemaBuilder, Attribute attribute,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    AttributeType at = attribute.getAttributeDescription().getAttributeType();
    if (at.equals(attributeTypesType))
    {
      for (ByteString v : attribute)
      {
        addAttributeType(v.toString(), currentSchema, newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(objectClassesType))
    {
      for (ByteString v : attribute)
      {
        addObjectClass(v.toString(), currentSchema, newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(nameFormsType))
    {
      for (ByteString v : attribute)
      {
        addNameForm(v.toString(), currentSchema, newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(ditContentRulesType))
    {
      for (ByteString v : attribute)
      {
        addDITContentRule(v.toString(), currentSchema, newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(ditStructureRulesType))
    {
      for (ByteString v : attribute)
      {
        addDITStructureRule(v.toString(), currentSchema, newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(matchingRuleUsesType))
    {
      for (ByteString v : attribute)
      {
        addMatchingRuleUse(v.toString(), currentSchema, newSchemaBuilder, modifiedSchemaFiles);
      }
    }
    else if (at.equals(ldapSyntaxesType))
    {
      for (ByteString v : attribute)
      {
        try
        {
          addLdapSyntaxDescription(v.toString(), currentSchema, newSchemaBuilder, modifiedSchemaFiles);
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
      LocalizableMessage message =
         ERR_SCHEMA_MODIFY_UNSUPPORTED_ATTRIBUTE_TYPE.get(attribute.getAttributeDescription());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }
  }

  private void deleteAttribute(SchemaBuilder newSchema, Attribute attribute,
      List<Modification> mods, int pos, Set<String> modifiedSchemaFiles) throws DirectoryException
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
      LocalizableMessage message =
         ERR_SCHEMA_MODIFY_UNSUPPORTED_ATTRIBUTE_TYPE.get(attribute.getAttributeDescription());
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
  private void addAttributeType(String definition, Schema currentSchema, SchemaBuilder schemaBuilder,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    String oid = SchemaUtils.parseAttributeTypeOID(definition);
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
    String givenSchemaFile = SchemaUtils.parseSchemaFileFromElementDefinition(definition);
    String finalSchemaFile = givenSchemaFile == null ? FILE_USER_SCHEMA_ELEMENTS : givenSchemaFile;
    modifiedSchemaFiles.add(finalSchemaFile);
    return SchemaUtils.addSchemaFileToElementDefinitionIfAbsent(definition, finalSchemaFile);
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
    String givenSchemaFile = SchemaUtils.parseSchemaFileFromElementDefinition(definition);
    String oldSchemaFile = getElementSchemaFile(existingElement);

    if (givenSchemaFile == null)
    {
      if (oldSchemaFile == null)
      {
        oldSchemaFile = FILE_USER_SCHEMA_ELEMENTS;
      }
      modifiedSchemaFiles.add(oldSchemaFile);
      return SchemaUtils.addSchemaFileToElementDefinitionIfAbsent(definition, oldSchemaFile);
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
  private void removeAttributeType(String definition, SchemaBuilder newSchemaBuilder,
      List<Modification> modifications, int currentPosition, Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    Schema currentSchema = newSchemaBuilder.toSchema();
    String atOID = SchemaUtils.parseAttributeTypeOID(definition);

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
          String oid = SchemaUtils.parseAttributeTypeOID(v.toString());
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
  private void addObjectClass(String definition, Schema currentSchema, SchemaBuilder schemaBuilder,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    String oid = SchemaUtils.parseObjectClassOID(definition);
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
    Schema currentSchema = newSchemaBuilder.toSchema();
    String ocOID = SchemaUtils.parseObjectClassOID(definition);

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
          oid = SchemaUtils.parseObjectClassOID(v.toString());
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
  private void addNameForm(String definition, Schema currentSchema, SchemaBuilder schemaBuilder,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    String oid = SchemaUtils.parseNameFormOID(definition);
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
    Schema currentSchema = newSchemaBuilder.toSchema();
    String nfOID = SchemaUtils.parseNameFormOID(definition);

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
          String oid = SchemaUtils.parseNameFormOID(v.toString());
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
  private void addDITContentRule(String definition, Schema currentSchema, SchemaBuilder schemaBuilder,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    String oid = SchemaUtils.parseDITContentRuleOID(definition);
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
  private void removeDITContentRule(String definition, SchemaBuilder newSchemaBuilder, Set<String> modifiedSchemaFiles)
      throws DirectoryException
  {
    Schema currentSchema = newSchemaBuilder.toSchema();
    String ruleOid = SchemaUtils.parseDITContentRuleOID(definition);
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
  private void addDITStructureRule(String definition, Schema currentSchema, SchemaBuilder schemaBuilder,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    int ruleId = SchemaUtils.parseRuleID(definition);
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
    Schema currentSchema = newSchemaBuilder.toSchema();
    int ruleID = SchemaUtils.parseRuleID(definition);

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
        int id = SchemaUtils.parseRuleID(v.toString());
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
  private void addMatchingRuleUse(String definition, Schema currentSchema, SchemaBuilder schemaBuilder,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    String oid = SchemaUtils.parseMatchingRuleUseOID(definition);
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
    Schema currentSchema = newSchemaBuilder.toSchema();
    String mruOid = SchemaUtils.parseMatchingRuleUseOID(definition);

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
  private void addLdapSyntaxDescription(String definition, Schema currentSchema, SchemaBuilder schemaBuilder,
      Set<String> modifiedSchemaFiles) throws DirectoryException
  {
    // TODO: not sure of the correct implementation here. There was previously a check that would
    // reject a change if a syntax with oid already exists, but I don't understand why.
    // I kept an implementation that behave like other schema elements.
    String oid = SchemaUtils.parseSyntaxOID(definition);
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
    Schema currentSchema = newSchemaBuilder.toSchema();
    String oid = SchemaUtils.parseSyntaxOID(definition);

    if (!currentSchema.hasSyntax(oid))
    {
      LocalizableMessage message = ERR_SCHEMA_MODIFY_REMOVE_NO_SUCH_LSD.get(oid);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    newSchemaBuilder.removeSyntax(oid);
    addElementIfNotNull(modifiedSchemaFiles, getElementSchemaFile(currentSchema.getSyntax(oid)));
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
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
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
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
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
                DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
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
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
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
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
          ERR_MEMORYBACKEND_CANNOT_CREATE_LDIF_READER.get(e), e);
    }
  }

  /**
   * Import an entry in the schema.
   * <p>
   * FIXME : attributeTypes and objectClasses are the only elements
   * currently taken into account.
   *
   * @param newSchemaEntry
   *            The entry to be imported.
   */
  private void importEntry(Entry newSchemaEntry) throws DirectoryException
  {
    schemaHandler.importEntry(newSchemaEntry, this);
  }

  private <T> void addElementIfNotNull(Collection<T> col, T element)
  {
    if (element != null)
    {
      col.add(element);
    }
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
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
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
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
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
          serverContext.getBackendConfigManager().deregisterBaseDN(dn);
          ccr.addMessage(INFO_SCHEMA_DEREGISTERED_BASE_DN.get(dn));
        }
        catch (Exception e)
        {
          logger.traceException(e);

          ccr.addMessage(ERR_SCHEMA_CANNOT_DEREGISTER_BASE_DN.get(dn, getExceptionMessage(e)));
          ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
        }
      }

      baseDNs = newBaseDNs;
      for (DN dn : baseDNs)
      {
        try
        {
          serverContext.getBackendConfigManager().registerBaseDN(dn, this, true);
          ccr.addMessage(INFO_SCHEMA_REGISTERED_BASE_DN.get(dn));
        }
        catch (Exception e)
        {
          logger.traceException(e);

          ccr.addMessage(ERR_SCHEMA_CANNOT_REGISTER_BASE_DN.get(dn, getExceptionMessage(e)));
          ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
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
    try
    {
      return schemaHandler.getSchemaDirectoryPath();
    }
    catch (InitializationException e)
    {
      logger.traceException(e);
      return null;
    }
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
