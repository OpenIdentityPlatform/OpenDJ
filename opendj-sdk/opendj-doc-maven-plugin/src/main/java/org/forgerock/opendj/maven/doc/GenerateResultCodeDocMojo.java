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

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.JavaType;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.forgerock.opendj.ldap.ResultCode;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Generates documentation source for LDAP result codes based on
 * {@code org.forgerock.opendj.ldap.ResultCode}.
 * <br>
 * This implementation parses the source to match Javadoc comments with result codes.
 * It is assumed that the class's ResultCode fields are named with result code enum values,
 * and that those fields have Javadoc comments describing each result code.
 */
@Mojo(name = "generate-result-code-doc", defaultPhase = LifecyclePhase.COMPILE)
public class GenerateResultCodeDocMojo extends AbstractMojo {
    /**
     * The Java file containing the source of the ResultCode class,
     * {@code org.forgerock.opendj.ldap.ResultCode}.
     * <br>
     * For example, {@code opendj-core/src/main/java/org/forgerock/opendj/ldap/ResultCode.java}.
     */
    @Parameter(required = true)
    private File resultCodeSource;

    /** The XML file to generate. */
    @Parameter(required = true)
    private File xmlFile;

    /**
     * Generates documentation source for LDAP result codes.
     *
     * @throws MojoExecutionException   Generation failed
     * @throws MojoFailureException     Not used
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Map<String, Object> map = new HashMap<>();
        map.put("year", new SimpleDateFormat("yyyy").format(new Date()));

        // The overall explanation in the generated doc is the class comment.
        final JavaClass resultCodeClass;
        try {
            resultCodeClass = getJavaClass();
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read " + resultCodeSource.getPath(), e);
        }
        map.put("classComment", cleanComment(resultCodeClass.getComment()));

        // Documentation for each result code comes from the Javadoc for the code,
        // and from the value and friendly name of the code.
        final Map<String, Object> comments = new HashMap<>();
        for (final JavaField field : resultCodeClass.getFields()) {
            final JavaType type = field.getType();
            if (type.getValue().equals("ResultCode")) {
                comments.put(field.getName(), cleanComment(field.getComment()));
            }
        }
        map.put("resultCodes", getResultCodesDoc(comments));

        final String template = "appendix-ldap-result-codes.ftl";
        try {
            writeStringToFile(applyTemplate(template, map), xmlFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not write to " + xmlFile.getPath(), e);
        }
        getLog().info("Wrote " + xmlFile.getPath());
    }

    /**
     * Returns an object to access to the result code Java source.
     * @return An object to access to the result code Java source.
     * @throws IOException  Could not read the source
     */
    private JavaClass getJavaClass() throws IOException {
        final JavaProjectBuilder builder = new JavaProjectBuilder();
        builder.addSource(resultCodeSource);
        return builder.getClassByName("org.forgerock.opendj.ldap.ResultCode");
    }

    /**
     * Returns a clean string for use in generated documentation.
     * @param comment   The comment to clean.
     * @return A clean string for use in generated documentation.
     */
    private String cleanComment(String comment) {
        return stripCodeValueSentences(stripTags(convertLineSeparators(comment))).trim();
    }

    /**
     * Returns a string with line separators converted to spaces.
     * @param string    The string to convert.
     * @return A string with line separators converted to spaces.
     */
    private String convertLineSeparators(String string) {
        return string.replaceAll(System.lineSeparator(), " ");
    }

    /**
     * Returns a string with the HTML tags removed.
     * @param string    The string to strip.
     * @return A string with the HTML tags removed.
     */
    private String stripTags(String string) {
        return string.replaceAll("<[^>]*>", "");
    }

    /**
     * Returns a string with lines sentences of the following form removed:
     * This result code corresponds to the LDAP result code value of &#x7b;&#x40;code 0&#x7d;.
     * @param string    The string to strip.
     * @return A string with lines sentences of the matching form removed.
     */
    private String stripCodeValueSentences(String string) {
        return string
                .replaceAll("This result code corresponds to the LDAP result code value of \\{@code \\d+\\}.", "");
    }

    /**
     * Returns a list of documentation objects for all result codes.
     * @param comments  A map of field names to the clean comments.
     * @return A list of documentation objects for all result codes.
     */
    private List<Map<String, Object>> getResultCodesDoc(Map<String, Object> comments) {
        final List<Map<String, Object>> list = new LinkedList<>();
        if (comments == null || comments.isEmpty()) {
            return list;
        }

        for (ResultCode resultCode : ResultCode.values()) {
            final Map<String, Object> doc = new HashMap<>();
            doc.put("intValue", resultCode.intValue());
            doc.put("name", resultCode.getName());
            final Object comment = comments.get(resultCode.asEnum().toString());
            doc.put("comment", comment);
            list.add(doc);
        }
        return list;
    }
}
