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
 * Portions Copyright 2013 ForgeRock AS.
 */

/**
 * This package contains the API for the changelog database. The changelog
 * contains:
 * <ul>
 * <li>a changelog of all the changes that happened on each server in the
 * replication domain / suffix,</li>
 * <li>a changelog as defined by draft-good-ldap-changelog,</li>
 * <li>a state database containing specific information about each serverId in
 * the suffix, and in particular the generationId for each server.</li>
 * </ul>
 *
 * The changelog must be purged at regular intervals to ensure it does not
 * consume too much space on disk.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.replication.server.changelog.api;
