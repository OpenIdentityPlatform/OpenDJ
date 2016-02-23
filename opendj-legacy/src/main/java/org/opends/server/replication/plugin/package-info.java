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
 * This package contains the part of the Multi-master
 * replication code that works on the Directory Server side.
 * <br>
 * The main classes of this core are :
 * <ul>
 * <li>
 * <A HREF="MultimasterReplication.html"><B>MultimasterReplication
 * </B></A>contains the synchronization provider
 * code and more generally all the code that makes the glue between the core
 * server and the replication code.
 * </li>
 * <li>
 * <A HREF="ReplicationDomain.html"><B>ReplicationDomain</B></A>
 * contains the bulk of the Directory Server side of the
 * replication code. Most notably it contains the root method for
 * publishing a change, processing a change received from the replicationServer
 * service, handle conflict resolution, handle protocol messages from the
 * replicationServer.
 * </li>
 * </ul>
 */
package org.opends.server.replication.plugin;

