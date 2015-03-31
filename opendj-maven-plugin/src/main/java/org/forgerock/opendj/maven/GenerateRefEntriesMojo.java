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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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

    private static final String EOL = System.getProperty("line.separator");

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
        if (!isOutputDirAvailable()) {
            throw new MojoFailureException("Output directory " + outputDir.getPath() + " not available");
        }

        // A Maven plugin classpath does not include project files.
        // Prepare a ClassLoader capable of loading the command-line tools.
        final URLClassLoader toolsClassLoader = getBootToolsClassLoader();
        for (CommandLineTool tool : tools) {
            generateManPageForTool(toolsClassLoader, tool);
        }
    }

    /**
     * Returns a ClassLoader capable of loading the command-line tools.
     * @return A ClassLoader capable of loading the command-line tools.
     * @throws MojoFailureException     Failed to build classpath.
     */
    private URLClassLoader getBootToolsClassLoader() throws MojoFailureException {
        try {
            List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();
            Set<URL> runtimeUrls = new LinkedHashSet<URL>();
            for (String element : runtimeClasspathElements) {
                runtimeUrls.add(new File(element).toURI().toURL());
            }

            final URLClassLoader toolsClassLoader = new URLClassLoader(
                    runtimeUrls.toArray(new URL[runtimeClasspathElements.size()]),
                    Thread.currentThread().getContextClassLoader());
            debugClassPathElements(toolsClassLoader);
            return toolsClassLoader;
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoFailureException("Failed to access the runtime classpath.", e);
        } catch (MalformedURLException e) {
            throw new MojoFailureException("Failed to add element to classpath.", e);
        }
    }

    /**
     * Generate a RefEntry file to the output directory for a tool.
     * The files name corresponds to the tool name: {@code man-&lt;name>.xml}.
     * @param toolsClassLoader          The ClassLoader to run the tool.
     * @param tool                      The tool to run in order to generate the page.
     * @throws MojoExecutionException   Failed to run the tool.
     * @throws MojoFailureException     Tool did not exit successfully.
     */
    private void generateManPageForTool(final URLClassLoader toolsClassLoader, final CommandLineTool tool)
            throws MojoExecutionException, MojoFailureException {
        final File   manPage    = new File(outputDir, "man-" + tool.getName() + ".xml");
        final String toolScript = tool.getName();
        final String toolSects  = pathsToXIncludes(tool.getTrailingSectionPaths());
        final String toolClass  = tool.getApplication();
        List<String> commands   = new LinkedList<String>();
        commands.add(getJavaCommand());
        commands.addAll(getJavaArgs(toolScript, toolSects));
        commands.add("-classpath");
        commands.add(getClassPath(toolsClassLoader));
        commands.add(toolClass);
        commands.add(getUsageArgument(toolScript));

        getLog().info("Writing man page: " + manPage.getPath());
        try {
            // Tools tend to use System.exit() so run them as separate processes.
            ProcessBuilder builder = new ProcessBuilder(commands);
            Process process = builder.start();
            writeToFile(process.getInputStream(), manPage);
            process.waitFor();
            final int result = process.exitValue();
            if (result != 0) {
                final StringBuilder message = new StringBuilder();
                message.append("Failed to write page. Tool exit code: ").append(result).append(EOL);
                message.append("To debug the problem, run the following command and connect your IDE:").append(EOL);
                commands.add(1, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000");
                for (String arg: commands) {
                    // Surround with quotes to handle trailing sections.
                    message.append("\"").append(arg).append("\"").append(' ');
                }
                message.append(EOL);
                throw new MojoFailureException(message.toString());
            }
        }  catch (InterruptedException e) {
            throw new MojoExecutionException(toolClass + " interrupted", e);
        } catch (IOException e) {
            throw new MojoExecutionException(toolClass + " not found", e);
        }
    }

    /**
     * Returns true if the output directory is available.
     * Attempts to create the directory if it does not exist.
     * @return True if the output directory is available.
     */
    private boolean isOutputDirAvailable() {
        return outputDir != null && (outputDir.exists() && outputDir.isDirectory() || outputDir.mkdirs());
    }

    /**
     * Returns the path to the current Java executable.
     * @return The path to the current Java executable.
     */
    private String getJavaCommand() {
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    }

    /**
     * Returns the Java args for running a tool.
     * @param scriptName        The name of the tool.
     * @param trailingSections  The man page sections to Xinclude.
     * @return The Java args for running a tool.
     */
    private List<String> getJavaArgs(final String scriptName, final String trailingSections) {
        List<String> args = new LinkedList<String>();
        args.add("-Dorg.forgerock.opendj.gendoc=true");
        args.add("-Dorg.opends.server.ServerRoot=" + System.getProperty("java.io.tmpdir"));
        args.add("-Dcom.forgerock.opendj.ldap.tools.scriptName=" + scriptName);
        args.add("-Dorg.forgerock.opendj.gendoc.trailing=" + trailingSections + "");
        return args;
    }

    /**
     * Returns the classpath for the class loader.
     * @param classLoader   Contains the URLs of the class path to return.
     * @return The classpath for the class loader.
     */
    private String getClassPath(final URLClassLoader classLoader) {
        final StringBuilder stringBuilder = new StringBuilder();
        final URL[] urls = classLoader.getURLs();
        for (int i = 0; i < urls.length; i++) {
            if (i > 0) {
                stringBuilder.append(File.pathSeparator);
            }
            try {
                stringBuilder.append(new File(urls[i].toURI()).getPath());
            } catch (URISyntaxException e) {
                getLog().info("Failed to add classpath element", e);
            }
        }
        return stringBuilder.toString();
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
            result.append("<").append(nameSpace).append(":include href='").append(path).append("' />");
        }
        return result.toString();
    }

    /**
     * Returns the usage argument.
     * @param scriptName The name of the tool.
     * @return The usage argument.
     */
    private String getUsageArgument(final String scriptName) {
        return scriptName.equals("dsjavaproperties") ? "-H" : "-?";
    }

    /**
     * Write the content of the input stream to the output file.
     * @param input     The input stream to write.
     * @param output    The file to write it to.
     * @throws IOException  Failed to write the content of the input stream.
     */
    private void writeToFile(final InputStream input, final File output) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(output);
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write(EOL);
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
