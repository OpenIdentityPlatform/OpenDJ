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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 */



/**
 * Contains various implementations of Directory Server APIs that are not
 * appropriate for other packages.  This includes components like:
 * <BR>
 * <UL>
 *   <LI>Password Storage Schemes</LI>
 *   <LI>SASL Mechanism Handlers</LI>
 *   <LI>Extended Operation Handlers</LI>
 *   <LI>Key Manager Providers</LI>
 *   <LI>Trust Manager Providers</LI>
 *   <LI>Entry Caches</LI>
 *   <LI>Alert Handlers</LI>
 *   <LI>Connection Security Providers</LI>
 * </UL>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.extensions;

