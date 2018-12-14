/**
 * Copyright (C) 2010-2015 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *       • Apache License, version 2.0
 *       • Apache Software License, version 1.0
 *       • GNU Lesser General Public License, version 3
 *       • Mozilla Public License, versions 1.0, 1.1 and 2.0
 *       • Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.wps.server.r;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.n52.shetland.ogc.ows.exception.NoApplicableCodeException;
import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.javaps.annotation.ConfigurableClass;
import org.n52.javaps.annotation.Properties;
import org.n52.wps.server.r.util.RConnector;
import org.n52.wps.server.r.util.RFileExtensionFilter;
import org.n52.wps.server.r.util.RStarter;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

@Properties(defaultPropertyFileName="r_config.default.json", propertyFileName="r_config.json")
public class R_Config implements ConfigurableClass{

    private static final Logger LOGGER = LoggerFactory.getLogger(R_Config.class);

    public static final String SCRIPT_FILE_EXTENSION = "R";

    public static final String SCRIPT_FILE_SUFFIX = "." + SCRIPT_FILE_EXTENSION;

    private String wknPrefix = "org.n52.wps.server.r.";

    public static final String LOCK_SUFFIX = "lock";

    private static final String DIR_DELIMITER = ";";

    private ArrayList<File> utilsFiles = null;

    private final RConnector connector;

    private final RStarter starter;

    private Path baseDir = null;

    private static final FileFilter rFileFilter = new RFileExtensionFilter();

    private final Map<Path, File> utilFileCache = new HashMap<>();

    private static final String enableBatchStartKey = "R_enableBatchStart";
    private static final String datatypeConfigKey = "R_datatypeConfig";
    private static final String wdStrategyKey = "R_wdStrategy";
    private static final String wdNameKey = "R_wdName";
    private static final String resourceDirectoryKey = "R_resourceDirectory";
    private static final String scriptDirectoryKey = "R_scriptDirectory";
    private static final String rServeHostKey = "R_RserveHost";
    private static final String rServePortKey = "R_RservePort";
    private static final String rServeUserKey = "R_RserveUser";
    private static final String rServePasswordKey = "R_RservePassword";
    private static final String cacheProcessesKey = "R_cacheProcesses";
    private static final String sessionMemoryLimitKey = "R_session_memoryLimit";
    private static final String resourceDownloadEnabledKey = "R_enableResourceDownload";
    private static final String importDownloadEnabledKey = "R_enableImportDownload";
    private static final String scriptDownloadEnabledKey = "R_enableScriptDownload";
    private static final String sessionInfoDownloadEnabledKey = "R_enableSessionInfoDownload";
    private static final String rServeUtilsScriptDirectoryKey = "R_utilsScriptDirectory";

    private Boolean enableBatchStart;
    private String datatypeConfig;
    private String wdStrategy;
    private String wdName;
    private String resourceDirectory;
    private String scriptDirectory;
    private String rServeHost;
    private int rServePort;
    private String rServeUser;
    private String rServePassword;
    private String rServeUtilsScriptDirectory;
    private Boolean cacheProcesses;
    private int sessionMemoryLimit;
    private boolean resourceDownloadEnabled;
    private boolean importDownloadEnabled;
    private boolean scriptDownloadEnabled;
    private boolean sessionInfoDownloadEnabled;

    public R_Config() {
        this.starter = new RStarter();
        this.connector = new RConnector(starter);

        JsonNode propertyNode = getProperties().get("properties");

        if(propertyNode != null){

            try {
                enableBatchStart = propertyNode.get(enableBatchStartKey).asBoolean();
                datatypeConfig = propertyNode.get(datatypeConfigKey).asText();
                wdStrategy = propertyNode.get(wdStrategyKey).asText();
                wdName = propertyNode.get(wdNameKey).asText();
                resourceDirectory = propertyNode.get(resourceDirectoryKey).asText();
                scriptDirectory = propertyNode.get(scriptDirectoryKey).asText();
                rServeHost = propertyNode.get(rServeHostKey).asText();
                rServePort = propertyNode.get(rServePortKey).asInt();
                rServeUser = propertyNode.get(rServeUserKey).asText();
                rServePassword = propertyNode.get(rServePasswordKey).asText();
                cacheProcesses = propertyNode.get(cacheProcessesKey).asBoolean();
                sessionMemoryLimit = propertyNode.get(sessionMemoryLimitKey).asInt();
                rServeUtilsScriptDirectory = propertyNode.get(rServeUtilsScriptDirectoryKey).asText();
                resourceDownloadEnabled = propertyNode.get(resourceDownloadEnabledKey).asBoolean();
                importDownloadEnabled = propertyNode.get(importDownloadEnabledKey).asBoolean();
                scriptDownloadEnabled = propertyNode.get(scriptDownloadEnabledKey).asBoolean();
                sessionInfoDownloadEnabled = propertyNode.get(sessionInfoDownloadEnabledKey).asBoolean();
            } catch (Exception e) {
                LOGGER.error("Could not parse properties for class {}", this.getClass().getName());
                LOGGER.error(e.getMessage());
            }

        }else{
            LOGGER.error("Could not parse properties for class {}", this.getClass().getName());
        }

        LOGGER.info("NEW {}", this);
    }

   public String resolveFullPath(String pathToResolve) throws OwsExceptionReport {
        File file = new File(pathToResolve);
        if ( !file.isAbsolute()) {
            file = getBaseDir().resolve(Paths.get(pathToResolve)).toFile();
        }

        if ( !file.exists()) {
            LOGGER.error("'" + pathToResolve + "' denotes a non-existent path.");
//            throw new ExceptionReport("Configuration Error!", "Inconsistent property");
            throw new NoApplicableCodeException();
        }

        return file.getAbsolutePath();
    }

    public Collection<Path> getResourceDirectories() {
        String resourceDirConfigParam = resourceDirectory;
        Collection<Path> resourceDirectories = new ArrayList<>();

        String[] dirs = resourceDirConfigParam.split(DIR_DELIMITER);
        for (String s : dirs) {
            Path dir = Paths.get(s);
            if ( !dir.isAbsolute()){
                dir = getBaseDir().resolve(s);
            }

            resourceDirectories.add(dir);
            LOGGER.debug("Found resource directory configured in config variable: {}", dir);
        }

        return resourceDirectories;
    }

    public Collection<File> getScriptFiles() {
        String scriptDirConfigParam = scriptDirectory;
        Collection<File> rScripts = new ArrayList<>();

        String[] scriptDirs = scriptDirConfigParam.split(DIR_DELIMITER);
        for (String s : scriptDirs) {
            File dir = new File(s);
            if ( !dir.isAbsolute()) {
                dir = new File(getBaseDir().toFile(), s);
            }
            File[] files = dir.listFiles(new RFileExtensionFilter());
            if (files == null) {
                LOGGER.info("Configured script dir does not exist: {}", dir);
                continue;
            }
            for (File rScript : files) {
                rScripts.add(rScript);
                LOGGER.debug("Registered script: {}", rScript.getAbsoluteFile());
            }
        }
        return rScripts;
    }

    public Collection<File> getScriptDirectories() {
        String scriptDirConfigParam = scriptDirectory;
        Collection<File> scriptDirectories = new ArrayList<File>();

        String[] scriptDirs = scriptDirConfigParam.split(DIR_DELIMITER);
        for (String s : scriptDirs) {
            File dir = new File(s);
            scriptDirectories.add(dir);
        }

        return scriptDirectories;
    }

    // TODO the config should not open connections
    public FilteredRConnection openRConnection() throws RserveException {
        return this.connector.getNewConnection(this.getEnableBatchStart(),
                                               this.getRServeHost(),
                                               this.getRServePort(),
                                               this.getRServeUser(),
                                               this.getRServePassword());
    }

    private String getRServePassword() {
        return rServePassword;
    }

    private String getRServeUser() {
        return rServeUser;
    }

    private int getRServePort() {
        return rServePort;
    }

    public String getRServeHost() {
        return rServeHost;
    }

    public boolean getEnableBatchStart() {
        return enableBatchStart;
    }

    public URL getProcessDescriptionURL(String processWKN) {
        String s = "" + "/WebProcessingService?Request=DescribeProcess&identifier=" + processWKN;
        try {
            return new URL(s);
        }
        catch (MalformedURLException e) {
            LOGGER.error("Could not create URL for process {}", processWKN, e);
            return null;
        }
    }

    public boolean isCacheProcesses() {
        return cacheProcesses;
    }

    public Collection<File> getUtilsFiles() {
        if (this.utilsFiles == null) {
            this.utilsFiles = new ArrayList<File>();
            Path basedir = getBaseDir();
            String configVariable = rServeUtilsScriptDirectory;
            if (configVariable != null) {
                String[] configVariableDirs = configVariable.split(DIR_DELIMITER);
                for (String s : configVariableDirs) {
                    Collection<File> files = resolveFilesFromResourcesOrFromWebapp(s, basedir);
                    this.utilsFiles.addAll(files);
                    LOGGER.debug("Added {} files to the list of util files: {}",
                                 files.size(),
                                 Arrays.toString(files.toArray()));
                }
            }
            else{
                LOGGER.error("Could not load utils directory variable from config, not loading any utils files!");
            }
        }

        return utilsFiles;
    }

    /**
     * given a relative path, this method tries to locate the directory first within the webapp folder, then
     * within the resources directory.
     *
     * @param p
     * @param baseDir
     *        the full path to the webapp directory
     * @return
     */
    private Collection<File> resolveFilesFromResourcesOrFromWebapp(String s, Path baseDir) {
        LOGGER.debug("Loading util files from {}", s);

        Path p = Paths.get(s);
        if ( !baseDir.isAbsolute()){
            throw new RuntimeException(String.format("The given basedir (%s) is not absolute, cannot resolve path %s.",
                                                     baseDir, p));
        }

        ArrayList<File> foundFiles = new ArrayList<>();
        File f = null;

        // try resource first
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(s);) {
        //URL url = Resources.getResource(p.toString());
        //try (InputStream input = Resources.asByteSource(url).openStream();) {
            if (input != null) {
                if (this.utilFileCache.containsKey(p) && this.utilFileCache.get(p).exists()) {
                    f = this.utilFileCache.get(p);
                }
                else {
                    try {
                        f = File.createTempFile("wps4rutil_", "_" + p.getFileName().toString());
                        Files.copy(input, Paths.get(f.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
                        if (f.exists() && rFileFilter.accept(f)) {
                            foundFiles.add(f);
                            this.utilFileCache.put(p, f);
                        }
                    }
                    catch (IOException e) {
                        LOGGER.warn("Could not add util file from classpath.", e);
                    }
                }
            }
        }
        catch (IOException e) {
            LOGGER.warn("Could not add util file from classpath.", e);
        }

        if (f == null) {
            // try resolving with basedir
            Path resolved = baseDir.resolve(p);
            if (Files.exists(resolved)) {
                f = resolved.toFile();
                File[] files = f.listFiles(rFileFilter);
                foundFiles.addAll(Arrays.asList(files));
            }
            else{
                LOGGER.warn("Configured utils directory does not exist: {}", p);
            }
        }

        LOGGER.debug("Found {} util files in directory configured as '{}' at {}", foundFiles.size(), p, f);

        return foundFiles;
    }

    public Path getBaseDir() {
//        return baseDir;
        try {
            return this.baseDir == null
                    ? Paths.get(getClass().getResource("/").toURI())
                    : baseDir;
        } catch (URISyntaxException e) {
            LOGGER.error("Could not determine fallback base dir!", e);
            return Paths.get(""); // empty path
        }
    }

    protected void setBaseDir(Path baseDir) {
        this.baseDir = baseDir;
    }

    public String getPublicScriptId(String s) {
        return getWknPrefix() + s;
    }

    public String getWknPrefix() {
        return wknPrefix;
    }

    public void setWknPrefix(String wknPrefix) {
        this.wknPrefix = wknPrefix;
    }

    public boolean isResourceDownloadEnabled() {
        return resourceDownloadEnabled;
    }

    public boolean isImportDownloadEnabled() {
        return importDownloadEnabled;
    }

    public boolean isScriptDownloadEnabled() {
        return scriptDownloadEnabled;
    }

    public boolean isSessionInfoLinkEnabled() {
        return sessionInfoDownloadEnabled;
    }

    public String getDatatypeConfig() {
        return datatypeConfig;
    }

    public int getSessionMemoryLimit() {
        return sessionMemoryLimit;
    }

    public String getWdStrategy() {
        return wdStrategy;
    }

    public String getWdName() {
        return wdName;
    }
}
