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
 *      Copyright 2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import org.forgerock.i18n.LocalizableMessage;

/**
 * An interface for an object that holds reference documentation for a command-line tool.
 */
public interface ToolRefDocContainer {

    /**
     * Gets a short description for this tool, suitable in a man page summary line.
     *
     * @return  A short description for this tool,
     *          suitable in a man page summary line,
     *          or LocalizableMessage.EMPTY if there is no short description.
     */
    LocalizableMessage getShortToolDescription();

    /**
     * Sets a short description for this tool, suitable in a man page summary line.
     *
     * @param   shortDescription    The short description for this tool,
     *                              suitable in a man page summary line.
     */
    void setShortToolDescription(final LocalizableMessage shortDescription);

    /**
     * Retrieves a supplement to the description for this tool,
     * intended for use in generated reference documentation.
     *
     * @return A supplement to the description for this tool
     *         intended for use in generated reference documentation,
     *         or LocalizableMessage.EMPTY if there is no supplement.
     */
    LocalizableMessage getDocToolDescriptionSupplement();

    /**
     * Sets a supplement to the description for this tool,
     * intended for use in generated reference documentation.
     *
     * @param docToolDescriptionSupplement  The supplement to the description for this tool
     *                                      intended for use in generated reference documentation.
     */
    void setDocToolDescriptionSupplement(final LocalizableMessage docToolDescriptionSupplement);

    /**
     * Retrieves a supplement to the description for all subcommands of this tool,
     * intended for use in generated reference documentation.
     *
     * @return A supplement to the description for all subcommands of this tool
     *         intended for use in generated reference documentation,
     *         or LocalizableMessage.EMPTY if there is no supplement.
     */
    LocalizableMessage getDocSubcommandsDescriptionSupplement();

    /**
     * Sets a supplement to the description for all subcommands of this tool,
     * intended for use in generated reference documentation.
     *
     * @param docSubcommandsDescriptionSupplement
     *          The supplement to the description for all subcommands of this tool
     *          intended for use in generated reference documentation.
     */
    void setDocSubcommandsDescriptionSupplement(final LocalizableMessage docSubcommandsDescriptionSupplement);
}
