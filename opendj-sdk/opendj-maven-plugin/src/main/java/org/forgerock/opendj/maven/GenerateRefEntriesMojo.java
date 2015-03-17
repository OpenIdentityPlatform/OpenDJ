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

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;

/**
 * Generate DocBook RefEntry source documents for command-line tools man pages.
 */
@Mojo(name = "generate-refentry", defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public final class GenerateRefEntriesMojo extends AbstractMojo {

    /** The Maven Project. */
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    /** Tools for which to generate RefEntry files. */
    @Parameter
    private List<CommandLineTool> tools;

    /** Where to write the RefEntry files. */
    @Parameter(required = true)
    private File outputDir;

    /** End of line. */
    public static final String EOL = System.getProperty("line.separator");

    /**
     * Writes a RefEntry file to the output directory for each tool.
     * Files names correspond to script names: {@code man-&lt;name>.xml}.
     *
     * @throws MojoExecutionException   Encountered a problem writing a file.
     * @throws MojoFailureException     Failed to initialize effectively,
     *                                  or to write one or more RefEntry files.
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        PrintStream out = System.out;

        // Set the magic property for generating DocBook XML.
        System.setProperty("org.forgerock.opendj.gendoc", "true");

        // A Maven plugin classpath does not include project files.
        // Prepare a ClassLoader capable of loading the command-line tools.
        URLClassLoader toolsClassLoader;
        try {
            List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();
            List<URL> runtimeUrls = new LinkedList<URL>();
            for (String element : runtimeClasspathElements) {
                runtimeUrls.add(new File(element).toURI().toURL());
            }
            toolsClassLoader = new URLClassLoader(
                    runtimeUrls.toArray(new URL[runtimeClasspathElements.size()]),
                    Thread.currentThread().getContextClassLoader());
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoFailureException("Failed to access the runtime classpath.", e);
        } catch (MalformedURLException e) {
            throw new MojoFailureException("Failed to add element to classpath.", e);
        }
        debugClassPathElements(toolsClassLoader);

        List<String> failures = new LinkedList<String>();
        for (CommandLineTool tool : tools) {
            final File manPage = new File(outputDir, "man-" + tool.getName() + ".xml");
            try {
                setSystemOut(refEntryFile(manPage.getPath()));
            } catch (FileNotFoundException e) {
                setSystemOut(out);
                failures.add(manPage.getPath());
                throw new MojoExecutionException("Failed to write " + manPage.getPath(), e);
            }

            // Set the properties for script name and list of trailing sections.
            System.setProperty("com.forgerock.opendj.ldap.tools.scriptName", tool.getName());
            final String xInclude = pathsToXIncludes(tool.getTrailingSectionPaths());
            System.setProperty("org.forgerock.opendj.gendoc.trailing", xInclude);

            try {
                final Class<?> toolClass = toolsClassLoader.loadClass(tool.getApplication());
                final Class[] argTypes = new Class[]{String[].class};
                final Method main = toolClass.getDeclaredMethod("main", argTypes);
                final String[] args = {"-?"};
                main.invoke(null, (Object) args);
            } catch (ClassNotFoundException e) {
                failures.add(manPage.getPath());
                throw new MojoExecutionException(tool.getApplication() + " not found", e);
            } catch (NoSuchMethodException e) {
                failures.add(manPage.getPath());
                throw new MojoExecutionException(tool.getApplication() + " has no main method.", e);
            } catch (IllegalAccessException e) {
                failures.add(manPage.getPath());
                throw new MojoExecutionException("Failed to run " + tool.getApplication() + ".main()", e);
            } catch (InvocationTargetException e) {
                failures.add(manPage.getPath());
                throw new MojoExecutionException("Failed to run " + tool.getApplication() + ".main()", e);
            } finally {
                setSystemOut(out);
            }
        }

        final StringBuilder list = new StringBuilder();
        if (!failures.isEmpty()) {
            for (final String failure : failures) {
                list.append(failure).append(EOL);
            }
            throw new MojoFailureException("Failed to write the following RefEntry files: " + list);
        }
    }

    /**
     * Logs what is on the classpath for debugging.
     * @param classLoader   The ClassLoader with the classpath.
     */
    private void debugClassPathElements(ClassLoader classLoader) {
        if (null == classLoader) {
            return;
        }
        getLog().debug("--------------------");
        getLog().debug(classLoader.toString());
        if (classLoader instanceof URLClassLoader) {
            final URLClassLoader ucl = (URLClassLoader) classLoader;
            int i = 0;
            for (URL url : ucl.getURLs()) {
                getLog().debug("url[" + (i++) + "]=" + url);
            }
        }
        debugClassPathElements(classLoader.getParent());
    }

    /**
     * Returns a PrintStream to a file to which to write a RefEntry.
     * @param   path                    Path to the file to be written.
     * @return                          PrintStream to a file to which to write a RefEntry.
     * @throws  FileNotFoundException   Failed to open the file for writing.
     */
    private PrintStream refEntryFile(final String path) throws FileNotFoundException {
        return new PrintStream(new BufferedOutputStream(new FileOutputStream(path)), true);
    }

    /**
     * Sets the System output stream.
     * @param out   The stream to use.
     */
    private void setSystemOut(PrintStream out) {
        if (out != null) {
            System.setOut(out);
        }
    }

    /**
     * Translates relative paths to XML files into XInclude elements.
     *
     * @param paths Paths to XInclude'd files, relative to the RefEntry.
     * @return      String of XInclude elements corresponding to the paths.
     */
    private String pathsToXIncludes(final List<String> paths) {
        if (paths == null) {
            return "";
        }

        // Assume xmlns:xinclude="http://www.w3.org/2001/XInclude",
        // as in the declaration of resources/templates/refEntry.ftl.
        final String nameSpace = "xinclude";
        final StringBuilder result = new StringBuilder();
        for (String path : paths) {
            result.append("<").append(nameSpace).append(":include href=\"").append(path).append("\" />").append(EOL);
        }
        return result.toString();
    }
}
