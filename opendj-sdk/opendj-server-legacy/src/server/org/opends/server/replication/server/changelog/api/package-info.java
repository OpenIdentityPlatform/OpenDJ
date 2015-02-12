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
 *      Portions Copyright 2013 ForgeRock AS
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
