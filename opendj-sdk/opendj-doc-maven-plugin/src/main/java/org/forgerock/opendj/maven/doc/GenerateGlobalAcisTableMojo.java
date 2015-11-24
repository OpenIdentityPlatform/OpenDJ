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
 *      Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.maven.doc;

import static org.forgerock.opendj.maven.doc.DocsMessages.*;
import static org.forgerock.opendj.maven.doc.Utils.applyTemplate;
import static org.forgerock.opendj.maven.doc.Utils.writeStringToFile;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldif.LDIFEntryReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates documentation source table listing global ACIs.
 */
@Mojo(name = "generate-global-acis-table")
public class GenerateGlobalAcisTableMojo extends AbstractMojo {
    /** The locale for which to generate the documentation. */
    @Parameter(defaultValue = "en")
    private String locale;

    /** The config.ldif file containing default global ACIs. **/
    @Parameter(defaultValue = "${basedir}/resource/config/config.ldif")
    private File configDotLdif;

    /** Output directory for source files. */
    @Parameter(defaultValue = "${project.build.directory}/docbkx-sources/shared")
    private File outputDirectory;

    /** Holds descriptions for ACIs. */
    private Map<String, String> descriptions;

    /** Holds documentation for an ACI. */
    private class Aci {
        String name;
        String description;
        String definition;
    }

    /** Holds the list of global ACIs. */
    private static List<Aci> allGlobalAcis = new LinkedList<>();

    /**
     * Writes documentation source table listing global ACIs.
     * @throws MojoExecutionException   Not used.
     * @throws MojoFailureException     Failed to read ACIs or to write the table file.
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            readAcis(getAciDescriptions());
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }

        File table = new File(outputDirectory, "table-global-acis.xml");
        try {
            writeStringToFile(getGlobalAcisTable(), table);
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    /**
     * Populates map of {@code ds-cfg-global-aci} descriptions from comments in {@code config.ldif}.
     * Keys are ACI names. Values are descriptions.
     * <br>
     * The format expected for ACI description comments is the following:
     * {@code # @aci name: description},
     * where {@code name} matches the name embedded in the ACI,
     * and {@code description} is a longer description.
     * @throws IOException  Failed to read the LDIF.
     */
    private Map<String, String> getAciDescriptions() throws IOException {
        final Map<String, String> descriptions = new HashMap<>();
        BufferedReader reader = new BufferedReader(new FileReader(configDotLdif));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("# @aci ")) {
                String[] split = line.replace("# @aci ", "").split(":", 2);
                descriptions.put(split[0], split[1]);
            }
        }
        return descriptions;
    }

    /**
     * Reads {@code ds-cfg-global-aci} values from {@code config.ldif} into the list of Acis.
     * @param descriptions Long descriptions from comments in {@code config.ldif}.
     *                     Keys are ACI names. Values are descriptions.
     * @throws IOException  Failed to read the LDIF.
     */
    private void readAcis(Map<String, String> descriptions) throws IOException {
        LDIFEntryReader reader = new LDIFEntryReader(new FileInputStream(configDotLdif));
        reader.setIncludeBranch(DN.valueOf("cn=Access Control Handler,cn=config"));

        while (reader.hasNext()) {
            Entry entry = reader.readEntry();
            for (String attribute : entry.parseAttribute("ds-cfg-global-aci").asSetOfString()) {
                Aci aci = new Aci();
                aci.name = getName(attribute);
                if (descriptions != null) {
                    aci.description = descriptions.get(aci.name);
                }
                aci.definition = attribute;
                allGlobalAcis.add(aci);
            }
        }
    }

    /**
     * Returns the user-friendly name embedded in the ACI.
     * @param aci   The string representation of the ACI value.
     * @return  The user-friendly name embedded in the ACI,
     *          or an empty string if no name is found.
     */
    private String getName(String aci) {
        // Extract the user-friendly string in
        // {@code ...version 3.0; acl "user-friendly name"...}.
        Pattern pattern = Pattern.compile(".+version 3.0; ?acl \"([^\"]+)\".+");
        Matcher matcher = pattern.matcher(aci);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Returns a DocBook XML table listing global ACIs.
     * @return A DocBook XML table listing global ACIs.
     */
    private String getGlobalAcisTable() {
        final Map<String, Object> map = new HashMap<>();
        map.put("year", new SimpleDateFormat("yyyy").format(new Date()));
        map.put("lang", locale);
        map.put("title", DOC_GLOBAL_ACIS_TABLE_TITLE.get());
        map.put("summary", DOC_GLOBAL_ACIS_TABLE_SUMMARY.get());
        map.put("nameTitle", DOC_GLOBAL_ACIS_NAME_COLUMN_TITLE.get());
        map.put("descTitle", DOC_GLOBAL_ACIS_DESCRIPTION_COLUMN_TITLE.get());
        map.put("defTitle", DOC_GLOBAL_ACIS_DEFINITION_COLUMN_TITLE.get());
        map.put("acis", getDefaultGlobalAciList());
        return applyTemplate("table-global-acis.ftl", map);
    }

    /**
     * Returns a list of information about default global ACIs.
     * @return A list of information about default global ACIs.
     */
    private List<Map<String, Object>> getDefaultGlobalAciList() {
        final List<Map<String, Object>> globalAciList = new LinkedList<>();
        for (final Aci aci : allGlobalAcis) {
            final Map<String, Object> map = new HashMap<>();
            map.put("name", aci.name);
            map.put("description", aci.description);
            map.put("definition", aci.definition);
            globalAciList.add(map);
        }
        return globalAciList;
    }
}
