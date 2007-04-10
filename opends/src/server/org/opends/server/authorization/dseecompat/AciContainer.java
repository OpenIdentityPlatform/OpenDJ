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

import org.opends.server.types.*;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.Group;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.core.AddOperation;
import org.opends.server.core.Operation;
import org.opends.server.extensions.TLSConnectionSecurityProvider;
import java.net.InetAddress;
import java.util.LinkedList;
import static org.opends.server.authorization.dseecompat.AciHandler.*;

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
     * The rights to use in the evaluation of the LDAP operation.
     */
    private int rights;

    /*
     * The entry being evaluated (resource entry).
     */
    private Entry resourceEntry;

    /*
     * The client connection information.
     */
    private ClientConnection clientConnection;

    /*
     * The operation being evaluated.
     */
    private Operation operation;

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
    private Entry saveAuthorizationEntry;

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

    /**
     * This constructor is used by all currently supported LDAP operations.
     *
     * @param operation The Operation object being evaluated and target
     * matching.
     * @param rights The rights array to use in evaluation and target matching.
     * @param entry The current entry being evaluated and target matched.
     */
    protected AciContainer(Operation operation, int rights, Entry entry) {
      this.resourceEntry=entry;
      this.operation=operation;
      this.clientConnection=operation.getClientConnection();
      if(operation instanceof AddOperation)
          this.isAddOp=true;

      //If the proxied authorization control was processed, then the operation
      //will contain an attachment containing the original authorization entry.
      this.origAuthorizationEntry =
                      (Entry) operation.getAttachment(ORIG_AUTH_ENTRY);
      if(origAuthorizationEntry != null)
         this.proxiedAuthorization=true;
      this.authorizationEntry=operation.getAuthorizationEntry();

      //Reference the current authorization entry, so it can be put back
      //if an access proxy check was performed.
      this.saveAuthorizationEntry=this.authorizationEntry;
      this.rights = rights;
    }

  /**
   * Returns true if an entry has already been processed by an access proxy
   * check.
   * @return True if an entry has already been processed by an access proxy
   * check.
   */
   public boolean hasSeenEntry() {
      return this.seenEntry;
    }

  /**
   * Set to true if an entry has already been processsed by an access proxy
   * check.
   * @param val The value to set the seenEntry boolean to.
   */
    public void setSeenEntry(boolean val) {
     this.seenEntry=val;
    }

  /**
   * Returns true if proxied authorization is being used.
   * @return  True if proxied authorization is being used.
   */
    public boolean isProxiedAuthorization() {
         return this.proxiedAuthorization;
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
     * The list of deny ACIs. These are all of the applicable
     * ACIs that have a deny permission. Note that an ACI can
     * be on both allow and deny list if it has multiple
     * permission-bind rule pairs.
     *
     * @param denys The list of deny ACIs.
     */
    public void setDenyList(LinkedList<Aci> denys) {
        denyList=denys;
    }

    /**
     * The list of allow ACIs. These are all of the applicable
     * ACIs that have an allow permission.
     *
     * @param allows  The list of allow ACIs.
     */
    public void setAllowList(LinkedList<Aci> allows) {
        allowList=allows;
    }

    /**
     * Return the current attribute type being evaluated.
     * @return  Attribute type being evaluated.
     */
    public AttributeType getCurrentAttributeType() {
        return attributeType;
    }

    /**
     * Return the current attribute type value being evaluated.
     * @return Attribute type value being evaluated.
     */
    public AttributeValue getCurrentAttributeValue() {
        return attributeValue;
    }

    /**
     * Set the attribute type to be evaluated.
     * @param type The attribute type to evaluate.
     */
    public void setCurrentAttributeType(AttributeType type) {
        attributeType=type;
    }

    /**
     * Set the attribute type value to be evaluated.
     * @param value The attribute type value to evaluate.
     */
    public void setCurrentAttributeValue(AttributeValue value) {
        attributeValue=value;
    }

    /**
     * Check is this the first attribute being evaluated in an entry.
     * @return  True if it is the first attribute.
     */
    public boolean isFirstAttribute() {
        return isFirst;
    }

    /**
     * Set if this is the first attribute in the entry.
     * @param val True if this is the first attribute being evaluated in the
     * entry.
     */
    public void setIsFirstAttribute(boolean val) {
        isFirst=val;
    }

    /**
     * Check if an entry test rule was seen during target evaluation.
     * @return True if an entry test rule was seen.
     */
    public boolean hasEntryTestRule() {
        return isEntryTestRule;
    }

    /**
     * Used to set if an entry test rule was seen during target evaluation.
     * @param val Set to true if an entry test rule was seen.
     */
    public void setEntryTestRule(boolean val) {
        isEntryTestRule=val;
    }

    /**
     * Get the entry being evaluated (known as the resource entry).
     * @return  The entry being evaluated.
     */
    public Entry getResourceEntry() {
        return resourceEntry;
    }

    /**
     * Get the entry that corresponds to the client DN.
     * @return The client entry.
     */
    public Entry getClientEntry() {
      return this.authorizationEntry;
    }

    /**
     * Get the deny list of ACIs.
     * @return The deny ACI list.
     */
    public LinkedList<Aci> getDenyList() {
        return denyList;
     }

    /**
     * Get the allow list of ACIs.
     * @return The allow ACI list.
     */
    public LinkedList<Aci> getAllowList() {
       return allowList;
    }

    /**
     * Check is this is a deny ACI evaluation.
     * @return  True if the evaluation is using an ACI from
     * deny list.
     */
    public boolean isDenyEval() {
        return isDenyEval;
    }

    /**
     * Check is this operation bound anonymously.
     * @return  True if the authentication is anonymous.
     */
    public boolean isAnonymousUser() {
        return !clientConnection.getAuthenticationInfo().isAuthenticated();
    }

    /**
     * Set the deny evaluation flag.
     * @param val True if this evaluation is a deny ACI.
     */
    public void setDenyEval(boolean val) {
        isDenyEval = val;
    }

    /**
     * Returns the client authorization DN known as the client DN.
     * @return  The client's authorization DN.
     */
    public DN getClientDN() {
      return this.authorizationEntry.getDN();
    }

    /**
     * Get the DN of the entry being evaluated.
     * @return The DN of the entry.
     */
    public DN getResourceDN() {
        return resourceEntry.getDN();
    }

    /**
     * Checks if the container's rights has the specified rights.
     * @param  rights The rights to check for.
     * @return True if the container's rights has the specified rights.
     */
    public boolean hasRights(int rights) {
       return (this.rights & rights) != 0;
    }

    /**
     * Return the rights set for this container's LDAP operation.
     * @return  The rights set for the container's LDAP operation.
     */
    public int getRights() {
        return this.rights;
    }

    /**
     * Sets the rights for this container to the specified rights.
     * @param rights The rights to set the container's rights to.
     */
    public void setRights(int rights) {
         this.rights=rights;
    }

    /**
     * Gets the hostname of the remote client.
     * @return  Cannonical hostname of remote client.
     */
    public String getHostName() {
        return clientConnection.getRemoteAddress().getCanonicalHostName();
    }

    /**
     * Gets the remote client's address information.
     * @return  Remote client's address.
     */
    public InetAddress getRemoteAddress() {
        return clientConnection.getRemoteAddress();
    }

    /**
     * Return true if the current operation is a LDAP add operation.
     * @return True if this is an add operation.
     */
    public boolean isAddOperation() {
        return isAddOp;
    }

    /**
     * Set to true  if the ACI had a targattrfilter rule that matched.
     * @param v  The value to use.
     */
    public void setTargAttrFiltersMatch(boolean v) {
        this.targAttrFiltersMatch=v;
    }

    /**
     * Return the value of the targAttrFiltersMatch variable. This is set to
     * true if the ACI had a targattrfilter rule that matched.
     * @return  True if the ACI had a targattrfilter rule that matched.
     */
    public boolean getTargAttrFiltersMatch() {
        return targAttrFiltersMatch;
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
        AuthenticationInfo authInfo=clientConnection.getAuthenticationInfo();
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
     * Convenience method that checks if the the clientDN is a member of the
     * specified group.
     * @param group The group to check membership in.
     * @return True if the clientDN is a member of the specified group.
     */
    public boolean isMemberOf(Group group) {
        boolean ret;
        try {
           ret=clientConnection.isMemberOf(group, operation);
        } catch (DirectoryException ex) {
            ret=false;
        }
        return  ret;
    }
}
