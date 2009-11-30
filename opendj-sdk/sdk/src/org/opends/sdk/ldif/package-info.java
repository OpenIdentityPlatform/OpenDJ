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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

/**
 * OpenDS LDIF support.
 *
 * <h1>TO DO</h1>
 * <ul>
 * <li>Make LDIFEntryReader concurrent and support DN reservation.
 * <li>LDIF*Reader Reject and skip support
 * <li>Remaining schema checking (e.g. binary option)
 * <li>Fix error messages (prefix with file/lineno)
 * <li>Support multiple LDIF*Reader sources
 * <li>Support EntryWriter splitting
 * <li>Support LDIFConnectionFactory
 * <li>Comments and optional charset encoding?
 * </ul>
 */
package org.opends.sdk.ldif;



