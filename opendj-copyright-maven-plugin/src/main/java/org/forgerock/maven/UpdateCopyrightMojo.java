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

import static java.util.regex.Pattern.*;

import static org.apache.maven.plugins.annotations.LifecyclePhase.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *    [COMMMENT_CHAR][lineBeforeCopyrightRegExp]
 *    [COMMMENT_CHAR]* //This line references 0..N commented empty lines.
 *    ([COMMMENT_CHAR][oldCopyrightToken])*
 *    ([COMMMENT_CHAR] [YEAR] [copyrightEndToken])?
 * </pre>
 * <p>
 *  Formatter details:
 *  <ul>
 *  <li>COMMENT_CHAR: Auto-detected by plugin.
 *               Comment character used in comment blocks ('*' for Java, '!' for xml...)</li>
 *
 *  <li>lineBeforeCopyrightRegExp: Parameter regExp case insensitive
 *               Used by the plugin to start it's inspection for the copyright line.
 *               Next non blank commented lines after this lines must be
 *               old copyright owner lines or/and old ForgeRock copyright lines.</li>
 *
 *  <li>oldCopyrightToken: Detected by plugin ('copyright' keyword case insensitive)
 *               If one line contains this token, the plugin will use
 *               the newPortionsCopyrightLabel instead of the newCopyrightLabel
 *               if there is no ForgeRock copyrighted line.</li>
 *
 *  <li>forgerockCopyrightRegExp: Parameter regExp case insensitive
 *               The regular expression which identifies a copyrighted line as a ForgeRock one.</li>
 *
 *  <li>YEAR: Computed by plugin
 *               Current year if there is no existing copyright line.
 *               If the copyright section already exists, the year will be updated as follow:
 *               <ul>
 *                  <li>OLD_YEAR => OLD_YEAR-CURRENT_YEAR</li>
 *                  <li>VERY_OLD_YEAR-OLD_YEAR => VERY_OLD_YEAR-CURRENT_YEAR</li>
 *              </ul></li>
 * </ul>
 * </p>
 * <p>
 * If no ForgeRock copyrighted line is detected, the plugin will add according to the following format
 * <ul>
 *      <li> If there is one or more old copyright lines:
 *              <pre>
 *              [COMMMENT_CHAR][lineBeforeCopyrightRegExp]
 *              [COMMMENT_CHAR]* //This line references 0..N commented empty lines.
 *              ([COMMMENT_CHAR][oldCopyrightToken])*
 *              [indent][newPortionsCopyrightLabel] [YEAR] [forgerockCopyrightLabel]
 *              </pre></li><br>
 *      <li> If there is no old copyright lines:
 *              <pre>
 *              [COMMMENT_CHAR][lineBeforeCopyrightRegExp]
 *              [COMMMENT_CHAR]*{nbLinesToSkip} //This line nbLinesToSkip commented empty lines.
 *              [indent][newCopyrightLabel] [YEAR] [forgerockCopyrightLabel]
 *              </pre></li>
 * </ul>
 *
 */
@Mojo(name = "update-copyright", defaultPhase = VALIDATE)
public class UpdateCopyrightMojo extends CopyrightAbstractMojo {

    private final class UpdateCopyrightFile {
        private final String filePath;
        private final List<String> bufferedLines = new LinkedList<>();
        private boolean copyrightUpdated;
        private boolean lineBeforeCopyrightReaded;
        private boolean commentBlockEnded;
        private boolean portionsCopyrightNeeded;
        private boolean copyrightSectionPresent;
        private String curLine;
        private String curLowerLine;
        private Integer startYear;
        private Integer endYear;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private UpdateCopyrightFile(String filePath) throws IOException {
            this.filePath = filePath;
            reader = new BufferedReader(new FileReader(filePath));
            final File tmpFile = new File(filePath + ".tmp");
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
            } catch (final Exception e) {
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

            for (final String line : bufferedLines) {
                writer.write(line);
                writer.newLine();
            }
            writer.close();

            if (!dryRun) {
                final File updatedFile = new File(filePath);
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
            final Pattern stopRegExp = portionsCopyrightNeeded ? OLD_COPYRIGHT_REGEXP
                                                               : lineBeforeCopyrightCompiledRegExp;
            String previousLine = curLine;
            while (!lineMatches(previousLine, stopRegExp)) {
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
                    + indent() + (portionsCopyrightNeeded ? newPortionsCopyrightLabel : newCopyrightLabel)
                    + " " + currentYear + " " + forgeRockCopyrightLabel;
            bufferedLines.add(indexAdd, newCopyrightLine);
            copyrightUpdated = true;
        }

        private void updateExistingCopyrightLine() throws Exception {
            readYearSection();
            final String newCopyrightLine;
            if (endYear == null) {
                // OLD_YEAR => OLD_YEAR-CURRENT_YEAR
                newCopyrightLine = curLine.replace(startYear.toString(), intervalToString(startYear, currentYear));
            } else {
                // VERY_OLD_YEAR-OLD_YEAR => VERY_OLD_YEAR-CURRENT_YEAR
                newCopyrightLine = curLine.replace(intervalToString(startYear, endYear),
                                                   intervalToString(startYear, currentYear));
            }
            bufferedLines.remove(bufferedLines.size() - 1);
            bufferedLines.add(newCopyrightLine);
        }

        private void readYearSection() throws Exception {
            final String copyrightLineRegExp = ".*\\s+(\\d{4})(-(\\d{4}))?\\s+" + forgerockCopyrightRegExp + ".*";
            final Matcher copyrightMatcher = Pattern.compile(copyrightLineRegExp, CASE_INSENSITIVE).matcher(curLine);
            if (copyrightMatcher.matches()) {
                startYear = Integer.parseInt(copyrightMatcher.group(1));
                final String endYearString = copyrightMatcher.group(3);
                if (endYearString != null) {
                    endYear = Integer.parseInt(endYearString);
                }
            } else {
                throw new Exception("Malformed year section in copyright line " + curLine);
            }
        }

        private void readLineBeforeCopyrightToken() throws Exception {
            nextLine();
            while (curLine != null) {
                if (curLineMatches(lineBeforeCopyrightCompiledRegExp)) {
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
            return curLineMatches(OLD_COPYRIGHT_REGEXP) && !curLineMatches(copyrightOwnerCompiledRegExp);
        }

        private boolean isCopyrightLine() {
            return curLineMatches(copyrightOwnerCompiledRegExp);
        }

        private boolean curLineMatches(Pattern compiledRegExp) {
            return lineMatches(curLine, compiledRegExp);
        }

        private boolean lineMatches(String line, Pattern compiledRegExp) {
            return compiledRegExp.matcher(line).matches();
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

    private static final Pattern OLD_COPYRIGHT_REGEXP = Pattern.compile(".*copyright.*", CASE_INSENSITIVE);

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

    /** The last non empty commented line before the copyright section. */
    @Parameter(required = true, defaultValue = "CDDL\\s+HEADER\\s+END")
    private String lineBeforeCopyrightRegExp;

    /** The regular expression which identifies a copyrighted line. */
    @Parameter(required = true, defaultValue = "ForgeRock\\s+AS")
    private String forgerockCopyrightRegExp;

    /** Line to add if there is no existing copyright. */
    @Parameter(required = true, defaultValue = "Copyright")
    private String newCopyrightLabel;

    /** Portions copyright start line token. */
    @Parameter(required = true, defaultValue = "Portions Copyright")
    private String newPortionsCopyrightLabel;

    /** ForgeRock copyright label to print if a new (portions) copyright line is needed. */
    @Parameter(required = true, defaultValue = "ForgeRock AS.")
    private String forgeRockCopyrightLabel;

    /** A dry run will not change source code. It creates new files with '.tmp' extension. */
    @Parameter(required = true, defaultValue = "false")
    private boolean dryRun;

    /** RegExps corresponding to user token. */
    private Pattern lineBeforeCopyrightCompiledRegExp;
    private Pattern copyrightOwnerCompiledRegExp;

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
        compileRegExps();
        checkCopyrights();
        for (final String filePath : getIncorrectCopyrightFilePaths()) {
            try {
                new UpdateCopyrightFile(filePath).updateCopyrightForFile();
                getLog().info("Copyright of file " + filePath + " has been successfully updated.");
            } catch (final Exception e) {
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

    private void compileRegExps() {
        lineBeforeCopyrightCompiledRegExp = compileRegExp(lineBeforeCopyrightRegExp);
        copyrightOwnerCompiledRegExp = compileRegExp(forgerockCopyrightRegExp);
    }

    private Pattern compileRegExp(String regExp) {
        return Pattern.compile(".*" + regExp + ".*", CASE_INSENSITIVE);
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
        this.lineBeforeCopyrightRegExp = lineBeforeCopyrightToken;
    }

    void setNbLinesToSkip(Integer nbLinesToSkip) {
        this.nbLinesToSkip = nbLinesToSkip;
    }

    void setNumberSpaceIdentation(Integer numberSpaceIdentation) {
        this.numberSpaceIdentation = numberSpaceIdentation;
    }

    void setNewPortionsCopyrightString(String portionsCopyrightString) {
        this.newPortionsCopyrightLabel = portionsCopyrightString;
    }

    void setNewCopyrightOwnerString(String newCopyrightOwnerString) {
        this.forgeRockCopyrightLabel = newCopyrightOwnerString;
    }

    void setNewCopyrightStartToken(String copyrightStartString) {
        this.newCopyrightLabel = copyrightStartString;
    }

    void setCopyrightEndToken(String copyrightEndToken) {
        this.forgerockCopyrightRegExp = copyrightEndToken;
    }

    void setDryRun(final boolean dryRun) {
        this.dryRun = true;
    }

}
