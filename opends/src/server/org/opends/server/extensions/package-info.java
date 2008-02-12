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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
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

