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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import org.opends.server.types.*;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.Group;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.core.SearchOperation;
import org.opends.server.extensions.TLSConnectionSecurityProvider;
import org.opends.server.types.Operation;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;

import static org.opends.server.authorization.dseecompat.Aci.*;
import static org.opends.server.authorization.dseecompat.AciHandler.*;
import org.opends.server.controls.GetEffectiveRights;
import static org.opends.server.util.ServerConstants.OID_GET_EFFECTIVE_RIGHTS;

/**
 *  The AciContainer class contains all of the needed information to perform
 *  both target match and evaluate an ACI. Target matching is the process
 *  of testing if an ACI is applicable to an operation, and evaluation is
 *  the actual access evaluation of the ACI.
 */
public abstract class AciContainer
implements AciTargetMatchContext, AciEvalContext {

    /*
     * The allow and deny lists.
     */
    private LinkedList<Aci> denyList, allowList;

    /*
     * The attribute type in the resource entry currently being evaluated.
     */
    private AttributeType attributeType;

    /*
     * The attribute type value in the resource entry currently being
     * evaluated.
     */
    private AttributeValue attributeValue;

    /*
     * True if this is the first attribute type in the resource entry being
     * evaluated.
     */
    private boolean isFirst = false;

    /*
     * True if an entry test rule was seen during target matching of an ACI
     * entry. A entry test rule is an ACI with targetattrs target keyword.
     */
    private boolean isEntryTestRule = false;

    /*
     * True if the evaluation of an ACI is from the deny list.
     */
    private boolean isDenyEval;

    /*
     * True if the evaluation is a result of an LDAP add operation.
     */
    private boolean isAddOp=false;

    /*
     * The right mask to use in the evaluation of the LDAP operation.
     */
    private int rightsMask;

    /*
     * The entry being evaluated (resource entry).
     */
    private Entry resourceEntry;

    /*
     * Saves the resource entry. Used in geteffectiverights evaluation to
     * restore the current resource entry state after a read right was
     * evaluated.
     */
    private final Entry saveResourceEntry;

    /*
     * The client connection information.
     */
    private final ClientConnection clientConnection;

    /*
     * The operation being evaluated.
     */
    private final Operation operation;

    /*
     * True if a targattrfilters match was found.
     */
    private boolean targAttrFiltersMatch=false;

    /*
     * The authorization entry currently being evaluated. If proxied
     * authorization is being used and the handler is doing a proxy access
     * check, then this entry will switched to the original authorization entry
     * rather than the proxy ID entry. If the check succeeds, it will be
     * switched back for non-proxy access checking. If proxied authentication
     * is not being used then this entry never changes.
     */
    private Entry authorizationEntry;

    /*
     * Used to save the current authorization entry when the authorization
     * entry is switched during a proxy access check.
     */
    private final Entry saveAuthorizationEntry;

    /*
     * This entry is only used if proxied authorization is being used.  It is
     * the original authorization entry before the proxied authorization change.
     */
    private Entry origAuthorizationEntry=null;

    /*
     * True if proxied authorization is being used.
     */
    private boolean proxiedAuthorization=false;

    /*
     * Used by proxied authorization processing. True if the entry has already
     * been processed by an access proxy check. Some operations might perform
     * several access checks on the same entry (modify DN), this
     * flag is used to bypass the proxy check after the initial evaluation.
     */
    private boolean seenEntry=false;

    /*
     *  True if geteffectiverights evaluation is in progress.
     */
    private boolean isGetEffectiveRightsEval=false;

     /*
     *  True if the operation has a geteffectiverights control.
     */
    private boolean hasGetEffectiveRightsControl=false;

    /*
     * The geteffectiverights authzID in DN format.
     */
    private DN authzid=null;

    /*
     * True if the authZid should be used as the client DN, only used in
     * geteffectiverights evaluation.
     */
    private boolean useAuthzid=false;

    /*
     * The list of specific attributes to get rights for, in addition to
     * any attributes requested in the search.
     */
    private List<AttributeType> specificAttrs=null;

    /*
     * The entry with all of its attributes available. Used in
     * geteffectiverights read entry level evaluation.
     */
    private Entry fullEntry=null;

    /*
     * Table of ACIs that have targattrfilter keywords that matched. Used
     * in geteffectiverights attributeLevel write evaluation.
     */
    private final HashMap<Aci,Aci> targAttrFilterAcis=new HashMap<Aci, Aci>();

    /*
     * The name of a ACI that decided an evaluation and contained a
     * targattrfilter keyword. Used in geteffectiverights attributeLevel
     * write evaluation.
     */
    private String targAttrFiltersAciName=null;

    /*
     * Value that is used to store the allow/deny result of a deciding ACI
     * containing a targattrfilter keyword.  Used in geteffectiverights
     * attributeLevel write evaluation.
     */
    private int targAttrMatch=0;

    /*
     * The ACI that decided the last evaluation. Used in geteffectiverights
     * loginfo processing.
     */
    private Aci decidingAci=null;

    /*
     * The reason the last evaluation decision was made. Used both
     * in geteffectiverights loginfo processing and attributeLevel write
     * evaluation.
     */
    private EnumEvalReason evalReason=null;

    /*
     * A summary string holding the last evaluation information in textual
     * format. Used in geteffectiverights loginfo processing.
     */
    private String summaryString=null;

   /*
    * Flag used to determine if ACI all attributes target matched.
    */
    private int evalAllAttributes=0;

   /*
    * String used to hold a control OID string.
    */
    private String controlOID;

   /*
    * String used to hold an extended operation OID string.
    */
    private String extOpOID;

    /*
     * AuthenticationInfo class to use.
     */
    private AuthenticationInfo authInfo;

  /**
     * This constructor is used by all currently supported LDAP operations
     * except the generic access control check that can be used by
     * plugins.
     *
     * @param operation The Operation object being evaluated and target
     * matching.
     *
     * @param rights The rights array to use in evaluation and target matching.
     *
     * @param entry The current entry being evaluated and target matched.
     */
    protected AciContainer(Operation operation, int rights, Entry entry) {
      this.resourceEntry=entry;
      this.operation=operation;
      this.clientConnection=operation.getClientConnection();
      if(operation instanceof AddOperationBasis)
          this.isAddOp=true;
      this.authInfo = clientConnection.getAuthenticationInfo();

      //If the proxied authorization control was processed, then the operation
      //will contain an attachment containing the original authorization entry.
      this.origAuthorizationEntry =
                      (Entry) operation.getAttachment(ORIG_AUTH_ENTRY);
      if(origAuthorizationEntry != null)
         this.proxiedAuthorization=true;
      this.authorizationEntry=operation.getAuthorizationEntry();
      //The ACI_READ right at constructor time can only be the result of the
      //AciHandler.filterEntry method. This method processes the
      //geteffectiverights control, so it needs to check for it.  There are
      //two other checks done, because the resource entry passed to that method
      //is filtered (it may not contain enough attribute information
      //to evaluate correctly). See the the comments below.
      if(operation instanceof SearchOperation && (rights == ACI_READ)) {
        //Checks if a geteffectiverights control was sent and
        //sets up the structures needed.
        GetEffectiveRights getEffectiveRightsControl =
              (GetEffectiveRights)
                      operation.getAttachment(OID_GET_EFFECTIVE_RIGHTS);
        if(getEffectiveRightsControl != null) {
          hasGetEffectiveRightsControl=true;
          if(getEffectiveRightsControl.getAuthzDN() == null)
            this.authzid=getClientDN();
          else
            this.authzid=getEffectiveRightsControl.getAuthzDN();
          this.specificAttrs=getEffectiveRightsControl.getAttributes();
        }
        //If an ACI evaluated because of an Targetattr="*", then the
        //AciHandler.maySend method signaled this via adding this attachment
        //string.
        String allUserAttrs=
                  (String)operation.getAttachment(ALL_USER_ATTRS_MATCHED);
        if(allUserAttrs != null)
          evalAllAttributes |= ACI_USER_ATTR_STAR_MATCHED;
        //If an ACI evaluated because of an Targetattr="+", then the
        //AciHandler.maySend method signaled this via adding this attachment
        //string.
        String allOpAttrs=(String)operation.getAttachment(ALL_OP_ATTRS_MATCHED);
        if(allOpAttrs != null)
          evalAllAttributes |= ACI_OP_ATTR_PLUS_MATCHED;

        //The AciHandler.maySend method also adds the full attribute version of
        //the resource entry in this attachment.
        fullEntry=(Entry)operation.getAttachment(ALL_ATTRS_RESOURCE_ENTRY);
      } else
        fullEntry=this.resourceEntry;
      //Reference the current authorization entry, so it can be put back
      //if an access proxy check was performed.
      this.saveAuthorizationEntry=this.authorizationEntry;
      this.saveResourceEntry=this.resourceEntry;
      this.rightsMask = rights;
    }

    /**
     * This constructor is used by the generic access control check.
     *
     * @param operation The operation to use in the access evaluation.
     * @param e The entry to check access for.
     * @param authInfo The authentication information to use in the evaluation.
     * @param rights The rights to check access of.
     */
    protected AciContainer(Operation operation, Entry e,
                            AuthenticationInfo authInfo,
                            int rights) {
        this.resourceEntry=e;
        this.operation=operation;
        this.clientConnection=operation.getClientConnection();
        this.authInfo = authInfo;
        this.authorizationEntry = authInfo.getAuthorizationEntry();
        this.saveAuthorizationEntry=this.authorizationEntry;
        this.saveResourceEntry=this.resourceEntry;
        this.rightsMask = rights;
    }
  /**
   * Returns true if an entry has already been processed by an access proxy
   * check.
   *
   * @return True if an entry has already been processed by an access proxy
   * check.
   */
   public boolean hasSeenEntry() {
      return this.seenEntry;
    }

  /**
   * Set to true if an entry has already been processsed by an access proxy
   * check.
   *
   * @param val The value to set the seenEntry boolean to.
   */
    public void setSeenEntry(boolean val) {
     this.seenEntry=val;
    }

  /**
   * {@inheritDoc}
   */
    public boolean isProxiedAuthorization() {
         return this.proxiedAuthorization;
    }

  /**
   * {@inheritDoc}
   */
    public boolean isGetEffectiveRightsEval() {
        return this.isGetEffectiveRightsEval;
    }

  /**
   * The container is going to be used in a geteffectiverights evaluation, set
   * the flag isGetEffectiveRightsEval to true.
   */
  public void setGetEffectiveRightsEval() {
       this.isGetEffectiveRightsEval=true;
    }

  /**
   * Return true if the container is being used in a geteffectiverights
   * evaluation.
   *
   * @return True if the container is being used in a geteffectiverights
   * evaluation.
   */
    public boolean hasGetEffectiveRightsControl() {
      return this.hasGetEffectiveRightsControl;
    }

  /**
   * Use the DN from the geteffectiverights control's authzId as the
   * client DN, rather than the authorization entry's DN.
   *
   * @param v The valued to set the useAuthzid to.
   */
    public void useAuthzid(boolean v) {
       this.useAuthzid=v;
    }

  /**
   * Return the list of additional attributes specified in the
   * geteffectiveritghts control.
   *
   * @return The list of attributes to return rights information about in the
   * entry.
   */
    public List<AttributeType> getSpecificAttributes() {
       return this.specificAttrs;
    }

  /**
   * During the geteffectiverights entrylevel read evaluation, an entry with all
   * of the attributes used in the AciHandler's maysend method evaluation is
   * needed to perform the evaluation over again. This entry was saved
   * in the operation's attachment mechanism when the container was created
   * during the SearchOperation read evaluation.
   *
   * This method is used to replace the current resource entry with that saved
   * entry to perform the entrylevel read evaluation described above and to
   * switch back to the current resource entry when needed.
   *
   * @param val Specifies if the saved entry should be used or not. True if it
   * should be used, false if the original resource entry should be used.
   *
   */
    public void useFullResourceEntry(boolean val) {
      if(val)
        resourceEntry=fullEntry;
      else
        resourceEntry=saveResourceEntry;
    }

   /**
    * {@inheritDoc}
    */
    public void addTargAttrFiltersMatchAci(Aci aci) {
      this.targAttrFilterAcis.put(aci, aci);
    }

   /**
    * {@inheritDoc}
    */
    public boolean hasTargAttrFiltersMatchAci(Aci aci) {
      return this.targAttrFilterAcis.containsKey(aci);
    }

   /**
    * {@inheritDoc}
    */
    public boolean isTargAttrFilterMatchAciEmpty() {
       return this.targAttrFilterAcis.isEmpty();
    }

  /**
   * Reset the values used by the geteffectiverights evaluation to
   * original values. The geteffectiverights evaluation uses the same container
   * repeatedly for different rights evaluations (read, write, proxy,...) and
   * this method resets variables that are specific to a single evaluation.
   */
    public void resetEffectiveRightsParams() {
      this.targAttrFilterAcis.clear();
      this.decidingAci=null;
      this.evalReason=null;
      this.targAttrFiltersMatch=false;
      this.summaryString=null;
      this.targAttrMatch=0;
    }

   /**
    * {@inheritDoc}
    */
    public void setTargAttrFiltersAciName(String name) {
      this.targAttrFiltersAciName=name;
    }

   /**
    * {@inheritDoc}
    */
    public String getTargAttrFiltersAciName() {
      return this.targAttrFiltersAciName;
    }

   /**
    * {@inheritDoc}
    */
    public void setTargAttrFiltersMatchOp(int flag) {
      this.targAttrMatch |= flag;
    }

   /**
    * {@inheritDoc}
    */
    public boolean hasTargAttrFiltersMatchOp(int flag) {
       return (this.targAttrMatch & flag) != 0;
    }

   /**
    * {@inheritDoc}
    */
    public void setDecidingAci(Aci aci) {
      this.decidingAci=aci;
    }

   /**
    * {@inheritDoc}
    */
    public String getDecidingAciName() {
      if(this.decidingAci != null)
         return this.decidingAci.getName();
      else return null;
    }

   /**
    * {@inheritDoc}
    */
    public void setEvalReason(EnumEvalReason reason) {
      this.evalReason=reason;
    }

   /**
    * {@inheritDoc}
    */
    public EnumEvalReason getEvalReason() {
      return this.evalReason;
    }

   /**
    * {@inheritDoc}
    */
    public void setEvalSummary(String summary) {
      this.summaryString=summary;
    }

   /**
    * {@inheritDoc}
    */
     public String getEvalSummary() {
      return this.summaryString;
    }

  /**
   * Returns true if the geteffectiverights control's authZid DN is equal to the
   * authoritzation entry's DN.
   *
   * @return True if the authZid is equal to the authorization entry's DN.
   */
    public boolean isAuthzidAuthorizationDN() {
     return this.authzid.equals(this.authorizationEntry.getDN());
    }

  /**
   * If the specified value is true, then the original authorization entry,
   * which is the  entry before the switch performed by the proxied
   * authorization control processing should be set to the current
   * authorization entry. If the specified value is false then the proxied
   * authorization entry is switched back using the saved copy.
   * @param val The value used to select the authorization entry to use.
   */
    public void useOrigAuthorizationEntry(boolean val) {
      if(val)
        authorizationEntry=origAuthorizationEntry;
      else
        authorizationEntry=saveAuthorizationEntry;
    }

   /**
    * {@inheritDoc}
    */
    public void setDenyList(LinkedList<Aci> denys) {
        denyList=denys;
    }

   /**
    * {@inheritDoc}
    */
    public void setAllowList(LinkedList<Aci> allows) {
        allowList=allows;
    }

   /**
    * {@inheritDoc}
    */
    public AttributeType getCurrentAttributeType() {
        return attributeType;
    }

   /**
    * {@inheritDoc}
    */
    public AttributeValue getCurrentAttributeValue() {
        return attributeValue;
    }

   /**
    * {@inheritDoc}
    */
    public void setCurrentAttributeType(AttributeType type) {
        attributeType=type;
    }

   /**
    * {@inheritDoc}
    */
    public void setCurrentAttributeValue(AttributeValue value) {
        attributeValue=value;
    }

   /**
    * {@inheritDoc}
    */
    public boolean isFirstAttribute() {
        return isFirst;
    }

   /**
    * {@inheritDoc}
    */
    public void setIsFirstAttribute(boolean val) {
        isFirst=val;
    }

   /**
    * {@inheritDoc}
    */
    public boolean hasEntryTestRule() {
        return isEntryTestRule;
    }

   /**
    * {@inheritDoc}
    */
   public void setEntryTestRule(boolean val) {
        isEntryTestRule=val;
    }

   /**
    * {@inheritDoc}
    */
    public Entry getResourceEntry() {
        return resourceEntry;
    }

   /**
    * {@inheritDoc}
    */
    public Entry getClientEntry() {
      return this.authorizationEntry;
    }

   /**
    * {@inheritDoc}
    */
    public LinkedList<Aci> getDenyList() {
        return denyList;
     }

   /**
    * {@inheritDoc}
    */
    public LinkedList<Aci> getAllowList() {
       return allowList;
    }

   /**
    * {@inheritDoc}
    */
    public boolean isDenyEval() {
        return isDenyEval;
    }

   /**
    * {@inheritDoc}
    */
    public boolean isAnonymousUser() {
        return !authInfo.isAuthenticated();
    }

   /**
    * {@inheritDoc}
    */
    public void setDenyEval(boolean val) {
        isDenyEval = val;
    }

   /**
    * {@inheritDoc}
    */
    public DN getClientDN() {
      if(this.useAuthzid)
        return this.authzid;
      else
       if (this.authorizationEntry == null)
         return DN.nullDN();
       else
         return this.authorizationEntry.getDN();
    }

   /**
    * {@inheritDoc}
    */
    public DN getResourceDN() {
        return resourceEntry.getDN();
    }

   /**
    * {@inheritDoc}
    */
    public boolean hasRights(int rights) {
       return (this.rightsMask & rights) != 0;
    }

   /**
    * {@inheritDoc}
    */
    public int getRights() {
        return this.rightsMask;
    }

   /**
    * {@inheritDoc}
    */
    public void setRights(int rights) {
         this.rightsMask=rights;
    }

   /**
    * {@inheritDoc}
    */
    public String getHostName() {
        return clientConnection.getRemoteAddress().getCanonicalHostName();
    }

   /**
    * {@inheritDoc}
    */
    public InetAddress getRemoteAddress() {
        return clientConnection.getRemoteAddress();
    }

   /**
    * {@inheritDoc}
    */
    public boolean isAddOperation() {
        return isAddOp;
    }

   /**
    * {@inheritDoc}
    */
    public void setTargAttrFiltersMatch(boolean v) {
        this.targAttrFiltersMatch=v;
    }

   /**
    * {@inheritDoc}
    */
    public boolean getTargAttrFiltersMatch() {
        return targAttrFiltersMatch;
    }

    /**
    * {@inheritDoc}
    */
    public String getControlOID() {
      return controlOID;
    }

   /**
    * {@inheritDoc}
    */
    public String getExtOpOID() {
      return extOpOID;
    }

    /**
     * Set the the controlOID value to the specified oid string.
     *
     * @param oid  The control oid string.
     */
    protected void setControlOID(String oid) {
      this.controlOID=oid;
    }


    /**
     * Set the extended operation OID value to the specified oid string.
     *
     * @param oid  The extended operation oid string.
     */
    protected void setExtOpOID(String oid) {
      this.extOpOID=oid;
    }

    /**
     * {@inheritDoc}
     */
    public EnumEvalResult hasAuthenticationMethod(EnumAuthMethod authMethod,
                                                  String saslMech) {
      EnumEvalResult matched=EnumEvalResult.FALSE;

      if(authMethod==EnumAuthMethod.AUTHMETHOD_NONE) {
        /**
         * None actually means any, in that we don't care what method was used.
         * This doesn't seem very intuitive or useful, but that's the way it is.
         */
        matched = EnumEvalResult.TRUE;
      } else {
        /*
         * Some kind of authentication is required.
         */
        if(authInfo.isAuthenticated()) {
          if(authMethod==EnumAuthMethod.AUTHMETHOD_SIMPLE) {
            if(authInfo.hasAuthenticationType(AuthenticationType.SIMPLE)) {
              matched = EnumEvalResult.TRUE;
            }
          } else if(authMethod == EnumAuthMethod.AUTHMETHOD_SSL) {
            /*
             * This means authentication using a certificate over TLS.
             *
             * We check the following:
             * - SASL EXTERNAL has been used, and
             * - TLS is the security provider, and
             * - The client provided a certificate.
             */
            if (authInfo.hasAuthenticationType(AuthenticationType.SASL) &&
                 authInfo.hasSASLMechanism(saslMech)) {
              ConnectionSecurityProvider provider =
                    clientConnection.getConnectionSecurityProvider();
              if (provider instanceof TLSConnectionSecurityProvider) {
                TLSConnectionSecurityProvider tlsProvider =
                      (TLSConnectionSecurityProvider) provider;
                 if (tlsProvider.getClientCertificateChain() != null) {
                   matched = EnumEvalResult.TRUE;
                 }
              }
            }
          } else {
            // A particular SASL mechanism.
            if (authInfo.hasAuthenticationType(AuthenticationType.SASL) &&
                 authInfo.hasSASLMechanism(saslMech)) {
              matched = EnumEvalResult.TRUE;
            }
          }
        }
      }
      return matched;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isMemberOf(Group<?> group) {
        boolean ret;
        try {
            if(useAuthzid) {
                ret = group.isMember(this.authzid);
            } else {
                Entry e = getClientEntry();
                if(e != null) {
                    ret=group.isMember(e);
                } else {
                    ret=group.isMember(getClientDN());
                }
            }
        } catch (DirectoryException ex) {
            ret=false;
        }
        return  ret;
    }

  /**
   * {@inheritDoc}
   */
    public String rightToString() {
      if(hasRights(ACI_SEARCH))
        return "search";
      else if(hasRights(ACI_COMPARE))
        return "compare";
      else if(hasRights(ACI_READ))
        return "read";
      else if(hasRights(ACI_DELETE))
        return "delete";
      else if(hasRights(ACI_ADD))
        return "add";
      else if(hasRights(ACI_WRITE))
        return "write";
      else if(hasRights(ACI_PROXY))
        return "proxy";
      else if(hasRights(ACI_IMPORT))
        return "import";
      else if(hasRights(ACI_EXPORT))
        return "export";
      else if(hasRights(ACI_WRITE) &&
              hasRights(ACI_SELF))
        return "selfwrite";
      return null;
  }

  /**
   * {@inheritDoc}
   */
  public  void setEvalUserAttributes(int v) {
    if(operation instanceof SearchOperation && (rightsMask == ACI_READ)) {
      if(v == ACI_FOUND_USER_ATTR_RULE) {
        evalAllAttributes |= ACI_FOUND_USER_ATTR_RULE;
        evalAllAttributes &= ~ACI_USER_ATTR_STAR_MATCHED;
      } else
        evalAllAttributes |= ACI_USER_ATTR_STAR_MATCHED;
    }
  }

     /**
   * {@inheritDoc}
   */
  public  void setEvalOpAttributes(int v) {
    if(operation instanceof SearchOperation && (rightsMask == ACI_READ)) {
      if(v == ACI_FOUND_OP_ATTR_RULE) {
        evalAllAttributes |= ACI_FOUND_OP_ATTR_RULE;
        evalAllAttributes &= ~ACI_OP_ATTR_PLUS_MATCHED;
      } else
        evalAllAttributes |= ACI_OP_ATTR_PLUS_MATCHED;
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean hasEvalUserAttributes() {
    return (evalAllAttributes & ACI_FOUND_USER_ATTR_RULE) ==
            ACI_FOUND_USER_ATTR_RULE;
  }

  /**
   * {@inheritDoc}
   */
  public boolean hasEvalOpAttributes() {
    return (evalAllAttributes & ACI_FOUND_OP_ATTR_RULE) ==
            ACI_FOUND_OP_ATTR_RULE;
  }

  /**
   * Return true if the evaluating ACI contained a targetattr all
   * user attributes rule match.
   *
   * @return  True if the above condition was seen.
   **/
  public boolean hasAllUserAttributes() {
    return (evalAllAttributes & ACI_USER_ATTR_STAR_MATCHED) ==
            ACI_USER_ATTR_STAR_MATCHED;
  }

  /**
   * Return true if the evaluating ACI contained a targetattr all
   * operational attributes rule match.
   *
   * @return  True if the above condition was seen.
   **/
    public boolean hasAllOpAttributes() {
    return (evalAllAttributes & ACI_OP_ATTR_PLUS_MATCHED) ==
            ACI_OP_ATTR_PLUS_MATCHED;
  }

  /**
   * {@inheritDoc}
   */
  public void clearEvalAttributes(int v) {
    if(v == 0)
      evalAllAttributes=0;
    else
      evalAllAttributes &= ~v;
  }
}
