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
 * This package contains utilities that can are used by all the packages
 * below org.opends.server.replication.
 * <br>
 * The main classes of this core are :
 * <ul>
 * <li>
 * <A HREF="CSN.html"><B>CSN</B></A>
 * Define CSNs used to identify and to order the LDAP changes
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

