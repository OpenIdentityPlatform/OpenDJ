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
 * This package contains the part of the Multimaster
 * synchronization code that works on the Directory Server side.
 * <br>
 * The main classes of this core are :
 * <ul>
 * <li>
 * <A HREF="MultimasterSynchronization.html"><B>MultimasterSynchronization
 * </B></A>contains the synchronization provider
 * code and more generally all the code that makes the glue between the core
 * server and the synchronization code.
 * </li>
 * <li>
 * <A HREF="SynchronizationDomain.html"><B>SynchronizationDomain</B></A>
 * contains the bulk of the Directory Server side of the
 * synchronization code. Most notably it contains the root method for
 * publishing a change, processing a change received from the changelog
 * service, handle conflict resolution, handle protocol messages from the
 * changelog server.
 * </li>
 * <li>
 * <A HREF="ChangeNumber.html"><B>ChangeNumber</B></A>
 * and <A HREF="ChangeNumberGenerator.html"><B>ChangeNumberGenerator</B></A>
 * contain the code related to Change Numbers code and their generation.
 * </li>
 * <li>
 * <A HREF="ServerState.html"><B>ServerState</B></A>
 * contain the code necessary for maintaining the updatedness
 * of a server.
 * Historical.java and the classes that it uses contain the code for
 * generating and loading the historical information (only modify aspects are
 * implemented)
 * </li>
 * <li>
 * <A HREF="SynchronizationMessage.html"><B>SynchronizationMessage</B></A>
 * and the classes that inherit from it contain the
 * description of the protocol messages that are exchanged between the
 * directory servers and the changelog servers and their encoding/decoding.
 * </li>
 * </ul>
 */
package org.opends.server.synchronization;
