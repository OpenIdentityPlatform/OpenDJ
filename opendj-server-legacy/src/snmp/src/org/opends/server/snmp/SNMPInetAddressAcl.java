/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.snmp;

import com.sun.management.snmp.InetAddressAcl;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.Vector;

import org.forgerock.opendj.server.config.server.SNMPConnectionHandlerCfg;

/**
 * This class allows to manage the IP-ACL based access rights
 * for SNMP v1/v2c.
 */
public class SNMPInetAddressAcl implements InetAddressAcl {

    /**
     * Current Security Configuration for the SNMP Connection Handler.
     */
    private SNMPConnectionHandlerCfg currentConfig;
    /**
     * If * then all the users are allowed to access in read.
     */
    private static final String ALL_MANAGERS_ALLOWED = "*";

    private Set<InetAddress> hostsList;
    private boolean allManagers = false;

    private SortedSet<String> trapsDestinations;
    private String trapsCommunity;

    private String communities;


    /**
     * Creates an IP-Based ACL controller.
     * @param configuration of the Configuration
     */
    public SNMPInetAddressAcl(SNMPConnectionHandlerCfg configuration) {
        super();
        // Get the current configuration
        this.currentConfig = configuration;

        // hostsList
        SortedSet<String> tmp = this.currentConfig.getAllowedManager();
        if (tmp.contains(ALL_MANAGERS_ALLOWED)) {
            this.allManagers=true;
        }
        this.hostsList = new HashSet<InetAddress>();
        // Transform the String list into InetAddress List
        for (String dest : tmp) {
            try {
                this.hostsList.add(InetAddress.getByName(dest));
            } catch (UnknownHostException ignore) {
            }
        }

        // Get the list of trap destinations
        this.trapsDestinations = this.currentConfig.getTrapsDestination();
        // Get the community string to accept
        this.communities = this.currentConfig.getCommunity();
        // Get the community string to set in the traps
        this.trapsCommunity = this.currentConfig.getTrapsCommunity();
    }

    /**
     * Gets the name of the acl.
     * @return the name of the acl as a String
     */
    public String getName() {
        return "OpenDS";
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkReadPermission(InetAddress address) {
        if (this.allManagers) {
            return true;
        }

        if ((this.hostsList==null) || (this.hostsList.isEmpty())) {
            return false;
        }

        // check the address is in the configured allowed managers
        return this.hostsList.contains(address);
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkReadPermission(InetAddress address, String community) {
        if ((this.checkReadPermission(address)) &&
                (this.checkCommunity(community))) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkCommunity(String community) {
        return this.communities.equals(community);
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkWritePermission(InetAddress address) {
        // WRITE Access are always denied
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkWritePermission(InetAddress address, String community) {
        // WRITE Access are always denied
        return false;
    }

    /**
     * {@inheritDoc}
     * @return the list of traps destinations
     */
    public Enumeration getTrapDestinations() {
        Vector<InetAddress> tempDests = new Vector<InetAddress>();
        for (String dest : this.trapsDestinations) {
            try {
                tempDests.add(InetAddress.getByName(dest));
            } catch (UnknownHostException ignore) {
            }
        }
        return tempDests.elements();
    }

    /**
     * {@inheritDoc}
     * @param address
     * @return the list of communities
     */
    public Enumeration getTrapCommunities(InetAddress address) {
        Vector<String> trapCommunities = new Vector<String>();
        trapCommunities.add(this.trapsCommunity);
        return trapCommunities.elements();
    }

    /**
     * {@inheritDoc}
     * @return an empty enumeration
     */
    public Enumeration getInformDestinations() {
        Vector<String> informDests = new Vector<String>();
        return informDests.elements();
    }

    /**
     * {@inheritDoc}
     * @param address
     * @return an empty enumeration
     */
    public Enumeration getInformCommunities(InetAddress address) {
        Vector<String> informCommunities = new Vector<String>();
        return informCommunities.elements();
    }

}
