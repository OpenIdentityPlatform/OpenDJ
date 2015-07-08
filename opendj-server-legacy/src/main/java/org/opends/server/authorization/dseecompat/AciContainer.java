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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.server.authorization.dseecompat.Aci.*;
import static org.opends.server.authorization.dseecompat.AciHandler.*;
import static org.opends.server.util.ServerConstants.*;

import java.net.InetAddress;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.Group;
import org.opends.server.controls.GetEffectiveRightsRequestControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.ldap.LDAPClientConnection;
import org.opends.server.types.*;

/**
 *  The AciContainer class contains all of the needed information to perform
 *  both target match and evaluate an ACI. Target matching is the process
 *  of testing if an ACI is applicable to an operation, and evaluation is
 *  the actual access evaluation of the ACI.
 */
public abstract class AciContainer
implements AciTargetMatchContext, AciEvalContext {

    /**
     * The allow and deny lists.
     */
    private List<Aci> denyList, allowList;

    /**
     * The attribute type in the resource entry currently being evaluated.
     */
    private AttributeType attributeType;

    /**
     * The attribute type value in the resource entry currently being
     * evaluated.
     */
    private ByteString attributeValue;

    /**
     * True if this is the first attribute type in the resource entry being
     * evaluated.
     */
    private boolean isFirst;

    /**
     * True if an entry test rule was seen during target matching of an ACI
     * entry. A entry test rule is an ACI with targetattrs target keyword.
     */
    private boolean isEntryTestRule;

    /**
     * The right mask to use in the evaluation of the LDAP operation.
     */
    private int rightsMask;

    /**
     * The entry being evaluated (resource entry).
     */
    private Entry resourceEntry;

    /**
     * The client connection information.
     */
    private final ClientConnection clientConnection;

    /**
     * The operation being evaluated.
     */
    private final Operation operation;

    /**
     * True if a targattrfilters match was found.
     */
    private boolean targAttrFiltersMatch;

    /**
     * The authorization entry currently being evaluated. If proxied
     * authorization is being used and the handler is doing a proxy access
     * check, then this entry will switched to the original authorization entry
     * rather than the proxy ID entry. If the check succeeds, it will be
     * switched back for non-proxy access checking. If proxied authentication
     * is not being used then this entry never changes.
     */
    private Entry authorizationEntry;

    /**
     * True if proxied authorization is being used.
     */
    private boolean proxiedAuthorization;

    /**
     * Used by proxied authorization processing. True if the entry has already
     * been processed by an access proxy check. Some operations might perform
     * several access checks on the same entry (modify DN), this
     * flag is used to bypass the proxy check after the initial evaluation.
     */
    private boolean seenEntry;

    /**
     *  True if geteffectiverights evaluation is in progress.
     */
    private boolean isGetEffectiveRightsEval;

    /**
     *  True if the operation has a geteffectiverights control.
     */
    private boolean hasGetEffectiveRightsControl;

    /**
     * The geteffectiverights authzID in DN format.
     */
    private DN authzid;

    /**
     * True if the authZid should be used as the client DN, only used in
     * geteffectiverights evaluation.
     */
    private boolean useAuthzid;

    /**
     * The list of specific attributes to get rights for, in addition to
     * any attributes requested in the search.
     */
    private List<AttributeType> specificAttrs;

    /**
     * Table of ACIs that have targattrfilter keywords that matched. Used
     * in geteffectiverights attributeLevel write evaluation.
     */
    private final HashMap<Aci,Aci> targAttrFilterAcis = new HashMap<>();

    /**
     * The name of a ACI that decided an evaluation and contained a
     * targattrfilter keyword. Used in geteffectiverights attributeLevel
     * write evaluation.
     */
    private String targAttrFiltersAciName;

    /**
     * Value that is used to store the allow/deny result of a deciding ACI
     * containing a targattrfilter keyword.  Used in geteffectiverights
     * attributeLevel write evaluation.
     */
    private int targAttrMatch;

    /**
     * The ACI that decided the last evaluation. Used in geteffectiverights
     * loginfo processing.
     */
    private Aci decidingAci;

    /**
     * The reason the last evaluation decision was made. Used both
     * in geteffectiverights loginfo processing and attributeLevel write
     * evaluation.
     */
    private EnumEvalReason evalReason;

    /**
     * A summary string holding the last evaluation information in textual
     * format. Used in geteffectiverights loginfo processing.
     */
    private String summaryString;

   /**
    * Flag used to determine if ACI all attributes target matched.
    */
    private int evalAllAttributes;

   /**
    * String used to hold a control OID string.
    */
    private String controlOID;

   /**
    * String used to hold an extended operation OID string.
    */
    private String extOpOID;

    /**
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
      this.authInfo = clientConnection.getAuthenticationInfo();

      //If the proxied authorization control was processed, then the operation
      //will contain an attachment containing the original authorization entry.
      final Entry origAuthorizationEntry = (Entry) operation.getAttachment(ORIG_AUTH_ENTRY);
      this.proxiedAuthorization = origAuthorizationEntry != null;
      this.authorizationEntry=operation.getAuthorizationEntry();

      //The ACI_READ right at constructor time can only be the result of the
      //AciHandler.filterEntry method. This method processes the
      //geteffectiverights control, so it needs to check for it.  There are
      //two other checks done, because the resource entry passed to that method
      //is filtered (it may not contain enough attribute information
      //to evaluate correctly). See the the comments below.
      if (rights == ACI_READ) {
        //Checks if a geteffectiverights control was sent and
        //sets up the structures needed.
        GetEffectiveRightsRequestControl getEffectiveRightsControl =
              (GetEffectiveRightsRequestControl)
                      operation.getAttachment(OID_GET_EFFECTIVE_RIGHTS);
        if (getEffectiveRightsControl != null
            && operation instanceof SearchOperation)
        {
          hasGetEffectiveRightsControl = true;
          if (getEffectiveRightsControl.getAuthzDN() == null) {
            this.authzid = getClientDN();
          } else {
            this.authzid = getEffectiveRightsControl.getAuthzDN();
          }
          this.specificAttrs = getEffectiveRightsControl.getAttributes();
        }

        //If an ACI evaluated because of an Targetattr="*", then the
        //AciHandler.maySend method signaled this via adding this attachment
        //string.
        String allUserAttrs=
                  (String)operation.getAttachment(ALL_USER_ATTRS_MATCHED);
        if(allUserAttrs != null)
        {
          evalAllAttributes |= ACI_USER_ATTR_STAR_MATCHED;
        }
        //If an ACI evaluated because of an Targetattr="+", then the
        //AciHandler.maySend method signaled this via adding this attachment
        //string.
        String allOpAttrs=(String)operation.getAttachment(ALL_OP_ATTRS_MATCHED);
        if(allOpAttrs != null)
        {
          evalAllAttributes |= ACI_OP_ATTR_PLUS_MATCHED;
        }
      }

      //Reference the current authorization entry, so it can be put back
      //if an access proxy check was performed.
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
   * Set to true if an entry has already been processed by an access proxy
   * check.
   *
   * @param val The value to set the seenEntry boolean to.
   */
    public void setSeenEntry(boolean val) {
     this.seenEntry=val;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isProxiedAuthorization() {
         return this.proxiedAuthorization;
    }

    /** {@inheritDoc} */
    @Override
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
   * geteffectiverights control.
   *
   * @return The list of attributes to return rights information about in the
   * entry.
   */
    public List<AttributeType> getSpecificAttributes() {
       return this.specificAttrs;
    }

    /** {@inheritDoc} */
    @Override
    public void addTargAttrFiltersMatchAci(Aci aci) {
      this.targAttrFilterAcis.put(aci, aci);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasTargAttrFiltersMatchAci(Aci aci) {
      return this.targAttrFilterAcis.containsKey(aci);
    }

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
    public void setTargAttrFiltersAciName(String name) {
      this.targAttrFiltersAciName=name;
    }

    /** {@inheritDoc} */
    @Override
    public String getTargAttrFiltersAciName() {
      return this.targAttrFiltersAciName;
    }

    /** {@inheritDoc} */
    @Override
    public void setTargAttrFiltersMatchOp(int flag) {
      this.targAttrMatch |= flag;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasTargAttrFiltersMatchOp(int flag) {
       return (this.targAttrMatch & flag) != 0;
    }

    /** {@inheritDoc} */
    @Override
    public String getDecidingAciName() {
      if(this.decidingAci != null) {
        return this.decidingAci.getName();
      }
      return null;
    }

  /** {@inheritDoc} */
  @Override
  public void setEvaluationResult(EnumEvalReason reason, Aci decidingAci)
  {
    this.evalReason = reason;
    this.decidingAci = decidingAci;
  }

    /** {@inheritDoc} */
    @Override
    public EnumEvalReason getEvalReason() {
      return this.evalReason;
    }

    /** {@inheritDoc} */
    @Override
    public void setEvalSummary(String summary) {
      this.summaryString=summary;
    }

    /** {@inheritDoc} */
    @Override
    public String getEvalSummary() {
      return this.summaryString;
    }

  /**
   * Returns true if the geteffectiverights control's authZid DN is equal to the
   * authorization entry's DN.
   *
   * @return True if the authZid is equal to the authorization entry's DN.
   */
    public boolean isAuthzidAuthorizationDN() {
     return this.authzid.equals(this.authorizationEntry.getName());
    }

    /** {@inheritDoc} */
    @Override
    public void setDenyList(List<Aci> denys) {
        denyList=denys;
    }

    /** {@inheritDoc} */
    @Override
    public void setAllowList(List<Aci> allows) {
        allowList=allows;
    }

    /** {@inheritDoc} */
    @Override
    public AttributeType getCurrentAttributeType() {
        return attributeType;
    }

    /** {@inheritDoc} */
    @Override
    public ByteString getCurrentAttributeValue() {
        return attributeValue;
    }

    /** {@inheritDoc} */
    @Override
    public void setCurrentAttributeType(AttributeType type) {
        attributeType=type;
    }

    /** {@inheritDoc} */
    @Override
    public void setCurrentAttributeValue(ByteString value) {
        attributeValue=value;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFirstAttribute() {
        return isFirst;
    }

    /** {@inheritDoc} */
    @Override
    public void setIsFirstAttribute(boolean val) {
        isFirst=val;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasEntryTestRule() {
        return isEntryTestRule;
    }

    /** {@inheritDoc} */
    @Override
    public void setEntryTestRule(boolean val) {
        isEntryTestRule=val;
    }

    /** {@inheritDoc} */
    @Override
    public Entry getResourceEntry() {
        return resourceEntry;
    }

    /** {@inheritDoc} */
    @Override
    public Entry getClientEntry() {
      return this.authorizationEntry;
    }

    /** {@inheritDoc} */
    @Override
    public List<Aci> getDenyList() {
        return denyList;
    }

    /** {@inheritDoc} */
    @Override
    public List<Aci> getAllowList() {
       return allowList;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDenyEval() {
        return EnumEvalReason.NO_ALLOW_ACIS.equals(evalReason)
            || EnumEvalReason.EVALUATED_DENY_ACI.equals(evalReason);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAnonymousUser() {
        return !authInfo.isAuthenticated();
    }

    /** {@inheritDoc} */
    @Override
    public DN getClientDN() {
      if(this.useAuthzid)
      {
        return this.authzid;
      }
      else if (this.authorizationEntry != null)
      {
        return this.authorizationEntry.getName();
      }
      return DN.rootDN();
    }

    /** {@inheritDoc} */
    @Override
    public DN getResourceDN() {
        return resourceEntry.getName();
    }

   /**
    * {@inheritDoc}
    * <p>
    * JNR: I find the implementation in this method dubious.
    *
    * @see EnumRight#hasRights(int, int)
    */
    @Override
    public boolean hasRights(int rights) {
       return (this.rightsMask & rights) != 0;
    }

    /** {@inheritDoc} */
    @Override
    public int getRights() {
        return this.rightsMask;
    }

    /** {@inheritDoc} */
    @Override
    public void setRights(int rights) {
         this.rightsMask=rights;
    }

    /** {@inheritDoc} */
    @Override
    public String getHostName() {
        return clientConnection.getRemoteAddress().getCanonicalHostName();
    }

    /** {@inheritDoc} */
    @Override
    public InetAddress getRemoteAddress() {
        return clientConnection.getRemoteAddress();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAddOperation() {
        return operation instanceof AddOperation;
    }

    /** {@inheritDoc} */
    @Override
    public void setTargAttrFiltersMatch(boolean v) {
        this.targAttrFiltersMatch=v;
    }

    /** {@inheritDoc} */
    @Override
    public boolean getTargAttrFiltersMatch() {
        return targAttrFiltersMatch;
    }

    /** {@inheritDoc} */
    @Override
    public String getControlOID() {
      return controlOID;
    }

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
    public EnumEvalResult hasAuthenticationMethod(EnumAuthMethod authMethod,
                                                  String saslMech) {
      EnumEvalResult matched=EnumEvalResult.FALSE;

      if(authMethod==EnumAuthMethod.AUTHMETHOD_NONE) {
        /*
         * None actually means any, in that we don't care what method was used.
         * This doesn't seem very intuitive or useful, but that's the way it is.
         */
        matched = EnumEvalResult.TRUE;
      } else {
        // Some kind of authentication is required.
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
            if (authInfo.hasAuthenticationType(AuthenticationType.SASL)
                && authInfo.hasSASLMechanism(saslMech)
                && clientConnection instanceof LDAPClientConnection) {
                LDAPClientConnection lc = (LDAPClientConnection) clientConnection;
                Certificate[] certChain = lc.getClientCertificateChain();
                if (certChain.length != 0) {
                  matched = EnumEvalResult.TRUE;
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

    /** {@inheritDoc} */
    @Override
    public boolean isMemberOf(Group<?> group) {
        try {
            if(useAuthzid) {
                return group.isMember(this.authzid);
            }
            Entry e = getClientEntry();
            if (e != null) {
                return group.isMember(e);
            }
            return group.isMember(getClientDN());
        } catch (DirectoryException ex) {
            return false;
        }
    }

  /**
   * {@inheritDoc}
   * <p>
   * JNR: I find the implementation in this method dubious.
   *
   * @see EnumRight#getEnumRight(int)
   */
    @Override
    public String rightToString() {
      if(hasRights(ACI_SEARCH))
      {
        return "search";
      }
      else if(hasRights(ACI_COMPARE))
      {
        return "compare";
      }
      else if(hasRights(ACI_READ))
      {
        return "read";
      }
      else if(hasRights(ACI_DELETE))
      {
        return "delete";
      }
      else if(hasRights(ACI_ADD))
      {
        return "add";
      }
      else if(hasRights(ACI_WRITE))
      {
        return "write";
      }
      else if(hasRights(ACI_PROXY))
      {
        return "proxy";
      }
      else if(hasRights(ACI_IMPORT))
      {
        return "import";
      }
      else if(hasRights(ACI_EXPORT))
      {
        return "export";
      }
      else if(hasRights(ACI_WRITE) &&
              hasRights(ACI_SELF))
      {
        return "selfwrite";
      }
      return null;
  }

  /** {@inheritDoc} */
  @Override
  public  void setEvalUserAttributes(int v) {
    if(operation instanceof SearchOperation && (rightsMask == ACI_READ)) {
      if(v == ACI_FOUND_USER_ATTR_RULE) {
        evalAllAttributes |= ACI_FOUND_USER_ATTR_RULE;
        evalAllAttributes &= ~ACI_USER_ATTR_STAR_MATCHED;
      }
      else
      {
        evalAllAttributes |= ACI_USER_ATTR_STAR_MATCHED;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public  void setEvalOpAttributes(int v) {
    if(operation instanceof SearchOperation && (rightsMask == ACI_READ)) {
      if(v == ACI_FOUND_OP_ATTR_RULE) {
        evalAllAttributes |= ACI_FOUND_OP_ATTR_RULE;
        evalAllAttributes &= ~ACI_OP_ATTR_PLUS_MATCHED;
      }
      else
      {
        evalAllAttributes |= ACI_OP_ATTR_PLUS_MATCHED;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasEvalUserAttributes() {
    return hasAttribute(ACI_FOUND_USER_ATTR_RULE);
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasEvalOpAttributes() {
    return hasAttribute(ACI_FOUND_OP_ATTR_RULE);
  }

  /**
   * Return true if the evaluating ACI contained a targetattr all
   * user attributes rule match.
   *
   * @return  True if the above condition was seen.
   */
  public boolean hasAllUserAttributes() {
    return hasAttribute(ACI_USER_ATTR_STAR_MATCHED);
  }

  /**
   * Return true if the evaluating ACI contained a targetattr all
   * operational attributes rule match.
   *
   * @return  True if the above condition was seen.
   */
  public boolean hasAllOpAttributes() {
    return hasAttribute(ACI_OP_ATTR_PLUS_MATCHED);
  }

  private boolean hasAttribute(int aciAttribute)
  {
    return (evalAllAttributes & aciAttribute) == aciAttribute;
  }

  /** {@inheritDoc} */
  @Override
  public void clearEvalAttributes(int v) {
    if(v == 0)
    {
      evalAllAttributes=0;
    }
    else
    {
      evalAllAttributes &= ~v;
    }
  }

  /** {@inheritDoc} */
  @Override
  public int getCurrentSSF() {
      return clientConnection.getSSF();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    final StringBuilder sb = new StringBuilder();
    if (attributeType != null)
    {
      appendSeparatorIfNeeded(sb);
      sb.append("attributeType: ").append(attributeType.getNameOrOID());
      if (attributeValue != null)
      {
        sb.append(":").append(attributeValue);
      }
    }
    appendSeparatorIfNeeded(sb);
    sb.append(size(allowList)).append(" allow ACIs");
    appendSeparatorIfNeeded(sb);
    sb.append(size(denyList)).append(" deny ACIs");
    if (evalReason != null)
    {
      appendSeparatorIfNeeded(sb);
      sb.append("evaluationResult: ").append(evalReason);
      if (decidingAci != null)
      {
        sb.append(",").append(decidingAci);
      }
    }
    return sb.toString();
  }

  private void appendSeparatorIfNeeded(StringBuilder sb)
  {
    if (sb.length() > 0)
    {
      sb.append(", ");
    }
  }

  private int size(Collection<?> col)
  {
    if (col != null)
    {
      return col.size();
    }
    return 0;
  }
}
