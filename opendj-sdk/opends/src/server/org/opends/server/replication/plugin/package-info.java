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
 * publishing a change, processing a change received from the changelog
 * service, handle conflict resolution, handle protocol messages from the
 * changelog server.
 * </li>
 * </ul>
 */
package org.opends.server.replication.plugin;
