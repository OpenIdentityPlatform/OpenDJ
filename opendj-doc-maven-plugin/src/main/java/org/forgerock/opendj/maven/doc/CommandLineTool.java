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
package org.forgerock.opendj.maven.doc;

import java.util.List;

/**
 * Represents a command-line tool as used in the configuration for {@see GenerateRefEntriesMojo}.
 * <br>
 * Command-line tools are associated with a script name, the Java class of the tool,
 * and a list of relative paths to hand-written files for trailing sections.
 * <br>
 * Trailing section paths are relative to the RefEntry file to write.
 */
public class CommandLineTool {
    /** The script name. */
    private String name;

    /** The tool class. */
    private String application;

    /**
     * Additional paths to DocBook XML {@code RefSect1} documents
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
     * The paths in this case are therefore relative to the current RefEntry.
     */
    private List<String> trailingSectionPaths;

    /**
     * Returns the script name.
     * @return The script name.
     */
    public String getName() {
        return name;
    }

    /**
     * Set the script name.
     * @param name The script name.
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Returns the tool class.
     * @return The tool class.
     */
    public String getApplication() {
        return application;
    }

    /**
     * Set the tool class.
     * @param application The tool class.
     */
    public void setApplication(final String application) {
        this.application = application;
    }

    /**
     * Returns additional paths to DocBook XML {@code RefSect1} documents
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
     * The paths in this case are therefore relative to the current RefEntry.
     *
     * @return The relative paths to trailing section files.
     */
    public List<String> getTrailingSectionPaths() {
        return trailingSectionPaths;
    }

    /**
     * Set additional paths to DocBook XML {@code RefSect1} documents.
     * @param paths The paths relative to the current RefEntry.
     */
    public void setTrailingSectionPaths(final List<String> paths) {
        this.trailingSectionPaths = paths;
    }

    /** Whether the tool is enabled. Default: true. */
    private boolean enabled = true;

    /**
     * Returns true if the tool is enabled.
     * @return true if the tool is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set to true if the tool is enabled, false otherwise.
     * @param enabled true if the tool is enabled, false otherwise.
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
