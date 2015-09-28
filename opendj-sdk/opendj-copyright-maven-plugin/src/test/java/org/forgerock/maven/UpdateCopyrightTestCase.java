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
import java.io.FilenameFilter;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.Utils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class UpdateCopyrightTestCase extends ForgeRockTestCase {

    private final class FilenameExtensionFilter implements FilenameFilter {
        private final String extension;

        private FilenameExtensionFilter(String suffix) {
            this.extension = suffix;
        }

        @Override
        public boolean accept(File directory, String fileName) {
            return fileName.endsWith(extension);
        }
    }

    private static final String CURRENT_YEAR = Integer.toString(Calendar.getInstance().get(Calendar.YEAR));
    private static final String RESOURCE_DIR = "src/test/resources/files/";
    private static final String[] TEST_FOLDERS = { "openam-copyrights", "opendj-copyrights", "openidm-copyrights"};

    /** Customs tags in tests files. */
    private static final String MUST_BE_REMOVED_TAG = "MUST BE REMOVED:";
    private static final String EXPECTED_OUTPUT_TAG = "EXPECTED OUTPUT:";
    private static final String YEAR_TAG = "YEAR";

    @AfterTest
    public void deleteTempFiles() {
        FilenameFilter tmpFilter = new FilenameExtensionFilter(".tmp");
        for (String testFolder : TEST_FOLDERS) {
            for (File file : new File(RESOURCE_DIR, testFolder).listFiles(tmpFilter)) {
                file.delete();
            }
        }
    }

    @DataProvider
    public Object[][] testCases() {
        return new Object[][] {
                // Test case folder, Line before copyright regexp, NB lines to skip, NB spaces indentation,
                // New portion copyright string, New copyright start string, Copyright end regexp,
                // New copyright end String
            { TEST_FOLDERS[0], "Portions\\s+Copyright\\s+\\[year\\]\\s+\\[name\\s+of\\s+copyright\\s+owner\\]",
                1, 1, "Portions copyright", "Copyright", "ForgeRock\\s+AS", "ForgeRock AS." },
            { TEST_FOLDERS[1], "CDDL\\s+HEADER\\s+END", 1, 6, "Portions Copyright", "Copyright",
                "ForgeRock\\s+AS\\.", "ForgeRock AS." },
            { TEST_FOLDERS[2],
                "DO\\s+NOT\\s+ALTER\\s+OR\\s+REMOVE\\s+COPYRIGHT\\s+NOTICES\\s+OR\\s+THIS\\s+HEADER.", 1, 1,
                "Portions Copyrighted", "Copyright (c)", "ForgeRock\\s+AS\\.",
                "ForgeRock AS. All rights reserved." }
        };
    }

    @Test(dataProvider = "testCases")
    public void testUpdateCopyright(String testCaseFolderPath, String lineBeforeCopyrightToken,
            int nbLinesToSkip, int numberSpacesIndentation, String newPortionCopyrightString,
            String newCopyrightStartString, String copyrightEndToken, String newCopyrightOwnerStr) throws Exception {
        List<String> testFilePaths = new LinkedList<>();
        List<File> updatedTestFilePaths = new LinkedList<>();

        File[] changedFiles = new File(RESOURCE_DIR, testCaseFolderPath)
            .listFiles(new FilenameExtensionFilter(".txt"));
        for (File file : changedFiles) {
            testFilePaths.add(file.getAbsolutePath());
            updatedTestFilePaths.add(new File(file.getPath() + ".tmp"));
        }

        UpdateCopyrightMojo spyMojo = spy(new UpdateCopyrightMojo());
        spyMojo.setDryRun(true);
        spyMojo.setLineBeforeCopyrightToken(lineBeforeCopyrightToken);
        spyMojo.setNbLinesToSkip(nbLinesToSkip);
        spyMojo.setNumberSpaceIdentation(numberSpacesIndentation);
        spyMojo.setNewPortionsCopyrightString(newPortionCopyrightString);
        spyMojo.setNewCopyrightStartToken(newCopyrightStartString);
        spyMojo.setNewCopyrightOwnerString(newCopyrightOwnerStr);
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
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String mustBeRemoved = null;
            String expectedOutput = null;
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                if (currentLine.contains(MUST_BE_REMOVED_TAG)) {
                    mustBeRemoved = currentLine.split(MUST_BE_REMOVED_TAG)[1].trim();
                } else if (currentLine.contains(EXPECTED_OUTPUT_TAG)) {
                    expectedOutput = currentLine.split(EXPECTED_OUTPUT_TAG)[1].trim()
                        .replace(YEAR_TAG, CURRENT_YEAR);
                }
            }
            checkIfNewFileIsValid(mustBeRemoved, expectedOutput, filePath + ".tmp");
        } finally {
            Utils.closeSilently(reader);
        }
    }


    private void checkIfNewFileIsValid(String mustBeRemoved, String expectedOutput, String filePath) throws Exception {
        if (mustBeRemoved == null && expectedOutput == null) {
            return;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filePath));

            boolean expectedOutputFound = false;
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                if (lineContainsTagContent(currentLine, mustBeRemoved)) {
                    throw new Exception("Generated file " + filePath + " must not contains " + mustBeRemoved);
                }

                if (!expectedOutputFound && lineContainsTagContent(currentLine, expectedOutput)) {
                    expectedOutputFound = true;
                    if (mustBeRemoved == null) {
                        return;
                    }
                }
            }
            if (!expectedOutputFound) {
                throw new Exception("Generated file " + filePath + " should contains " + expectedOutput);
            }
        } finally {
            Utils.closeSilently(reader);
        }
    }


    private boolean lineContainsTagContent(String line, String content) {
        String trimedLine = line.trim();
        return content != null
                && !trimedLine.startsWith(MUST_BE_REMOVED_TAG)
                && !trimedLine.startsWith(MUST_BE_REMOVED_TAG)
                && trimedLine.contains(content);
    }
}
