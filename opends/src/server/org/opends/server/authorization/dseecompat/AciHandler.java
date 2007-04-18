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

package org.opends.server.authorization.dseecompat;

import org.opends.server.admin.std.server.DseeCompatAccessControlHandlerCfg;
import org.opends.server.api.AccessControlHandler;
import static org.opends.server.messages.AciMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;
import org.opends.server.core.*;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import org.opends.server.types.*;
import static org.opends.server.util.StaticUtils.toLowerCase;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import static org.opends.server.schema.SchemaConstants.*;

import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * The AciHandler class performs the main processing for the
 * dseecompat package.
 */
public class AciHandler extends AccessControlHandler
{
    /*
     * The list that holds that ACIs keyed by the DN of the entry
      * holding the ACI.
     */
    private AciList aciList;

    /**
     * Attribute type corresponding to "aci" attribute.
     */
    public static AttributeType aciType;

    /**
     * Attribute type corresponding to global "ds-cfg-global-aci" attribute.
     */
    public static AttributeType globalAciType;

    /**
     * String used to save the original authorization entry in an operation
     * attachment if a proxied authorization control was seen.
     */
    public static String ORIG_AUTH_ENTRY="origAuthorizationEntry";

    /**
     * This constructor instantiates the ACI handler class that performs the
     * main processing for the dseecompat ACI package. It does the following
     * initializations:
     *
     *  - Instantiates the ACI list cache.
     *
     *  - Instantiates and registers the change notification listener that is
     *    used to manage the ACI list cache after ACI modifications have been
     *    performed.
     *
     *  - Instantiates and registers the backend initialization listener that is
     *    used to manage the ACI list cache when backends are
     *    initialized/finalized.
     *
     *  - Processes all global attribute types found in the configuration entry
     *    and adds them to the ACI list cache.
     *
     *  - Processes all "aci" attributes found in the "cn=config" naming
     *    context and adds them to the ACI list cache.
     *
     * @param configuration The config handler containing the ACI
     *  configuration information.
     * @throws InitializationException if there is a problem processing the
     * config entry or config naming context.
    */
    public AciHandler(DseeCompatAccessControlHandlerCfg configuration)
    throws InitializationException  {
        aciList = new AciList(configuration.dn());
        AciListenerManager aciListenerMgr =
            new AciListenerManager(aciList);
        DirectoryServer.registerChangeNotificationListener(aciListenerMgr);
        DirectoryServer.registerBackendInitializationListener(aciListenerMgr);
        if((aciType = DirectoryServer.getAttributeType("aci")) == null)
            aciType = DirectoryServer.getDefaultAttributeType("aci");
        if((globalAciType =
               DirectoryServer.getAttributeType(ATTR_AUTHZ_GLOBAL_ACI)) == null)
            globalAciType =
                 DirectoryServer.getDefaultAttributeType(ATTR_AUTHZ_GLOBAL_ACI);
        processGlobalAcis(configuration);
        processConfigAcis();
    }

    /**
     * Process all global ACI attribute types found in the configuration
     * entry and adds them to that ACI list cache. It also logs messages about
     * the number of ACI attribute types added to the cache. This method is
     * called once at startup.
     * @param configuration   The config handler containing the ACI
     *  configuration information.
     * @throws InitializationException If there is an error reading
     * the global ACIs from the configuration entry.
     */
    private void processGlobalAcis(
        DseeCompatAccessControlHandlerCfg configuration)
    throws InitializationException {
        int msgID;
        SortedSet<String> globalAci = configuration.getGlobalACI();
        try {
            if (globalAci != null)   {
                LinkedHashSet<AttributeValue> attVals =
                  new LinkedHashSet<AttributeValue>(globalAci.size());
                for (String aci : globalAci)
                {
                  attVals.add(new AttributeValue(globalAciType,aci));
                }
                Attribute attr = new Attribute(globalAciType,
                        globalAciType.toString(),
                        attVals);
                Entry e = new Entry(configuration.dn(), null, null, null);
                e.addAttribute(attr, new ArrayList<AttributeValue>());
                int aciCount =  aciList.addAci(e, false, true);
                msgID  = MSGID_ACI_ADD_LIST_GLOBAL_ACIS;
                String message = getMessage(msgID, Integer.toString(aciCount));
                logError(ErrorLogCategory.ACCESS_CONTROL,
                        ErrorLogSeverity.INFORMATIONAL,
                        message, msgID);
            }  else {
                msgID  = MSGID_ACI_ADD_LIST_NO_GLOBAL_ACIS;
                String message = getMessage(msgID);
                logError(ErrorLogCategory.ACCESS_CONTROL,
                        ErrorLogSeverity.INFORMATIONAL, message, msgID);

            }
        }  catch (Exception e) {
            if (debugEnabled())
                debugCaught(DebugLogLevel.ERROR, e);
            msgID = MSGID_ACI_HANDLER_FAIL_PROCESS_GLOBAL_ACI;
            String message =
                    getMessage(msgID, String.valueOf(configuration.dn()),
                    stackTraceToSingleLineString(e));
            throw new InitializationException(msgID, message, e);
        }
    }

    /**
     * Process all ACIs under the "cn=config" naming context and adds them to
     * the ACI list cache. It also logs messages about the number of ACIs added
     * to the cache. This method is called once at startup.
     * @throws InitializationException If there is an error searching for
     * the ACIs in the naming context.
     */
    private void processConfigAcis() throws InitializationException {
        try
        {
            DN configDN=DN.decode("cn=config");
            LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
            attrs.add("aci");
            InternalClientConnection conn =
                    InternalClientConnection.getRootConnection();
            InternalSearchOperation op = conn.processSearch(configDN,
                    SearchScope.WHOLE_SUBTREE,
                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                    SearchFilter.createFilterFromString("aci=*"), attrs);
            if(op.getSearchEntries().isEmpty()) {
                int    msgID  = MSGID_ACI_ADD_LIST_NO_ACIS;
                String message = getMessage(msgID, String.valueOf(configDN));
                logError(ErrorLogCategory.ACCESS_CONTROL,
                        ErrorLogSeverity.INFORMATIONAL, message, msgID);
            } else {
                int validAcis = aciList.addAci(op.getSearchEntries());
                int    msgID  = MSGID_ACI_ADD_LIST_ACIS;
                String message = getMessage(msgID, Integer.toString(validAcis),
                        String.valueOf(configDN));
                logError(ErrorLogCategory.ACCESS_CONTROL,
                        ErrorLogSeverity.INFORMATIONAL,
                        message, msgID);
            }
        } catch (DirectoryException e) {
            int  msgID = MSGID_ACI_HANDLER_FAIL_PROCESS_ACI;
            String message = getMessage(msgID, stackTraceToSingleLineString(e));
            throw new InitializationException(msgID, message, e);
        }
    }

    /**
     * Checks to see if a LDAP modification is allowed access.
     *
     * @param container  The structure containing the LDAP modifications
     * @param operation The operation to check modify privileges on.
     * operation to check and the evaluation context to apply the check against.
     * @param skipAccessCheck True if access checking should be skipped.
     * @return  True if access is allowed.
     */
    private boolean aciCheckMods(AciLDAPOperationContainer container,
                                 ModifyOperation operation,
                                 boolean skipAccessCheck) {
        Entry resourceEntry=container.getResourceEntry();
        DN dn=resourceEntry.getDN();
        List<Modification> modifications=container.getModifications();
        for(Modification m : modifications) {
            Attribute modAttr=m.getAttribute();
            AttributeType modAttrType=modAttr.getAttributeType();

            if(modAttrType.equals(aciType)) {
              /*
               * Check that the operation has modify privileges if
               * it contains an "aci" attribute type.
               */
              if (!operation.getClientConnection().
                   hasPrivilege(Privilege.MODIFY_ACL, operation)) {
                int  msgID  = MSGID_ACI_MODIFY_FAILED_PRIVILEGE;
                String message =
                     getMessage(msgID,
                                String.valueOf(container.getResourceDN()),
                                String.valueOf(container.getClientDN()));
                logError(ErrorLogCategory.ACCESS_CONTROL,
                         ErrorLogSeverity.INFORMATIONAL,
                         message, msgID);
                return false;
              }
            }
            //This access check handles the case where all attributes of this
            //type are being replaced or deleted. If only a subset is being
            //deleted than this access check is skipped.
            ModificationType modType=m.getModificationType();
            if((modType == ModificationType.DELETE &&
                modAttr.getValues().isEmpty()) ||
               (modType == ModificationType.REPLACE ||
                modType == ModificationType.INCREMENT)) {
                                /*
                 * Check if we have rights to delete all values of
                 * an attribute type in the resource entry.
                 */
                if(resourceEntry.hasAttribute(modAttrType)) {
                  container.setCurrentAttributeType(modAttrType);
                  List<Attribute> attrList =
                   resourceEntry.getAttribute(modAttrType,modAttr.getOptions());
                  for (Attribute a : attrList) {
                    for (AttributeValue v : a.getValues()) {
                      container.setCurrentAttributeValue(v);
                      container.setRights(ACI_WRITE_DELETE);
                      if(!skipAccessCheck &&
                           !accessAllowed(container))
                        return false;
                    }
                  }
                }
             }

            if(modAttr.hasValue()) {
               for(AttributeValue v : modAttr.getValues()) {
                   container.setCurrentAttributeType(modAttrType);
                   switch (m.getModificationType())
                   {
                     case ADD:
                     case REPLACE:
                       container.setCurrentAttributeValue(v);
                       container.setRights(ACI_WRITE_ADD);
                       if(!skipAccessCheck && !accessAllowed(container))
                           return false;
                       break;
                     case DELETE:
                       container.setCurrentAttributeValue(v);
                       container.setRights(ACI_WRITE_DELETE);
                       if(!skipAccessCheck && !accessAllowed(container))
                           return false;
                       break;
                     case INCREMENT:
                       Entry modifiedEntry = operation.getModifiedEntry();
                       List<Attribute> modifiedAttrs =
                            modifiedEntry.getAttribute(modAttrType,
                                                       modAttr.getOptions());
                       if (modifiedAttrs != null)
                       {
                         for (Attribute attr : modifiedAttrs)
                         {
                           for (AttributeValue val : attr.getValues())
                           {
                             container.setCurrentAttributeValue(val);
                             container.setRights(ACI_WRITE_ADD);
                             if(!skipAccessCheck && !accessAllowed(container))
                                 return false;
                           }
                         }
                       }
                       break;
                   }
                  /*
                   Check if the modification type has an "aci" attribute type.
                   If so, check the syntax of that attribute value. Fail the
                   the operation if the syntax check fails.
                   */
                   if(modAttrType.equals(aciType)  ||
                      modAttrType.equals(globalAciType)) {
                       try {
                           //A global ACI needs a NULL DN, not the DN of the
                           //modification.
                           if(modAttrType.equals(globalAciType))
                               dn=DN.nullDN();
                           Aci.decode(v.getValue(),dn);
                       } catch (AciException ex) {
                           int    msgID  = MSGID_ACI_MODIFY_FAILED_DECODE;
                           String message = getMessage(msgID,
                                   String.valueOf(dn),
                                   ex.getMessage());
                           logError(ErrorLogCategory.ACCESS_CONTROL,
                                   ErrorLogSeverity.INFORMATIONAL,
                                   message, msgID);
                           return false;
                       }
                   }
               }
            }
        }
        return true;
    }

    /**
     * Performs the test of the deny and allow access lists using the
     * provided evaluation context. The deny list is checked first.
     *
     * @param evalCtx  The evaluation context to use.
     * @return  True if access is allowed.
     */
    private boolean testApplicableLists(AciEvalContext evalCtx) {
        EnumEvalResult res=EnumEvalResult.FALSE;
        //First check deny lists
        LinkedList<Aci>denys=evalCtx.getDenyList();
        evalCtx.setDenyEval(true);
        for(Aci denyAci : denys) {
           res=Aci.evaluate(evalCtx, denyAci);
            //Failure could be returned if a system limit is hit or
            //search fails
           if((res.equals(EnumEvalResult.FAIL) ||
              (res.equals(EnumEvalResult.TRUE)))) {
               return false;
           }
        }
        //Now check the allows -- flip the deny flag to false first.
        evalCtx.setDenyEval(false);
        LinkedList<Aci>allows=evalCtx.getAllowList();
        for(Aci allowAci : allows) {
           res=Aci.evaluate(evalCtx, allowAci);
           if(res.equals(EnumEvalResult.TRUE)) {
               break;
           }
        }
        return res.getBoolVal();
    }

    /**
     * Creates the allow and deny ACI lists based on the provided target
     * match context. These lists are stored in the evaluation context.
     * @param candidates  List of all possible ACI candidates.
     * @param targetMatchCtx Target matching context to use for testing each
     * ACI.
     */
    private void createApplicableList(LinkedList<Aci> candidates,
                                      AciTargetMatchContext targetMatchCtx)
    {
        LinkedList<Aci>denys=new LinkedList<Aci>();
        LinkedList<Aci>allows=new LinkedList<Aci>();
        for(Aci aci : candidates) {
            if(Aci.isApplicable(aci, targetMatchCtx)) {
                if (aci.hasAccessType(EnumAccessType.DENY)) {
                    denys.add(aci);
                }
                if(aci.hasAccessType(EnumAccessType.ALLOW)) {
                   allows.add(aci);
                }
            }
        }
        targetMatchCtx.setAllowList(allows);
        targetMatchCtx.setDenyList(denys);
    }


    /**
     * Check to see if the client entry has BYPASS_ACL privileges
     * for this operation.
     * @param operation The operation to check privileges on.
     * @return True if access checking can be skipped because
     * the operation client connection has BYPASS_ACL privileges.
     */
    boolean skipAccessCheck(Operation operation) {
        return operation.getClientConnection().
                hasPrivilege(Privilege.BYPASS_ACL, operation);
    }

    /**
     * Check access using the specified container. This container will have all
     * of the information to gather applicable ACIs and perform evaluation on
     * them.
     *
     * @param container An ACI operation container which has all of the
     * information needed to check access.
     *
     * @return True if access is allowed.
     */
    private boolean accessAllowed(AciContainer container)
    {
        DN dn = container.getResourceEntry().getDN();
        //For ACI_WRITE_ADD and ACI_WRITE_DELETE set the ACI_WRITE
        //right.
        if(container.hasRights(ACI_WRITE_ADD) ||
           container.hasRights(ACI_WRITE_DELETE))
                container.setRights(container.getRights() | ACI_WRITE);
        //Check if the ACI_SELF right needs to be set (selfwrite right).
        //Only done if the right is ACI_WRITE,  an attribute value is set and
        //that attribute value is a DN.
        if((container.getCurrentAttributeValue() != null) &&
           (container.hasRights(ACI_WRITE)) &&
           (isAttributeDN(container.getCurrentAttributeType())))  {
          try {
            String DNString =
                        container.getCurrentAttributeValue().getStringValue();
            DN tmpDN = DN.decode(DNString);
            //Have a valid DN, compare to clientDN to see if the ACI_SELF
            //right should be set.
            if(tmpDN.equals(container.getClientDN())) {
              container.setRights(container.getRights() | ACI_SELF);
            }
          } catch (DirectoryException ex) {
             return false;
          }
        }

        //Check proxy authorization only if the entry has not already been
        //processed (working on a new entry). If working on a new entry, then
        //only do a proxy check if the right is not set to ACI_PROXY and the
        //proxied authorization control has been decoded.
        if(!container.hasSeenEntry()) {
          if(!container.hasRights(ACI_PROXY) &&
             container.isProxiedAuthorization()) {
              int currentRights=container.getRights();
              //Save the current rights so they can be put back if on success.
              container.setRights(ACI_PROXY);
              //Switch to the original authorization entry, not the proxied one.
              container.useOrigAuthorizationEntry(true);
              if(!accessAllowed(container))
                  return false;
              //Access is ok, put the original rights back.
              container.setRights(currentRights);
              //Put the proxied authorization entry back to the current
              //authorization entry.
              container.useOrigAuthorizationEntry(false);
          }
          //Set the seen flag so proxy processing is not performed for this
          //entry again.
          container.setSeenEntry(true);
       }

        /*
         * First get all allowed candidate ACIs.
         */
        LinkedList<Aci>candidates = aciList.getCandidateAcis(dn);
        /*
         * Create an applicable list of ACIs by target matching each
         * candidate ACI against the container's target match view.
         */
        createApplicableList(candidates,container);
        /*
         * Lastly, evaluate the applicable list.
         */
        return(testApplicableLists(container));
    }

  /**
   * Check if the specified attribute type is a DN by checking if its syntax
   * OID is equal to the DN syntax OID.
   * @param attribute The attribute type to check.
   * @return True if the attribute type syntax OID is equal to a DN syntax OID.
   */
  private boolean isAttributeDN(AttributeType attribute) {
    return (attribute.getSyntaxOID().equals(SYNTAX_DN_OID));
  }


    /**
     * Performs an access check against all of the attributes of an entry.
     * The attributes that fail access are removed from the entry. This method
     * performs the processing needed for the filterEntry method processing.
     *
     * @param container The search or compare container which has all of the
     * information needed to filter the attributes for this entry.
     * @return The  entry to send back to the client, minus any attribute
     * types that failed access check.
     */
    private SearchResultEntry
    accessAllowedAttrs(AciLDAPOperationContainer container) {
        Entry e=container.getResourceEntry();
        List<AttributeType> typeList=getAllAttrs(e);
        for(AttributeType attrType : typeList) {
            container.setCurrentAttributeType(attrType);
            if(!accessAllowed(container)) {
                e.removeAttribute(attrType);
            }
        }
        return container.getSearchResultEntry();
    }

    /**
     * Gathers all of the attribute types in an entry along with the
     * "objectclass" attribute type in a List. The "objectclass" attribute is
     * added to the list first so it is evaluated first.
     *
     * @param e Entry to gather the attributes for.
     * @return List containing the attribute types.
     */
    private List<AttributeType> getAllAttrs(Entry e) {
        Map<AttributeType,List<Attribute>> attrMap = e.getUserAttributes();
        List<AttributeType> typeList=new LinkedList<AttributeType>();
        Attribute attr=e.getObjectClassAttribute();
        /*
         * When a search is not all attributes returned, the "objectclass"
         * attribute type is missing from the entry.
         */
        if(attr != null) {
           AttributeType ocType=attr.getAttributeType();
           typeList.add(ocType);
        }
        typeList.addAll(attrMap.keySet());
        return typeList;
    }

    /*
     * TODO Evaluate performance of this method.
     * TODO Evaluate security concerns of this method. Logic from this method
     * taken almost directly from DS6 implementation.
     *
     *  I find the work done in the accessAllowedEntry method, particularly
     *  with regard to the entry test evaluation, to be very confusing and
     *  potentially pretty inefficient.  I'm also concerned that the "return
     *  "true" inside the for loop could potentially allow access when it
     *  should be denied.
     */
    /**
     * Check if access is allowed on an entry. Access is checked by iterating
     * through each attribute of an entry, starting with the "objectclass"
     * attribute type.
     *
     * If access is allowed on the entry based on one of it's attribute types,
     * then a possible second access check is performed. This second check is
     * only performed if an entry test ACI was found during the earlier
     * successful access check. An entry test ACI has no "targetattrs" keyword,
     * so allowing access based on an attribute type only would be incorrect.
     *
     * @param container ACI search container containing all of the information
     * needed to check access.
     *
     * @return True if access is allowed.
     */
    private boolean accessAllowedEntry(AciLDAPOperationContainer container) {
        boolean ret=false;
        //set flag that specifies this is the first attribute evaluated
        //in the entry
        container.setIsFirstAttribute(true);
        List<AttributeType> typeList=getAllAttrs(container.getResourceEntry());
        for(AttributeType attrType : typeList) {
            container.setCurrentAttributeType(attrType);
            /*
             * Check if access is allowed. If true, then check to see if an
             * entry test rule was found (no targetattrs) during target match
             * evaluation. If such a rule was found, set the current attribute
             * type to "null" and check access again so that rule is applied.
             */
            if(accessAllowed(container)) {
                if(container.hasEntryTestRule()) {
                    container.setCurrentAttributeType(null);
                    if(!accessAllowed(container)) {
                        /*
                         * If we failed because of a deny permission-bind rule,
                         * we need to stop and return false.
                         */
                        if(container.isDenyEval()) {
                            return false;
                        }
                        /*
                         * If we failed because there was no explicit
                         * allow rule, then we grant implicit access to the
                         * entry.
                         */
                    }
                }
                return true;
            }
        }
        return ret;
    }

    /**
     * Test the attribute types of the search filter for access. This method
     * supports the search right.
     *
     * @param container  The container used in the access evaluation.
     * @param filter The filter to check access on.
     * @return  True if all attribute types in the filter have access.
     */
    private boolean
    testFilter(AciLDAPOperationContainer container, SearchFilter filter) {
        boolean ret=true;
        switch (filter.getFilterType()) {
            case AND:
            case OR: {
                for (SearchFilter f : filter.getFilterComponents())
                    if(!testFilter(container, f))
                        return false ;
                break;
            }
            case NOT:  {
                SearchFilter f = filter.getNotComponent();
                ret=!testFilter(container, f);
                break;
            }
            default: {
                AttributeType attrType=filter.getAttributeType();
                container.setCurrentAttributeType(attrType);
                ret=accessAllowed(container);
            }
        }
        return ret;
    }

    /**
     * Check access using the accessAllowed method. The
     * LDAP add, compare, modify and delete operations use this function.
     * The other supported LDAP operations have more specialized checks.
     * @param operationContainer  The container containing the information
     * needed to evaluate this operation.
     * @param operation The operation being evaluated.
     * @return True if this operation is allowed access.
     */
    private boolean isAllowed(AciLDAPOperationContainer operationContainer,
                              Operation operation) {
        return skipAccessCheck(operation) || accessAllowed(operationContainer);
    }

    /**
     * Evaluate an entry to be added to see if it has any "aci"
     * attribute type. If it does, examines each "aci" attribute type
     * value for syntax errors. All of the "aci" attribute type values
     * must pass syntax check for the add operation to proceed. Any
     * entry with an "aci" attribute type must have "modify-acl"
     * privileges.
     *
     * @param entry  The entry to be examined.
     * @param operation The operation to to check privileges on.
     * @param clientDN The authorization DN.
     * @return True if the entry has no ACI attributes or if all of the "aci"
     * attributes values pass ACI syntax checking.
     */
    private boolean
       verifySyntax(Entry entry, Operation operation, DN clientDN) {
      if(entry.hasOperationalAttribute(aciType)) {
        /*
         * Check that the operation has "modify-acl" privileges since the
         * entry to be added has an "aci" attribute type.
         */
        if (!operation.getClientConnection().
             hasPrivilege(Privilege.MODIFY_ACL, operation))  {
          int    msgID  = MSGID_ACI_ADD_FAILED_PRIVILEGE;
          String message = getMessage(msgID,
                                      String.valueOf(entry.getDN()),
                                      String.valueOf(clientDN));
          logError(ErrorLogCategory.ACCESS_CONTROL,
                   ErrorLogSeverity.INFORMATIONAL,
                   message, msgID);
          return false;
        }
        List<Attribute> attributeList =
             entry.getOperationalAttribute(aciType, null);
        for (Attribute attribute : attributeList)
        {
          for (AttributeValue value : attribute.getValues())
          {
            try {
              DN dn=entry.getDN();
              Aci.decode(value.getValue(),dn);
            } catch (AciException ex) {
              int    msgID  = MSGID_ACI_ADD_FAILED_DECODE;
              String message = getMessage(msgID,
                                          String.valueOf(entry.getDN()),
                                          ex.getMessage());
              logError(ErrorLogCategory.ACCESS_CONTROL,
                       ErrorLogSeverity.INFORMATIONAL,
                       message, msgID);
              return false;
            }
          }
        }
      }
    return true;
  }

    /**
     * Check access on add operations.
     *
     * @param operation The add operation to check access on.
     * @return  True if access is allowed.
     */
    public boolean isAllowed(AddOperation operation) {
        AciLDAPOperationContainer operationContainer =
                new AciLDAPOperationContainer(operation, ACI_ADD);
        boolean ret=isAllowed(operationContainer,operation);

        //LDAP add needs a verify ACI syntax step in case any
        //"aci" attribute types are being added.
        if(ret)
          ret=verifySyntax(operation.getEntryToAdd(), operation,
                           operationContainer.getClientDN());
        return ret;
    }

   /**
     * Check access on compare operations. Note that the attribute
     * type is unavailable at this time, so this method partially
     * parses the raw attribute string to get the base attribute
     * type. Options are ignored.
     *
     * @param operation The compare operation to check access on.
     * @return  True if access is allowed.
     */
   public boolean isAllowed(CompareOperation operation) {
       AciLDAPOperationContainer operationContainer =
               new AciLDAPOperationContainer(operation, ACI_COMPARE);
       String baseName;
       String rawAttributeType=operation.getRawAttributeType();
       int  semicolonPosition=rawAttributeType.indexOf(';');
       if (semicolonPosition > 0)
         baseName =
             toLowerCase(rawAttributeType.substring(0, semicolonPosition));
       else
         baseName = toLowerCase(rawAttributeType);
       AttributeType attributeType;
       if((attributeType =
           DirectoryServer.getAttributeType(baseName)) == null)
           attributeType = DirectoryServer.getDefaultAttributeType(baseName);
       AttributeValue attributeValue =
           new AttributeValue(attributeType, operation.getAssertionValue());
       operationContainer.setCurrentAttributeType(attributeType);
       operationContainer.setCurrentAttributeValue(attributeValue);
       return isAllowed(operationContainer, operation);
   }

   /**
     * Check access on delete operations.
     *
     * @param operation The delete operation to check access on.
     * @return  True if access is allowed.
     */
   public boolean isAllowed(DeleteOperation operation) {
       AciLDAPOperationContainer operationContainer=
               new AciLDAPOperationContainer(operation, ACI_DELETE);
       return isAllowed(operationContainer, operation);
   }

   /**
    * Check access on modify operations.
    *
    * @param operation The modify operation to check access on.
    * @return  True if access is allowed.
    */

  public boolean isAllowed(ModifyOperation operation) {
      AciLDAPOperationContainer operationContainer=
              new AciLDAPOperationContainer(operation, ACI_NULL);
      return aciCheckMods(operationContainer, operation,
                          skipAccessCheck(operation));
  }

  /**
   * Checks access on a search operation.
   * @param operation The search operation class containing information to
   * check the access on.
   * @param entry  The entry to evaluate access.
   * @return   True if access is allowed.
   */
  public boolean
  maySend(SearchOperation operation, SearchResultEntry entry) {
      AciLDAPOperationContainer operationContainer =
              new AciLDAPOperationContainer(operation,
                      (ACI_SEARCH), entry);
      boolean ret;
      if(!(ret=skipAccessCheck(operation))) {
          ret=testFilter(operationContainer, operation.getFilter());
          if (ret) {
              operationContainer.setRights(ACI_READ);
              ret=accessAllowedEntry(operationContainer);
          }
      }
      return ret;
  }

  /*
   * TODO Rename this method. Needs to be changed in SearchOperation.
   *
   * I find the name of the filterEntry method to be misleading because
   * it works on a search operation but has nothing to do with the search
   * filter.  Something like "removeDisallowedAttributes" would be clearer.
   */
  /**
   * Checks access on each attribute in an entry. It removes those attributes
   * that fail access check.
   *
   * @param operation The search operation class containing information to
   * check access on.
   * @param entry   The entry containing the attributes.
   * @return    The entry to return minus filtered attributes.
   */
  public SearchResultEntry filterEntry(SearchOperation operation,
                                       SearchResultEntry entry) {
      AciLDAPOperationContainer operationContainer =
              new AciLDAPOperationContainer(operation,
                                            (ACI_READ), entry);
      //Proxy access check has already been done for this entry in the maySend
      //method, set the seen flag to true to bypass any proxy check.
      operationContainer.setSeenEntry(true);
      SearchResultEntry returnEntry;
      if(!skipAccessCheck(operation)) {
          returnEntry=accessAllowedAttrs(operationContainer);
      } else
          returnEntry=entry;
      return returnEntry;
  }

  /**
   * Perform all needed RDN checks for the modifyDN operation. The old RDN is
   * not equal to the new RDN. The access checks are:
   *
   *  - Verify WRITE access to the original entry.
   *  - Verfiy WRITE_ADD access on each RDN component of the new RDN. The
   *    WRITE_ADD access is used because this access could be restricted by
   *    the targattrfilters keyword.
   *  - If the deleteOLDRDN flag is set, verify WRITE_DELETE access on the
   *    old RDN. The WRITE_DELETE access is used because this access could be
   *    restricted by the targattrfilters keyword.
   *
   * @param operation   The ModifyDN operation class containing information to
   * check access on.
   * @param oldRDN      The old RDN component.
   * @param newRDN      The new RDN component.
   * @return True if access is allowed.
   */
  private boolean aciCheckRDNs(ModifyDNOperation operation, RDN oldRDN,
                               RDN newRDN) {
      boolean ret;

      AciLDAPOperationContainer operationContainer =
              new AciLDAPOperationContainer(operation, (ACI_WRITE),
                      operation.getOriginalEntry());
      ret=accessAllowed(operationContainer);
      if(ret)
          ret=checkRDN(ACI_WRITE_ADD, newRDN, operationContainer);
      if(ret && operation.deleteOldRDN()) {
          ret =
            checkRDN(ACI_WRITE_DELETE, oldRDN, operationContainer);
      }
      return ret;
  }


  /**
   * Check access on each attribute-value pair component of the specified RDN.
   * There may be more than one attribute-value pair if the RDN is multi-valued.
   *
   * @param right  The access right to check for.
   * @param rdn  The RDN to examine the attribute-value pairs of.
   * @param container The container containing the information needed to
   * evaluate the specified RDN.
   * @return  True if access is allowed for all attribute-value pairs.
   */
  private boolean checkRDN(int right, RDN rdn, AciContainer container) {
        boolean ret=false;
        int numAVAs = rdn.getNumValues();
        container.setRights(right);
        for (int i = 0; i < numAVAs; i++){
            AttributeType type=rdn.getAttributeType(i);
            AttributeValue value=rdn.getAttributeValue(i);
            container.setCurrentAttributeType(type);
            container.setCurrentAttributeValue(value);
            if(!(ret=accessAllowed(container)))
                break;
        }
        return ret;
  }

  /**
   * Check access on the new superior entry if it exists. If the entry does not
   * exist or the DN cannot be locked then false is returned.
   *
   * @param superiorDN The DN of the new superior entry.
   * @param op The modifyDN operation to check access on.
   * @return True if access is granted to the new superior entry.
   * @throws DirectoryException  If a problem occurs while trying to
   *                             retrieve the new superior entry.
   */
  private boolean aciCheckSuperiorEntry(DN superiorDN, ModifyDNOperation op)
  throws DirectoryException {
    boolean ret=false;
    Lock entryLock = null;
    for (int i=0; i < 3; i++)  {
      entryLock = LockManager.lockRead(superiorDN);
      if (entryLock != null)
        break;
    }
    if (entryLock == null) {
      int    msgID   = MSGID_ACI_HANDLER_CANNOT_LOCK_NEW_SUPERIOR_USER;
      String message = getMessage(msgID, String.valueOf(superiorDN));
       logError(ErrorLogCategory.ACCESS_CONTROL, ErrorLogSeverity.INFORMATIONAL,
                message, msgID);
      return false;
    }
    try {
      Entry superiorEntry=DirectoryServer.getEntry(superiorDN);
      if(superiorEntry!= null) {
        AciLDAPOperationContainer operationContainer =
                new AciLDAPOperationContainer(op, (ACI_IMPORT),
                        superiorEntry);
        ret=accessAllowed(operationContainer);
      }
    }  finally {
          LockManager.unlock(superiorDN, entryLock);
    }
    return ret;
  }

  /**
   * Checks access on a modifyDN operation.
   *
   * @param operation The modifyDN operation to check access on.
   * @return True if access is allowed.
   *
   */
  public boolean isAllowed(ModifyDNOperation operation) {
      boolean ret=true;
      DN newSuperiorDN;
      RDN oldRDN=operation.getOriginalEntry().getDN().getRDN();
      RDN newRDN=operation.getNewRDN();
      if(!skipAccessCheck(operation)) {
          //If this is a modifyDN move to a new superior, then check if the
          //superior DN has import accesss.
          if((newSuperiorDN=operation.getNewSuperior()) != null) {
             try {
               ret=aciCheckSuperiorEntry(newSuperiorDN, operation);
             } catch (DirectoryException ex) {
               ret=false;
             }
          }
          boolean rdnEquals=oldRDN.equals(newRDN);
          //Perform the RDN access checks only if the RDNs are not equal.
          if(ret && !rdnEquals)
              ret=aciCheckRDNs(operation, oldRDN, newRDN);

          //If this is a modifyDN move to a new superior, then check if the
          //original entry DN has export access.
          if(ret && (newSuperiorDN != null)) {
              AciLDAPOperationContainer operationContainer =
                      new AciLDAPOperationContainer(operation, (ACI_EXPORT),
                                             operation.getOriginalEntry());
                 //The RDNs are not equal, skip the proxy check since it was
                 //already performed in the aciCheckRDNs call above.
                 if(!rdnEquals)
                     operationContainer.setSeenEntry(true);
                 ret=accessAllowed(operationContainer);
          }
      }
      return ret;
  }

  //TODO Check access to control, issue #452.
  /**
   * Called when a proxied authorization control was decoded. Currently used
   * to save the current authorization entry in an operation attachment, but
   * eventually will be used to check access to the actual control.
   * @param operation The operation to save the attachment to.
   * @param entry  The new authorization entry.
   * @return  True if the control is allowed access.
   */
  public boolean isProxiedAuthAllowed(Operation operation, Entry entry) {
    operation.setAttachment(ORIG_AUTH_ENTRY, operation.getAuthorizationEntry());
    return true;
  }

  //Not planned to be implemented methods.

   /**
   * {@inheritDoc}
   */
  @Override
  public boolean maySend(SearchOperation operation,
      SearchResultReference reference) {
    //TODO: Deferred.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(BindOperation bindOperation) {
      //Not planned to be implemented.
      return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(ExtendedOperation extendedOperation) {
      //Not planned to be implemented.
      return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(SearchOperation searchOperation) {
      //Not planned to be implemented.
      return true;
  }
}
