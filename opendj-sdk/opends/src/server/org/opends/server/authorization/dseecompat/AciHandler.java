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

import org.opends.server.api.AccessControlHandler;
import static org.opends.server.authorization.dseecompat.AciMessages.*;
import org.opends.server.core.*;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import static org.opends.server.util.StaticUtils.toLowerCase;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The AciHandler class performs the main processing for the
 * dseecompat package.
 */
public class AciHandler extends AccessControlHandler
{


    /**
     * ACI_ADD is used to set the container rights for a LDAP add operation.
     */
    public static final int ACI_ADD = 0x0001;

    /**
     * ACI_DELETE is used to set the container rights for a LDAP
     * delete operation.
     */
    public static final int ACI_DELETE = 0x0002;

    /**
     * ACI_READ is used to set the container rights for a LDAP
     * search operation.
     */
    public static final int ACI_READ = 0x0004;

    /**
     * ACI_WRITE is used to set the container rights for a LDAP
     * modify operation.
     */
    public static final int ACI_WRITE = 0x0008;

    /**
     * ACI_COMPARE is used to set the container rights for a LDAP
     * compare operation.
     */
    public static final int ACI_COMPARE = 0x0010;

    /**
     * ACI_SEARCH is used to set the container rights a LDAP search operation.
     */
    public static final int ACI_SEARCH = 0x0020;

    /**
     * ACI_SELF is used for the SELFWRITE right. Currently not implemented.
     */
    public static final int ACI_SELF = 0x0040;

    /**
     * ACI_ALL is used to as a mask for all of the above. These
     * six below are not masked by the ACI_ALL.
     */
    public static final int ACI_ALL = 0x007F;

    /**
     * ACI_PROXY is used for the PROXY right. Currently not implemented.
     */
    public static final int ACI_PROXY = 0x0080;

    /**
     * ACI_IMPORT is used to set the container rights for a LDAP
     * modify dn operation. Currently not implemented.
     */
    public static final int ACI_IMPORT = 0x0100;

    /**
     * ACI_EXPORT is used to set the container rights for a LDAP
     * modify dn operation. Currently not implemented.
     */
    public static final int ACI_EXPORT = 0x0200;

    /**
     * ACI_WRITE_ADD and ACI_WRITE_DELETE are used by the LDAP modify
     * operation. They currently don't have much value; but will be needed
     * once the targetattrfilters target and modify dn are implemented.
     */
    public static final int ACI_WRITE_ADD = 0x800;
    /**
     * See above.
     */
    public static final int ACI_WRITE_DELETE = 0x400;

    /**
     * ACI_NULL is used to set the container rights to all zeros. Used
     * by LDAP modify.
     */
    public static final int ACI_NULL = 0x0000;

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
     * Constructor that registers the message catalog, creates the ACI list
     * class that manages the ACI list. Instantiates and registers the change
     * notification listener that is used to manage the ACI list on
     * modifications and the backend initialization listener that is used to
     * register/de-register aci attribute types in backends when backends
     * are initialized/finalized.
    */
    public AciHandler() {
        AciMessages.registerMessages();
        aciList = new AciList();
        AciListenerManager aciListenerMgr =
            new AciListenerManager(aciList);
        DirectoryServer.registerChangeNotificationListener(aciListenerMgr);
        DirectoryServer.registerBackendInitializationListener(aciListenerMgr);
        if((aciType = DirectoryServer.getAttributeType("aci")) == null)
            aciType = DirectoryServer.getDefaultAttributeType("aci");
    }

    /*
     * TODO
     * The internal search performed by the searchAcis method will require
     * a presence index on the aci attribute for any database of any significant
     * size.  We should probably consider making this index present by default,
     * because if they aren't using the DSEE-compatible implementation then
     * they probably won't have any instances of the aci attribute.
     */
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
                                 Operation operation,
                                 boolean skipAccessCheck) {
        Entry resourceEntry=container.getResourceEntry();
        DN dn=resourceEntry.getDN();
        List<Modification> modifications=container.getModifications();
        for(Modification m : modifications) {
            Attribute modAttr=m.getAttribute();
            AttributeType modType=modAttr.getAttributeType();
            switch(m.getModificationType()) {
            /*
             * TODO Increment modification type needs to be handled.
             */
                case DELETE:
                case REPLACE:
                {
                    /*
                        Check if we have rights to delete all values of
                        an attribute type in the resource entry.
                    */
                    if(resourceEntry.hasAttribute(modType)) {
                        container.setCurrentAttributeType(modType);
                        List<Attribute> attrList =
                            resourceEntry.getAttribute(modType,null);
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
            }
            if(modAttr.hasValue()) {
               boolean checkPrivileges=true;
               for(AttributeValue v : modAttr.getValues()) {
                   container.setCurrentAttributeType(modType);
                   container.setCurrentAttributeValue(v);
                   if((m.getModificationType() == ModificationType.ADD) ||
                      (m.getModificationType() == ModificationType.REPLACE)) {
                       container.setRights(ACI_WRITE_ADD);
                       if(!skipAccessCheck && !accessAllowed(container))
                           return false;
                   } else if(m.getModificationType()
                           == ModificationType.DELETE) {
                       container.setRights(ACI_WRITE_DELETE);
                       if(!skipAccessCheck && !accessAllowed(container))
                           return false;
                   } else {
                     if(!skipAccessCheck)
                       return false;
                   }
                   /*
                    Check if the modification type has an "aci" attribute type.
                    If so, check the syntax of that attribute value. Fail the
                    the operation if the syntax check fails.
                    */
                   if(modType.equals(aciType)) {
                       try {
                           /*
                            * Check that the operation has modify privileges if
                            * it contains an "aci" attribute type. Flip the
                            * boolean to false so this check isn't made again
                            * if there are several ACI values being added.
                            */
                           if(checkPrivileges) {
                            if (!operation.getClientConnection().
                               hasPrivilege(Privilege.MODIFY_ACL, operation)) {
                              int  msgID  =
                                    MSGID_ACI_MODIFY_FAILED_PRIVILEGE;
                              String message = getMessage(msgID,
                                      String.valueOf(container.getResourceDN()),
                                    String.valueOf(container.getClientDN()));
                              logError(ErrorLogCategory.ACCESS_CONTROL,
                                         ErrorLogSeverity.SEVERE_WARNING,
                                         message, msgID);
                              return false;
                            }
                            checkPrivileges=false;
                           }
                           Aci.decode(v.getValue(),dn);
                       } catch (AciException ex) {
                           int    msgID  = MSGID_ACI_MODIFY_FAILED_DECODE;
                           String message = getMessage(msgID,
                                   String.valueOf(dn),
                                   ex.getMessage());
                           logError(ErrorLogCategory.ACCESS_CONTROL,
                                    ErrorLogSeverity.SEVERE_WARNING,
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
                         ErrorLogSeverity.SEVERE_WARNING,
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
                                 ErrorLogSeverity.SEVERE_WARNING,
                                 message, msgID);
                        return false;
                    }
                }
            }
        }
        return true;
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


  /*
   * TODO Add access testing of the filter against the entry. This was
   * brought up in the first code review.
   *
   *  The static block that creates the arrays of EnumRight objects needs to
   *  be documented to explain what they are.  Also, I still disagree with
   *  the  interpretation that the READ right is all that is necessary to
   *  perform either search or compare operations.  That definitely goes
   *  against the documentation, which states that READ applies only to
   *  the search operation, and that users must have both SEARCH and READ
   *  in order to access the results.
   */
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
                                           (ACI_READ | ACI_SEARCH), entry);
      return skipAccessCheck(operation) ||
              accessAllowedEntry(operationContainer);
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
                                            (ACI_READ | ACI_SEARCH), entry);
      SearchResultEntry returnEntry;
      if(!skipAccessCheck(operation)) {
          returnEntry=accessAllowedAttrs(operationContainer);
      } else
          returnEntry=entry;
      return returnEntry;
  }

  //Planned to be implemented methods

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean maySend(SearchOperation operation,
      SearchResultReference reference) {
    //TODO: Planned to be implemented.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(ModifyDNOperation modifyDNOperation) {
      // TODO: Planned to be implemented.
      return true;
  }

  //Not planned to be implemented methods.
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
