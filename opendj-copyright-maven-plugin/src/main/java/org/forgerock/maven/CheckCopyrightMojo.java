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
package org.forgerock.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * This be used to check that if a modified file contains a line that appears to
 * be a comment and includes the word "copyright", then it should contain the
 * current year.
 */
@Mojo(name = "check-copyright", defaultPhase = LifecyclePhase.VALIDATE)
public class CheckCopyrightMojo extends CopyrightAbstractMojo {

    /**
     * The property that may be used to prevent copyright date problems from
     * failing the build.
     */
    @Parameter(required = true, property = "ignoreCopyrightErrors", defaultValue = "false")
    private boolean ignoreCopyrightErrors;

    @Parameter(required = true, property = "skipCopyrightCheck", defaultValue = "false")
    private boolean checkDisabled;

    /**
     * Uses maven-scm API to identify all modified files in the current
     * workspace. For all source files, check if the copyright is up to date.
     *
     * @throws MojoFailureException
     *             if any
     * @throws MojoExecutionException
     *             if any
     */
    public void execute() throws MojoFailureException, MojoExecutionException {
        if (checkDisabled) {
            getLog().info("Copyright check is disabled");
            return;
        }

        checkCopyrights();
        if (!getIncorrectCopyrightFilePaths().isEmpty()) {
            getLog().warn("Potential copyright year updates needed for the following files:");
            for (String filename : getIncorrectCopyrightFilePaths()) {
                getLog().warn("     " + filename);
            }

            if (!ignoreCopyrightErrors) {
                getLog().warn("Fix copyright date problems before proceeding, "
                                + "or use '-DignoreCopyrightErrors=true' to ignore copyright errors.");
                getLog().warn("You can use update-copyrights maven profile "
                        + "(mvn validate -Pupdate-copyrights) to automatically update copyrights.");
                throw new MojoExecutionException("Found files with potential copyright year updates needed");
            }
        } else {
            getLog().info("Copyrights are up to date");
        }
    }
}
