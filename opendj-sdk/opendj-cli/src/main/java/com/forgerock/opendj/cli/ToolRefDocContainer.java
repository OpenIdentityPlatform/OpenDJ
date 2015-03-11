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

    /**
     * Get additional paths to DocBook XML {@code RefSect1} documents
     * to be appended after generated content in reference documentation.
     *
     * <br>
     *
     * DocBook represents a reference manual page with the {@code RefEntry}.
     * See <a href="http://www.docbook.org/tdg51/en/html/refentry.html">refentry</a>.
     *
     * <br>
     *
     * A {@code RefEntry} describing an OpenDJ tool contains
     * block elements in the following order:
     *
     * <pre>
     *     RefMeta
     *     RefNameDiv
     *     RefSynopsisDiv
     *     RefSect1 - Description (generated, potentially with a hand-written supplement)
     *     RefSect1 - Options (generated)
     *     RefSect1 - Subcommands (optional, hand-written intro + generated RefSect2s)
     *     RefSect1 - Filter (optional, hand-written)
     *     RefSect1 - Attribute (optional, hand-written)
     *     RefSect1 - Exit Codes (hand-written)
     *     RefSect1 - Files (optional, hand-written)
     *     RefSect1 - Examples (hand-written)
     *     RefSect1 - See Also (hand-written)
     * </pre>
     *
     * As the trailing RefSect1s following Subcommands are hand-written,
     * they are included in the generated content as XIncludes elements.
     *
     * @return  The paths to trailing {@code RefSect1} documents.
     */
    String[] getPathsToTrailingRefSect1s();

    /**
     * Set additional paths to DocBook XML {@code RefSect1} documents
     * to be appended after generated content in reference documentation.
     *
     * <br>
     *
     * DocBook represents a reference manual page with the {@code RefEntry}.
     * See <a href="http://www.docbook.org/tdg51/en/html/refentry.html">refentry</a>.
     *
     * <br>
     *
     * A {@code RefEntry} describing an OpenDJ tool contains
     * block elements in the following order:
     *
     * <pre>
     *     RefMeta
     *     RefNameDiv
     *     RefSynopsisDiv
     *     RefSect1 - Description (generated, potentially with a hand-written supplement)
     *     RefSect1 - Options (generated)
     *     RefSect1 - Subcommands (optional, hand-written intro + generated RefSect2s)
     *     RefSect1 - Filter (optional, hand-written)
     *     RefSect1 - Attribute (optional, hand-written)
     *     RefSect1 - Exit Codes (hand-written)
     *     RefSect1 - Files (optional, hand-written)
     *     RefSect1 - Examples (hand-written)
     *     RefSect1 - See Also (hand-written)
     * </pre>
     *
     * As the trailing RefSect1s following Subcommands are hand-written,
     * they are included in the generated content as XIncludes elements.
     *
     * @param paths The paths to trailing {@code RefSect1} documents.
     */
    public void setPathsToTrailingRefSect1s(final String... paths);
}
