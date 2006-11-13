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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

/**
 * This package contains the code for the changelog service part
 * of the Multimaster synchronization feature.
 * <br>
 *
 * A changelog server is responsible for :
 * <br>
 * <ul>
 * <li>listen for connections from ldap servers.</li>
 * <li>Connect/manage connection to other changelog servers.</li>
 * <li>Receive changes from ldap servers.</li>
 * <li>Forward changes to ldap server and other changelog servers.</li>
 * <li>Save changes to stable storage (includes trimming of older operations).
 * </li>
 * </ul>
 * <br>
 * The main classes of this packages are :
 * <br>
 * <ul>
 * <li><A HREF="SocketSession.html"><B>SocketSession</B></A>
 * implements the ProtocolSession interface that is
 * used by the changelog server and the directory server to communicate.
 * This is done by using the innate encoding/decoding capabilities of the
 * SynchronizationMessages objects. This class is used by both the
 * changelog and the synchronization package.
 * </li>
 * <li><A HREF="ChangelogCache.html"><B>ChangelogCache</B></A>
 * implements the multiplexing part of the changelog
 * server. It contains method for forwarding all the received messages to
 * the ServerHandler and to the dbHandler objects.<br>
 * </li>
 * <li><A HREF="ServerHandler.html"><B>ServerHandler</B></A>
 * contains the code related to handler of remote
 * server. It can manage changelog servers of directory servers (may be it
 * shoudl be splitted in two different classes, one for each of these).<br>
 * </li>
 * <li><A HREF="ServerWriter.html"><B>ServerWriter</B></A>
 * the thread responsible for writing to the remote
 * server.<br>
 * </li>
 * <li><A HREF="ServerReader.html"><B>ServerReader</B></A>
 * the thread responsible for reading from the remote
 * object.<br>
 * </li>
 * <li><A HREF="DbHandler.html"><B>DbHandler</B></A>
 * DbHandler contains the code responsible for saving the changes to
 * stable storage.<br>
 *  </li>
 *  </ul>
 */

package org.opends.server.synchronization.changelog;
