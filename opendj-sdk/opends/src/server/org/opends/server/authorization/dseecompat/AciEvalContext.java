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

import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.api.Group;

import java.net.InetAddress;
import java.util.LinkedList;

/**
 * Interface that provides a view of the AciContainer that is
 * used by the ACI evaluation code to evaluate an ACI.
 */
public interface AciEvalContext
{
    /**
     * Get client DN. The client DN is the authorization DN.
     * @return   The client DN.
     */
    public DN getClientDN();

    /**
     * Get the client entry. The client entry is the entry that corresponds
     * to the client DN.
     * @return The client entry corresponding to the client DN.
     */
    public Entry getClientEntry();

    /**
     * Get the resource DN. The resource DN is the DN of the entry being
     * evaluated.
     * @return   The resource DN.
     */
    public DN getResourceDN();

    /**
     * Get the list of deny ACIs.
     * @return The deny ACI list.
     */
    public LinkedList<Aci> getDenyList();

    /**
     * Get the list allow ACIs.
     * @return The allow ACI list.
     */
    public LinkedList<Aci> getAllowList();

    /**
     * Set when the deny list is being evaluated.
     * @param v True if deny's are being evaluated.
     */
    public void setDenyEval(boolean v);

    /**
     * Returns true if the deny list is being evaluated.
     * @return True if the deny list is being evaluated.
     */
    public boolean isDenyEval();

    /**
     * Check if the remote client is bound anonymously.
     * @return True if client is bound anonymously.
     */
    public boolean isAnonymousUser();

    /**
     * Return the rights set for this container's LDAP operation.
     * @return  The rights set for the container's LDAP operation.
     */
    public int getRights();

    /**
     * Return the entry being evaluated
     * .
     * @return The evaluation entry.
     */
    public Entry getResourceEntry();

    /**
     * Get the hostname of the bound connection.
     * @return The hostname of the connection.
     */
    public String getHostName();

    /**
     * Determine whether the client connection has been authenticated using
     * a specified authentication method.  This method is used for the
     * authmethod bind rule keyword.
     *
     * @param authMethod The required authentication method.
     * @param saslMech The required SASL mechanism if the authentication method
     * is SASL.
     * @return An evaluation result indicating whether the client connection
     * has been authenticated using the required authentication method.
     */
    public EnumEvalResult hasAuthenticationMethod(EnumAuthMethod authMethod,
                                                  String saslMech);

    /**
     * Get the  address of the bound connection.
     * @return The  address of the bound connection.
     */
    public InetAddress getRemoteAddress();

    /**
     * Return true if this is an add operation, needed by the userattr
     * USERDN parent inheritance level 0 processing.
     * @return True if this is an add operation.
     */
    public boolean isAddOperation();

    /**
     * Return true if the operation associated with this evaluation
     * context is a member of the specified group. Calls the
     * ClientConnection.isMemberOf() method, which checks authorization
     * DN membership in the specified group.
     * @param group The group to check membership in.
     * @return True if the authorization DN of the operation is a
     * member of the specified group.
     */
    public boolean isMemberOf(Group group);
}
