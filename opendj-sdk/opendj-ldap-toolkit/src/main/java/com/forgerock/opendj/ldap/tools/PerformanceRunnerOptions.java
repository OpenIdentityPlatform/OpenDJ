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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package com.forgerock.opendj.ldap.tools;

import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.ConsoleApplication;

/**
 * SDK Performance Runner options wrapper.
 */
class PerformanceRunnerOptions {
    private ArgumentParser argParser;
    private ConsoleApplication app;

    private boolean supportsRebind = true;
    private boolean supportAsynchronousRequests = true;
    private boolean supportsMultipleThreadsPerConnection = true;
    private boolean supportsGeneratorArgument = true;

    PerformanceRunnerOptions(ArgumentParser argParser, ConsoleApplication app) {
        this.argParser = argParser;
        this.app = app;
    }

    boolean supportsRebind() {
        return supportsRebind;
    }

    void setSupportsRebind(boolean supportsRebind) {
        this.supportsRebind = supportsRebind;
    }

    boolean supportsAsynchronousRequests() {
        return supportAsynchronousRequests;
    }

    void setSupportsAsynchronousRequests(boolean supportAsynchronousRequests) {
        this.supportAsynchronousRequests = supportAsynchronousRequests;
    }

    boolean supportsMultipleThreadsPerConnection() {
        return supportsMultipleThreadsPerConnection;
    }

    void setSupportsMultipleThreadsPerConnection(boolean supportsMultipleThreadsPerConnection) {
        this.supportsMultipleThreadsPerConnection = supportsMultipleThreadsPerConnection;
    }

    boolean supportsGeneratorArgument() {
        return supportsGeneratorArgument;
    }

    void setSupportsGeneratorArgument(boolean supportsGeneratorArgument) {
        this.supportsGeneratorArgument = supportsGeneratorArgument;
    }

    ArgumentParser getArgumentParser() {
        return argParser;
    }

    ConsoleApplication getConsoleApplication() {
        return app;
    }

}
