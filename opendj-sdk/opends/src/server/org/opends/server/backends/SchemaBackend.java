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



import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;

import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.MatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.schema.AttributeTypeSyntax;
import org.opends.server.schema.DITContentRuleSyntax;
import org.opends.server.schema.DITStructureRuleSyntax;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.schema.MatchingRuleUseSyntax;
import org.opends.server.schema.NameFormSyntax;
import org.opends.server.schema.ObjectClassSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DITContentRule;
import org.opends.server.types.DITStructureRule;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.MatchingRuleUse;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.NameForm;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ObjectClassType;
import org.opends.server.types.Privilege;
import org.opends.server.types.RDN;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.Schema;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFWriter;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.BackendMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.admin.std.server.SchemaBackendCfg;
import org.opends.server.admin.std.meta.SchemaBackendCfgDefn;
import org.opends.server.admin.server.ConfigurationChangeListener;
import static org.opends.server.messages.ConfigMessages.*;


/**
 * This class defines a backend to hold the Directory Server schema information.
 * It is a kind of meta-backend in that it doesn't actually hold any data but
 * rather dynamically generates the schema entry whenever it is requested.
 */
public class SchemaBackend
     extends Backend
     implements ConfigurationChangeListener<SchemaBackendCfg>, AlertGenerator
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.SchemaBackend";



  // The set of user-defined attributes that will be included in the schema
  // entry.
  private ArrayList<Attribute> userDefinedAttributes;

  // The attribute type that will be used to include the defined attribute
  // types.
  private AttributeType attributeTypesType;

  // The attribute type that will be used to hold the schema creation timestamp.
  private AttributeType createTimestampType;

  // The attribute type that will be used to hold the schema creator's name.
  private AttributeType creatorsNameType;

  // The attribute type that will be used to include the defined DIT content
  // rules.
  private AttributeType ditContentRulesType;

  // The attribute type that will be used to include the defined DIT structure
  // rules.
  private AttributeType ditStructureRulesType;

  // The attribute type that will be used to include the defined attribute
  // syntaxes.
  private AttributeType ldapSyntaxesType;

  // The attribute type that will be used to include the defined matching rules.
  private AttributeType matchingRulesType;

  // The attribute type that will be used to include the defined matching rule
  // uses.
  private AttributeType matchingRuleUsesType;

  // The attribute that will be used to hold the schema modifier's name.
  private AttributeType modifiersNameType;

  // The attribute type that will be used to hold the schema modification
  // timestamp.
  private AttributeType modifyTimestampType;

  // The attribute type that will be used to include the defined object classes.
  private AttributeType objectClassesType;

  // The attribute type that will be used to include the defined name forms.
  private AttributeType nameFormsType;

  // The attribute type that will be used to save the synchronization state.
  private AttributeType synchronizationStateType;

  // The value containing DN of the user we'll say created the configuration.
  private AttributeValue creatorsName;

  // The value containing the DN of the last user to modify the configuration.
  private AttributeValue modifiersName;

  // The timestamp that will be used for the schema creation time.
  private AttributeValue createTimestamp;

  // The timestamp that will be used for the latest schema modification time.
  private AttributeValue modifyTimestamp;

  // Indicates whether the attributes of the schema entry should always be
  // treated as user attributes even if they are defined as operational.
  private boolean showAllAttributes;

  // The DN of the configuration entry for this backend.
  private DN configEntryDN;

  // The current configuration state.
  private SchemaBackendCfg currentConfig;

  // The set of base DNs for this backend.
  private DN[] baseDNs;

  // The set of objectclasses that will be used in the schema entry.
  private HashMap<ObjectClass,String> schemaObjectClasses;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;

  // The time that the schema was last modified.
  private long modifyTime;



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


  /**
   * Initialization shared by multiple backend methods.
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this
   *                      backend.
   * @param  baseDNs      The set of base DNs that have been
   *                      configured for this backend.
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   */
  private void initializeCommon(ConfigEntry configEntry, DN[] baseDNs)
       throws ConfigException
  {
    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (configEntry == null)
    {
      int    msgID   = MSGID_SCHEMA_CONFIG_ENTRY_NULL;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }

    SchemaBackendCfg cfg = getSchemaBackendCfg(configEntry);
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
    synchronizationStateType =
      DirectoryServer.getAttributeType(ATTR_SYNCHRONIZATION_STATE_LC, true);


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


    // Define empty sets for the supported controls and features.
    supportedControls = new HashSet<String>(0);
    supportedFeatures = new HashSet<String>(0);


    configEntryDN = configEntry.getDN();
    this.baseDNs = baseDNs;

    creatorsName  = new AttributeValue(creatorsNameType, baseDNs[0].toString());
    modifiersName =
         new AttributeValue(modifiersNameType, baseDNs[0].toString());

    long createTime = DirectoryServer.getSchema().getOldestModificationTime();
    createTimestamp =
         GeneralizedTimeSyntax.createGeneralizedTimeValue(createTime);

    long modifyTime = DirectoryServer.getSchema().getYoungestModificationTime();
    modifyTimestamp =
         GeneralizedTimeSyntax.createGeneralizedTimeValue(modifyTime);


    // Get the set of user-defined attributes for the configuration entry.  Any
    // attributes that we don't recognize will be included directly in the
    // schema entry.
    userDefinedAttributes = new ArrayList<Attribute>();
    for (List<Attribute> attrs :
         configEntry.getEntry().getUserAttributes().values())
    {
      for (Attribute a : attrs)
      {
        if (! isSchemaConfigAttribute(a))
        {
          userDefinedAttributes.add(a);
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
          userDefinedAttributes.add(a);
        }
      }
    }


    // Determine whether to show all attributes.
    showAllAttributes = cfg.isShowAllAttributes();


    currentConfig = cfg;
  }


  /**
   * {@inheritDoc}
   */
  public void initializeBackend(ConfigEntry configEntry, DN[] baseDNs)
         throws ConfigException, InitializationException
  {
    initializeCommon(configEntry, baseDNs);

    // Register each of the suffixes with the Directory Server.  Also, register
    // the first one as the schema base.
    DirectoryServer.setSchemaDN(baseDNs[0]);
    for (int i=0; i < baseDNs.length; i++)
    {
      try
      {
        DirectoryServer.registerBaseDN(baseDNs[i], this, true, false);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_BACKEND_CANNOT_REGISTER_BASEDN;
        String message = getMessage(msgID, baseDNs[i].toString(),
                                    getExceptionMessage(e));
        throw new InitializationException(msgID, message, e);
      }
    }


    // Identify any differences that may exist between the concatenated schema
    // file from the last online modification and the current schema files.  If
    // there are any differences, then they should be from making changes to the
    // schema files with the server offline.
    try
    {
      // First, generate lists of elements from the current schema.
      LinkedHashSet<String> newATs  = new LinkedHashSet<String>();
      LinkedHashSet<String> newOCs  = new LinkedHashSet<String>();
      LinkedHashSet<String> newNFs  = new LinkedHashSet<String>();
      LinkedHashSet<String> newDCRs = new LinkedHashSet<String>();
      LinkedHashSet<String> newDSRs = new LinkedHashSet<String>();
      LinkedHashSet<String> newMRUs = new LinkedHashSet<String>();
      Schema.genConcatenatedSchema(newATs, newOCs, newNFs, newDCRs, newDSRs,
                                   newMRUs);

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
          if ((runningUnitTestsStr != null) &&
              runningUnitTestsStr.equalsIgnoreCase("true"))
          {
            Schema.writeConcatenatedSchema();
            concatFile = new File(upgradeDirectory, SCHEMA_CONCAT_FILE_NAME);
            concatFilePath = concatFile.getAbsolutePath();
          }
          else
          {
            int msgID = MSGID_SCHEMA_CANNOT_FIND_CONCAT_FILE;
            String message = getMessage(msgID,
                                        upgradeDirectory.getAbsolutePath(),
                                        SCHEMA_CONCAT_FILE_NAME,
                                        concatFile.getName());
            throw new InitializationException(msgID, message);
          }
        }
      }

      LinkedHashSet<String> oldATs  = new LinkedHashSet<String>();
      LinkedHashSet<String> oldOCs  = new LinkedHashSet<String>();
      LinkedHashSet<String> oldNFs  = new LinkedHashSet<String>();
      LinkedHashSet<String> oldDCRs = new LinkedHashSet<String>();
      LinkedHashSet<String> oldDSRs = new LinkedHashSet<String>();
      LinkedHashSet<String> oldMRUs = new LinkedHashSet<String>();
      Schema.readConcatenatedSchema(concatFilePath, oldATs, oldOCs, oldNFs,
                                    oldDCRs, oldDSRs, oldMRUs);

      // Create a list of modifications and add any differences between the old
      // and new schema into them.
      LinkedList<Modification> mods = new LinkedList<Modification>();
      Schema.compareConcatenatedSchema(oldATs, newATs, attributeTypesType,
                                       mods);
      Schema.compareConcatenatedSchema(oldOCs, newOCs, objectClassesType, mods);
      Schema.compareConcatenatedSchema(oldNFs, newNFs, nameFormsType, mods);
      Schema.compareConcatenatedSchema(oldDCRs, newDCRs, ditContentRulesType,
                                       mods);
      Schema.compareConcatenatedSchema(oldDSRs, newDSRs, ditStructureRulesType,
                                       mods);
      Schema.compareConcatenatedSchema(oldMRUs, newMRUs, matchingRuleUsesType,
                                       mods);
      if (! mods.isEmpty())
      {
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
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_SCHEMA_ERROR_DETERMINING_SCHEMA_CHANGES;
      String message = getMessage(msgID, getExceptionMessage(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    // Register with the Directory Server as a configurable component.
    currentConfig.addSchemaChangeListener(this);
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
    currentConfig.removeSchemaChangeListener(this);

    for (DN baseDN : baseDNs)
    {
      try
      {
        DirectoryServer.deregisterBaseDN(baseDN, false);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
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
    if (attrType.hasName(ATTR_SCHEMA_ENTRY_DN.toLowerCase()) ||
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
        attrType.hasName(OP_ATTR_MODIFY_TIMESTAMP_LC))
    {
      return true;
    }

    return false;
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
    // There is always only a single entry in this backend.
    return 1;
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
    // If the requested entry was one of the schema entries, then create and
    // return it.
    DN[] dnArray = baseDNs;
    for (DN baseDN : dnArray)
    {
      if (entryDN.equals(baseDN))
      {
        return getSchemaEntry(entryDN);
      }
    }


    // There is never anything below the schema entries, so we will return null.
    return null;
  }



  /**
   * Generates and returns a schema entry for the Directory Server.
   *
   * @param  entryDN  The DN to use for the generated entry.
   *
   * @return  The schema entry that was generated.
   */
  public Entry getSchemaEntry(DN entryDN)
  {
    LinkedHashMap<AttributeType,List<Attribute>> userAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>();

    LinkedHashMap<AttributeType,List<Attribute>> operationalAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>();


    // Add the RDN attribute(s) for the provided entry.
    RDN rdn = entryDN.getRDN();
    if (rdn != null)
    {
      int numAVAs = rdn.getNumValues();
      for (int i=0; i < numAVAs; i++)
      {
        LinkedHashSet<AttributeValue> valueSet =
             new LinkedHashSet<AttributeValue>(1);
        valueSet.add(rdn.getAttributeValue(i));

        AttributeType attrType = rdn.getAttributeType(i);
        String attrName = rdn.getAttributeName(i);
        Attribute a = new Attribute(attrType, attrName, valueSet);
        ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
        attrList.add(a);

        if (attrType.isOperational())
        {
          operationalAttrs.put(attrType, attrList);
        }
        else
        {
          userAttrs.put(attrType, attrList);
        }
      }
    }


    // Add the "attributeTypes" attribute.
    LinkedHashSet<AttributeValue> valueSet =
         DirectoryServer.getAttributeTypeSet();
    Attribute attr = new Attribute(attributeTypesType, ATTR_ATTRIBUTE_TYPES,
                                   valueSet);
    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(attr);
    if (attributeTypesType.isOperational() && (! showAllAttributes))
    {
      operationalAttrs.put(attributeTypesType, attrList);
    }
    else
    {
      userAttrs.put(attributeTypesType, attrList);
    }


    // Add the "objectClasses" attribute.
    valueSet = DirectoryServer.getObjectClassSet();
    attr = new Attribute(objectClassesType, ATTR_OBJECTCLASSES, valueSet);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(attr);
    if (objectClassesType.isOperational() && (! showAllAttributes))
    {
      operationalAttrs.put(objectClassesType, attrList);
    }
    else
    {
      userAttrs.put(objectClassesType, attrList);
    }


    // Add the "matchingRules" attribute.
    valueSet = DirectoryServer.getMatchingRuleSet();
    attr = new Attribute(matchingRulesType, ATTR_MATCHING_RULES, valueSet);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(attr);
    if (matchingRulesType.isOperational() && (! showAllAttributes))
    {
      operationalAttrs.put(matchingRulesType, attrList);
    }
    else
    {
      userAttrs.put(matchingRulesType, attrList);
    }


    // Add the "ldapSyntaxes" attribute.
    valueSet = DirectoryServer.getAttributeSyntaxSet();
    attr = new Attribute(ldapSyntaxesType, ATTR_LDAP_SYNTAXES, valueSet);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(attr);

    // Note that we intentionally ignore showAllAttributes for attribute
    // syntaxes, name forms, matching rule uses, DIT content rules, and DIT
    // structure rules because those attributes aren't allowed in the subschema
    // objectclass, and treating them as user attributes would cause schema
    // updates to fail.  This means that you'll always have to explicitly
    // request these attributes in order to be able to see them.
    if (ldapSyntaxesType.isOperational())
    {
      operationalAttrs.put(ldapSyntaxesType, attrList);
    }
    else
    {
      userAttrs.put(ldapSyntaxesType, attrList);
    }


    // If there are any name forms defined, then add them.
    valueSet = DirectoryServer.getNameFormSet();
    if (! valueSet.isEmpty())
    {
      attr = new Attribute(nameFormsType, ATTR_NAME_FORMS, valueSet);
      attrList = new ArrayList<Attribute>(1);
      attrList.add(attr);
      if (nameFormsType.isOperational())
      {
        operationalAttrs.put(nameFormsType, attrList);
      }
      else
      {
        userAttrs.put(nameFormsType, attrList);
      }
    }


    // If there are any DIT content rules defined, then add them.
    valueSet = DirectoryServer.getDITContentRuleSet();
    if (! valueSet.isEmpty())
    {
      attr = new Attribute(ditContentRulesType, ATTR_DIT_CONTENT_RULES,
                           valueSet);
      attrList = new ArrayList<Attribute>(1);
      attrList.add(attr);
      if (ditContentRulesType.isOperational())
      {
        operationalAttrs.put(ditContentRulesType, attrList);
      }
      else
      {
        userAttrs.put(ditContentRulesType, attrList);
      }
    }


    // If there are any DIT structure rules defined, then add them.
    valueSet = DirectoryServer.getDITStructureRuleSet();
    if (! valueSet.isEmpty())
    {
      attr = new Attribute(ditStructureRulesType, ATTR_DIT_STRUCTURE_RULES,
                           valueSet);
      attrList = new ArrayList<Attribute>(1);
      attrList.add(attr);
      if (ditStructureRulesType.isOperational())
      {
        operationalAttrs.put(ditStructureRulesType, attrList);
      }
      else
      {
        userAttrs.put(ditStructureRulesType, attrList);
      }
    }


    // If there are any matching rule uses defined, then add them.
    valueSet = DirectoryServer.getMatchingRuleUseSet();
    if (! valueSet.isEmpty())
    {
      attr = new Attribute(matchingRuleUsesType, ATTR_MATCHING_RULE_USE,
                           valueSet);
      attrList = new ArrayList<Attribute>(1);
      attrList.add(attr);
      if (matchingRuleUsesType.isOperational())
      {
        operationalAttrs.put(matchingRuleUsesType, attrList);
      }
      else
      {
        userAttrs.put(matchingRuleUsesType, attrList);
      }
    }


    // Add the lastmod attributes.
    valueSet = new LinkedHashSet<AttributeValue>(1);
    valueSet.add(creatorsName);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(creatorsNameType, OP_ATTR_CREATORS_NAME,
                               valueSet));
    operationalAttrs.put(creatorsNameType, attrList);

    valueSet = new LinkedHashSet<AttributeValue>(1);
    valueSet.add(createTimestamp);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(createTimestampType, OP_ATTR_CREATE_TIMESTAMP,
                               valueSet));
    operationalAttrs.put(createTimestampType, attrList);

    if (DirectoryServer.getSchema().getYoungestModificationTime() != modifyTime)
    {
      synchronized (this)
      {
        modifyTime = DirectoryServer.getSchema().getYoungestModificationTime();
        modifyTimestamp =
             GeneralizedTimeSyntax.createGeneralizedTimeValue(modifyTime);
      }
    }

    valueSet = new LinkedHashSet<AttributeValue>(1);
    valueSet.add(modifiersName);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(modifiersNameType, OP_ATTR_MODIFIERS_NAME,
                               valueSet));
    operationalAttrs.put(modifiersNameType, attrList);

    valueSet = new LinkedHashSet<AttributeValue>(1);
    valueSet.add(modifyTimestamp);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(modifyTimestampType, OP_ATTR_MODIFY_TIMESTAMP,
                               valueSet));
    operationalAttrs.put(modifyTimestampType, attrList);

    //  Add the synchronization State attribute.
    valueSet = DirectoryServer.getSchema().getSynchronizationState();
    attr = new Attribute(synchronizationStateType,
                         ATTR_SYNCHRONIZATION_STATE_LC, valueSet);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(attr);
    operationalAttrs.put(synchronizationStateType, attrList);

    // Add all the user-defined attributes.
    for (Attribute a : userDefinedAttributes)
    {
      AttributeType type = a.getAttributeType();

      if (type.isOperational())
      {
        List<Attribute> attrs = operationalAttrs.get(type);
        if (attrs == null)
        {
          attrs = new ArrayList<Attribute>();
          attrs.add(a);
          operationalAttrs.put(type, attrs);
        }
        else
        {
          attrs.add(a);
        }
      }
      else
      {
        List<Attribute> attrs = userAttrs.get(type);
        if (attrs == null)
        {
          attrs = new ArrayList<Attribute>();
          attrs.add(a);
          userAttrs.put(type, attrs);
        }
        else
        {
          attrs.add(a);
        }
      }
    }


    // Construct and return the entry.
    Entry e = new Entry(entryDN, schemaObjectClasses, userAttrs,
                        operationalAttrs);
    e.processVirtualAttributes();
    return e;
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
    int    msgID   = MSGID_SCHEMA_ADD_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(entry.getDN()));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
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
    int    msgID   = MSGID_SCHEMA_DELETE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(entryDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
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
    // Make sure that the authenticated user has the necessary UPDATE_SCHEMA
    // privilege.
    ClientConnection clientConnection = modifyOperation.getClientConnection();
    if (! clientConnection.hasPrivilege(Privilege.UPDATE_SCHEMA,
                                        modifyOperation))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_INSUFFICIENT_PRIVILEGES;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                   message, msgID);
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
    Iterator<Modification> iterator = mods.iterator();
    while (iterator.hasNext())
    {
      Modification m = iterator.next();
      pos++;

      if (m.isInternal())
      {
        // We don't need to do anything for internal modifications (e.g., like
        // those that set modifiersName and modifyTimestamp).
        iterator.remove();
        continue;
      }


      // Determine the type of modification to perform.  We will support add and
      // delete operations in the schema, and we will also support the ability
      // to add a schema element that already exists and treat it as a
      // replacement of that existing element.
      Attribute     a  = m.getAttribute();
      AttributeType at = a.getAttributeType();
      switch (m.getModificationType())
      {
        case ADD:
          LinkedHashSet<AttributeValue> values = a.getValues();
          if (values.isEmpty())
          {
            continue;
          }

          if (at.equals(attributeTypesType))
          {
            for (AttributeValue v : values)
            {
              AttributeType type;
              try
              {
                type = AttributeTypeSyntax.decodeAttributeType(v.getValue(),
                                                newSchema, false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_ATTRTYPE;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              addAttributeType(type, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(objectClassesType))
          {
            for (AttributeValue v : values)
            {
              ObjectClass oc;
              try
              {
                oc = ObjectClassSyntax.decodeObjectClass(v.getValue(),
                                                         newSchema, false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_OBJECTCLASS;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              addObjectClass(oc, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(nameFormsType))
          {
            for (AttributeValue v : values)
            {
              NameForm nf;
              try
              {
                nf = NameFormSyntax.decodeNameForm(v.getValue(), newSchema,
                                                   false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_NAME_FORM;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              addNameForm(nf, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(ditContentRulesType))
          {
            for (AttributeValue v : values)
            {
              DITContentRule dcr;
              try
              {
                dcr = DITContentRuleSyntax.decodeDITContentRule(v.getValue(),
                                                newSchema, false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_DCR;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              addDITContentRule(dcr, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(ditStructureRulesType))
          {
            for (AttributeValue v : values)
            {
              DITStructureRule dsr;
              try
              {
                dsr = DITStructureRuleSyntax.decodeDITStructureRule(
                           v.getValue(), newSchema, false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_DSR;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              addDITStructureRule(dsr, newSchema, modifiedSchemaFiles);
            }
          }
          else if (at.equals(matchingRuleUsesType))
          {
            for (AttributeValue v : values)
            {
              MatchingRuleUse mru;
              try
              {
                mru = MatchingRuleUseSyntax.decodeMatchingRuleUse(v.getValue(),
                                                 newSchema, false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_MR_USE;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              addMatchingRuleUse(mru, newSchema, modifiedSchemaFiles);
            }
          }
          else
          {
            int    msgID   = MSGID_SCHEMA_MODIFY_UNSUPPORTED_ATTRIBUTE_TYPE;
            String message = getMessage(msgID, a.getName());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message, msgID);
          }

          break;


        case DELETE:
          values = a.getValues();
          if (values.isEmpty())
          {
            int    msgID   = MSGID_SCHEMA_MODIFY_DELETE_NO_VALUES;
            String message = getMessage(msgID, a.getName());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message, msgID);
          }

          if (at.equals(attributeTypesType))
          {
            for (AttributeValue v : values)
            {
              AttributeType type;
              try
              {
                type = AttributeTypeSyntax.decodeAttributeType(v.getValue(),
                                                newSchema, false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_ATTRTYPE;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              removeAttributeType(type, newSchema, mods, pos,
                                  modifiedSchemaFiles);
            }
          }
          else if (at.equals(objectClassesType))
          {
            for (AttributeValue v : values)
            {
              ObjectClass oc;
              try
              {
                oc = ObjectClassSyntax.decodeObjectClass(v.getValue(),
                                                         newSchema, false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_OBJECTCLASS;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              removeObjectClass(oc, newSchema, mods, pos, modifiedSchemaFiles);
            }
          }
          else if (at.equals(nameFormsType))
          {
            for (AttributeValue v : values)
            {
              NameForm nf;
              try
              {
                nf = NameFormSyntax.decodeNameForm(v.getValue(), newSchema,
                                                   false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_NAME_FORM;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              removeNameForm(nf, newSchema, mods, pos, modifiedSchemaFiles);
            }
          }
          else if (at.equals(ditContentRulesType))
          {
            for (AttributeValue v : values)
            {
              DITContentRule dcr;
              try
              {
                dcr = DITContentRuleSyntax.decodeDITContentRule(v.getValue(),
                                                newSchema, false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_DCR;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              removeDITContentRule(dcr, newSchema, mods, pos,
                                   modifiedSchemaFiles);
            }
          }
          else if (at.equals(ditStructureRulesType))
          {
            for (AttributeValue v : values)
            {
              DITStructureRule dsr;
              try
              {
                dsr = DITStructureRuleSyntax.decodeDITStructureRule(
                           v.getValue(), newSchema, false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_DSR;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              removeDITStructureRule(dsr, newSchema, mods, pos,
                                     modifiedSchemaFiles);
            }
          }
          else if (at.equals(matchingRuleUsesType))
          {
            for (AttributeValue v : values)
            {
              MatchingRuleUse mru;
              try
              {
                mru = MatchingRuleUseSyntax.decodeMatchingRuleUse(v.getValue(),
                                                 newSchema, false);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_MR_USE;
                String message = getMessage(msgID, v.getStringValue(),
                                            de.getErrorMessage());
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID, de);
              }

              removeMatchingRuleUse(mru, newSchema, mods, pos,
                                    modifiedSchemaFiles);
            }
          }
          else
          {
            int    msgID   = MSGID_SCHEMA_MODIFY_UNSUPPORTED_ATTRIBUTE_TYPE;
            String message = getMessage(msgID, a.getName());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message, msgID);
          }

          break;


        default:
          if (!modifyOperation.isSynchronizationOperation())
          {
            int    msgID   = MSGID_SCHEMA_INVALID_MODIFICATION_TYPE;
            String message = getMessage(msgID, m.getModificationType());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message, msgID);
          }
          else
          {
            if (at.equals(synchronizationStateType))
              newSchema.setSynchronizationState(a.getValues());
            modifiedSchemaFiles.add(FILE_USER_SCHEMA_ELEMENTS);
          }
      }
    }


    // If we've gotten here, then everything looks OK.  We'll re-write all
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
      DirectoryServer.setSchema(newSchema);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, de);
      }

      throw de;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_SCHEMA_MODIFY_CANNOT_WRITE_NEW_SCHEMA;
      String message = getMessage(msgID, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
    finally
    {
      cleanUpTempSchemaFiles(tempSchemaFiles);
    }


    // Create a single file with all of the concatenated schema information
    // that we can use on startup to detect whether the schema files have been
    // edited with the server offline.
    Schema.writeConcatenatedSchema();


    DN authzDN = modifyOperation.getAuthorizationDN();
    if (authzDN == null)
    {
      authzDN = DN.nullDN();
    }

    modifiersName = new AttributeValue(modifiersNameType, authzDN.toString());
    modifyTimestamp = GeneralizedTimeSyntax.createGeneralizedTimeValue(
                           System.currentTimeMillis());
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
        int msgID = MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_ATTRTYPE;
        String message = getMessage(msgID, attributeType.getNameOrOID(),
                                    existingType.getNameOrOID(),
                                    t.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }


    // Make sure that the new attribute type doesn't reference an undefined
    // or OBSOLETE superior attribute type.
    AttributeType superiorType = attributeType.getSuperiorType();
    if (superiorType != null)
    {
      if (! schema.hasAttributeType(superiorType.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_UNDEFINED_SUPERIOR_ATTRIBUTE_TYPE;
        String message = getMessage(msgID, attributeType.getNameOrOID(),
                                    superiorType.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      else if (superiorType.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_OBSOLETE_SUPERIOR_ATTRIBUTE_TYPE;
        String message = getMessage(msgID, attributeType.getNameOrOID(),
                                    superiorType.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }


    // Make sure that none of the associated matching rules are marked OBSOLETE.
    MatchingRule mr = attributeType.getEqualityMatchingRule();
    if ((mr != null) && mr.isObsolete())
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_ATTRTYPE_OBSOLETE_MR;
      String message = getMessage(msgID, attributeType.getNameOrOID(),
                                  mr.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    mr = attributeType.getOrderingMatchingRule();
    if ((mr != null) && mr.isObsolete())
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_ATTRTYPE_OBSOLETE_MR;
      String message = getMessage(msgID, attributeType.getNameOrOID(),
                                  mr.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    mr = attributeType.getSubstringMatchingRule();
    if ((mr != null) && mr.isObsolete())
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_ATTRTYPE_OBSOLETE_MR;
      String message = getMessage(msgID, attributeType.getNameOrOID(),
                                  mr.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    mr = attributeType.getApproximateMatchingRule();
    if ((mr != null) && mr.isObsolete())
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_ATTRTYPE_OBSOLETE_MR;
      String message = getMessage(msgID, attributeType.getNameOrOID(),
                                  mr.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }


    // If there is no existing type, then we're adding a new attribute.
    // Otherwise, we're replacing an existing one.
    if (existingType == null)
    {
      schema.registerAttributeType(attributeType, false);
      String schemaFile = attributeType.getSchemaFile();
      if ((schemaFile == null) || (schemaFile.length() == 0))
      {
        schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        attributeType.setSchemaFile(schemaFile);
      }

      modifiedSchemaFiles.add(schemaFile);
    }
    else
    {
      schema.deregisterAttributeType(existingType);
      schema.registerAttributeType(attributeType, false);
      schema.rebuildDependentElements(existingType);

      if ((attributeType.getSchemaFile() == null) ||
          (attributeType.getSchemaFile().length() == 0))
      {
        String schemaFile = existingType.getSchemaFile();
        if ((schemaFile == null) || (schemaFile.length() == 0))
        {
          schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        }

        attributeType.setSchemaFile(schemaFile);
        modifiedSchemaFiles.add(schemaFile);
      }
      else
      {
        String newSchemaFile = attributeType.getSchemaFile();
        String oldSchemaFile = existingType.getSchemaFile();
        if ((oldSchemaFile == null) || oldSchemaFile.equals(newSchemaFile))
        {
          modifiedSchemaFiles.add(newSchemaFile);
        }
        else
        {
          modifiedSchemaFiles.add(newSchemaFile);
          modifiedSchemaFiles.add(oldSchemaFile);
        }
      }
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
    if ((removeType == null) || (! removeType.equals(attributeType)))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_ATTRIBUTE_TYPE;
      String message = getMessage(msgID, attributeType.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // See if there is another modification later to add the attribute type back
    // into the schema.  If so, then it's a replace and we should ignore the
    // remove because adding it back will handle the replace.
    for (int i=currentPosition+1; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute    a = m.getAttribute();

      if ((m.getModificationType() != ModificationType.ADD) ||
          (! a.getAttributeType().equals(attributeTypesType)))
      {
        continue;
      }

      for (AttributeValue v : a.getValues())
      {
        AttributeType at;
        try
        {
          at = AttributeTypeSyntax.decodeAttributeType(v.getValue(), schema,
                                                       true);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, de);
          }

          int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_ATTRTYPE;
          String message = getMessage(msgID, v.getStringValue(),
                                      de.getErrorMessage());
          throw new DirectoryException(
                         ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                         msgID, de);
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
      if ((superiorType != null) && superiorType.equals(removeType))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_AT_SUPERIOR_TYPE;
        String message = getMessage(msgID, removeType.getNameOrOID(),
                                    superiorType.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }


    // Make sure that the attribute type isn't used as a required or optional
    // attribute type in any objectclass.
    for (ObjectClass oc : schema.getObjectClasses().values())
    {
      if (oc.getRequiredAttributes().contains(removeType) ||
          oc.getOptionalAttributes().contains(removeType))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_OC;
        String message = getMessage(msgID, removeType.getNameOrOID(),
                                    oc.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }


    // Make sure that the attribute type isn't used as a required or optional
    // attribute type in any name form.
    for (NameForm nf : schema.getNameFormsByObjectClass().values())
    {
      if (nf.getRequiredAttributes().contains(removeType) ||
          nf.getOptionalAttributes().contains(removeType))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_NF;
        String message = getMessage(msgID, removeType.getNameOrOID(),
                                    nf.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
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
        int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_DCR;
        String message = getMessage(msgID, removeType.getNameOrOID(),
                                    dcr.getName());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }


    // Make sure that the attribute type isn't referenced by any matching rule
    // use.
    for (MatchingRuleUse mru : schema.getMatchingRuleUses().values())
    {
      if (mru.getAttributes().contains(removeType))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_MR_USE;
        String message = getMessage(msgID, removeType.getNameOrOID(),
                                    mru.getName());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }


    // If we've gotten here, then it's OK to remove the attribute type from
    // the schema.
    schema.deregisterAttributeType(removeType);
    String schemaFile = removeType.getSchemaFile();
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
        int msgID = MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_OBJECTCLASS;
        String message = getMessage(msgID, objectClass.getNameOrOID(),
                                    existingClass.getNameOrOID(),
                                    oc.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }


    // Make sure that the new objectclass doesn't reference an undefined
    // superior class, or an undefined required or optional attribute type,
    // and that none of them are OBSOLETE.
    ObjectClass superiorClass = objectClass.getSuperiorClass();
    if (superiorClass != null)
    {
      if (! schema.hasObjectClass(superiorClass.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_UNDEFINED_SUPERIOR_OBJECTCLASS;
        String message = getMessage(msgID, objectClass.getNameOrOID(),
                                    superiorClass.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      else if (superiorClass.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_OBSOLETE_SUPERIOR_OBJECTCLASS;
        String message = getMessage(msgID, objectClass.getNameOrOID(),
                                    superiorClass.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }

    for (AttributeType at : objectClass.getRequiredAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_OC_UNDEFINED_REQUIRED_ATTR;
        String message = getMessage(msgID, objectClass.getNameOrOID(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      else if (at.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_OC_OBSOLETE_REQUIRED_ATTR;
        String message = getMessage(msgID, objectClass.getNameOrOID(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }

    for (AttributeType at : objectClass.getOptionalAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_OC_UNDEFINED_OPTIONAL_ATTR;
        String message = getMessage(msgID, objectClass.getNameOrOID(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      else if (at.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_OC_OBSOLETE_OPTIONAL_ATTR;
        String message = getMessage(msgID, objectClass.getNameOrOID(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }


    // If there is no existing class, then we're adding a new objectclass.
    // Otherwise, we're replacing an existing one.
    if (existingClass == null)
    {
      schema.registerObjectClass(objectClass, false);
      String schemaFile = objectClass.getSchemaFile();
      if ((schemaFile == null) || (schemaFile.length() == 0))
      {
        schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        objectClass.setSchemaFile(schemaFile);
      }

      modifiedSchemaFiles.add(schemaFile);
    }
    else
    {
      schema.deregisterObjectClass(existingClass);
      schema.registerObjectClass(objectClass, false);
      schema.rebuildDependentElements(existingClass);

      if ((objectClass.getSchemaFile() == null) ||
          (objectClass.getSchemaFile().length() == 0))
      {
        String schemaFile = existingClass.getSchemaFile();
        if ((schemaFile == null) || (schemaFile.length() == 0))
        {
          schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        }

        objectClass.setSchemaFile(schemaFile);
        modifiedSchemaFiles.add(schemaFile);
      }
      else
      {
        String newSchemaFile = objectClass.getSchemaFile();
        String oldSchemaFile = existingClass.getSchemaFile();
        if ((oldSchemaFile == null) || oldSchemaFile.equals(newSchemaFile))
        {
          modifiedSchemaFiles.add(newSchemaFile);
        }
        else
        {
          modifiedSchemaFiles.add(newSchemaFile);
          modifiedSchemaFiles.add(oldSchemaFile);
        }
      }
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
    if ((removeClass == null) || (! removeClass.equals(objectClass)))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_OBJECTCLASS;
      String message = getMessage(msgID, objectClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // See if there is another modification later to add the objectclass back
    // into the schema.  If so, then it's a replace and we should ignore the
    // remove because adding it back will handle the replace.
    for (int i=currentPosition+1; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute    a = m.getAttribute();

      if ((m.getModificationType() != ModificationType.ADD) ||
          (! a.getAttributeType().equals(objectClassesType)))
      {
        continue;
      }

      for (AttributeValue v : a.getValues())
      {
        ObjectClass oc;
        try
        {
          oc = ObjectClassSyntax.decodeObjectClass(v.getValue(), schema, true);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, de);
          }

          int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_OBJECTCLASS;
          String message = getMessage(msgID, v.getStringValue(),
                                      de.getErrorMessage());
          throw new DirectoryException(
                         ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                         msgID, de);
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
      ObjectClass superiorClass = oc.getSuperiorClass();
      if ((superiorClass != null) && superiorClass.equals(removeClass))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_OC_SUPERIOR_CLASS;
        String message = getMessage(msgID, removeClass.getNameOrOID(),
                                    superiorClass.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }


    // Make sure that the objectclass isn't used as the structural class for
    // any name form.
    NameForm nf = schema.getNameForm(removeClass);
    if (nf != null)
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_OC_IN_NF;
      String message = getMessage(msgID, removeClass.getNameOrOID(),
                                  nf.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // Make sure that the objectclass isn't used as a structural or auxiliary
    // class for any DIT content rule.
    for (DITContentRule dcr : schema.getDITContentRules().values())
    {
      if (dcr.getStructuralClass().equals(removeClass) ||
          dcr.getAuxiliaryClasses().contains(removeClass))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_OC_IN_DCR;
        String message = getMessage(msgID, removeClass.getNameOrOID(),
                                    dcr.getName());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }


    // If we've gotten here, then it's OK to remove the objectclass from the
    // schema.
    schema.deregisterObjectClass(removeClass);
    String schemaFile = removeClass.getSchemaFile();
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
        int msgID = MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_NAME_FORM;
        String message = getMessage(msgID, nameForm.getNameOrOID(),
                                    existingNF.getNameOrOID(),
                                    nf.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }


    // Make sure that the new name form doesn't reference an undefined
    // structural class, or an undefined required or optional attribute type, or
    // that any of them are marked OBSOLETE.
    ObjectClass structuralClass = nameForm.getStructuralClass();
    if (! schema.hasObjectClass(structuralClass.getOID()))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_NF_UNDEFINED_STRUCTURAL_OC;
      String message = getMessage(msgID, nameForm.getNameOrOID(),
                                  structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }
    if (structuralClass.getObjectClassType() != ObjectClassType.STRUCTURAL)
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_NF_OC_NOT_STRUCTURAL;
      String message = getMessage(msgID, nameForm.getNameOrOID(),
                                  structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }
    if (structuralClass.isObsolete())
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_NF_OC_OBSOLETE;
      String message = getMessage(msgID, nameForm.getNameOrOID(),
                                  structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }

    NameForm existingNFForClass = schema.getNameForm(structuralClass);
    if ((existingNFForClass != null) && (existingNFForClass != existingNF))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_STRUCTURAL_OC_CONFLICT_FOR_ADD_NF;
      String message = getMessage(msgID, nameForm.getNameOrOID(),
                                  structuralClass.getNameOrOID(),
                                  existingNFForClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }

    for (AttributeType at : nameForm.getRequiredAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_NF_UNDEFINED_REQUIRED_ATTR;
        String message = getMessage(msgID, nameForm.getNameOrOID(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      else if (at.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_NF_OBSOLETE_REQUIRED_ATTR;
        String message = getMessage(msgID, nameForm.getNameOrOID(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }

    for (AttributeType at : nameForm.getOptionalAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_NF_UNDEFINED_OPTIONAL_ATTR;
        String message = getMessage(msgID, nameForm.getNameOrOID(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      else if (at.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_NF_OBSOLETE_OPTIONAL_ATTR;
        String message = getMessage(msgID, nameForm.getNameOrOID(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }


    // If there is no existing class, then we're adding a new name form.
    // Otherwise, we're replacing an existing one.
    if (existingNF == null)
    {
      schema.registerNameForm(nameForm, false);
      String schemaFile = nameForm.getSchemaFile();
      if ((schemaFile == null) || (schemaFile.length() == 0))
      {
        schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        nameForm.setSchemaFile(schemaFile);
      }

      modifiedSchemaFiles.add(schemaFile);
    }
    else
    {
      schema.deregisterNameForm(existingNF);
      schema.registerNameForm(nameForm, false);
      schema.rebuildDependentElements(existingNF);

      if ((nameForm.getSchemaFile() == null) ||
          (nameForm.getSchemaFile().length() == 0))
      {
        String schemaFile = existingNF.getSchemaFile();
        if ((schemaFile == null) || (schemaFile.length() == 0))
        {
          schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        }

        nameForm.setSchemaFile(schemaFile);
        modifiedSchemaFiles.add(schemaFile);
      }
      else
      {
        String newSchemaFile = nameForm.getSchemaFile();
        String oldSchemaFile = existingNF.getSchemaFile();
        if ((oldSchemaFile == null) || oldSchemaFile.equals(newSchemaFile))
        {
          modifiedSchemaFiles.add(newSchemaFile);
        }
        else
        {
          modifiedSchemaFiles.add(newSchemaFile);
          modifiedSchemaFiles.add(oldSchemaFile);
        }
      }
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
    if ((removeNF == null) || (! removeNF.equals(nameForm)))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_NAME_FORM;
      String message = getMessage(msgID, nameForm.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // See if there is another modification later to add the name form back
    // into the schema.  If so, then it's a replace and we should ignore the
    // remove because adding it back will handle the replace.
    for (int i=currentPosition+1; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute    a = m.getAttribute();

      if ((m.getModificationType() != ModificationType.ADD) ||
          (! a.getAttributeType().equals(nameFormsType)))
      {
        continue;
      }

      for (AttributeValue v : a.getValues())
      {
        NameForm nf;
        try
        {
          nf = NameFormSyntax.decodeNameForm(v.getValue(), schema, true);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, de);
          }

          int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_NAME_FORM;
          String message = getMessage(msgID, v.getStringValue(),
                                      de.getErrorMessage());
          throw new DirectoryException(
                         ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                         msgID, de);
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
      int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_NF_IN_DSR;
      String message = getMessage(msgID, removeNF.getNameOrOID(),
                                  dsr.getNameOrRuleID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // If we've gotten here, then it's OK to remove the name form from the
    // schema.
    schema.deregisterNameForm(removeNF);
    String schemaFile = removeNF.getSchemaFile();
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
   *                              should be be added.
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
            int    msgID   = MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_DCR;
            String message = getMessage(msgID, ditContentRule.getName(),
                                        existingDCR.getName(),
                                        dcr.getName());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message, msgID);
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
    if ((existingRuleForClass != null) && (existingRuleForClass != existingDCR))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_STRUCTURAL_OC_CONFLICT_FOR_ADD_DCR;
      String message = getMessage(msgID, ditContentRule.getName(),
                                  structuralClass.getNameOrOID(),
                                  existingRuleForClass.getName());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // Make sure that the new DIT content rule doesn't reference an undefined
    // structural or auxiliaryclass, or an undefined required, optional, or
    // prohibited attribute type.
    if (! schema.hasObjectClass(structuralClass.getOID()))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_STRUCTURAL_OC;
      String message = getMessage(msgID, ditContentRule.getName(),
                                  structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }

    if (structuralClass.getObjectClassType() != ObjectClassType.STRUCTURAL)
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_DCR_OC_NOT_STRUCTURAL;
      String message = getMessage(msgID, ditContentRule.getName(),
                                  structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }

    if (structuralClass.isObsolete())
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_DCR_STRUCTURAL_OC_OBSOLETE;
      String message = getMessage(msgID, ditContentRule.getName(),
                                  structuralClass.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    for (ObjectClass oc : ditContentRule.getAuxiliaryClasses())
    {
      if (! schema.hasObjectClass(oc.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_AUXILIARY_OC;
        String message = getMessage(msgID, ditContentRule.getName(),
                                    oc.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      if (oc.getObjectClassType() != ObjectClassType.AUXILIARY)
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_DCR_OC_NOT_AUXILIARY;
        String message = getMessage(msgID, ditContentRule.getName(),
                                    oc.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      if (oc.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_AUXILIARY_OC;
        String message = getMessage(msgID, ditContentRule.getName(),
                                    oc.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }

    for (AttributeType at : ditContentRule.getRequiredAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_REQUIRED_ATTR;
        String message = getMessage(msgID, ditContentRule.getName(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      else if (at.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_REQUIRED_ATTR;
        String message = getMessage(msgID, ditContentRule.getName(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }

    for (AttributeType at : ditContentRule.getOptionalAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_OPTIONAL_ATTR;
        String message = getMessage(msgID, ditContentRule.getName(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      else if (at.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_OPTIONAL_ATTR;
        String message = getMessage(msgID, ditContentRule.getName(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }

    for (AttributeType at : ditContentRule.getProhibitedAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_PROHIBITED_ATTR;
        String message = getMessage(msgID, ditContentRule.getName(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      else if (at.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_PROHIBITED_ATTR;
        String message = getMessage(msgID, ditContentRule.getName(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }


    // If there is no existing rule, then we're adding a new DIT content rule.
    // Otherwise, we're replacing an existing one.
    if (existingDCR == null)
    {
      schema.registerDITContentRule(ditContentRule, false);
      String schemaFile = ditContentRule.getSchemaFile();
      if ((schemaFile == null) || (schemaFile.length() == 0))
      {
        schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        ditContentRule.setSchemaFile(schemaFile);
      }

      modifiedSchemaFiles.add(schemaFile);
    }
    else
    {
      schema.deregisterDITContentRule(existingDCR);
      schema.registerDITContentRule(ditContentRule, false);
      schema.rebuildDependentElements(existingDCR);

      if ((ditContentRule.getSchemaFile() == null) ||
          (ditContentRule.getSchemaFile().length() == 0))
      {
        String schemaFile = existingDCR.getSchemaFile();
        if ((schemaFile == null) || (schemaFile.length() == 0))
        {
          schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        }

        ditContentRule.setSchemaFile(schemaFile);
        modifiedSchemaFiles.add(schemaFile);
      }
      else
      {
        String newSchemaFile = ditContentRule.getSchemaFile();
        String oldSchemaFile = existingDCR.getSchemaFile();
        if ((oldSchemaFile == null) || oldSchemaFile.equals(newSchemaFile))
        {
          modifiedSchemaFiles.add(newSchemaFile);
        }
        else
        {
          modifiedSchemaFiles.add(newSchemaFile);
          modifiedSchemaFiles.add(oldSchemaFile);
        }
      }
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
   * @param  modifications        The full set of modifications to be processed
   *                              against the server schema.
   * @param  currentPosition      The position of the modification currently
   *                              being performed.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to remove
   *                              the provided DIT content rule from the server
   *                              schema.
   */
  private void removeDITContentRule(DITContentRule ditContentRule,
                                    Schema schema,
                                    ArrayList<Modification> modifications,
                                    int currentPosition,
                                    Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // See if the specified DIT content rule is actually defined in the server
    // schema.  If not, then fail.
    DITContentRule removeDCR =
         schema.getDITContentRule(ditContentRule.getStructuralClass());
    if ((removeDCR == null) || (! removeDCR.equals(ditContentRule)))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_DCR;
      String message = getMessage(msgID, ditContentRule.getName());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // Since DIT content rules don't have any dependencies, then we don't need
    // to worry about the difference between a remove or a replace.  We can
    // just remove the DIT content rule now, and if it is added back later then
    // there still won't be any conflict.
    schema.deregisterDITContentRule(removeDCR);
    String schemaFile = removeDCR.getSchemaFile();
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
   *                              should be be added.
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
    for (DITStructureRule dsr : schema.getDITStructureRulesByID().values())
    {
      for (String name : ditStructureRule.getNames().keySet())
      {
        if (dsr.hasName(name))
        {
          // We really do want to use the "!=" operator here because it's
          // acceptable if we find match for the same object instance.
          if ((existingDSR != null) && (existingDSR != dsr))
          {
            int    msgID   = MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_DSR;
            String message = getMessage(msgID,
                                        ditStructureRule.getNameOrRuleID(),
                                        existingDSR.getNameOrRuleID(),
                                        dsr.getNameOrRuleID());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message, msgID);
          }
        }
      }
    }


    // Get the name form for the new DIT structure rule and see if there's
    // already an existing rule that is associated with that name form.  If
    // there is, then it will only be acceptable if it's the DIT structure rule
    // that we are replacing (in which case we really do want to use the "!="
    // operator).
    NameForm nameForm = ditStructureRule.getNameForm();
    DITStructureRule existingRuleForNameForm =
         schema.getDITStructureRule(nameForm);
    if ((existingRuleForNameForm != null) &&
        (existingRuleForNameForm != existingDSR))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_NAME_FORM_CONFLICT_FOR_ADD_DSR;
      String message = getMessage(msgID, ditStructureRule.getNameOrRuleID(),
                                  nameForm.getNameOrOID(),
                                  existingRuleForNameForm.getNameOrRuleID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // Make sure that the new DIT structure rule doesn't reference an undefined
    // name form or superior DIT structure rule.
    if (! schema.hasNameForm(nameForm.getOID()))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_DSR_UNDEFINED_NAME_FORM;
      String message = getMessage(msgID, ditStructureRule.getNameOrRuleID(),
                                  nameForm.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }
    if (nameForm.isObsolete())
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_DSR_OBSOLETE_NAME_FORM;
      String message = getMessage(msgID, ditStructureRule.getNameOrRuleID(),
                                  nameForm.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }


    // If there are any superior rules, then make sure none of them are marked
    // OBSOLETE.
    for (DITStructureRule dsr : ditStructureRule.getSuperiorRules())
    {
      if (dsr.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_DSR_OBSOLETE_SUPERIOR_RULE;
        String message = getMessage(msgID, ditStructureRule.getNameOrRuleID(),
                                    dsr.getNameOrRuleID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }


    // If there is no existing rule, then we're adding a new DIT structure rule.
    // Otherwise, we're replacing an existing one.
    if (existingDSR == null)
    {
      schema.registerDITStructureRule(ditStructureRule, false);
      String schemaFile = ditStructureRule.getSchemaFile();
      if ((schemaFile == null) || (schemaFile.length() == 0))
      {
        schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        ditStructureRule.setSchemaFile(schemaFile);
      }

      modifiedSchemaFiles.add(schemaFile);
    }
    else
    {
      schema.deregisterDITStructureRule(existingDSR);
      schema.registerDITStructureRule(ditStructureRule, false);
      schema.rebuildDependentElements(existingDSR);

      if ((ditStructureRule.getSchemaFile() == null) ||
          (ditStructureRule.getSchemaFile().length() == 0))
      {
        String schemaFile = existingDSR.getSchemaFile();
        if ((schemaFile == null) || (schemaFile.length() == 0))
        {
          schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        }

        ditStructureRule.setSchemaFile(schemaFile);
        modifiedSchemaFiles.add(schemaFile);
      }
      else
      {
        String newSchemaFile = ditStructureRule.getSchemaFile();
        String oldSchemaFile = existingDSR.getSchemaFile();
        if ((oldSchemaFile == null) || oldSchemaFile.equals(newSchemaFile))
        {
          modifiedSchemaFiles.add(newSchemaFile);
        }
        else
        {
          modifiedSchemaFiles.add(newSchemaFile);
          modifiedSchemaFiles.add(oldSchemaFile);
        }
      }
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
    if ((removeDSR == null) || (! removeDSR.equals(ditStructureRule)))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_DSR;
      String message = getMessage(msgID, ditStructureRule.getNameOrRuleID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // See if there is another modification later to add the DIT structure rule
    // back into the schema.  If so, then it's a replace and we should ignore
    // the remove because adding it back will handle the replace.
    for (int i=currentPosition+1; i < modifications.size(); i++)
    {
      Modification m = modifications.get(i);
      Attribute    a = m.getAttribute();

      if ((m.getModificationType() != ModificationType.ADD) ||
          (! a.getAttributeType().equals(ditStructureRulesType)))
      {
        continue;
      }

      for (AttributeValue v : a.getValues())
      {
        DITStructureRule dsr;
        try
        {
          dsr = DITStructureRuleSyntax.decodeDITStructureRule(
                     v.getValue(), schema, true);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, de);
          }

          int msgID = MSGID_SCHEMA_MODIFY_CANNOT_DECODE_DSR;
          String message = getMessage(msgID, v.getStringValue(),
                                      de.getErrorMessage());
          throw new DirectoryException(
                         ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                         msgID, de);
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
        int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_DSR_SUPERIOR_RULE;
        String message = getMessage(msgID, removeDSR.getNameOrRuleID(),
                                    dsr.getNameOrRuleID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
    }


    // If we've gotten here, then it's OK to remove the DIT structure rule from
    // the schema.
    schema.deregisterDITStructureRule(removeDSR);
    String schemaFile = removeDSR.getSchemaFile();
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
            int msgID = MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_MR_USE;
            String message = getMessage(msgID, matchingRuleUse.getName(),
                                        existingMRU.getName(), mru.getName());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message, msgID);
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
    if ((existingMRUForRule != null) && (existingMRUForRule != existingMRU))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_MR_CONFLICT_FOR_ADD_MR_USE;
      String message = getMessage(msgID, matchingRuleUse.getName(),
                                  matchingRule.getNameOrOID(),
                                  existingMRUForRule.getName());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }

    if (matchingRule.isObsolete())
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_MRU_OBSOLETE_MR;
      String message = getMessage(msgID, matchingRuleUse.getName(),
                                  matchingRule.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }


    // Make sure that the new matching rule use doesn't reference an undefined
    // attribute type.
    for (AttributeType at : matchingRuleUse.getAttributes())
    {
      if (! schema.hasAttributeType(at.getOID()))
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_MRU_UNDEFINED_ATTR;
        String message = getMessage(msgID, matchingRuleUse.getName(),
                                    at.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                     msgID);
      }
      else if (at.isObsolete())
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_MRU_OBSOLETE_ATTR;
        String message = getMessage(msgID, matchingRuleUse.getName(),
                                    matchingRule.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }


    // If there is no existing matching rule use, then we're adding a new one.
    // Otherwise, we're replacing an existing matching rule use.
    if (existingMRU == null)
    {
      schema.registerMatchingRuleUse(matchingRuleUse, false);
      String schemaFile = matchingRuleUse.getSchemaFile();
      if ((schemaFile == null) || (schemaFile.length() == 0))
      {
        schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        matchingRuleUse.setSchemaFile(schemaFile);
      }

      modifiedSchemaFiles.add(schemaFile);
    }
    else
    {
      schema.deregisterMatchingRuleUse(existingMRU);
      schema.registerMatchingRuleUse(matchingRuleUse, false);
      schema.rebuildDependentElements(existingMRU);

      if ((matchingRuleUse.getSchemaFile() == null) ||
          (matchingRuleUse.getSchemaFile().length() == 0))
      {
        String schemaFile = existingMRU.getSchemaFile();
        if ((schemaFile == null) || (schemaFile.length() == 0))
        {
          schemaFile = FILE_USER_SCHEMA_ELEMENTS;
        }

        matchingRuleUse.setSchemaFile(schemaFile);
        modifiedSchemaFiles.add(schemaFile);
      }
      else
      {
        String newSchemaFile = matchingRuleUse.getSchemaFile();
        String oldSchemaFile = existingMRU.getSchemaFile();
        if ((oldSchemaFile == null) || oldSchemaFile.equals(newSchemaFile))
        {
          modifiedSchemaFiles.add(newSchemaFile);
        }
        else
        {
          modifiedSchemaFiles.add(newSchemaFile);
          modifiedSchemaFiles.add(oldSchemaFile);
        }
      }
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
   * @param  modifications        The full set of modifications to be processed
   *                              against the server schema.
   * @param  currentPosition      The position of the modification currently
   *                              being performed.
   * @param  modifiedSchemaFiles  The names of the schema files containing
   *                              schema elements that have been updated as part
   *                              of the schema modification.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to remove
   *                              the provided matching rule use from the server
   *                              schema.
   */
  private void removeMatchingRuleUse(MatchingRuleUse matchingRuleUse,
                                     Schema schema,
                                     ArrayList<Modification> modifications,
                                     int currentPosition,
                                     Set<String> modifiedSchemaFiles)
          throws DirectoryException
  {
    // See if the specified DIT content rule is actually defined in the server
    // schema.  If not, then fail.
    MatchingRuleUse removeMRU =
         schema.getMatchingRuleUse(matchingRuleUse.getMatchingRule());
    if ((removeMRU == null) || (! removeMRU.equals(matchingRuleUse)))
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_MR_USE;
      String message = getMessage(msgID, matchingRuleUse.getName());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }


    // Since matching rule uses don't have any dependencies, then we don't need
    // to worry about the difference between a remove or a replace.  We can
    // just remove the DIT content rule now, and if it is added back later then
    // there still won't be any conflict.
    schema.deregisterMatchingRuleUse(removeMRU);
    String schemaFile = removeMRU.getSchemaFile();
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
    LinkedHashMap<ObjectClass,String> objectClasses =
         new LinkedHashMap<ObjectClass,String>();
    objectClasses.put(DirectoryServer.getTopObjectClass(), OC_TOP);
    objectClasses.put(DirectoryServer.getObjectClass(OC_LDAP_SUBENTRY_LC, true),
                      OC_LDAP_SUBENTRY);
    objectClasses.put(DirectoryServer.getObjectClass(OC_SUBSCHEMA, true),
                      OC_SUBSCHEMA);

    LinkedHashMap<AttributeType,List<Attribute>> userAttributes =
         new LinkedHashMap<AttributeType,List<Attribute>>();

    LinkedHashMap<AttributeType,List<Attribute>> operationalAttributes =
         new LinkedHashMap<AttributeType,List<Attribute>>();

    DN  dn  = DirectoryServer.getSchemaDN();
    RDN rdn = dn.getRDN();
    for (int i=0; i < rdn.getNumValues(); i++)
    {
      AttributeType type = rdn.getAttributeType(i);
      String        name = rdn.getAttributeName(i);

      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>(1);
      values.add(rdn.getAttributeValue(i));

      LinkedList<Attribute> attrList = new LinkedList<Attribute>();
      attrList.add(new Attribute(type, name, values));
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


    // Add all of the appropriate attribute types to the schema entry.  We need
    // to be careful of the ordering to ensure that any superior types in the
    // same file are written before the subordinate types.
    HashSet<AttributeType> addedTypes = new HashSet<AttributeType>();
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    for (AttributeType at : schema.getAttributeTypes().values())
    {
      if (schemaFile.equals(at.getSchemaFile()))
      {
        addAttrTypeToSchemaFile(schema, schemaFile, at, values, addedTypes, 0);
      }
    }

    if (! values.isEmpty())
    {
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(attributeTypesType,
                                 attributeTypesType.getPrimaryName(), values));
      schemaEntry.putAttribute(attributeTypesType, attrList);
    }


    // Add all of the appropriate objectclasses to the schema entry.  We need
    // to be careful of the ordering to ensure that any superior classes in the
    // same file are written before the subordinate classes.
    HashSet<ObjectClass> addedClasses = new HashSet<ObjectClass>();
    values = new LinkedHashSet<AttributeValue>();
    for (ObjectClass oc : schema.getObjectClasses().values())
    {
      if (schemaFile.equals(oc.getSchemaFile()))
      {
        addObjectClassToSchemaFile(schema, schemaFile, oc, values, addedClasses,
                                   0);
      }
    }

    if (! values.isEmpty())
    {
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(objectClassesType,
                                 objectClassesType.getPrimaryName(), values));
      schemaEntry.putAttribute(objectClassesType, attrList);
    }


    // Add all of the appropriate name forms to the schema entry.  Since there
    // is no hierarchical relationship between name forms, we don't need to
    // worry about ordering.
    values = new LinkedHashSet<AttributeValue>();
    for (NameForm nf : schema.getNameFormsByObjectClass().values())
    {
      if (schemaFile.equals(nf.getSchemaFile()))
      {
        values.add(new AttributeValue(nameFormsType, nf.getDefinition()));
      }
    }

    if (! values.isEmpty())
    {
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(nameFormsType,
                                 nameFormsType.getPrimaryName(), values));
      schemaEntry.putAttribute(nameFormsType, attrList);
    }


    // Add all of the appropriate DIT content rules to the schema entry.  Since
    // there is no hierarchical relationship between DIT content rules, we don't
    // need to worry about ordering.
    values = new LinkedHashSet<AttributeValue>();
    for (DITContentRule dcr : schema.getDITContentRules().values())
    {
      if (schemaFile.equals(dcr.getSchemaFile()))
      {
        values.add(new AttributeValue(ditContentRulesType,
                                      dcr.getDefinition()));
      }
    }

    if (! values.isEmpty())
    {
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(ditContentRulesType,
                                 ditContentRulesType.getPrimaryName(), values));
      schemaEntry.putAttribute(ditContentRulesType, attrList);
    }


    // Add all of the appropriate DIT structure rules to the schema entry.  We
    // need to be careful of the ordering to ensure that any superior rules in
    // the same file are written before the subordinate rules.
    HashSet<DITStructureRule> addedDSRs = new HashSet<DITStructureRule>();
    values = new LinkedHashSet<AttributeValue>();
    for (DITStructureRule dsr : schema.getDITStructureRulesByID().values())
    {
      if (schemaFile.equals(dsr.getSchemaFile()))
      {
        addDITStructureRuleToSchemaFile(schema, schemaFile, dsr, values,
                                        addedDSRs, 0);
      }
    }

    if (! values.isEmpty())
    {
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(ditStructureRulesType,
                                 ditStructureRulesType.getPrimaryName(),
                                 values));
      schemaEntry.putAttribute(ditStructureRulesType, attrList);
    }


    // Add all of the appropriate matching rule uses to the schema entry.  Since
    // there is no hierarchical relationship between matching rule uses, we
    // don't need to worry about ordering.
    values = new LinkedHashSet<AttributeValue>();
    for (MatchingRuleUse mru : schema.getMatchingRuleUses().values())
    {
      if (schemaFile.equals(mru.getSchemaFile()))
      {
        values.add(new AttributeValue(matchingRuleUsesType,
                                      mru.getDefinition()));
      }
    }

    if (! values.isEmpty())
    {
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(matchingRuleUsesType,
                                 matchingRuleUsesType.getPrimaryName(),
                                 values));
      schemaEntry.putAttribute(matchingRuleUsesType, attrList);
    }

    if (schemaFile.equals(FILE_USER_SCHEMA_ELEMENTS))
    {
      values = schema.getSynchronizationState();
      if (values != null)
      {
        ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
        attrList.add(new Attribute(synchronizationStateType,
                                   synchronizationStateType.getPrimaryName(),
                                   values));
        schemaEntry.putAttribute(synchronizationStateType, attrList);
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
                                       LinkedHashSet<AttributeValue> values,
                                       HashSet<AttributeType> addedTypes,
                                       int depth)
          throws DirectoryException
  {
    if (depth > 20)
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_CIRCULAR_REFERENCE_AT;
      String message = getMessage(msgID, attributeType.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }

    if (addedTypes.contains(attributeType))
    {
      return;
    }

    AttributeType superiorType = attributeType.getSuperiorType();
    if ((superiorType != null) &&
        schemaFile.equals(superiorType.getSchemaFile()) &&
        (! addedTypes.contains(superiorType)))
    {
      addAttrTypeToSchemaFile(schema, schemaFile, superiorType, values,
                              addedTypes, depth+1);
    }

    values.add(new AttributeValue(attributeTypesType,
                                  attributeType.getDefinition()));
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
                                          LinkedHashSet<AttributeValue> values,
                                          HashSet<ObjectClass> addedClasses,
                                          int depth)
          throws DirectoryException
  {
    if (depth > 20)
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_CIRCULAR_REFERENCE_OC;
      String message = getMessage(msgID, objectClass.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }

    if (addedClasses.contains(objectClass))
    {
      return;
    }

    ObjectClass superiorClass = objectClass.getSuperiorClass();
    if ((superiorClass != null) &&
        schemaFile.equals(superiorClass.getSchemaFile()) &&
        (! addedClasses.contains(superiorClass)))
    {
      addObjectClassToSchemaFile(schema, schemaFile, superiorClass, values,
                                 addedClasses, depth+1);
    }

    values.add(new AttributeValue(objectClassesType,
                                  objectClass.getDefinition()));
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
                    LinkedHashSet<AttributeValue> values,
                    HashSet<DITStructureRule> addedDSRs, int depth)
          throws DirectoryException
  {
    if (depth > 20)
    {
      int    msgID   = MSGID_SCHEMA_MODIFY_CIRCULAR_REFERENCE_DSR;
      String message = getMessage(msgID, ditStructureRule.getNameOrRuleID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                   msgID);
    }

    if (addedDSRs.contains(ditStructureRule))
    {
      return;
    }

    for (DITStructureRule dsr : ditStructureRule.getSuperiorRules())
    {
      if (schemaFile.equals(dsr.getSchemaFile()) && (! addedDSRs.contains(dsr)))
      {
        addDITStructureRuleToSchemaFile(schema, schemaFile, dsr, values,
                                        addedDSRs, depth+1);
      }
    }

    values.add(new AttributeValue(ditStructureRulesType,
                                  ditStructureRule.getDefinition()));
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

    File schemaDir = new File(SchemaConfigManager.getSchemaDirectoryPath());

    for (String name : tempSchemaFiles.keySet())
    {
      installedFileList.add(new File(schemaDir, name));
      tempFileList.add(tempSchemaFiles.get(name));
      origFileList.add(new File(schemaDir, name + ".orig"));
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
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      boolean allCleaned = true;
      for (File f : origFileList)
      {
        try
        {
          if (f.exists())
          {
            if (! f.delete())
            {
              allCleaned = false;
            }
          }
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e2);
          }

          allCleaned = false;
        }
      }

      if (allCleaned)
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_CANNOT_WRITE_ORIG_FILES_CLEANED;
        String message = getMessage(msgID, getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
      else
      {
        int msgID = MSGID_SCHEMA_MODIFY_CANNOT_WRITE_ORIG_FILES_NOT_CLEANED;
        String message = getMessage(msgID, getExceptionMessage(e));

        DirectoryServer.sendAlertNotification(this,
                             ALERT_TYPE_CANNOT_COPY_SCHEMA_FILES, msgID,
                             message);

        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
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
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      for (File f : installedFileList)
      {
        try
        {
          if (f.exists())
          {
            f.delete();
          }
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e2);
          }
        }
      }

      boolean allRestored = true;
      for (int i=0; i < installedFileList.size(); i++)
      {
        File installedFile = installedFileList.get(i);
        File origFile      = origFileList.get(i);

        try
        {
          if (origFile.exists())
          {
            if (! origFile.renameTo(installedFile))
            {
              allRestored = false;
            }
          }
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e2);
          }

          allRestored = false;
        }
      }

      if (allRestored)
      {
        int    msgID   = MSGID_SCHEMA_MODIFY_CANNOT_WRITE_NEW_FILES_RESTORED;
        String message = getMessage(msgID, getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
      else
      {
        int msgID = MSGID_SCHEMA_MODIFY_CANNOT_WRITE_NEW_FILES_NOT_RESTORED;
        String message = getMessage(msgID, getExceptionMessage(e));

        DirectoryServer.sendAlertNotification(this,
                             ALERT_TYPE_CANNOT_WRITE_NEW_SCHEMA_FILES, msgID,
                             message);

        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
    }


    // At this point, we're committed to the schema change, so we can't throw
    // any more exceptions, but all we have left is to clean up the original and
    // temporary files.
    for (File f : origFileList)
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
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    for (File f : tempFileList)
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
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
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
      if (inputStream != null)
      {
        try
        {
          inputStream.close();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }

      if (outputStream != null)
      {
        outputStream.close();
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
    if ((tempSchemaFiles == null) || tempSchemaFiles.isEmpty())
    {
      return;
    }

    for (File f : tempSchemaFiles.values())
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
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }
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
    int    msgID   = MSGID_SCHEMA_MODIFY_DN_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(currentDN));
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
      int    msgID   = MSGID_SCHEMA_INVALID_BASE;
      String message = getMessage(msgID, searchOperation.getConnectionID(),
                                  searchOperation.getOperationID(),
                                  String.valueOf(baseDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID,
                                   matchedDN, null);
    }


    // If it's a onelevel or subordinate subtree search, then we will never
    // match anything since there isn't anything below the schema.
    SearchScope scope = searchOperation.getScope();
    if ((scope == SearchScope.SINGLE_LEVEL) ||
        (scope == SearchScope.SUBORDINATE_SUBTREE))
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
    // We will only export the DSE entry itself.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void exportLDIF(ConfigEntry configEntry, DN[] baseDNs,
                         LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    try
    {
      initializeCommon(configEntry, baseDNs);
    }
    catch (ConfigException e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessage(), e.getMessageID());
    }

    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_SCHEMA_UNABLE_TO_CREATE_LDIF_WRITER;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }


    // Write the root schema entry to it.  Make sure to close the LDIF
    // writer when we're done.
    try
    {
      ldifWriter.writeEntry(getSchemaEntry(baseDNs[0]));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_SCHEMA_UNABLE_TO_EXPORT_BASE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }
    finally
    {
      try
      {
        ldifWriter.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }
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
    // This backend does not support LDIF imports.
    // FIXME -- Should we support them?
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void importLDIF(ConfigEntry configEntry, DN[] baseDNs,
                         LDIFImportConfig importConfig)
         throws DirectoryException
  {
    // This backend does not support LDIF imports.
    int    msgID   = MSGID_SCHEMA_IMPORT_NOT_SUPPORTED;
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
    // We do support an online backup mechanism for the schema.
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
    // We should support online backup for the schema in any form.  This
    // implementation does not support incremental backups, but in this case
    // even if we're asked to do an incremental we'll just do a full backup
    // instead.  So the answer to this should always be "true".
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void createBackup(ConfigEntry configEntry, BackupConfig backupConfig)
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
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_SCHEMA_BACKUP_CANNOT_GET_MAC;
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
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_SCHEMA_BACKUP_CANNOT_GET_DIGEST;
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
      filename = SCHEMA_BACKUP_BASE_FILENAME + backupID;
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_SCHEMA_BACKUP_CANNOT_CREATE_ARCHIVE_FILE;
      String message = getMessage(msgID, String.valueOf(filename),
                                  backupDirectory.getPath(),
                                  getExceptionMessage(e));
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
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_SCHEMA_BACKUP_CANNOT_GET_CIPHER;
        String message = getMessage(msgID, cipherAlgorithm,
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }

      outputStream = new CipherOutputStream(outputStream, cipher);
    }


    // Wrap the file output stream in a zip output stream.
    ZipOutputStream zipStream = new ZipOutputStream(outputStream);

    int    msgID   = MSGID_SCHEMA_BACKUP_ZIP_COMMENT;
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


    // Get the path to the directory in which the schema files reside and
    // then get a list of all the files in that directory.
    String schemaDirPath = SchemaConfigManager.getSchemaDirectoryPath();
    File[] schemaFiles;
    try
    {
      File schemaDir = new File(schemaDirPath);
      schemaFiles = schemaDir.listFiles();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID   = MSGID_SCHEMA_BACKUP_CANNOT_LIST_SCHEMA_FILES;
      message = getMessage(msgID, schemaDirPath, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }


    // Iterate through the schema files and write them to the zip stream.  If
    // we're using a hash or MAC, then calculate that as well.
    byte[] buffer = new byte[8192];
    for (File schemaFile : schemaFiles)
    {
      if (! schemaFile.isFile())
      {
        // If there are any non-file items in the directory (e.g., one or more
        // subdirectories), then we'll skip them.
        continue;
      }

      String baseName = schemaFile.getName();


      // We'll put the name in the hash, too.
      if (hash)
      {
        if (signHash)
        {
          mac.update(getBytes(baseName));
        }
        else
        {
          digest.update(getBytes(baseName));
        }
      }

      InputStream inputStream = null;
      try
      {
        ZipEntry zipEntry = new ZipEntry(baseName);
        zipStream.putNextEntry(zipEntry);

        inputStream = new FileInputStream(schemaFile);
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

        zipStream.closeEntry();
        inputStream.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        try
        {
          inputStream.close();
        } catch (Exception e2) {}

        try
        {
          zipStream.close();
        } catch (Exception e2) {}

        msgID   = MSGID_SCHEMA_BACKUP_CANNOT_BACKUP_SCHEMA_FILE;
        message = getMessage(msgID, baseName, stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID   = MSGID_SCHEMA_BACKUP_CANNOT_CLOSE_ZIP_STREAM;
      message = getMessage(msgID, filename, backupDirectory.getPath(),
                           stackTraceToSingleLineString(e));
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SCHEMA_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR;
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
    // This backend does not provide a backup/restore mechanism.
    int    msgID   = MSGID_SCHEMA_BACKUP_AND_RESTORE_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
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
  public void restoreBackup(ConfigEntry configEntry,
                            RestoreConfig restoreConfig)
         throws DirectoryException
  {
    // First, make sure that the requested backup exists.
    BackupDirectory backupDirectory = restoreConfig.getBackupDirectory();
    String          backupPath      = backupDirectory.getPath();
    String          backupID        = restoreConfig.getBackupID();
    BackupInfo      backupInfo      = backupDirectory.getBackupInfo(backupID);
    if (backupInfo == null)
    {
      int    msgID   = MSGID_SCHEMA_RESTORE_NO_SUCH_BACKUP;
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
      int    msgID   = MSGID_SCHEMA_RESTORE_NO_BACKUP_FILE;
      String message = getMessage(msgID, backupID, backupPath);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }

    File backupFile = new File(backupPath + File.separator + backupFilename);
    try
    {
      if (! backupFile.exists())
      {
        int    msgID   = MSGID_SCHEMA_RESTORE_NO_SUCH_FILE;
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
      int    msgID   = MSGID_SCHEMA_RESTORE_CANNOT_CHECK_FOR_ARCHIVE;
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
        int    msgID   = MSGID_SCHEMA_RESTORE_UNKNOWN_DIGEST;
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
        int    msgID   = MSGID_SCHEMA_RESTORE_CANNOT_GET_DIGEST;
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
        int    msgID   = MSGID_SCHEMA_RESTORE_UNKNOWN_MAC;
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
        int    msgID   = MSGID_SCHEMA_RESTORE_CANNOT_GET_MAC;
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
      int    msgID   = MSGID_SCHEMA_RESTORE_CANNOT_OPEN_BACKUP_FILE;
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
        int    msgID   = MSGID_SCHEMA_RESTORE_UNKNOWN_CIPHER;
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
        int    msgID   = MSGID_SCHEMA_RESTORE_CANNOT_GET_CIPHER;
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
    // try to verify the archive.  If we are not going to verify only, then
    // move the current schema directory out of the way so we can keep it around
    // to restore if a problem occurs.
    String schemaDirPath   = SchemaConfigManager.getSchemaDirectoryPath();
    File   schemaDir       = new File(schemaDirPath);
    String backupDirPath   = null;
    File   schemaBackupDir = null;
    boolean verifyOnly = restoreConfig.verifyOnly();
    if (! verifyOnly)
    {
      // Rename the current schema directory if it exists.
      try
      {
        if (schemaDir.exists())
        {
          String schemaBackupDirPath = schemaDirPath + ".save";
          backupDirPath = schemaBackupDirPath;
          schemaBackupDir = new File(backupDirPath);
          if (schemaBackupDir.exists())
          {
            int i=2;
            while (true)
            {
              backupDirPath = schemaBackupDirPath + i;
              schemaBackupDir = new File(backupDirPath);
              if (schemaBackupDir.exists())
              {
                i++;
              }
              else
              {
                break;
              }
            }
          }

          schemaDir.renameTo(schemaBackupDir);
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_SCHEMA_RESTORE_CANNOT_RENAME_CURRENT_DIRECTORY;
        String message = getMessage(msgID, backupID, schemaDirPath,
                                    String.valueOf(backupDirPath),
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }


      // Create a new directory to hold the restored schema files.
      try
      {
        schemaDir.mkdirs();
      }
      catch (Exception e)
      {
        // Try to restore the previous schema directory if possible.  This will
        // probably fail in this case, but try anyway.
        if (schemaBackupDir != null)
        {
          try
          {
            schemaBackupDir.renameTo(schemaDir);
            int    msgID   = MSGID_SCHEMA_RESTORE_RESTORED_OLD_SCHEMA;
            String message = getMessage(msgID, schemaDirPath);
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                     msgID);
          }
          catch (Exception e2)
          {
            int msgID = MSGID_SCHEMA_RESTORE_CANNOT_RESTORE_OLD_SCHEMA;
            String message = getMessage(msgID, schemaBackupDir.getPath());
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
          }
        }


        int    msgID   = MSGID_SCHEMA_RESTORE_CANNOT_CREATE_SCHEMA_DIRECTORY;
        String message = getMessage(msgID, backupID, schemaDirPath,
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
    }


    // Read through the archive file an entry at a time.  For each entry, update
    // the digest or MAC if necessary, and if we're actually doing the restore,
    // then write the files out into the schema directory.
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
        // Tell the user where the previous schema was archived.
        if (schemaBackupDir != null)
        {
          int    msgID   = MSGID_SCHEMA_RESTORE_OLD_SCHEMA_SAVED;
          String message = getMessage(msgID, schemaBackupDir.getPath());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                   msgID);
        }

        int    msgID   = MSGID_SCHEMA_RESTORE_CANNOT_GET_ZIP_ENTRY;
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
        String filePath = schemaDirPath + File.separator + fileName;
        try
        {
          outputStream = new FileOutputStream(filePath);
        }
        catch (Exception e)
        {
          // Tell the user where the previous schema was archived.
          if (schemaBackupDir != null)
          {
            int    msgID   = MSGID_SCHEMA_RESTORE_OLD_SCHEMA_SAVED;
            String message = getMessage(msgID, schemaBackupDir.getPath());
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                     msgID);
          }

          int    msgID   = MSGID_SCHEMA_RESTORE_CANNOT_CREATE_FILE;
          String message = getMessage(msgID, backupID, filePath,
                                      stackTraceToSingleLineString(e));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), message,
                         msgID, e);
        }
      }


      // Read the contents of the file and update the digest or MAC as
      // necessary.  If we're actually restoring it, then write it into the
      // new schema directory.
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
        // Tell the user where the previous schema was archived.
        if (schemaBackupDir != null)
        {
          int    msgID   = MSGID_SCHEMA_RESTORE_OLD_SCHEMA_SAVED;
          String message = getMessage(msgID, schemaBackupDir.getPath());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                   msgID);
        }

        int msgID = MSGID_SCHEMA_RESTORE_CANNOT_PROCESS_ARCHIVE_FILE;
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
      int    msgID   = MSGID_SCHEMA_RESTORE_ERROR_ON_ZIP_STREAM_CLOSE;
      String message = getMessage(msgID, backupID, backupFile.getPath(),
                                  stackTraceToSingleLineString(e));
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
        int    msgID = MSGID_SCHEMA_RESTORE_UNSIGNED_HASH_VALID;
        String message = getMessage(msgID);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                 msgID);
      }
      else
      {
        // Tell the user where the previous schema was archived.
        if (schemaBackupDir != null)
        {
          int    msgID   = MSGID_SCHEMA_RESTORE_OLD_SCHEMA_SAVED;
          String message = getMessage(msgID, schemaBackupDir.getPath());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                   msgID);
        }

        int    msgID = MSGID_SCHEMA_RESTORE_UNSIGNED_HASH_INVALID;
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
        int    msgID = MSGID_SCHEMA_RESTORE_SIGNED_HASH_VALID;
        String message = getMessage(msgID);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                 msgID);
      }
      else
      {
        // Tell the user where the previous schema was archived.
        if (schemaBackupDir != null)
        {
          int    msgID   = MSGID_SCHEMA_RESTORE_OLD_SCHEMA_SAVED;
          String message = getMessage(msgID, schemaBackupDir.getPath());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                   msgID);
        }

        int    msgID = MSGID_SCHEMA_RESTORE_SIGNED_HASH_INVALID;
        String message = getMessage(msgID);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
      }
    }


    // If we are just verifying the archive, then we're done.
    if (verifyOnly)
    {
      int    msgID   = MSGID_SCHEMA_RESTORE_VERIFY_SUCCESSFUL;
      String message = getMessage(msgID, backupID, backupPath);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
               msgID);
      return;
    }


    // If we've gotten here, then the archive was restored successfully.  Get
    // rid of the temporary copy we made of the previous schema directory and
    // exit.
    if (schemaBackupDir != null)
    {
      recursiveDelete(schemaBackupDir);
    }

    int    msgID   = MSGID_SCHEMA_RESTORE_SUCCESSFUL;
    String message = getMessage(msgID, backupID, backupPath);
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
             msgID);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
       SchemaBackendCfg configEntry,
       List<String> unacceptableReasons)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
       SchemaBackendCfg backendCfg)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Check to see if we should apply a new set of base DNs.
    Set<DN> newBaseDNs;
    try
    {
      newBaseDNs = backendCfg.getSchemaEntryDN();
      if (newBaseDNs.isEmpty())
      {
        newBaseDNs.add(DN.decode(DN_DEFAULT_SCHEMA_ROOT));
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_SCHEMA_CANNOT_DETERMINE_BASE_DN;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              getExceptionMessage(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
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
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      // Get an array containing the new base DNs to use.
      DN[] dnArray = new DN[newBaseDNs.size()];
      newBaseDNs.toArray(dnArray);


      // Determine the set of DNs to add and delete.  When this is done, the
      // deleteBaseDNs will contain the set of DNs that should no longer be used
      // and should be deregistered from the server, and the newBaseDNs set will
      // just contain the set of DNs to add.
      HashSet<DN> deleteBaseDNs = new HashSet<DN>(baseDNs.length);
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
          DirectoryServer.deregisterBaseDN(dn, false);
          int msgID = MSGID_SCHEMA_DEREGISTERED_BASE_DN;
          messages.add(getMessage(msgID, String.valueOf(dn)));
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_SCHEMA_CANNOT_DEREGISTER_BASE_DN;
          messages.add(getMessage(msgID, String.valueOf(dn),
                                  getExceptionMessage(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }

      baseDNs = dnArray;
      for (DN dn : newBaseDNs)
      {
        try
        {
          DirectoryServer.registerBaseDN(dn, this, true, false);
          int msgID = MSGID_SCHEMA_REGISTERED_BASE_DN;
          messages.add(getMessage(msgID, String.valueOf(dn)));
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_SCHEMA_CANNOT_REGISTER_BASE_DN;
          messages.add(getMessage(msgID, String.valueOf(dn),
                                  getExceptionMessage(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }


      showAllAttributes = newShowAllAttributes;


      userDefinedAttributes = newUserAttrs;
      int msgID = MSGID_SCHEMA_USING_NEW_USER_ATTRS;
      String message = getMessage(msgID);
      messages.add(message);
    }


    currentConfig = backendCfg;
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
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



  /**
   * Retrieves the DN of the configuration entry with which this alert generator
   * is associated.
   *
   * @return  The DN of the configuration entry with which this alert generator
   *          is associated.
   */
  public DN getComponentEntryDN()
  {
    return configEntryDN;
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

    alerts.put(ALERT_TYPE_CANNOT_COPY_SCHEMA_FILES,
               ALERT_DESCRIPTION_CANNOT_COPY_SCHEMA_FILES);
    alerts.put(ALERT_TYPE_CANNOT_WRITE_NEW_SCHEMA_FILES,
               ALERT_DESCRIPTION_CANNOT_WRITE_NEW_SCHEMA_FILES);

    return alerts;
  }



  private static SchemaBackendCfg getSchemaBackendCfg(ConfigEntry configEntry)
      throws ConfigException {
    return BackendConfigManager.getConfiguration(
         SchemaBackendCfgDefn.getInstance(), configEntry);
  }
}

