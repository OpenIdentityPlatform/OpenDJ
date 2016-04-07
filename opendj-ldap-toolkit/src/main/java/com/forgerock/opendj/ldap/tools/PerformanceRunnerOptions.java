/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Portions Copyright 2014-2016 ForgeRock AS.
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
