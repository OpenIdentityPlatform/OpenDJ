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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */



/**
 * This package contains utilities that can are used by all the packages
 * below org.opends.server.replication.
 * <br>
 * The main classes of this core are :
 * <ul>
 * <li>
 * <A HREF="ChangeNumber.html"><B>ChangeNumber</B></A>
 * Define Change Numbers used to identify and to order the LDAP changes
 * </li>
 * <li>
 * <A HREF="ServerState.html"><B>ServerState</B></A>
 * This class is used to define and store the updatedness of any component
 * of the replication architecture (i.e : to know which changes
 * it has already processed).
 * </li>
 * </ul>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.replication.common;

