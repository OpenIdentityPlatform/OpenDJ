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



/**
 * Contains various classes that comprise the network group criteria
 * and resource limits of the Directory Server codebase.  This includes:
 * <BR>
 * <UL>
 *   <LI>
 *     The code that is invoked to initialize the network group criteria
 *     and check if a connection matches a criteria.
 *   </LI>
 *   <LI>
 *     The code that is invoked to initialize the network group resource limits
 *     and check that a new connection or a new operation is within
 *     the resource limits.
 *   </LI>
 * </UL>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.core.networkgroups;

