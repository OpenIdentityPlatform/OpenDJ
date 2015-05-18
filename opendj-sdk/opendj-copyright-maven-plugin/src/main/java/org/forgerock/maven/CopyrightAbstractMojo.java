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

import static org.forgerock.util.Utils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmFileStatus;
import org.apache.maven.scm.command.status.StatusScmResult;
import org.apache.maven.scm.manager.BasicScmManager;
import org.apache.maven.scm.manager.NoSuchScmProviderException;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.ScmProvider;
import org.apache.maven.scm.provider.git.gitexe.GitExeScmProvider;
import org.apache.maven.scm.provider.svn.svnexe.SvnExeScmProvider;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.scm.repository.ScmRepositoryException;

/**
 * Abstract class which is used for both copyright checks and updates.
 */
public abstract class CopyrightAbstractMojo extends AbstractMojo {

    /** The Maven Project. */
    @Parameter(required = true, property = "project", readonly = true)
    private MavenProject project;

    /**
     * Copyright owner.
     * This string token must be present on the same line with 'copyright' keyword and the current year.
     */
    @Parameter(required = true, defaultValue = "ForgeRock AS")
    private String copyrightOwnerToken;

    /** The path to the root of the Subversion workspace to check. */
    @Parameter(required = true, defaultValue = "${basedir}")
    private String scmWorkspaceRoot;

    @Parameter(required = true, defaultValue = "${project.scm.connection}")
    private String scmRepositoryUrl;

    /** The file extensions to test. */
    public static final List<String> CHECKED_EXTENSIONS = new LinkedList<>(Arrays.asList(
            "bat", "c", "h", "html", "java", "ldif", "Makefile", "mc", "sh", "txt", "xml", "xsd", "xsl"));

    private static final List<String> EXCLUDED_END_COMMENT_BLOCK_TOKEN = new LinkedList<>(Arrays.asList(
                    "*/", "-->"));

    private static final List<String> SUPPORTED_COMMENT_MIDDLE_BLOCK_TOKEN = new LinkedList<>(Arrays.asList(
                    "*", "#", "rem", "!"));

    private static final List<String> SUPPORTED_START_BLOCK_COMMENT_TOKEN = new LinkedList<>(Arrays.asList(
                    "/*", "<!--"));

    /** The string representation of the current year. */
    Integer currentYear = Calendar.getInstance().get(Calendar.YEAR);

    private final List<String> incorrectCopyrightFilePaths = new LinkedList<>();

    /** The overall SCM Client Manager. */
    private ScmManager scmManager;

    private ScmRepository scmRepository;

    List<String> getIncorrectCopyrightFilePaths() {
        return incorrectCopyrightFilePaths;
    }

    private ScmManager getScmManager() throws MojoExecutionException {
        if (scmManager == null) {
            scmManager = new BasicScmManager();
            String scmProviderID = getScmProviderID();
            ScmProvider scmProvider;
            if ("svn".equals(scmProviderID)) {
                scmProvider = new SvnExeScmProvider();
            } else if ("git".equals(scmProviderID)) {
                scmProvider = new GitExeScmProvider();
            } else {
                throw new MojoExecutionException("Unsupported scm provider: " + scmProviderID + " or "
                        + getIncorrectScmRepositoryUrlMsg());
            }
            scmManager.setScmProvider(scmProviderID, scmProvider);
        }

        return scmManager;
    }

    private String getScmProviderID() throws MojoExecutionException {
        try {
            return scmRepositoryUrl.split(":")[1];
        } catch (Exception e) {
            throw new MojoExecutionException(getIncorrectScmRepositoryUrlMsg(), e);
        }
    }

    String getIncorrectScmRepositoryUrlMsg() {
        return "the scmRepositoryUrl property with value '" + scmRepositoryUrl + "' is incorrect. "
                + "The URL has to respect the format: scm:[provider]:[provider_specific_url]";
    }

    ScmRepository getScmRepository() throws MojoExecutionException {
        if (scmRepository == null) {
            try {
                scmRepository = getScmManager().makeScmRepository(scmRepositoryUrl);
            } catch (NoSuchScmProviderException e) {
                throw new MojoExecutionException("Could not find a provider.", e);
            } catch (ScmRepositoryException e) {
                throw new MojoExecutionException("Error while connecting to the repository", e);
            }
        }

        return scmRepository;
    }

    String getScmWorkspaceRoot() {
        return scmWorkspaceRoot;
    }

    /** Performs a diff with current working directory state against remote HEAD revision. */
    List<String> getChangedFiles() throws MojoExecutionException, MojoFailureException  {
        try {
            ScmFileSet workspaceFileSet = new ScmFileSet(new File(getScmWorkspaceRoot()));
            StatusScmResult statusResult = getScmManager().status(getScmRepository(), workspaceFileSet);
            if (!statusResult.isSuccess()) {
                getLog().error("Impossible to perform scm status command because " + statusResult.getCommandOutput());
                throw new MojoFailureException("SCM error");
            }

            List<ScmFile> scmFiles = statusResult.getChangedFiles();
            List<String> changedFilePaths = new LinkedList<>();
            for (ScmFile scmFile : scmFiles) {
                if (scmFile.getStatus() != ScmFileStatus.UNKNOWN) {
                    changedFilePaths.add(scmFile.getPath());
                }
            }

            return changedFilePaths;
        } catch (ScmException e) {
            throw new MojoExecutionException("Encountered an error while examining modified files,  SCM status:  "
                    + e.getMessage() + "No further checks will be performed.", e);
        }
    }

    /** Examines the provided files list to determine whether each changed file copyright is acceptable. */
    void checkCopyrights() throws MojoExecutionException, MojoFailureException {
        for (String changedFileName : getChangedFiles()) {
            File changedFile = new File(getScmWorkspaceRoot(), changedFileName);
            if (!changedFile.exists() || !changedFile.isFile()) {
                continue;
            }

            int lastPeriodPos = changedFileName.lastIndexOf('.');
            if (lastPeriodPos > 0) {
                String extension = changedFileName.substring(lastPeriodPos + 1);
                if (!CHECKED_EXTENSIONS.contains(extension.toLowerCase())) {
                    continue;
                }
            } else if (fileNameEquals("bin", changedFile.getParentFile())
                    && fileNameEquals("resource", changedFile.getParentFile().getParentFile())) {
                // ignore resource/bin directory.
                continue;
            }

            if (!checkCopyrightForFile(changedFile)) {
                incorrectCopyrightFilePaths.add(changedFile.getAbsolutePath());
            }
        }
    }

    private boolean fileNameEquals(String folderName, File file) {
        return file != null && folderName.equals(file.getName());
    }

    /**
     * Check to see whether the provided file has a comment line containing a
     * copyright without the current year.
     */
    @SuppressWarnings("resource")
    private boolean checkCopyrightForFile(File changedFile) throws MojoExecutionException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(changedFile));
            String line;
            while ((line = reader.readLine()) != null) {
                String lowerLine = line.toLowerCase().trim();
                if (isCommentLine(lowerLine)
                        && lowerLine.contains("copyright")
                        && line.contains(currentYear.toString())
                        && line.contains(copyrightOwnerToken)) {
                    reader.close();
                    return true;
                }
            }

            return false;
        } catch (IOException ioe) {
            throw new MojoExecutionException("Could not read file " + changedFile.getPath()
                    + " to check copyright date. No further copyright date checking will be performed.");
        } finally {
            closeSilently(reader);
        }
    }

    private String getCommentToken(String line, boolean includesStartBlock) {
        List<String> supportedTokens = SUPPORTED_COMMENT_MIDDLE_BLOCK_TOKEN;
        if (includesStartBlock) {
            supportedTokens.addAll(SUPPORTED_START_BLOCK_COMMENT_TOKEN);
        }

        if (trimmedLineStartsWith(line, EXCLUDED_END_COMMENT_BLOCK_TOKEN) != null) {
            return null;
        }

        return trimmedLineStartsWith(line, supportedTokens);
    }

    private String trimmedLineStartsWith(String line, List<String> supportedTokens) {
        for (String token : supportedTokens) {
            if (line.trim().startsWith(token)) {
                return token;
            }
        }
        return null;
    }

    boolean isNonEmptyCommentedLine(String line) {
        String commentToken = getCommentTokenInBlock(line);
        return commentToken == null || !commentToken.equals(line.trim());
    }

    String getCommentTokenInBlock(String line) {
        return getCommentToken(line, false);
    }

    boolean isCommentLine(String line) {
        return getCommentToken(line, true) != null;
    }

}
