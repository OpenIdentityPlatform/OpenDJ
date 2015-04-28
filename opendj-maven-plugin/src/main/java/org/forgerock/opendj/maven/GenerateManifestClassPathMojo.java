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
package org.forgerock.opendj.maven;
import static java.lang.String.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Generate a class path suitable for the Class-Path header of a Manifest file,
 * allowing to filter on included jars, using excludes/includes properties.
 * <p>
 * There is a single goal that generates a property given by 'classPathProperty'
 * parameter, with the generated classpath as the value.
 */
@Mojo(name = "generate-manifest", defaultPhase = LifecyclePhase.VALIDATE,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public final class GenerateManifestClassPathMojo extends AbstractMojo {

    private static final int MAX_LINE_LENGTH = 72;
    private static final String HEADER_CLASSPATH = "Class-Path:";

    /**
     * The Maven Project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    /**
     * A property to set to the content of the generated classpath string.
     */
    @Parameter(required = true)
    private String classPathProperty;

    /**
     * List of artifacts to exclude from the classpath. Each item must be of format "groupId:artifactId".
     */
    @Parameter
    private List<String> excludes;

    /**
     * List of artifacts to include in the classpath. Each item must be of format "groupId:artifactId".
     */
    @Parameter
    private List<String> includes;

    /**
     * List of additional JARs to include in the classpath. Each item must be of format "file.jar".
     */
    @Parameter
    private List<String> additionalJars;

    /**
     * Name of product jar, e.g. "OpenDJ".
     */
    @Parameter
    private String productJarName;

    /**
     * List of supported locales, separated by a ",".
     * <p>
     * Example: "fr,es,de"
     */
    @Parameter
    private String supportedLocales;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            String classPath = getClasspath();
            getLog().info(
                    format("Setting the classpath property: [%s] (debug to see actual value)", classPathProperty));
            getLog().debug(String.format("Setting the classpath property %s to:\n%s", classPathProperty, classPath));
            project.getProperties().put(classPathProperty, classPath);
        } catch (DependencyResolutionRequiredException e) {
            getLog().error(
                    String.format("Unable to set the classpath property %s, an error occured", classPathProperty));
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    /**
     * Get the classpath.
     * <p>
     * The returned value is conform to Manifest Header syntax, where line length must be at most 72 bytes.
     *
     * @return the classpath string
     * @throws DependencyResolutionRequiredException
     */
    private String getClasspath() throws DependencyResolutionRequiredException {
        final List<String> classpathItems = getClasspathItems();
        final StringBuilder classpath = new StringBuilder(HEADER_CLASSPATH);
        for (String item : classpathItems) {
            classpath.append(" ").append(item);
        }
        int index = MAX_LINE_LENGTH - 2;
        while (index <= classpath.length()) {
            classpath.insert(index, "\n ");
            index += MAX_LINE_LENGTH - 1;
        }
        return classpath.toString();
    }

    private List<String> getClasspathItems() throws DependencyResolutionRequiredException {
        final List<String> classpathItems = new ArrayList<>();

        // add project dependencies
        for (String artifactFile : project.getRuntimeClasspathElements()) {
            final File file = new File(artifactFile);
            if (file.getAbsoluteFile().isFile()) {
                final Artifact artifact = findArtifactWithFile(project.getArtifacts(), file);
                if (isAccepted(artifact)) {
                    final String artifactString = artifact.getArtifactId() + "." + artifact.getType();
                    classpathItems.add(artifactString);
                }
            }
        }
        // add product jars, with localized versions
        Collections.sort(classpathItems);
        if (productJarName != null) {
            if (supportedLocales != null) {
                String[] locales = supportedLocales.split(",");
                for (int i = locales.length - 1; i >= 0; i--) {
                    classpathItems.add(0, productJarName + "_" + locales[i] + ".jar");
                }
            }
            classpathItems.add(0, productJarName + ".jar");
        }
        // add additional JARs
        if (additionalJars != null) {
            classpathItems.addAll(additionalJars);
        }

        return classpathItems;
    }

    private boolean isAccepted(Artifact artifact) {
        String artifactString = artifact.getGroupId() + ":" + artifact.getArtifactId();
        if (includes != null) {
            if (containsIgnoreCase(includes, artifactString)) {
                return true;
            }
            if (!includes.isEmpty()) {
                return false;
            }
        }
        return !containsIgnoreCase(excludes, artifactString);
    }

    private boolean containsIgnoreCase(List<String> strings, String toFind) {
        if (strings == null) {
            return false;
        }
        for (String s : strings) {
            if (toFind.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    private Artifact findArtifactWithFile(Set<Artifact> artifacts, File file) {
        for (Artifact artifact : artifacts) {
            if (artifact.getFile() != null
                    && artifact.getFile().equals(file)) {
                return artifact;
            }
        }
        return null;
    }
}
