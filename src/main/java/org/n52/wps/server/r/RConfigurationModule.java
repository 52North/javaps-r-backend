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

import java.util.ArrayList;
import java.util.List;

import org.n52.javaps.algorithm.AbstractAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RConfigurationModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(RConfigurationModule.class);

    private boolean isActive = true;

    private static final String enableBatchStartKey = "R_enableBatchStart"; //RWPSConfigVariables.ENABLE_BATCH_START.toString();
    private static final String datatypeConfigKey = "R_datatypeConfig"; //RWPSConfigVariables.R_DATATYPE_CONFIG.toString();
    private static final String wdStrategyKey = "R_wdStrategy"; //RWPSConfigVariables.R_WORK_DIR_STRATEGY.toString();
    private static final String wdNameKey = "R_wdName"; //RWPSConfigVariables.R_WORK_DIR_NAME.toString();
    private static final String resourceDirectoryKey = "R_resourceDirectory"; //RWPSConfigVariables.RESOURCE_DIR.toString();
    private static final String scriptDirectoryKey = "R_scriptDirectory"; //RWPSConfigVariables.SCRIPT_DIR.toString();
    private static final String rServeHostKey = "R_RserveHost"; //RWPSConfigVariables.RSERVE_HOST.toString();
    private static final String rServePortKey = "R_RservePort"; //RWPSConfigVariables.RSERVE_PORT.toString();
    private static final String rServeUserKey = "R_RserveUser"; //RWPSConfigVariables.RSERVE_USER.toString();
    private static final String rServePasswordKey = "R_RservePassword"; //RWPSConfigVariables.RSERVE_PASSWORD.toString();
    private static final String rServeUtilsScriptDirectoryKey = "R_utilsScriptDirectory"; //RWPSConfigVariables.R_UTILS_DIR.toString();
    private static final String cacheProcessesKey = "R_cacheProcesses"; //RWPSConfigVariables.R_CACHE_PROCESSES.toString();
    private static final String sessionMemoryLimitKey = "R_session_memoryLimit"; //RWPSConfigVariables.R_SESSION_MEMORY_LIMIT.toString();
    private static final String resourceDownloadEnabledKey = "R_enableResourceDownload";
    private static final String importDownloadEnabledKey = "R_enableImportDownload";
    private static final String scriptDownloadEnabledKey = "R_enableScriptDownload";
    private static final String sessionInfoDownloadEnabledKey = "R_enableSessionInfoDownload";

    private Boolean enableBatchStart = false;
    private String datatypeConfig = "D:/dev/GitHub4w/WPS/52n-wps-webapp/src/main/webapp/R/R_Datatype.conf";
    private String wdStrategy = "default";
    private String wdName = "wps4r_working_dir";
    private String resourceDirectory = "R/resources";
    private String scriptDirectory = "D:/dev/GitHub4w/WPS/52n-wps-webapp/src/main/webapp/R/scripts";
    private String rServeHost = "localhost";    
    private String rServePort = "6311";
    private String rServeUser = "";
    private String rServePassword = "";
    private String rServeUtilsScriptsDirectory = "R/utils;org/n52/wps/R/unzipRenameFile.R;org/n52/wps/R/wpsStatus.R";
    private Boolean cacheProcesses = true;
    private String sessionMemoryLimit = "1000";
    private boolean resourceDownloadEnabled = true;
    private boolean importDownloadEnabled = true;
    private boolean scriptDownloadEnabled = true;
    private boolean sessionInfoDownloadEnabled = true;

    private List<AbstractAlgorithm> algorithmEntries;

    public RConfigurationModule() {
        algorithmEntries = new ArrayList<>();
    }

    
    public String getModuleName() {
        return "R Configuration Module";
    }
    
    public boolean isActive() {
        return isActive;
    }

    
    public void setActive(boolean active) {
        this.isActive = active;
    }
    
    public List<AbstractAlgorithm> getAlgorithmEntries() {
        return algorithmEntries;
    }
    
    public String getClassName() {
        return RAlgorithmRepository.class.getName();
    }

    public Boolean isEnableBatchStart() {
        return enableBatchStart;
    }
    public void setEnableBatchStart(boolean enableBatchStart) {
        this.enableBatchStart = enableBatchStart;
    }

    public String getDatatypeConfig() {
        return datatypeConfig;
    }

    public void setDatatypeConfig(String datatypeConfig) {
        this.datatypeConfig = datatypeConfig;
    }

    public String getWdStrategy() {
        return wdStrategy;
    }
    
    public void setWdStrategy(String wdStrategy) {
        this.wdStrategy = wdStrategy;
    }

    public String getWdName() {
        return wdName;
    }

    public void setWdName(String wdName) {
        this.wdName = wdName;
    }

    public String getResourceDirectory() {
        return resourceDirectory;
    }

    public void setResourceDirectory(String resourceDirectory) {
        this.resourceDirectory = resourceDirectory;
    }

    public String getScriptDirectory() {
        return scriptDirectory;
    }

    public void setScriptDirectory(String scriptDirectory) {
        this.scriptDirectory = scriptDirectory;
    }

    public String getRServeHost() {
        return rServeHost;
    }

    public void setRServeHost(String rServeHost) {
        if (rServeHost != null && !rServeHost.isEmpty()) {
            this.rServeHost = rServeHost;
        }
    }

    public String getRServePort() {
        return rServePort;
    }

    public void setRServePort(String rServePort) {
        try {
            Integer.parseInt(rServePort);
        } catch (NumberFormatException e) {
            LOGGER.warn("Config variable {} does not contain a parseble integer. Using default port 6311.", rServePortKey, e);
            rServePort = "6311";
        }
        this.rServePort = rServePort;
    }

    public String getRServeUser() {
        return rServeUser;
    }

    public void setRServeUser(String rServeUser) {
        this.rServeUser = rServeUser;
    }

    public String getrServePassword() {
        return rServePassword;
    }

    public void setRServePassword(String rServePassword) {
        this.rServePassword = rServePassword;
    }

    public String getRServeUtilsScriptsDirectory() {
        return rServeUtilsScriptsDirectory;
    }
    
    public void setRServeUtilsScriptsDirectory(String rServeUtilsScriptsDirectory) {
        this.rServeUtilsScriptsDirectory = rServeUtilsScriptsDirectory;
    }

    public Boolean isCacheProcesses() {
        return cacheProcesses;
    }

    public void setCacheProcesses(boolean cacheProcesses) {
        this.cacheProcesses = cacheProcesses;
    }

    public String getSessionMemoryLimit() {
        return sessionMemoryLimit;
    }

    public void setSessionMemoryLimit(String sessionMemoryLimit) {
        this.sessionMemoryLimit = sessionMemoryLimit;
    }

    public boolean isImportDownloadEnabled() {
        return importDownloadEnabled;
    }

    public void setImportDownloadEnabled(boolean importDownloadEnabled) {
        this.importDownloadEnabled = importDownloadEnabled;
    }

    public boolean isResourceDownloadEnabled() {
        return resourceDownloadEnabled;
    }

    public void setResourceDownloadEnabled(boolean resourceDownloadEnabled) {
        this.resourceDownloadEnabled = resourceDownloadEnabled;
    }

    public boolean isScriptDownloadEnabled() {
        return scriptDownloadEnabled;
    }

    public void setScriptDownloadEnabled(boolean scriptDownloadEnabled) {
        this.scriptDownloadEnabled = scriptDownloadEnabled;
    }

    public boolean isSessionInfoDownloadEnabled() {
        return sessionInfoDownloadEnabled;
    }

    public void setSessionInfoDownloadEnabled(boolean sessionInfoDownloadEnabled) {
        this.sessionInfoDownloadEnabled = sessionInfoDownloadEnabled;
    }

}
