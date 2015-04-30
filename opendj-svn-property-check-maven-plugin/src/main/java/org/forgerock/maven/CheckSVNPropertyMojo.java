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
 *      Copyright 2015 ForgeRock AS
 */
package org.forgerock.maven;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * This be used to make sure that the file has the correct "svn:eol-style"
 * property value.
 */
@Mojo(name = "check-svn-property", defaultPhase = LifecyclePhase.VALIDATE)
public class CheckSVNPropertyMojo extends AbstractMojo implements ISVNStatusHandler {

    /** The Maven Project. */
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    /** The path to the root of the Subversion workspace to check. */
    @Parameter(required = true, defaultValue = "${basedir}")
    private String svnWorkspaceRoot;

    @Parameter(required = true)
    private String svnPropertyName;

    @Parameter(required = true)
    private String svnPropertyExpectedValue;

    @Parameter(property = "skipSvnPropCheck", required = true, defaultValue = "false")
    private boolean checkDisabled;

    /**
     * The name of the system property that may be used to prevent eol-style
     * problems from failing the build.
     */
    @Parameter(property = "ignoreSvnPropertyCheckErrors", required = true, defaultValue = "false")
    private boolean ignoreErrors;

    /** The overall SVN Client Manager. */
    private final SVNClientManager svnClientManager = SVNClientManager.newInstance();

    private final List<String> errorFilePaths = new LinkedList<>();

    /** {@inheritDoc} **/
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (checkDisabled) {
            getLog().info("Check svn property " + svnPropertyName + " is disabled");
            return;
        }

        try {
            svnClientManager.getStatusClient().doStatus(new File(svnWorkspaceRoot), SVNRevision.WORKING,
                    SVNDepth.INFINITY, false, false, false, false, this, null);
        } catch (Exception e) {
            throw new MojoExecutionException("Encountered an error while examining subversion status: "
                    + e.getMessage() + "No further checks will be performed.", e);
        }

        if (!errorFilePaths.isEmpty()) {
            logWithAppropriateLevel(" Potential " + svnPropertyName + " updates needed " + "for the following files:");
            for (String fileName : errorFilePaths) {
                logWithAppropriateLevel("     " + fileName);
            }

            if (!ignoreErrors) {
                throw new MojoExecutionException("Fix " + svnPropertyName + " problems before proceeding, or "
                        + "use '-DignoreSvnPropertyCheckErrors=true' to ignore these warnings warnings.");
            }
        }
    }

    private void logWithAppropriateLevel(final String message) {
        if (!ignoreErrors) {
            getLog().error(message);
        } else {
            getLog().warn(message);
        }
    }

    /**
     * Examines the provided status item to determine whether the associated
     * file is acceptable.
     *
     * @param status
     *            The SVN status information for the file of interest.
     */
    public void handleStatus(org.tmatesoft.svn.core.wc.SVNStatus status) {
        File changedFile = status.getFile();
        if (!changedFile.exists() || !changedFile.isFile()) {
            return;
        }

        try {
            SVNPropertyData propertyData = SVNClientManager.newInstance().getWCClient()
                    .doGetProperty(changedFile, svnPropertyName, SVNRevision.BASE, SVNRevision.WORKING);
            if (propertyData == null || !svnPropertyExpectedValue.equals(propertyData.getValue().getString())) {
                errorFilePaths.add(changedFile.getPath());
            }
        } catch (SVNException se) {
            // This could happen if the file isn't under version control.
            getLog().warn("Impossible to check svn:eol-style for the file " + changedFile.getAbsolutePath()
                            + " you might need to add it to svn.");
        }
    }
}
