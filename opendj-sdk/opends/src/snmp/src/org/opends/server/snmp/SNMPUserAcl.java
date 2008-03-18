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
package org.opends.server.snmp;

import com.sun.management.snmp.UserAcl;
import java.util.SortedSet;
import org.opends.server.admin.std.meta.SNMPConnectionHandlerCfgDefn.*;
import org.opends.server.admin.std.server.SNMPConnectionHandlerCfg;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;

public class SNMPUserAcl implements UserAcl {

    /**
     * The debug log tracer for this class.
     */
    private static final DebugTracer TRACER = DebugLogger.getTracer();
    /**
     * If * then all the users are allowed to access in read 
     */
    private static final String ALL_USERS_ALLOWED = "*";
    /**
     * Current Security Configuration for the SNMP Connection Handler.
     */
    private SNMPConnectionHandlerCfg currentConfig;
    /**
     * Configured hosts list 
     */
    private SortedSet usersList;
    /**
     * Configured traps destinations
     */
    private SortedSet trapDestinations;
    /**
     * Configured communities string
     */
    private SortedSet communityList;
    /**
     * Configured Security level
     */
    private SecurityLevel securityLevel;
    /**
     * User Name template - the only user getting the write access
     * on the USM MIB . Allowed to add user clones
     */
    private String templateUsername;

    /**
     * {@inheritDoc}
     * @param configuration of the SNMP Connection Handler
     */
    public SNMPUserAcl(SNMPConnectionHandlerCfg configuration) {
        // Keep the configuration
        this.currentConfig = configuration;
        // Get the community/context string to accept 
        this.communityList = this.currentConfig.getCommunity();
        // Get the list of allowed users (SNMPV3)
        this.usersList = this.currentConfig.getAllowedUser();
        // Get the traps destinations
        this.trapDestinations = this.currentConfig.getTrapsDestination();
        // Get the min security level to accept
        this.securityLevel = this.currentConfig.getSecurityLevel();
        // Get the user name template allowed to add users in the USM MIB
        this.templateUsername = this.currentConfig.getTemplateUsername();
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        // ACL Name
        return "OpenDS";
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkReadPermission(String user) {
        if ((this.usersList.contains(ALL_USERS_ALLOWED)) ||
                (this.usersList.contains(user))) {
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkReadPermission(String user, String contextName,
            int securityLevel) {

        // Special check for the defaultUser
        if ((user.equals(this.templateUsername)) && (contextName.equals("null")) && ((this.securityLevel.ordinal() + 1) >= securityLevel)) {
            return true;
        }

        // Else
        if ((checkReadPermission(user))  &&
                ((this.securityLevel.ordinal() + 1) >= securityLevel)) {
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkContextName(String contextName) {
        return this.communityList.contains(contextName);
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkWritePermission(String user) {
        if (user.equals(this.templateUsername)) {
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkWritePermission(String user, String contextName,
            int securityLevel) {
        if ((checkWritePermission(user)) &&
                (contextName.equals("null")) &&
                ((this.securityLevel.ordinal() + 1) >= securityLevel)) {
            return true;
        }
        return false;
    }
}
