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

/**
 * The SNMP User ACL.
 *
 */
public class SNMPUserAcl implements UserAcl {

    /**
     * The debug log tracer for this class.
     */
    private static final DebugTracer TRACER = DebugLogger.getTracer();
    /**
     * If * then all the users are allowed to access in read.
     */
    private static final String ALL_USERS_ALLOWED = "*";
    /**
     * Default User for cloning mechanism.
     */
    private static final String DEFAULT_USER = "defaultUser";
    /**
     * Admin User for cloning mechanism.
     */
    private static final String ADMIN_USER = "snmpAdmin";
    /**
     * Current Security Configuration for the SNMP Connection Handler.
     */
    private SNMPConnectionHandlerCfg currentConfig;
    /**
     * Configured hosts list.
     */
    private SortedSet usersList;
    /**
     * Configured traps destinations.
     */
    private SortedSet trapDestinations;
    /**
     * Configured context name.
     */
    private String contextName;
    /**
     * Configured Security level.
     */
    private int securityLevel;

    /**
     * {@inheritDoc}
     * @param configuration of the SNMP Connection Handler
     */
    public SNMPUserAcl(SNMPConnectionHandlerCfg configuration) {
        // Keep the configuration
        this.currentConfig = configuration;
        // Get the community/context string to accept
        this.contextName = this.currentConfig.getCommunity();
        // Get the list of allowed users (SNMPV3)
        this.usersList = this.currentConfig.getAllowedUser();
        // Get the traps destinations
        this.trapDestinations = this.currentConfig.getTrapsDestination();
        // Get the min security level to accept
        SecurityLevel level = this.currentConfig.getSecurityLevel();
        this.securityLevel =
                SNMPConnectionHandlerDefinitions.SECURITY_LEVELS.get(
                level.toString());
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

        // Test if clone user
        if (user.equals(DEFAULT_USER)) {
            return false;
        }

        // Test if clone user
        if (user.equals(ADMIN_USER)) {
            return false;
        }

        if ((this.usersList.contains(ALL_USERS_ALLOWED)) ||
                (this.usersList.contains(user))) {
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     * @param user
     * @param contextName
     * @param securityLevel
     */
    public boolean checkReadPermission(String user, String contextName,
            int securityLevel) {

        // Special check for the defaultUser
        if ((user.equals(ADMIN_USER)) && (contextName.equals("null"))
                && ((checkSecurityLevel(securityLevel)))) {
            return true;
        }

        // Else
        if ((checkReadPermission(user)) &&
                ((checkContextName(contextName))) &&
                (checkSecurityLevel(securityLevel))) {
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     * @return true if the context is correct, false otherwise.
     */
    public boolean checkContextName(String contextName) {
        return this.contextName.equals(contextName);
    }

    /**
     * {@inheritDoc}
     * @param user to check the write permission.
     * @return true if the user has the write permission, false otherwise.
     */
    public boolean checkWritePermission(String user) {
        if (user.equals(ADMIN_USER)) {
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
                (checkSecurityLevel(securityLevel))) {
            return true;
        }
        return false;
    }

    /**
     * Check the incoming security level of the request.
     * @param securityLevel
     * @return true if the securityLevel is appropriated, else return false
     */
    private boolean checkSecurityLevel(int securityLevel) {

        if (securityLevel >= this.securityLevel) {
            return true;
        }
        return false;
    }
}
