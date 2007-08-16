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
 * This package contains the code used by the replication server and by the
 * code running on the Directory Server side to exchange their information.
 * <br>
 * <br>
 * The main classes of this packages are :
 * <br>
 * <ul>
 * <li><A HREF="SocketSession.html"><B>SocketSession</B></A>
 * implements the ProtocolSession interface that is
 * used by the replication server and the directory server to communicate.
 * This is done by using the innate encoding/decoding capabilities of the
 * ReplicationMessages objects. This class is used by both the
 * server and the plugin package.
 * </li>
 * <li><A HREF="ReplicationMessage.html"><B>ReplicationMessage</B></A>
 * This class and the class that inherit from it contain the
 * messages that are used for communication between the replication server and
 * the Directory Server as well as the methods fro encoding/decoding them.
 * </li>
 *  </ul>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.replication.protocol;

