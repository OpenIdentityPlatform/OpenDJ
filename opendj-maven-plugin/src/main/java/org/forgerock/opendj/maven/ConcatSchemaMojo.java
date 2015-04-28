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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Concatenates the contents of the files in the schema directory to create a
 * base schema that may be used during the upgrade process. Each element will
 * also include the X-SCHEMA-FILE extension to indicate the source schema file.
 * <p>
 * There is a single goal that generates the base schema.
 * <p>
 */
@Mojo(name = "concat", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public final class ConcatSchemaMojo extends AbstractMojo {

    /**
     * The Maven Project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    /**
     * The path to the directory containing the schema files.
     */
    @Parameter(required = true, defaultValue = "${basedir}/resource/schema")
    private String schemaDirectory;

    /**
     * The directory path of the concatenated schema file to create. Must be in ${project.build.directory}
     */
    @Parameter(required = true)
    private String outputDirectory;

    /**
     * The file name of the concatenated schema file to create.
     */
    @Parameter(required = true)
    private String outputFile;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String projectBuildDir = project.getBuild().getDirectory();
        String outputFilePath = outputDirectory + System.getProperty("file.separator") + outputFile;

        if (!outputDirectory.contains(projectBuildDir)) {
            String errorMsg = String.format("outputDirectory parameter (%s) must be included "
                    + "in ${project.build.directory} (%s)", outputDirectory, projectBuildDir);
            getLog().error(errorMsg);
            throw new MojoExecutionException(errorMsg);
        }
        getLog().info(String.format("Concatenating all ldif files from directory: %s", schemaDirectory));
        getLog().info(String.format("Concatenated file: %s", outputFilePath));

        new File(outputFilePath).getParentFile().mkdirs();

        // Get a sorted list of the files in the schema directory.
        TreeSet<String> schemaFileNames = new TreeSet<>();
        for (File f : new File(schemaDirectory).listFiles()) {
            if (f.isFile()) {
                schemaFileNames.add(f.getName());
            }
        }

        // Create a set of lists that will hold the schema elements read from the files.
        LinkedList<String> attributeTypes = new LinkedList<>();
        LinkedList<String> objectClasses = new LinkedList<>();
        LinkedList<String> nameForms = new LinkedList<>();
        LinkedList<String> ditContentRules = new LinkedList<>();
        LinkedList<String> ditStructureRules = new LinkedList<>();
        LinkedList<String> matchingRuleUses = new LinkedList<>();
        LinkedList<String> ldapSyntaxes = new LinkedList<>();
        int curLineNumber = 0;

        // Open each of the files in order and read the elements that they contain,
        // appending them to the appropriate lists.
        for (String name : schemaFileNames) {
            // Read the contents of the file into a list with one schema element per
            // list element.
            LinkedList<StringBuilder> lines = new LinkedList<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(schemaDirectory, name)));
                String line = reader.readLine();
                while (line != null) {
                    curLineNumber++;
                    if (line.length() > 0 && !line.startsWith("#")) {
                        if (line.startsWith(" ")) {
                            lines.getLast().append(line.substring(1));
                        } else {
                            lines.add(new StringBuilder(line));
                        }
                    }
                    line = reader.readLine();
                }
                reader.close();
            } catch (Exception e) {
                getLog().error(String.format(
                        "Error while reading schema file %s at line %d: %s", name, curLineNumber, e.getMessage()));
                throw new MojoExecutionException(e.getMessage(), e);
            }

            // Iterate through each line in the list. Find the colon and get the
            // attribute name at the beginning. If it's someting that we don't
            // recognize, then skip it. Otherwise, add the X-SCHEMA-FILE extension
            // and add it to the appropriate schema element list.
            for (StringBuilder buffer : lines) {
                // Get the line and add the X-SCHEMA-FILE extension to the end of it.
                // All of them should end with " )" but some might have the parenthesis
                // crammed up against the last character so deal with that as well.
                String line = buffer.toString().trim();
                if (line.endsWith(" )")) {
                    line = line.substring(0, line.length() - 1) + "X-SCHEMA-FILE '" + name + "' )";
                } else if (line.endsWith(")")) {
                    line = line.substring(0, line.length() - 1) + " X-SCHEMA-FILE '" + name + "' )";
                } else {
                    continue;
                }

                String lowerLine = line.toLowerCase();
                if (lowerLine.startsWith("attributetypes:")) {
                    attributeTypes.add(line);
                } else if (lowerLine.startsWith("objectclasses:")) {
                    objectClasses.add(line);
                } else if (lowerLine.startsWith("nameforms:")) {
                    nameForms.add(line);
                } else if (lowerLine.startsWith("ditcontentrules:")) {
                    ditContentRules.add(line);
                } else if (lowerLine.startsWith("ditstructurerules:")) {
                    ditStructureRules.add(line);
                } else if (lowerLine.startsWith("matchingruleuse:")) {
                    matchingRuleUses.add(line);
                } else if (lowerLine.startsWith("ldapsyntaxes:")) {
                    ldapSyntaxes.add(line);
                }
            }
        }

        // Write the resulting output to the merged schema file.
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath));
            writer.write("dn: cn=schema");
            writer.newLine();
            writer.write("objectClass: top");
            writer.newLine();
            writer.write("objectClass: ldapSubentry");
            writer.newLine();
            writer.write("objectClass: subschema");
            writer.newLine();

            writeSchemaElements(ldapSyntaxes, writer);
            writeSchemaElements(attributeTypes, writer);
            writeSchemaElements(objectClasses, writer);
            writeSchemaElements(nameForms, writer);
            writeSchemaElements(ditContentRules, writer);
            writeSchemaElements(ditStructureRules, writer);
            writeSchemaElements(matchingRuleUses, writer);

            writer.close();
        } catch (Exception e) {
            getLog().error(
                    String.format("Error while writing concatenated schema file %s:  %s", outputFile, e.getMessage()));
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void writeSchemaElements(List<String> schemaElements, BufferedWriter writer) throws IOException {
        for (String line : schemaElements) {
            writer.write(line);
            writer.newLine();
        }
    }

}
