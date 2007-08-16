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
 * Contains the code for the Directory Server backend that provides an interface
 * for executing administrative tasks.  A task is a set of user-defined code
 * that may be executed either immediately or at a given time in the future by
 * adding an entry within the "cn=tasks" branch.  The attributes of that entry
 * may serve as arguments that can customize the operation of that task.
 * <BR><BR>
 * Recurring tasks may be used to perform an operation at regular intervals.
 * Task groups may define a set of tasks that should be executed in sequential
 * order, and may be executed as a recurring task.  The order of individual
 * scheduled tasks may be controlled by defining dependencies between those
 * tasks, but a task group must be used to achieve the same result for recurring
 * tasks.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.backends.task;

