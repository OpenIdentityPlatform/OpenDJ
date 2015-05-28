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

import static org.forgerock.opendj.maven.doc.Utils.*;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * Generates the configuration reference, a set of HTML documents describing the server configuration.
 */
@Mojo(name = "generate-config-ref", defaultPhase = LifecyclePhase.PRE_SITE)
public class GenerateConfigurationReferenceMojo extends AbstractMojo {
    /**
     * The Maven Project.
     */
    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    /**
     * The path to the directory where the configuration reference should be written.
     * This path must be under {@code ${project.build.directory} }.
     */
    @Parameter(defaultValue = "${project.build.directory}/site/configref")
    private String outputDirectory;

    /**
     * Generates the configuration reference under the output directory.
     * @throws MojoExecutionException   Generation failed
     * @throws MojoFailureException     Not used
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        createOutputDirectory();
        generateConfigRef();
        try {
            copyResources();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy resource files.", e);
        }
    }

    /**
     * Creates the output directory where the configuration reference is written.
     * @throws MojoExecutionException   The output directory is not under {@code ${project.build.directory} }
     *                                  or could not be created.
     */
    private void createOutputDirectory() throws MojoExecutionException {
        String projectBuildDir = project.getBuild().getDirectory();

        if (!outputDirectory.contains(projectBuildDir)) {
            String errorMsg = String.format(
                    "The outputDirectory (%s) must be under the ${project.build.directory} (%s).",
                    outputDirectory,
                    projectBuildDir);
            getLog().error(errorMsg);
            throw new MojoExecutionException(errorMsg);
        }

        try {
            createDirectory(outputDirectory);
        } catch (IOException e) {
            getLog().error(e.getMessage());
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Runs the configuration reference generator class.
     * @throws MojoExecutionException   Failed to run the generator
     */
    private void generateConfigRef() throws MojoExecutionException {
        String generatorClass = "org.opends.server.admin.doc.ConfigGuideGeneration";
        List<String> commands = new LinkedList<>();
        try {
            commands.add(getJavaCommand());
            commands.add("-classpath");
            commands.add(getClassPath(getRuntimeClassLoader(project, getLog())));
            commands.add("-DGenerationDir=" + outputDirectory);
            commands.add(generatorClass);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to set the classpath.", e);
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(commands);
            Process process = builder.start();
            process.waitFor();
            final int result = process.exitValue();
            if (result != 0) {
                final StringBuilder message = new StringBuilder();
                message.append("Failed to generate the config ref. Exit code: ").append(result).append(EOL)
                        .append("To debug the problem, run the following command and connect your IDE:").append(EOL);
                commands.add(1, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000");
                for (String arg: commands) {
                    message.append(arg).append(' ');
                }
                message.append(EOL);
                throw new MojoExecutionException(message.toString());
            }
        }  catch (InterruptedException e) {
            throw new MojoExecutionException(generatorClass + " interrupted", e);
        } catch (IOException e) {
            throw new MojoExecutionException(generatorClass + " not found", e);
        }
    }

    /** List of static file resources needed by the configuration reference. */
    private static String[] resourceFiles = {
        "duration-syntax.html",
        "opendj-config.css",
        "opendj_logo_sm.png",
        "pageaction.gif",
        "tab_deselected.jpg",
        "tab_selected.gif"
    };

    /**
     * Copies static files needed by the configuration reference.
     * @throws IOException  Failed to read a resource or to write a file
     */
    private void copyResources() throws IOException {
        for (String file : resourceFiles) {
            InputStream original = this.getClass().getResourceAsStream("/config-ref/" + file);
            File copy = new File(outputDirectory, file);
            copyInputStreamToFile(original, copy);
        }
    }
}
