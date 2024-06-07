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
 * Copyright 2024 3A Systems LLC.
 */

package org.openidentityplatform.opendj.embedded;

import org.apache.commons.io.FileUtils;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.ldif.EntryReader;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.embedded.ConfigParameters;
import org.forgerock.opendj.server.embedded.ConnectionParameters;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServerException;
import org.forgerock.opendj.server.embedded.SetupParameters;
import org.forgerock.opendj.server.embedded.UpgradeParameters;
import org.opends.server.backends.MemoryBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmbeddedOpenDJ implements Runnable, Closeable {
    private static final String JAR_SCHEMA_DIRECTORY = "opendj/config/schema/";

    final static Logger logger = LoggerFactory.getLogger(EmbeddedOpenDJ.class.getName());
    final EmbeddedDirectoryServer server;

    final Config config;

    public EmbeddedOpenDJ() {
        this(new Config());
    }

    public EmbeddedOpenDJ(Config config) {

        logger.info("Create embedded OpenDJ instance: {}", config);

        this.config = config;
        File tempDirectory = new File(System.getProperty("java.io.tmpdir"));
        File rootDirectory = new File(tempDirectory, "opendj");
        try {
            if(rootDirectory.exists()) {
                FileUtils.deleteDirectory(rootDirectory);
            }
            rootDirectory.mkdir();

            File configDirectory = new File(rootDirectory, "config");
            File schemaDirectory = new File(configDirectory, "schema");
            server = EmbeddedDirectoryServer.manageEmbeddedDirectoryServer(
                    ConfigParameters.configParams()
                            .serverRootDirectory(rootDirectory.getPath())
                            .configurationFile(Paths.get(rootDirectory.getPath(), "config", "config.ldif").toString()),
                    ConnectionParameters.connectionParams()
                            .hostName("localhost")
                            .ldapPort(config.getPort())
                            .adminPort(config.getAdminPort())
                            .bindDn("cn=Directory Manager")
                            .bindPassword(config.getAdminPassword()),
                    System.out,
                    System.err);

            copyFilesFromJar(Collections.singletonList("opendj.zip"),"embedded-opendj/",rootDirectory);
            server.extractArchiveForSetup(new File(rootDirectory,"opendj.zip"));

            server.setup(
                    SetupParameters.setupParams()
                            .baseDn(config.getBaseDN())
                            .backendType(config.getBackendType())
                            .jmxPort(config.getJmxPort())
            );

            List<String> schemaFiles = new ArrayList<>();
            if (config.getLdifSchema() != null) {
                schemaFiles.add(config.getLdifSchema());
            }

            copyFilesFromJar(schemaFiles, JAR_SCHEMA_DIRECTORY, schemaDirectory);

        }catch (Exception e) {
            logger.error("Error initializing OpenDJ");
            throw new RuntimeException(e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    @Override
    public void run() {
        try {
            final DN baseDN = DN.valueOf(config.getBaseDN());
            try {
                ManagementContext config = server.getConfiguration();
                BackendCfgClient userRoot = config.getRootConfiguration().getBackend("userRoot");
                userRoot.setBaseDN((Collections.singletonList(baseDN)));
                userRoot.setEnabled(true);
                userRoot.commit();
                config.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            logger.info("Check upgrade OpenDJ ...");
            server.upgrade(UpgradeParameters.upgradeParams().isIgnoreErrors(false));

            logger.info("Start OpenDJ ...");
            server.start();

        } catch (Exception e) {
            logger.error("Error starting OpenDJ", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close()  {
        if (server.isRunning())
            try {
                logger.info("Shutting down OpenDJ ...");
                server.stop(this.getClass().getName(), LocalizableMessage.raw("Stopped after receiving Control-C"));
            }catch (Throwable e) {
                logger.error("Error stopping OpenDJ", e);
            }
    }

    private void copyFilesFromJar(List<String> jarFiles, String jarDirectory, File outputDirectory) throws IOException{
        for(String jarFile : jarFiles) {
            File outputFile = new File(outputDirectory, new File(jarFile).getName());
            final String resourcePath = !jarFile.contains("/")
                    ? "/"+jarDirectory + jarFile
                    : jarFile;
            InputStream in = new File(jarFile).exists()
                    ? Files.newInputStream(new File(jarFile).toPath())
                    : MemoryBackend.class.getResourceAsStream(resourcePath);
            if (in == null) {
                throw new IOException("cannot find " + resourcePath);
            }
            FileUtils.copyInputStreamToFile(in, outputFile);
            in.close();
        }
    }

    public void importData(InputStream inputStream) throws EmbeddedDirectoryServerException, IOException {
        logger.info("start import ldif from stream");

        EntryReader reader;
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            reader = new LDIFEntryReader(bufferedReader);
        } catch (Exception e) {
            logger.error("import ldif : {}", e, e);
            throw e;
        }
        org.forgerock.opendj.ldap.Entry  entryBefore;
        final Connection connection = server.getInternalConnection();
        long recordCount = 0;
        while (reader.hasNext() && (entryBefore = reader.readEntry()) != null) {
            recordCount++;
            try {
                connection.add(entryBefore);
                logger.info("import ldif : {}",entryBefore.getName());
            }catch (LdapException e) {
                logger.error("import ldif : {} {}",entryBefore.getName(),e.toString());
            }
        }
        if(recordCount == 0) {
            logger.error("no records were imported, check file contents and permissions");
            throw new RuntimeException("no records were imported");
        }
        reader.close();
        connection.close();
    }

    public void getData(String baseDN, OutputStream out) throws IOException, EmbeddedDirectoryServerException {
        LDIFEntryWriter ldifWriter = new LDIFEntryWriter(out);
        final Connection connection = server.getInternalConnection();

        ConnectionEntryReader reader = connection.search(baseDN, SearchScope.WHOLE_SUBTREE, "(objectClass=*)");
        while(reader.hasNext()) {
            if (!reader.isReference()) {
                SearchResultEntry se = reader.readEntry();
                if (!skipEntry(se)) {
                    ldifWriter.writeEntry(se);
                    logger.info("export {}", se.toString());
                }
            }
        }
        reader.close();
        ldifWriter.close();
        connection.close();
    }

    private boolean skipEntry(SearchResultEntry se) {
        for (String skip : config.getSkipSet()) {
            if (se.getName().toString().toLowerCase().contains(skip)){
                logger.trace("ignore export {}", se);
                return true;
            }
        }
        return false;
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

}
