/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS
 */
package com.forgerock.opendj.cli;

/**
 * A handler for printing sub-command usage information.
 */
//@FunctionalInterface
public interface SubCommandUsageHandler {

    /**
     * Invoked when the sub-command usage information should be printed.
     *
     * @param builder
     *          the string builder
     * @param sc
     *          the sub command for which to print usage information
     * @param argLongID
     *          the argument long identifier
     */
    void appendUsage(StringBuilder builder, SubCommand sc, String argLongID);

}
