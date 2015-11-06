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

import static org.forgerock.util.Utils.closeSilently;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
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
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.ScmVersion;
import org.apache.maven.scm.command.diff.DiffScmResult;
import org.apache.maven.scm.command.status.StatusScmResult;
import org.apache.maven.scm.log.ScmLogDispatcher;
import org.apache.maven.scm.log.ScmLogger;
import org.apache.maven.scm.manager.BasicScmManager;
import org.apache.maven.scm.manager.NoSuchScmProviderException;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.ScmProviderRepository;
import org.apache.maven.scm.provider.git.command.GitCommand;
import org.apache.maven.scm.provider.git.command.diff.GitDiffConsumer;
import org.apache.maven.scm.provider.git.gitexe.GitExeScmProvider;
import org.apache.maven.scm.provider.git.gitexe.command.GitCommandLineUtils;
import org.apache.maven.scm.provider.git.gitexe.command.diff.GitDiffCommand;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.scm.repository.ScmRepositoryException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;
import org.codehaus.plexus.util.cli.Commandline;

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

    /** The path to the root of the scm local workspace to check. */
    @Parameter(required = true, defaultValue = "${basedir}")
    private String baseDir;

    @Parameter(required = true, defaultValue = "${project.scm.connection}")
    private String scmRepositoryUrl;

    /**
     * List of file patterns for which copyright check and/or update will be skipped.
     * Pattern can contain the following wildcards (*, ?, **{@literal /}).
     */
    @Parameter(required = false)
    private List<String> disabledFiles;

    /** The file extensions to test. */
    public static final List<String> CHECKED_EXTENSIONS = new LinkedList<>(Arrays.asList(
            "bat", "c", "h", "html", "java", "ldif", "Makefile", "mc", "sh", "txt", "xml", "xsd", "xsl"));

    private static final List<String> EXCLUDED_END_COMMENT_BLOCK_TOKEN = new LinkedList<>(Arrays.asList(
                    "*/", "-->"));

    private static final List<String> SUPPORTED_COMMENT_MIDDLE_BLOCK_TOKEN = new LinkedList<>(Arrays.asList(
                    "*", "#", "rem", "!"));

    private static final List<String> SUPPORTED_START_BLOCK_COMMENT_TOKEN = new LinkedList<>(Arrays.asList(
                    "/*", "<!--"));

    private static final class CustomGitExeScmProvider extends GitExeScmProvider {

        @Override
        protected GitCommand getDiffCommand() {
            return new CustomGitDiffCommand();
        }
    }

    private static class CustomGitDiffCommand extends GitDiffCommand implements GitCommand {

        @Override
        protected DiffScmResult executeDiffCommand(ScmProviderRepository repo, ScmFileSet fileSet,
                ScmVersion unused, ScmVersion unused2) throws ScmException {
            final GitDiffConsumer consumer = new GitDiffConsumer(getLogger(), fileSet.getBasedir());
            final StringStreamConsumer stderr = new CommandLineUtils.StringStreamConsumer();
            final Commandline cl = GitCommandLineUtils.getBaseGitCommandLine(fileSet.getBasedir(), "diff");
            cl.addArguments(new String[] { "--no-ext-diff", "--relative", "master...HEAD", "." });

            if (GitCommandLineUtils.execute(cl, consumer, stderr, getLogger()) != 0) {
                return new DiffScmResult(cl.toString(), "The git-diff command failed.", stderr.getOutput(), false);
            }
            return new DiffScmResult(
                    cl.toString(), consumer.getChangedFiles(), consumer.getDifferences(), consumer.getPatch());
        }

    }

    private String getLocalScmRootPath(final File basedir) throws ScmException {
        final Commandline cl = GitCommandLineUtils.getBaseGitCommandLine(basedir, "rev-parse");
        cl.addArguments(new String[] { "--show-toplevel" });

        final StringStreamConsumer stdout = new CommandLineUtils.StringStreamConsumer();
        final StringStreamConsumer stderr = new CommandLineUtils.StringStreamConsumer();
        final ScmLogger dummyLogger = new ScmLogDispatcher();

        final int exitCode = GitCommandLineUtils.execute(cl, stdout, stderr, dummyLogger);
        return exitCode == 0 ? stdout.getOutput().trim().replace(" ", "%20")
                             : basedir.getPath();
    }


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
            if (!"git".equals(scmProviderID)) {
                throw new MojoExecutionException(
                        "Unsupported scm provider: " + scmProviderID + " or " + getIncorrectScmRepositoryUrlMsg());
            }
            scmManager.setScmProvider(scmProviderID, new CustomGitExeScmProvider());
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

    String getBaseDir() {
        return baseDir;
    }

    /**
     * Performs a diff with current working directory state against remote HEAD revision.
     * Then do a status to check uncommited changes as well.
     */
    List<File> getChangedFiles() throws MojoExecutionException, MojoFailureException  {
        try {
            final ScmFileSet workspaceFileSet = new ScmFileSet(new File(getBaseDir()));
            final DiffScmResult diffMasterHeadResult = getScmManager().diff(
                    getScmRepository(), workspaceFileSet, null, null);
            ensureCommandSuccess(diffMasterHeadResult, "diff master...HEAD .");

            final StatusScmResult statusResult = getScmManager().status(getScmRepository(), workspaceFileSet);
            ensureCommandSuccess(statusResult, "status");

            final List<File> changedFilePaths = new ArrayList<>();
            addToChangedFiles(diffMasterHeadResult.getChangedFiles(), getBaseDir(), changedFilePaths);
            final String localScmRootPath = getLocalScmRootPath(new File(getBaseDir()));
            addToChangedFiles(statusResult.getChangedFiles(), localScmRootPath, changedFilePaths);

            return changedFilePaths;
        } catch (ScmException e) {
            throw new MojoExecutionException("Encountered an error while examining modified files,  SCM status:  "
                    + e.getMessage() + "No further checks will be performed.", e);
        }
    }

    private void ensureCommandSuccess(final ScmResult result, final String cmd) throws MojoFailureException {
        if (!result.isSuccess()) {
            final String message = "Impossible to perform scm " + cmd + " command because " + result.getCommandOutput();
            getLog().error(message);
            throw new MojoFailureException(message);
        }
    }

    private void addToChangedFiles(
            final List<ScmFile> scmChangedFiles, final String rootPath, final List<File> changedFiles) {
        for (final ScmFile scmFile : scmChangedFiles) {
            final String scmFilePath = scmFile.getPath();
            if (scmFile.getStatus() != ScmFileStatus.UNKNOWN
                    && new File(scmFilePath).exists()
                    && !changedFiles.contains(scmFilePath)
                    && !fileIsDisabled(scmFilePath)) {
                changedFiles.add(new File(rootPath, scmFilePath));
            }
        }
    }

    private boolean fileIsDisabled(final String scmFilePath) {
        if (disabledFiles == null) {
            return false;
        }
        for (final String disableFile : disabledFiles) {
            String regexp = disableFile.replace("**/", "(.+/)+").replace("?", ".?").replace("*", ".*?");
            if (scmFilePath.matches(regexp)) {
                return true;
            }
        }
        return false;
    }

    /** Examines the provided files list to determine whether each changed file copyright is acceptable. */
    void checkCopyrights() throws MojoExecutionException, MojoFailureException {
        for (final File changedFile : getChangedFiles()) {
            if (!changedFile.exists() || !changedFile.isFile()) {
                continue;
            }

            final String changedFileName = changedFile.getPath();
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
