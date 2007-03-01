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
 * Contains various Directory Server tool implementations.  These tools may be
 * either clients that communicate with the server over protocol (e.g.,
 * ldapsearch, ldapmodify, etc.), or those that help launch some administrative
 * operation (e.g., LDIF import/export, backend backup/restore, etc.).
 * <BR><BR>
 * All command-line tools should use the argument parsing capabilities defined
 * in the <CODE>com.sun.directory.util.args</CODE> package for a consistent and
 * full-featured manner for interacting with command-line arguments and
 * providing usage information.
 * <BR><BR>
 * Further, all tools that communicate with the server over protocol should have
 * the ability to make use of a centralized configuration so that they can share
 * a set of default values for the protocol, address, port, use or non-use of
 * SSL, etc. if no value is given.
 */
package org.opends.server.tools;

