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

import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.AfterTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class UpdateCopyrightTestCase extends ForgeRockTestCase {

    private static final String CURRENT_YEAR = Integer.toString(Calendar.getInstance().get(Calendar.YEAR));
    private static final String RESOURCE_DIR = "src/test/resources/files/";
    private static final String[] TEST_FOLDERS = new String[] {"openam-copyrights",
        "opendj-copyrights", "openidm-copyrights"};

    /** Customs tags in tests files */
    private static final String MUST_BE_REMOVE_TAG = "MUST BE REMOVED:";
    private static final String EXPECTED_OUTPUT_TAG = "EXPECTED OUTPUT:";
    private static final String YEAR_TAG = "YEAR";


    @AfterTest
    public void deleteTempFiles() {
        for (String testFolder : TEST_FOLDERS) {
            for (File file : new File(RESOURCE_DIR, testFolder).listFiles()) {
                if (file.getPath().endsWith(".tmp")) {
                    file.delete();
                }
            }
        }
    }


    @DataProvider
    public Object[][] testCases() {
        return new Object[][] {
            // Test case folder, Line before copyright token, NB lines to skip, NB spaces indentation,
            // Portion copyright token, Copyright start token, Copyright end token
            { TEST_FOLDERS[0], "Portions copyright [year] [name of copyright owner]", 1, 1,
                "Portions copyright", "Copyright", "ForgeRock AS." },
            { TEST_FOLDERS[1], "CDDL HEADER END", 1, 6, "Portions Copyright", "Copyright", "ForgeRock AS." },
            { TEST_FOLDERS[2], "DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.", 1, 1,
                "Portions Copyrighted", "Copyright (c)", "ForgeRock AS. All rights reserved." }
        };
    }

    @Test(dataProvider = "testCases")
    public void testUpdateCopyright(String testCaseFolderPath, String lineBeforeCopyrightToken,
            Integer nbLinesToSkip, Integer numberSpacesIndentation, String portionCopyrightToken,
            String copyrightStartToken, String copyrightEndToken) throws Exception {
        List<String> testFilePaths = new LinkedList<String>();
        List<String> updatedTestFilePaths = new LinkedList<String>();
        File[] changedFiles = new File(RESOURCE_DIR, testCaseFolderPath).listFiles();
        for (File file : changedFiles) {
            if (file.getPath().endsWith(".tmp")) {
                continue;
            }
            testFilePaths.add(file.getAbsolutePath());
            updatedTestFilePaths.add(file.getPath() + ".tmp");
        }

        UpdateCopyrightMojo spyMojo = spy(new UpdateCopyrightMojo());
        spyMojo.setDryRun(true);
        spyMojo.setLineBeforeCopyrightToken(lineBeforeCopyrightToken);
        spyMojo.setNbLinesToSkip(nbLinesToSkip);
        spyMojo.setNumberSpaceIdentation(numberSpacesIndentation);
        spyMojo.setPortionsCopyrightStartToken(portionCopyrightToken);
        spyMojo.setCopyrightStartToken(copyrightStartToken);
        spyMojo.setCopyrightEndToken(copyrightEndToken);

        doNothing().when(spyMojo).checkCopyrights();
        when(spyMojo.getIncorrectCopyrightFilePaths()).thenReturn(testFilePaths);
        spyMojo.execute();

        // Check copyrights of updated files
        CheckCopyrightMojo spyMojoCheck = spy(new CheckCopyrightMojo());
        doReturn(updatedTestFilePaths).when(spyMojoCheck).getChangedFiles();
        spyMojoCheck.execute();

        // Check updated files content
        for (String filePath : testFilePaths) {
            checkMofidiedFile(filePath);
        }
    }


    private void checkMofidiedFile(String filePath) throws Exception {
        final BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String mustBeRemoved = null;
        String expectedOutput = null;
        String currentLine = reader.readLine();
        while (currentLine != null) {
            if (currentLine.contains(MUST_BE_REMOVE_TAG)) {
                mustBeRemoved = currentLine.split(MUST_BE_REMOVE_TAG)[1].trim();
            } else if (currentLine.contains(EXPECTED_OUTPUT_TAG)) {
                expectedOutput = currentLine.split(EXPECTED_OUTPUT_TAG)[1].trim()
                                            .replace(YEAR_TAG, CURRENT_YEAR);
            }
            currentLine = reader.readLine();
        }
        reader.close();
        checkIfNewFileIsValid(mustBeRemoved, expectedOutput, filePath + ".tmp");
    }


    private void checkIfNewFileIsValid(String mustBeRemoved, String expectedOutput, String filePath) throws Exception {
        if (mustBeRemoved == null && expectedOutput == null) {
            return;
        }

        final BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String currentLine = reader.readLine();
        boolean expectedOutputFound = false;

        while (currentLine != null) {
            if (lineContainsTagContent(currentLine, mustBeRemoved)) {
                reader.close();
                throw new Exception("Generated file " + filePath + " must not contains " + mustBeRemoved);
            }

            if (!expectedOutputFound && lineContainsTagContent(currentLine, expectedOutput)) {
                expectedOutputFound = true;
                if (mustBeRemoved == null) {
                    reader.close();
                    return;
                }
            }
            currentLine = reader.readLine();
        }
        reader.close();

        if (!expectedOutputFound) {
            throw new Exception("Generated file " + filePath + " should contains " + expectedOutput);
        }
    }


    private boolean lineContainsTagContent(String line, String content) {
        String trimedLine = line.trim();
        return content != null
                && !trimedLine.startsWith(MUST_BE_REMOVE_TAG)
                && !trimedLine.startsWith(MUST_BE_REMOVE_TAG)
                && trimedLine.contains(content);
    }



}
