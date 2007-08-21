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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.plugins;

import org.opends.server.admin.std.server.UniqueAttributePluginCfg;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.config.ConfigException;
import org.opends.server.types.*;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.types.operation.PreOperationOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.messages.Message;
import static org.opends.messages.PluginMessages.*;

import java.util.*;

/**
 * This class implements a Directory Server plugin that performs attribute
 * uniqueness checking on the add, modify and modifyDN operations. If the
 * operation is eligible for checking based on a set of configuration criteria,
 * then the operation's attribute values will be checked, using that
 * configuration criteria, for uniqueness against the server's values to
 * determine if the operation can proceed.
 */
public class UniqueAttributePlugin
        extends DirectoryServerPlugin<UniqueAttributePluginCfg>
        implements ConfigurationChangeListener<UniqueAttributePluginCfg> {

  //Current plugin configuration.
  private UniqueAttributePluginCfg currentConfiguration;

  //List of attribute types that must be unique.
  private LinkedHashSet<AttributeType> uniqueAttributeTypes =
          new LinkedHashSet<AttributeType>();

//List of base DNs that limit the scope of the uniqueness checking.
 private LinkedHashSet<DN> baseDNs = new LinkedHashSet<DN>();

  /**
   * {@inheritDoc}
   */
  @Override()
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     UniqueAttributePluginCfg configuration)
          throws ConfigException {
    configuration.addUniqueAttributeChangeListener(this);
    currentConfiguration = configuration;
    for (PluginType t : pluginTypes)
      switch (t)  {
        case PRE_OPERATION_ADD:
        case PRE_OPERATION_MODIFY:
        case PRE_OPERATION_MODIFY_DN:
          // These are acceptable.
          break;
        default:
          Message message =
                  ERR_PLUGIN_UNIQUEATTR_INVALID_PLUGIN_TYPE.get(t.toString());
          throw new ConfigException(message);

      }
    //Load base DNs if any.
    for(DN baseDN : configuration.getUniqueAttributeBaseDN())
      baseDNs.add(baseDN);
    //Load attribute types if any.
    for(String attributeType : configuration.getUniqueAttributeType()) {
      AttributeType type =
              DirectoryServer.getAttributeType(attributeType.toLowerCase());
      if(type == null)
        type =
           DirectoryServer.getDefaultAttributeType(attributeType.toLowerCase());
      uniqueAttributeTypes.add(type);
    }
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
          UniqueAttributePluginCfg configuration,
          List<Message> unacceptableReasons) {
    boolean configAcceptable = true;
    for (PluginCfgDefn.PluginType pluginType : configuration.getPluginType())
    {
      switch (pluginType)
      {
        case PREOPERATIONADD:
        case PREOPERATIONMODIFY:
        case PREOPERATIONMODIFYDN:
          // These are acceptable.
          break;

        default:
          Message message =
           ERR_PLUGIN_UNIQUEATTR_INVALID_PLUGIN_TYPE.get(pluginType.toString());
          unacceptableReasons.add(message);
          configAcceptable = false;
      }
    }
    return configAcceptable;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
          UniqueAttributePluginCfg newConfiguration) {
    ResultCode resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();
    LinkedHashSet<AttributeType> newUniqueattributeTypes=
                                             new LinkedHashSet<AttributeType>();
    LinkedHashSet<DN> newConfiguredBaseDNs = new LinkedHashSet<DN>();
    //Load base DNs from new configuration.
    for(DN baseDN : newConfiguration.getUniqueAttributeBaseDN())
      newConfiguredBaseDNs.add(baseDN);
    //Load attribute types from new configuration.
    for(String attributeType : newConfiguration.getUniqueAttributeType()) {
      AttributeType type =
              DirectoryServer.getAttributeType(attributeType.toLowerCase());
      if(type == null)
        type =
           DirectoryServer.getDefaultAttributeType(attributeType.toLowerCase());
      newUniqueattributeTypes.add(type);
    }
    //Switch to the new lists and configurations.
    baseDNs = newConfiguredBaseDNs;
    uniqueAttributeTypes = newUniqueattributeTypes;
    currentConfiguration = newConfiguration;
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final PreOperationPluginResult
               doPreOperation(PreOperationAddOperation addOperation) {
    PreOperationPluginResult pluginResult=PreOperationPluginResult.SUCCESS;
    DN entryDN=addOperation.getEntryDN();
    if(isEntryUniquenessCandidate(entryDN)) {
      List<AttributeValue> valueList =
                         getEntryAttributeValues(addOperation.getEntryToAdd());
      if(!searchAllBaseDNs(valueList, entryDN))
        pluginResult =  getPluginErrorResult(addOperation,
                ERR_PLUGIN_UNIQUEATTR_ADD_NOT_UNIQUE.get(entryDN.toString()));
    }
    return pluginResult;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final PreOperationPluginResult
  doPreOperation(PreOperationModifyOperation modifyOperation) {
    PreOperationPluginResult pluginResult=PreOperationPluginResult.SUCCESS;
    DN entryDN = modifyOperation.getEntryDN();
    if(isEntryUniquenessCandidate(entryDN)) {
      List<AttributeValue> valueList =
              getModificationAttributeValues(modifyOperation.getModifications(),
                                        modifyOperation.getModifiedEntry());
      if(!searchAllBaseDNs(valueList, entryDN))
        pluginResult =  getPluginErrorResult(modifyOperation,
                  ERR_PLUGIN_UNIQUEATTR_MOD_NOT_UNIQUE.get(entryDN.toString()));
    }
    return pluginResult;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final PreOperationPluginResult
               doPreOperation(PreOperationModifyDNOperation modifyDNOperation) {
    PreOperationPluginResult pluginResult=PreOperationPluginResult.SUCCESS;
    DN entryDN=modifyDNOperation.getOriginalEntry().getDN();
    //If the operation has a new superior DN then use that, since any moves
    //need to make sure there are no conflicts in the new superior base DN.
    if(modifyDNOperation.getNewSuperior() != null)
        entryDN = modifyDNOperation.getNewSuperior();
    if(isEntryUniquenessCandidate(entryDN)) {
      List<AttributeValue> valueList =
              getRDNAttributeValues(modifyDNOperation.getNewRDN());
      if(!searchAllBaseDNs(valueList, entryDN))
        pluginResult =  getPluginErrorResult(modifyDNOperation,
                ERR_PLUGIN_UNIQUEATTR_MODDN_NOT_UNIQUE.get(entryDN.toString()));
    }
    return pluginResult;
  }

  /**
   * Determine if the specified DN is a candidate for attribute uniqueness
   * checking. Checking is skipped if the the unique attribute type list is
   * empty or if there are base DNS configured and the specified DN is not a
   * descendant of any of them. Checking is performed for all other cases.
   *
   * @param dn The DN to check.
   *
   * @return Returns <code>true</code> if the operation needs uniqueness
   *         checking performed.
   */
  private boolean
  isEntryUniquenessCandidate(DN dn) {
    if(uniqueAttributeTypes.isEmpty())
      return false;
    else if(baseDNs.isEmpty())
      return true;
    else {
      for(DN baseDN : baseDNs)
        if(baseDN.isAncestorOf(dn))
          return true;
    }
    return false;
  }

  /**
   * Returns a plugin result instance indicating that the operation should be
   * terminated; that no further pre-operation processing should be performed
   * and that the server should send the response immediately. It also adds
   * a CONSTRAINT_VIOLATION result code and the specified error message to
   * the specified operation.
   *
   * @param operation   The operation to add the result code and message to.
   *
   * @param message The message to add to the operation.
   *
   * @return Returns a plugin result instance that halts further processing
   *         on this operation.
   */
  private PreOperationPluginResult
  getPluginErrorResult(PreOperationOperation operation, Message message) {
        operation.appendErrorMessage(message);
        operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        return new PreOperationPluginResult(false, false, true);
  }

  /**
   * Searches all of the the attribute types of the specified RDN for matches
   * in the unique attribute type list. If matches are found, then the
   * corresponding values are added to a list of values that will be eventually
   * searched for uniqueness.

   * @param rdn  The RDN to examine.
   *
   * @return Returns a list of attribute values from the RDN that matches the
   *         unique attribute type list.
   */
  private List<AttributeValue> getRDNAttributeValues(RDN rdn) {
    LinkedList<AttributeValue> valueList=
                                            new LinkedList<AttributeValue>();
    int numAVAs = rdn.getNumValues();
    for (int i = 0; i < numAVAs; i++) {
      if(uniqueAttributeTypes.contains(rdn.getAttributeType(i)))
        valueList.add(rdn.getAttributeValue(i));
    }
    return valueList;
  }

  /**
   * Searches all of the attribute types of the specified entry for matches
   * in the unique attribute type list. Ff matches are found, then the
   * corresponding values are added to a list of values that will eventually
   * be searched for uniqueness.
   *
   * @param entry The entry to examine.
   *
   * @return Returns a list of attribute values from the entry that matches the
   *         unique attribute type list.
   */
  private List<AttributeValue> getEntryAttributeValues(Entry entry) {
    LinkedList<AttributeValue> valueList=new LinkedList<AttributeValue>();
    for(AttributeType attributeType : uniqueAttributeTypes) {
      if(entry.hasAttribute(attributeType))  {
        List<Attribute> attrList=entry.getAttribute(attributeType);
        for (Attribute a : attrList)
          valueList.addAll(a.getValues());
      }
    }
    return valueList;
  }

  /**
   * Iterate over the unique attribute type list calling a method that will
   * search the specified modification list for each attribute type and add
   * the corresponding values to a list of values.
   *
   * @param modificationList  The modification list to search over.
   *
   * @param modifedEntry The copy of the entry with modifications applied.
   *
   * @return Returns a list of attribute values from the modification list
   *         that matches the unique attribute type list.
   */
  private List<AttributeValue>
  getModificationAttributeValues(List<Modification>  modificationList,
                            Entry modifedEntry)  {
    LinkedList<AttributeValue> valueList =
                                            new LinkedList<AttributeValue>();
    for(AttributeType attributeType : uniqueAttributeTypes)
      getModValuesForAttribute(modificationList, attributeType, valueList,
                               modifedEntry);
    return valueList;
  }

  /**
   * Searches the specified modification list for the provided attribute type.
   * If a match is found than the attribute value is added to a list of
   * attribute values that will be eventually searched for uniqueness.
   *
   * @param modificationList The modification list to search over.
   *
   * @param attributeType The attribute type to search for.
   *
   * @param valueList A list of attribute values to put the values in.
   *
   * @param modifiedEntry A copy of the entry with modifications applied.
   */
  private void
  getModValuesForAttribute(List<Modification> modificationList,
                           AttributeType attributeType,
                           LinkedList<AttributeValue> valueList,
                           Entry modifiedEntry) {

    for(Modification modification : modificationList) {
      ModificationType modType=modification.getModificationType();
      //Skip delete modifications or modifications on attribute types not
      //matching the specified type.
      if(modType == ModificationType.DELETE ||
         !modification.getAttribute().getAttributeType().equals(attributeType))
          continue;
      //Increment uses modified entry to get value for the attribute type.
      if(modType == ModificationType.INCREMENT) {
        List<Attribute> modifiedAttrs =
           modifiedEntry.getAttribute(attributeType,
                                      modification.getAttribute().getOptions());
        if (modifiedAttrs != null)  {
          for (Attribute a : modifiedAttrs)
            valueList.addAll(a.getValues());
        }
      } else {
        Attribute modifiedAttribute=modification.getAttribute();
        if(modifiedAttribute.hasValue())
          valueList.addAll(modifiedAttribute.getValues());
      }
    }
  }


  /**
   * Iterates over the base DNs configured by the plugin entry searching for
   * value matches. If the base DN list is empty then the public naming
   * contexts are used instead.
   *
   * @param valueList The list of values to search for.
   *
   * @param entryDN  The DN of the entry related to the operation.
   *
   * @return  Returns <code>true</code> if a value is unique.
   */
  private boolean
  searchAllBaseDNs(List<AttributeValue> valueList, DN entryDN) {
    if(valueList.isEmpty())
      return true;
    if(baseDNs.isEmpty()) {
      for(DN baseDN : DirectoryServer.getPublicNamingContexts().keySet()) {
        if(searchBaseDN(valueList, baseDN, entryDN))
          return false;
      }
    } else {
      for(DN baseDN : baseDNs)  {
        if(searchBaseDN(valueList, baseDN, entryDN))
          return false;
      }
    }
    return true;
  }

  /**
   * Search a single base DN for all the values in a specified value list.
   * A filter is created to search all the attribute at once for each
   * value in the list.
   *
   * @param valueList The list of values to search for.
   *
   * @param baseDN  The base DN to base the search at.
   *
   * @param entryDN  The DN of the entry related to the operation.
   *
   * @return Returns <code>true</code> if the values are not unique under the
   *         under the base DN.
   */
  private boolean
  searchBaseDN(List<AttributeValue> valueList, DN baseDN,
                    DN entryDN) {
    //Filter set to hold component filters.
    HashSet<SearchFilter> componentFilters=new HashSet<SearchFilter>();
    for(AttributeValue value : valueList) {
      //Iterate over the unique attribute list and build a equality filter
      //using each attribute type in the list and the current value being
      //matched.
      for(AttributeType attributeType : uniqueAttributeTypes)
        componentFilters.add(SearchFilter.createEqualityFilter(attributeType,
                value));
      //Perform the search using the OR filter created from the filter
      //components created above.
      InternalClientConnection conn =
              InternalClientConnection.getRootConnection();
      InternalSearchOperation operation = conn.processSearch(baseDN,
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, true,
              SearchFilter.createORFilter(componentFilters),
              null);
      switch (operation.getResultCode()) {
        case SUCCESS:
          break;

        case NO_SUCH_OBJECT:
          //This base DN doesn't exist, return false.
          return false;

        case SIZE_LIMIT_EXCEEDED:
        case TIME_LIMIT_EXCEEDED:
        case ADMIN_LIMIT_EXCEEDED:
        default:
          //Couldn't determine if the attribute is unique because an
          //administrative limit was reached during the search. Fail the
          //operation by returning true. Possibly log an error here?
          return true;
      }
      for (SearchResultEntry entry : operation.getSearchEntries()) {
        //Only allow the entry DN to exist. The user might be modifying
        //the attribute values and putting the same value back. Any other entry
        //means the value is not unique.
        if(!entry.getDN().equals(entryDN))
          return true;
      }
      componentFilters.clear();
    }
    return false;
  }
}
