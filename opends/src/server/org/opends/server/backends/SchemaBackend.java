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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;

import org.opends.server.api.Backend;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.LDIFWriter;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.BackendMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a backend to hold the Directory Server schema information.
 * It is a kind of meta-backend in that it doesn't actually hold any data but
 * rather dynamically generates the schema entry whenever it is requested.
 */
public class SchemaBackend
       extends Backend
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.SchemaBackend";



  // The set of user-defined attributes that will be included in the schema
  // entry.
  private ArrayList<Attribute> userDefinedAttributes;

  // The attribute type that will be used to include the defined attribute
  // types.
  private AttributeType attributeTypesType;

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

  // The attribute type that will be used to include the defined object classes.
  private AttributeType objectClassesType;

  // The attribute type that will be used to include the defined name forms.
  private AttributeType nameFormsType;

  // Indicates whether the attributes of the schema entry should always be
  // treated as user attributes even if they are defined as operational.
  private boolean showAllAttributes;

  // The set of objectclasses that will be used in the schema entry.
  private HashMap<ObjectClass,String> schemaObjectClasses;

  // The DN of the configuration entry for this backend.
  private DN configEntryDN;

  // The set of base DNs for this backend.
  private DN[] baseDNs;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;



  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public SchemaBackend()
  {
    super();

    assert debugConstructor(CLASS_NAME);


    // Perform all initialization in initializeBackend.
  }



  /**
   * Initializes this backend based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this backend.
   * @param  baseDNs      The set of base DNs that have been configured for this
   *                      backend.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeBackend(ConfigEntry configEntry, DN[] baseDNs)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeBackend",
                      String.valueOf(configEntry));


    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (configEntry == null)
    {
      int    msgID   = MSGID_SCHEMA_CONFIG_ENTRY_NULL;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }

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
    nameFormsType = DirectoryServer.getAttributeType(ATTR_NAME_FORMS, true);


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
    showAllAttributes = DEFAULT_SCHEMA_SHOW_ALL_ATTRIBUTES;
    int msgID = MSGID_SCHEMA_DESCRIPTION_SHOW_ALL_ATTRIBUTES;
    BooleanConfigAttribute showAllStub =
         new BooleanConfigAttribute(ATTR_SCHEMA_SHOW_ALL_ATTRIBUTES,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute showAllAttr =
           (BooleanConfigAttribute) configEntry.getConfigAttribute(showAllStub);
      if (showAllAttr != null)
      {
        showAllAttributes = showAllAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_SCHEMA_CANNOT_DETERMINE_SHOW_ALL;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }


    // Register each of the suffixes with the Directory Server.  Also, register
    // the first one as the schema base.
    this.baseDNs = baseDNs;
    DirectoryServer.setSchemaDN(baseDNs[0]);
    for (int i=0; i < baseDNs.length; i++)
    {
      try
      {
        DirectoryServer.registerBaseDN(baseDNs[i], this, true, false);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackend", e);

        msgID = MSGID_BACKEND_CANNOT_REGISTER_BASEDN;
        String message = getMessage(msgID, baseDNs[i].toString(),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }
    }


    // Construct the set of objectclasses to include in the schema entry.
    schemaObjectClasses = new LinkedHashMap<ObjectClass,String>(3);
    schemaObjectClasses.put(DirectoryServer.getTopObjectClass(), OC_TOP);

    ObjectClass subentryOC =
         DirectoryServer.getObjectClass(OC_LDAP_SUBENTRY_LC);
    if (subentryOC == null)
    {
      subentryOC = DirectoryServer.getDefaultObjectClass(OC_LDAP_SUBENTRY);
    }
    schemaObjectClasses.put(subentryOC, OC_LDAP_SUBENTRY);

    ObjectClass subschemaOC = DirectoryServer.getObjectClass(OC_SUBSCHEMA);
    if (subschemaOC == null)
    {
      subschemaOC = DirectoryServer.getDefaultObjectClass(OC_SUBSCHEMA);
    }
    schemaObjectClasses.put(subschemaOC, OC_SUBSCHEMA);


    // Define an empty sets for the supported controls and features.
    supportedControls = new HashSet<String>(0);
    supportedFeatures = new HashSet<String>(0);


    // Register with the Directory Server as a configurable component.
    DirectoryServer.registerConfigurableComponent(this);
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
    assert debugEnter(CLASS_NAME, "finalizeBackend");

    DirectoryServer.deregisterConfigurableComponent(this);

    for (DN baseDN : baseDNs)
    {
      try
      {
        DirectoryServer.deregisterBaseDN(baseDN, false);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "finalizeBackend", e);
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
    assert debugEnter(CLASS_NAME, "isConfigAttribute",
                      String.valueOf(attribute));

    AttributeType attrType = attribute.getAttributeType();
    if (attrType.hasName(ATTR_SCHEMA_ENTRY_DN.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_ENABLED.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_CLASS.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_ID.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_BASE_DN.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_WRITABILITY_MODE.toLowerCase()) ||
        attrType.hasName(ATTR_SCHEMA_SHOW_ALL_ATTRIBUTES.toLowerCase()) ||
        attrType.hasName(ATTR_COMMON_NAME))
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
    assert debugEnter(CLASS_NAME, "getBaseDNs");

    return baseDNs;
  }



  /**
   * {@inheritDoc}
   */
  public long getEntryCount()
  {
    assert debugEnter(CLASS_NAME, "getEntryCount");

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
    assert debugEnter(CLASS_NAME, "isLocal");

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
    assert debugEnter(CLASS_NAME, "getEntry", String.valueOf(entryDN));


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
    assert debugEnter(CLASS_NAME, "getSchemaEntry", String.valueOf(entryDN));

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
    return new Entry(entryDN, schemaObjectClasses, userAttrs, operationalAttrs);
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
    assert debugEnter(CLASS_NAME, "entryExists", String.valueOf(entryDN));


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
    assert debugEnter(CLASS_NAME, "addEntry", String.valueOf(entry),
                      String.valueOf(addOperation));

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
    assert debugEnter(CLASS_NAME, "deleteEntry", String.valueOf(entryDN),
                      String.valueOf(deleteOperation));

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
    assert debugEnter(CLASS_NAME, "replaceEntry", String.valueOf(entry),
                      String.valueOf(modifyOperation));

    // FIXME -- We need to allow this.
    int    msgID   = MSGID_SCHEMA_MODIFY_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(entry.getDN()),
                                String.valueOf(configEntryDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
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
    assert debugEnter(CLASS_NAME, "renameEntry", String.valueOf(currentDN),
                      String.valueOf(entry), String.valueOf(modifyDNOperation));

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
    assert debugEnter(CLASS_NAME, "search", String.valueOf(searchOperation));

    DN baseDN = searchOperation.getBaseDN();

    boolean found = false;
    DN[] dnArray = baseDNs;
    for (DN dn : dnArray)
    {
      if (dn.equals(baseDN))
      {
        found = true;
        break;
      }
    }

    if (! found)
    {
      int    msgID   = MSGID_SCHEMA_INVALID_BASE;
      String message = getMessage(msgID, searchOperation.getConnectionID(),
                                  searchOperation.getOperationID(),
                                  String.valueOf(baseDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
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
    assert debugEnter(CLASS_NAME, "getSupportedControls");

    return supportedControls;
  }



  /**
   * Retrieves the OIDs of the features that may be supported by this backend.
   *
   * @return  The OIDs of the features that may be supported by this backend.
   */
  public HashSet<String> getSupportedFeatures()
  {
    assert debugEnter(CLASS_NAME, "getSupportedFeatures");

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
    assert debugEnter(CLASS_NAME, "supportsLDIFExport");

    // We will only export the DSE entry itself.
    return true;
  }



  /**
   * Exports the contents of this backend to LDIF.  This method should only be
   * called if <CODE>supportsLDIFExport</CODE> returns <CODE>true</CODE>.  Note
   * that the server will not explicitly initialize this backend before calling
   * this method.
   *
   * @param  configEntry   The configuration entry for this backend.
   * @param  baseDNs       The set of base DNs configured for this backend.
   * @param  exportConfig  The configuration to use when performing the export.
   *
   * @throws  DirectoryException  If a problem occurs while performing the LDIF
   *                              export.
   */
  public void exportLDIF(ConfigEntry configEntry, DN[] baseDNs,
                         LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "exportLDIF", String.valueOf(exportConfig));


    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "exportLDIF", e);

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
      assert debugException(CLASS_NAME, "exportLDIF", e);

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
        assert debugException(CLASS_NAME, "exportLDIF", e);
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
    assert debugEnter(CLASS_NAME, "supportsLDIFImport");

    // This backend does not support LDIF imports.
    // FIXME -- Should we support them?
    return false;
  }



  /**
   * Imports information from an LDIF file into this backend.  This method
   * should only be called if <CODE>supportsLDIFImport</CODE> returns
   * <CODE>true</CODE>.  Note that the server will not explicitly initialize
   * this backend before calling this method.
   *
   * @param  configEntry   The configuration entry for this backend.
   * @param  baseDNs       The set of base DNs configured for this backend.
   * @param  importConfig  The configuration to use when performing the import.
   *
   * @throws  DirectoryException  If a problem occurs while performing the LDIF
   *                              import.
   */
  public void importLDIF(ConfigEntry configEntry, DN[] baseDNs,
                         LDIFImportConfig importConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "importLDIF", String.valueOf(importConfig));


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
    assert debugEnter(CLASS_NAME, "supportsBackup");

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
    assert debugEnter(CLASS_NAME, "supportsBackup");


    // We should support online backup for the schema in any form.  This
    // implementation does not support incremental backups, but in this case
    // even if we're asked to do an incremental we'll just do a full backup
    // instead.  So the answer to this should always be "true".
    return true;
  }



  /**
   * Creates a backup of the contents of this backend in a form that may be
   * restored at a later date if necessary.  This method should only be called
   * if <CODE>supportsBackup</CODE> returns <CODE>true</CODE>.  Note that the
   * server will not explicitly initialize this backend before calling this
   * method.
   *
   * @param  configEntry   The configuration entry for this backend.
   * @param  backupConfig  The configuration to use when performing the backup.
   *
   * @throws  DirectoryException  If a problem occurs while performing the
   *                              backup.
   */
  public void createBackup(ConfigEntry configEntry, BackupConfig backupConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "createBackup",
                      String.valueOf(configEntry),
                      String.valueOf(backupConfig));


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
          assert debugException(CLASS_NAME, "createBackup", e);

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
          assert debugException(CLASS_NAME, "createBackup", e);

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
      assert debugException(CLASS_NAME, "createBackup", e);

      int    msgID   = MSGID_SCHEMA_BACKUP_CANNOT_CREATE_ARCHIVE_FILE;
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
        assert debugException(CLASS_NAME, "createBackup", e);

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
    String schemaDirPath = DirectoryServer.getServerRoot() + File.separator +
                           PATH_SCHEMA_DIR;
    File[] schemaFiles;
    try
    {
      File schemaDir = new File(schemaDirPath);
      schemaFiles = schemaDir.listFiles();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "createBackup", e);

      msgID   = MSGID_SCHEMA_BACKUP_CANNOT_LIST_SCHEMA_FILES;
      message = getMessage(msgID, schemaDirPath,
                           stackTraceToSingleLineString(e));
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
        assert debugException(CLASS_NAME, "createBackup", e);

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
      assert debugException(CLASS_NAME, "createBackup", e);

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
      assert debugException(CLASS_NAME, "createBackup", e);

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
    assert debugEnter(CLASS_NAME, "removeBackup",
                      String.valueOf(backupDirectory),
                      String.valueOf(backupID));


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
    assert debugEnter(CLASS_NAME, "supportsRestore");


    // We will provide a restore, but only for offline operations.
    return true;
  }



  /**
   * Restores a backup of the contents of this backend.  This method should only
   * be called if <CODE>supportsRestore</CODE> returns <CODE>true</CODE>.  Note
   * that the server will not explicitly initialize this backend before calling
   * this method.
   *
   * @param  configEntry    The configuration entry for this backend.
   * @param  restoreConfig  The configuration to use when performing the
   *                        restore.
   *
   * @throws  DirectoryException  If a problem occurs while performing the
   *                              restore.
   */
  public void restoreBackup(ConfigEntry configEntry,
                            RestoreConfig restoreConfig)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "restoreBackup", String.valueOf(configEntry),
                      String.valueOf(restoreConfig));


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
    String schemaDirPath   = DirectoryServer.getServerRoot() + File.separator +
                             PATH_SCHEMA_DIR;
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
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");

    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();

    String description = getMessage(MSGID_SCHEMA_DESCRIPTION_ENTRY_DN);

    ArrayList<DN> values = new ArrayList<DN>(baseDNs.length);
    for (DN baseDN : baseDNs)
    {
      values.add(baseDN);
    }

    attrList.add(new DNConfigAttribute(ATTR_SCHEMA_ENTRY_DN, description,
                                       false, true, false, values));


    description = getMessage(MSGID_SCHEMA_DESCRIPTION_SHOW_ALL_ATTRIBUTES);
    attrList.add(new BooleanConfigAttribute(ATTR_SCHEMA_SHOW_ALL_ATTRIBUTES,
                                            description, false,
                                            showAllAttributes));


    return attrList;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "java.util.List<String>");


    boolean configIsAcceptable = true;


    String description = getMessage(MSGID_SCHEMA_DESCRIPTION_ENTRY_DN);
    DNConfigAttribute baseDNStub =
         new DNConfigAttribute(ATTR_SCHEMA_ENTRY_DN, description, false, true,
                               false);
    try
    {
      // We don't care what the DNs are as long as we can parse them.
      DNConfigAttribute baseDNAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeBackend", e);

      int msgID = MSGID_SCHEMA_CANNOT_DETERMINE_BASE_DN;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      configIsAcceptable = false;
    }


    description = getMessage(MSGID_SCHEMA_DESCRIPTION_SHOW_ALL_ATTRIBUTES);
    BooleanConfigAttribute showAllStub =
         new BooleanConfigAttribute(ATTR_SCHEMA_SHOW_ALL_ATTRIBUTES,
                                    description, false);
    try
    {
      // We don't care what the value is as long as we can parse it.
      BooleanConfigAttribute showAllAttr =
           (BooleanConfigAttribute) configEntry.getConfigAttribute(showAllStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeBackend", e);

      int msgID = MSGID_SCHEMA_CANNOT_DETERMINE_SHOW_ALL;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      configIsAcceptable = false;
    }


    return configIsAcceptable;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));


    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Check to see if we should apply a new set of base DNs.
    HashSet<DN> newBaseDNs;
    int msgID = MSGID_SCHEMA_DESCRIPTION_ENTRY_DN;
    DNConfigAttribute baseDNStub =
         new DNConfigAttribute(ATTR_SCHEMA_ENTRY_DN, getMessage(msgID), false,
                               true, false);
    try
    {
      DNConfigAttribute baseDNAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
      if (baseDNAttr == null)
      {
        newBaseDNs = new HashSet<DN>(1);
        newBaseDNs.add(DN.decode(DN_DEFAULT_SCHEMA_ROOT));
      }
      else
      {
        List<DN> newDNList = baseDNAttr.activeValues();
        if ((newDNList == null) || newDNList.isEmpty())
        {
          newBaseDNs = new HashSet<DN>(1);
          newBaseDNs.add(DN.decode(DN_DEFAULT_SCHEMA_ROOT));
        }
        else
        {
          newBaseDNs = new HashSet<DN>(newDNList);
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_SCHEMA_CANNOT_DETERMINE_BASE_DN;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      newBaseDNs = null;
    }


    // Check to see if we should change the behavior regarding whether to show
    // all schema attributes.
    boolean newShowAllAttributes = DEFAULT_SCHEMA_SHOW_ALL_ATTRIBUTES;
    msgID = MSGID_SCHEMA_DESCRIPTION_SHOW_ALL_ATTRIBUTES;
    BooleanConfigAttribute showAllStub =
         new BooleanConfigAttribute(ATTR_SCHEMA_SHOW_ALL_ATTRIBUTES,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute showAllAttr =
           (BooleanConfigAttribute) configEntry.getConfigAttribute(showAllStub);
      if (showAllAttr != null)
      {
        newShowAllAttributes = showAllAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_SCHEMA_CANNOT_DETERMINE_SHOW_ALL;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      newBaseDNs = null;
    }


    // Check to see if there is a new set of user-defined attributes.
    ArrayList<Attribute> newUserAttrs = new ArrayList<Attribute>();
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
          if (detailedResults)
          {
            msgID = MSGID_SCHEMA_DEREGISTERED_BASE_DN;
            messages.add(getMessage(msgID, String.valueOf(dn)));
          }
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "applyNewConfiguration", e);

          msgID = MSGID_SCHEMA_CANNOT_DEREGISTER_BASE_DN;
          messages.add(getMessage(msgID, String.valueOf(dn),
                                  stackTraceToSingleLineString(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }

      baseDNs = dnArray;
      for (DN dn : newBaseDNs)
      {
        try
        {
          DirectoryServer.registerBaseDN(dn, this, true, false);
          if (detailedResults)
          {
            msgID = MSGID_SCHEMA_REGISTERED_BASE_DN;
            messages.add(getMessage(msgID, String.valueOf(dn)));
          }
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "applyNewConfiguration", e);

          msgID = MSGID_SCHEMA_CANNOT_REGISTER_BASE_DN;
          messages.add(getMessage(msgID, String.valueOf(dn),
                                  stackTraceToSingleLineString(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }


      showAllAttributes = newShowAllAttributes;


      userDefinedAttributes = newUserAttrs;
      if (detailedResults)
      {
        msgID = MSGID_SCHEMA_USING_NEW_USER_ATTRS;
        String message = getMessage(msgID);
        messages.add(message);
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

