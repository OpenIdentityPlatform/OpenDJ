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
 *      Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.project.MavenProject;

/**
 * AbstractMojo implementation for generation of opendj server configuration
 * classes.
 */
abstract class AbstractBuildMojo extends AbstractMojo {

    /**
     * The Maven Project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The Maven Session.
     *
     * @parameter property="session"
     * @required
     * @readonly
     */
    private MavenSession session;

    /**
     * The Maven PluginManager.
     *
     * @component
     * @required
     */
    private BuildPluginManager pluginManager;

    /**
     * The Maven Project.
     *
     * @return the project
     */
    public MavenProject getProject() {
        return project;
    }

    /**
     * The Maven Session.
     *
     * @return the session
     */
    public MavenSession getSession() {
        return session;
    }

    /**
     * The Maven PluginManager.
     *
     * @return the plugin manager
     */
    public BuildPluginManager getPluginManager() {
        return pluginManager;
    }
}
