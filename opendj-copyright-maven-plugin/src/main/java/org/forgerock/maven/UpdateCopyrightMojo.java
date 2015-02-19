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
 *      Copyright 2015 ForgeRock AS
 */
package org.forgerock.maven;

import static org.apache.maven.plugins.annotations.LifecyclePhase.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.forgerock.util.Utils;

/**
 * This goals can be used to automatically updates copyrights of modified files.
 *
 * <p>
 *    Copyright sections must respect the following format:
 * <pre>
 *    (.)* //This line references 0..N lines.
 *    [COMMMENT_CHAR][lineBeforeCopyrightToken]
 *    [COMMMENT_CHAR]* //This line references 0..N commented emptylines.
 *    ([COMMMENT_CHAR][oldCopyrightToken])*
 *    ([COMMMENT_CHAR][indent][copyrightStartToken | portionsCopyrightStartToken] [YEAR] [copyrightEndToken])?
 * </pre>
 * <p>
 *  Formatter details:
 *  <ul>
 *  <li>COMMENT_CHAR: Auto-detected by plugin.
 *               Comment character used in comment blocks ('*' for Java, '!' for xml...)</li>
 *  <li>lineBeforeCopyrightToken: Parameter
 *               Used by the plugin to start it's inspection for the copyright line.
 *               Next non blank commented lines after this lines must be
 *               old copyright owner lines or/and old copyright lines.</li>
 *
 *  <li>oldCopyrightToken: Detected by plugin ('copyright' keyword non case-sensitive and non copyrightEndToken)
 *               If one line contains this token, the plugin will use
 *               the portionsCopyrightStartToken instead of copyrightStartToken</li>
 *
 *  <li>nbLinesToSkip: Parameter (int)
 *               Used only if a new copyright line is needed (not if a new portion copyright section is needed).
 *               It gives the number of lines to add after the line which contains  the lineBeforeCopyrightToken.</li>
 *
 *  <li>indent: Parameter 'numberSpaceIdentation' (int)
 *               Used only if a new copyright or portion copyright line is needed.
 *               It gives the number of space to add after the COMMENT_CHAR.
 *               If there is already a copyright line, the existing indentation will be used.</li>
 *
 *  <li>copyrightStartToken: Parameter
 *               Used to recognize the copyright line. If the copyright section is
 *               missing, the plugin will add the line.</li>
 *
 *  <li>portionsCopyrightStartToken: Parameter
 *               Same as copyrightStartToken, but if the oldCopyrightToken is present.</li>
 *
 *  <li>copyrightEndToken: Parameter
 *               Same as copyrightStartToken, but for the end of the line.</li>
 *
 *  <li>YEAR: Computed by plugin
 *               Current year if there is no existing copyright line.
 *               If the copyright section already exists, the year will be updated as follow:
 *               <ul>
 *                  <li>OLD_YEAR => OLD_YEAR-CURRENT_YEAR</li>
 *                  <li>VERY_OLD_YEAR-OLD_YEAR => VERY_OLD_YEAR-CURRENT_YEAR</li>
 *              </ul></li>
 * </ul>
 */
@Mojo(name = "update-copyright", defaultPhase = VALIDATE)
public class UpdateCopyrightMojo extends CopyrightAbstractMojo {

    private final class UpdateCopyrightFile {
        private String filePath;
        private final List<String> bufferedLines = new LinkedList<String>();
        private boolean copyrightUpdated;
        private boolean lineBeforeCopyrightReaded;
        private boolean commentBlockEnded;
        private boolean portionsCopyrightNeeded;
        private boolean copyrightSectionPresent;
        private String curLine;
        private String curLowerLine;
        private Integer startYear;
        private Integer endYear;
        private BufferedReader reader;
        private BufferedWriter writer;

        private UpdateCopyrightFile(String filePath) throws IOException {
            this.filePath = filePath;
            reader = new BufferedReader(new FileReader(filePath));
            File tmpFile = new File(filePath + ".tmp");
            if (!tmpFile.exists()) {
                tmpFile.createNewFile();
            }
            writer = new BufferedWriter(new FileWriter(tmpFile));
        }

        private void updateCopyrightForFile() throws MojoExecutionException {
            try {
                readLineBeforeCopyrightToken();
                portionsCopyrightNeeded = readOldCopyrightLine();
                copyrightSectionPresent = readCopyrightLine();
                writeCopyrightLine();
                writeChanges();
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } finally {
                Utils.closeSilently(reader, writer);
            }
        }

        private void writeChanges() throws Exception {
            while (curLine != null) {
                nextLine();
            }
            reader.close();

            for (String line : bufferedLines) {
                writer.write(line);
                writer.newLine();
            }
            writer.close();

            if (!dryRun) {
                File updatedFile = new File(filePath);
                if (!updatedFile.delete()) {
                    throw new Exception("impossible to perform rename on the file.");
                }
                new File(filePath + ".tmp").renameTo(updatedFile);
            }
        }

        private void writeCopyrightLine() throws Exception {
            if (copyrightSectionPresent) {
                updateExistingCopyrightLine();
                copyrightUpdated = true;
                return;
            }

            int indexAdd = bufferedLines.size() - 1;
            String stopToken = portionsCopyrightNeeded ? OLD_COPYRIGHT_TOKEN : lineBeforeCopyrightToken;
            String previousLine = curLine;
            while (!previousLine.toLowerCase().contains(stopToken.toLowerCase())) {
                indexAdd--;
                previousLine = bufferedLines.get(indexAdd);
            }
            indexAdd++;
            if (!portionsCopyrightNeeded) {
                for (int i = 0; i < nbLinesToSkip; i++) {
                    bufferedLines.add(indexAdd++, getNewCommentedLine());
                }
            }
            final String newCopyrightLine = getNewCommentedLine()
                    + indent() + (portionsCopyrightNeeded ? portionsCopyrightStartToken : copyrightStartToken)
                    + " " + currentYear + " " + copyrightEndToken;
            bufferedLines.add(indexAdd, newCopyrightLine);
            copyrightUpdated = true;
        }

        private void updateExistingCopyrightLine() throws Exception {
            if (portionsCopyrightNeeded && copyrightSectionPresent
                    // Is it a new copyright line?
                    && curLine.contains(copyrightStartToken) && !curLine.contains(portionsCopyrightStartToken)) {
                getLog().warn("File " + filePath + " contains old copyright line and coyright line. "
                        + "The copyright line will be replaced by a portions copyright line.");
                curLine.replace(copyrightStartToken, portionsCopyrightStartToken);
            }
            readYearSection();
            final String newCopyrightLine;
            if (endYear == null) {
                //OLD_YEAR => OLD_YEAR-CURRENT_YEAR
                newCopyrightLine = curLine.replace(startYear.toString(), intervalToString(startYear, currentYear));
            } else {
                //VERY_OLD_YEAR-OLD_YEAR => VERY_OLD_YEAR-CURRENT_YEAR
                newCopyrightLine = curLine.replace(intervalToString(startYear, endYear),
                                                   intervalToString(startYear, currentYear));
            }
            bufferedLines.remove(bufferedLines.size() - 1);
            bufferedLines.add(newCopyrightLine);
        }

        private void readYearSection() throws Exception {
            try {
                String startToken = portionsCopyrightNeeded ? portionsCopyrightStartToken : copyrightStartToken;
                String yearSection = curLine.substring(curLine.indexOf(startToken) + startToken.length(),
                                                        curLine.indexOf(copyrightEndToken)).trim();
                if (yearSection.contains("-")) {
                    startYear = Integer.parseInt(yearSection.split("-")[0].trim());
                    endYear = Integer.parseInt(yearSection.split("-")[1].trim());
                } else {
                    startYear = Integer.parseInt(yearSection);
                }
            } catch (NumberFormatException nfe) {
                throw new Exception("Malformed year section in copyright line " + curLine);
            } catch (Exception e) {
                throw new Exception("Malformed copyright line " + curLine);
            }
        }

        private void readLineBeforeCopyrightToken() throws Exception {
            nextLine();
            while (curLine != null) {
                if (curLine.contains(lineBeforeCopyrightToken)) {
                    if (!isCommentLine(curLowerLine)) {
                        throw new Exception("The line before copyright token must be a commented line");
                    }
                    lineBeforeCopyrightReaded = true;
                    return;
                } else if (commentBlockEnded) {
                    throw new Exception("unexpected non commented line found before copyright section");
                }
                nextLine();
            }
        }

        private boolean readOldCopyrightLine() throws Exception {
            nextLine();
            while (curLine != null) {
                if (isOldCopyrightOwnerLine()) {
                    return true;
                } else if (isNonEmptyCommentedLine(curLine)
                            || isCopyrightLine()
                            || commentBlockEnded) {
                    return false;
                }
                nextLine();
            }
            throw new Exception("unexpected end of file while trying to read copyright");
        }

        private boolean readCopyrightLine() throws Exception {
            while (curLine != null) {
                if (isCopyrightLine()) {
                    return true;
                } else if ((isNonEmptyCommentedLine(curLine) && !isOldCopyrightOwnerLine())
                            || commentBlockEnded) {
                    return false;
                }
                nextLine();
            }
            throw new Exception("unexpected end of file while trying to read copyright");
        }

        private boolean isOldCopyrightOwnerLine() {
            return curLowerLine.contains(OLD_COPYRIGHT_TOKEN) && !curLine.contains(copyrightEndToken);
        }

        private boolean isCopyrightLine() {
            return (curLine.contains(copyrightStartToken) || curLine.contains(portionsCopyrightStartToken))
                    && curLine.contains(copyrightEndToken);
        }

        private void nextLine() throws Exception {
            curLine = reader.readLine();
            if (curLine == null && !copyrightUpdated) {
                throw new Exception("unexpected end of file while trying to read copyright");
            } else  if (curLine != null) {
                bufferedLines.add(curLine);
            }

            if (!copyrightUpdated) {
                curLowerLine = curLine.trim().toLowerCase();
                if (lineBeforeCopyrightReaded && !isCommentLine(curLowerLine)) {
                    commentBlockEnded = true;
                }
            }
        }

        private String getNewCommentedLine() throws Exception {
            int indexCommentToken = 1;
            String commentToken = null;
            String linePattern = null;
            while (bufferedLines.size() > indexCommentToken && commentToken == null) {
                linePattern = bufferedLines.get(indexCommentToken++);
                commentToken = getCommentTokenInBlock(linePattern);
            }
            if (commentToken != null) {
                return linePattern.substring(0, linePattern.indexOf(commentToken) + 1);
            } else {
                throw new Exception("Uncompatibles comments lines in the file.");
            }
        }

    }

    private static final String OLD_COPYRIGHT_TOKEN = "copyright";

    /** The last non empty commented line before the copyright section. */
    @Parameter(required = true, defaultValue = "CDDL HEADER END")
    private String lineBeforeCopyrightToken;

    /**
     * Number of lines to add after the line which contains the lineBeforeCopyrightToken.
     * Used only if a new copyright line is needed.
     */
    @Parameter(required = true, defaultValue = "2")
    private Integer nbLinesToSkip;

    /**
     * Number of spaces to add after the comment line token before adding new
     * copyright section. Used only if a new copyright or portion copyright is
     * needed.
     */
    @Parameter(required = true, defaultValue = "6")
    private Integer numberSpaceIdentation;

    /** Copyright start line token. */
    @Parameter(required = true, defaultValue = "Copyright")
    private String copyrightStartToken;

    @Parameter(required = true, defaultValue = "Portions Copyright")
    /** Portions copyright start line token. */
    private String portionsCopyrightStartToken;

    /** Copyright end line token. */
    @Parameter(required = true, defaultValue = "ForgeRock AS")
    private String copyrightEndToken;

    /** A dry run will not change source code. It creates new files with '.tmp' extension */
    @Parameter(required = true, defaultValue = "false")
    private boolean dryRun;

    private boolean buildOK = true;

    /**
     * Updates copyright of modified files.
     *
     * @throws MojoFailureException
     *             if any
     * @throws MojoExecutionException
     *             if any
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        checkCopyrights();
        for (String filePath : getIncorrectCopyrightFilePaths()) {
            try {
                new UpdateCopyrightFile(filePath).updateCopyrightForFile();
                getLog().info("Copyright of file " + filePath + " has been successfully updated.");
            } catch (Exception e) {
                getLog().error("Impossible to update copyright of file " + filePath);
                getLog().error("  Details: " + e.getMessage());
                getLog().error("  No modification has been performed on this file");
                buildOK = false;
            }
        }

        if (!buildOK) {
            throw new MojoFailureException("Error(s) occured while trying to update some copyrights.");
        }
    }

    private String intervalToString(Integer startYear, Integer endYear) {
        return startYear + "-" + endYear;
    }

    private String indent() {
        String indentation = "";
        for (int i = 0; i < numberSpaceIdentation; i++) {
            indentation += " ";
        }
        return indentation;
    }

    // Setters to allow tests

    void setLineBeforeCopyrightToken(String lineBeforeCopyrightToken) {
        this.lineBeforeCopyrightToken = lineBeforeCopyrightToken;
    }

    void setNbLinesToSkip(Integer nbLinesToSkip) {
        this.nbLinesToSkip = nbLinesToSkip;
    }

    void setNumberSpaceIdentation(Integer numberSpaceIdentation) {
        this.numberSpaceIdentation = numberSpaceIdentation;
    }

    void setPortionsCopyrightStartToken(String portionsCopyrightStartToken) {
        this.portionsCopyrightStartToken = portionsCopyrightStartToken;
    }

    void setCopyrightStartToken(String copyrightStartToken) {
        this.copyrightStartToken = copyrightStartToken;
    }

    void setCopyrightEndToken(String copyrightEndToken) {
        this.copyrightEndToken = copyrightEndToken;
    }

    void setDryRun(final boolean dryRun) {
        this.dryRun = true;
    }

}
