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
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.io.FilenameUtils;
import org.n52.javaps.algorithm.AlgorithmRepository;
import org.n52.javaps.algorithm.IAlgorithm;
import org.n52.javaps.description.TypedProcessDescription;
import org.n52.shetland.ogc.ows.OwsCode;
import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.wps.server.r.data.CustomDataTypeManager;
import org.n52.wps.server.r.data.RDataTypeRegistry;
import org.n52.wps.server.r.info.RProcessInfo;
import org.n52.wps.server.r.metadata.RAnnotationParser;
import org.n52.wps.server.r.metadata.RProcessDescriptionCreator;
import org.n52.wps.server.r.syntax.RAnnotation;
import org.n52.wps.server.r.syntax.RAnnotationException;
import org.n52.wps.server.r.syntax.RAnnotationType;
import org.n52.wps.server.r.util.InvalidRScriptException;
import org.n52.wps.server.r.util.ResourceUrlGenerator;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A repository to retrieve the available algorithms.
 *
 * @author Matthias Hinz, Daniel Nüst
 *
 */
public class RAlgorithmRepository implements AlgorithmRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RAlgorithmRepository.class);

    /*
     * instantiation is managed by IoC container (spring) and the RepositoryManager only
     * has the name of the config module at hand to retrieve or instantiate (hard wiring
     * via reflection) this instance.
     *
     * Using the repository's name instead of the config module everywhere would hinder
     * the WPS to add configured algorithms to the config module though.
     */
    static final String COMPONENT_NAME = "org.n52.wps.server.r.RConfigurationModule";
//    static final String COMPONENT_NAME = "RAlgorithmRepository";

    private static final String DESCRPTION_VERSION_FOR_VALIDATION = "2.0.0";

    private final Map<OwsCode, GenericRProcess> rProcesses = new HashMap<>();

    private final Map<String, RProcessInfo> processInfos = new HashMap<>();

    @Inject
    private R_Config config;

    @Inject
    private ScriptFileRepository scriptRepo;

    @Inject
    private RAnnotationParser parser;

    @Inject
    private ResourceFileRepository resourceRepo;

    // needed to autowire before script registration starts
    @Inject
    private CustomDataTypeManager customDataTypes;

    @Inject
    private RDataTypeRegistry dataTypeRegistry;

    @Inject
    private RProcessDescriptionCreator descriptionCreator;

    @Inject
    private ResourceUrlGenerator resourceUrlGenerator;

    public RAlgorithmRepository() {
        LOGGER.info("NEW {}", this);
    }

    public void init() {
        LOGGER.info("Initializing Local*R*ConfigurationModule..");

         // TODO tests expect a configuration manager injected here
//        SpringIntegrationHelper.autowireBean(WPSConfig.getInstance());

            if ( !isRServeAvailable()) {
                LOGGER.error("RServe is not available, not adding ANY algorithms!");
                return;
            }
            scriptRepo.registerScriptFiles(config.getScriptFiles());
            initializeResourceDirectoriesRepository();
            try {
                intializeAvailableAlgorithms();
            } catch (Exception e) {
                LOGGER.error("Could not initialize script repository", e);
            }
            LOGGER.info("Initialized *R*AlgorithmRepository");
    }

     /**
      * Check if Rserve can be found
      */
    private boolean isRServeAvailable() {
        LOGGER.debug("Trying to connect to Rserve to verify startup conditions.");
        RConnection testcon = null;
        try {
            testcon = config.openRConnection();
            LOGGER.info("WPS successfully connected to Rserve.");
        }
        catch (RserveException e) {
            LOGGER.error("[Rserve] Could not connect to Rserve.", e);
            return false;
        } finally {
            if (testcon != null) {
                testcon.close();
            }
        }
        return true;
    }

    private void initializeResourceDirectoriesRepository() {
        Collection<Path> resourceDirectory = config.getResourceDirectories();
        resourceDirectory.stream().forEach(rd -> {
            resourceRepo.addResourceDirectory(rd);
        });
    }

    private void intializeAvailableAlgorithms() throws RAnnotationException, OwsExceptionReport {

        Collection<File> scriptFiles = config.getScriptFiles();

        for (File file : scriptFiles) {
            String wkn = scriptRepo.getWKNForScriptFile(file);
            String publicId = config.getPublicScriptId(wkn);
            LOGGER.debug("Adding algorithm: {} with publicId: {}", wkn, publicId);
            addAlgorithm(wkn);
        }
        //TODO check, do we need a means to enable only selected algorithms!?
//        List<AlgorithmEntry> configuredAlgorithms = configModule.getAlgorithmEntries();
//        LOGGER.debug("Adding algorithms: {}", configuredAlgorithms.stream()
//                .map(a -> a.toString())
//                .collect(Collectors.joining(", ")));
//
//        configuredAlgorithms.stream()
//                .forEach((entry) -> {
//            if ( !entry.isActive()) {
//                LOGGER.warn("Inactive algorithm not added: {}", entry.toString());
//            } else{
//                final String algorithm = entry.getAlgorithm();
//                String publicId = config.getPublicScriptId(algorithm);
//                LOGGER.debug("Adding algorithm: {} with publicId: {}", algorithm, publicId);
//                addAlgorithm(publicId);
//            }
//        });
    }

    public boolean addAlgorithm(Object item) {
        if ( !canHandleItem(item)) {
            LOGGER.debug("Ignore unsupported item '{}' of class '{}'", item, item.getClass());
            return false;
        }

        if (item instanceof File) {
            return initializeRProcess((File) item);
        } else {
            return initializeRProcess((String) item);
        }
    }

    private boolean canHandleItem(Object item) {
        if (item instanceof String) {
            return true;
        }
        if (item instanceof File) {
            File file = (File) item;
            String extension = FilenameUtils.getExtension(file.getName());
            return "R".equalsIgnoreCase(extension);
        }
        return false;
    }

    private boolean initializeRProcess(File file) {
        boolean success = true;
        try {
            success &= scriptRepo.registerScriptFile(file);
            String wkn = scriptRepo.getWKNForScriptFile(file);
            return success && initializeRProcess(wkn);
        } catch (RAnnotationException | OwsExceptionReport e) {
            LOGGER.error("Could not initialize R process.", e);
        }
        return false;
    }

    private boolean initializeRProcess(String processName) {
        try {
            LOGGER.debug("Initialize RProcess with name {}", processName);
            RProcessInfo processInfo = createRProcessInfo(processName);
            processInfos.put(processName, processInfo);
            LOGGER.trace("Added internal info: '{}'", processInfo);

            GenericRProcess p = createRProcess(processName);
            rProcesses.put(new OwsCode(processName), p);
            LOGGER.info("ADDED algorithm as generic R process under name '{}': {}", processName, p);

            addResourcesForGenericRProcess(p);
            addImportsForGenericRProcess(p);
            return true;
        }
        catch (RuntimeException | InvalidRScriptException e) {
            LOGGER.error("Could not load algorithm '{}'", processName, e);
            processInfos.remove(processName);
            rProcesses.remove(processName);
            return false;
        }
    }

    private RProcessInfo createRProcessInfo(String wellKnownName) throws InvalidRScriptException {
        LOGGER.trace("Loading script for '{}'", wellKnownName);
        File f = scriptRepo.getValidatedScriptFile(wellKnownName);
        return new RProcessInfo(wellKnownName, f, parser);
    }

    private GenericRProcess createRProcess(String wellKnownName) {
        LOGGER.debug("Loading algorithm '{}'", wellKnownName);
        GenericRProcess algorithm = new GenericRProcess(wellKnownName, config, dataTypeRegistry, resourceUrlGenerator, scriptRepo, parser, descriptionCreator);//TODO
//        SpringIntegrationHelper.autowireBean(algorithm);
        /*
         * weak inheritance implementation. When using injected singleton beans
         * like R_Config we have to initialize description by hand
         */
        algorithm.initializeDescription();
        validateProcessDescription(algorithm);
        return algorithm;
    }

    private void validateProcessDescription(GenericRProcess algorithm) {
        //TODO
//        if ( !algorithm.processDescriptionIsValid(DESCRPTION_VERSION_FOR_VALIDATION)) {
//            // collect the errors
//            ProcessDescriptionType description = (ProcessDescriptionType) algorithm.getDescription().getProcessDescriptionType(DESCRPTION_VERSION_FOR_VALIDATION);
//            XmlOptions validateOptions = new XmlOptions();
//            ArrayList<XmlError> errorList = new ArrayList<>();
//            validateOptions.setErrorListener(errorList);
//            // run validation again
//            description.validate(validateOptions);
//            StringBuilder validationMessages = new StringBuilder();
//            validationMessages.append("\n");
//
//            for (XmlError e : errorList) {
//                validationMessages.append("[");
//                validationMessages.append(e.getLine());
//                validationMessages.append(" | ");
//                validationMessages.append(e.getErrorCode());
//                validationMessages.append("] ");
//                validationMessages.append(e.getMessage());
//                validationMessages.append("\n");
//            }
//            LOGGER.warn("Algorithm description is not valid {}. Errors: {}",
//                        algorithm.getWellKnownName(),
//                        validationMessages.toString());
//
//            throw new RuntimeException("Could not load algorithm " + algorithm.getWellKnownName() + ". ProcessDescription not valid: "
//                    + validationMessages.toString());
//        }
    }

    private void addResourcesForGenericRProcess(GenericRProcess process) {
        String algorithm_wkn = process.getWellKnownName();
        LOGGER.debug("Adding resources for algorithm {}", algorithm_wkn);
        getResourceAnnotations(process).stream()
                .forEach((rAnnotation) -> {
                    if (resourceRepo.registerResources(rAnnotation)) {
                        LOGGER.debug("Registered resources for algorithm {} based on annotation: {}",
                                algorithm_wkn,
                                rAnnotation);
                    } else {
                        LOGGER.warn("Could not register resources based on annotation {}", rAnnotation);
                    }
                });

    }

    private List<RAnnotation> getResourceAnnotations(GenericRProcess process) {
        try {
            return RAnnotation.filterAnnotations(process.getAnnotations(), RAnnotationType.RESOURCE);
        }
        catch (RAnnotationException e) {
            LOGGER.error("Could not get resoure annotations for algorithm  {}", process.getWellKnownName(), e);
            return Collections.emptyList();
        }
    }

    private void addImportsForGenericRProcess(GenericRProcess process) {
        String algorithm_wkn = process.getWellKnownName();
        LOGGER.debug("Adding imports for algorithm {}", algorithm_wkn);
        Path scriptParent = scriptRepo.getScriptFile(algorithm_wkn).toPath().getParent();
        getImportAnnotations(process).stream()
                .forEach((rAnnotation) -> {
                    if (resourceRepo.registerImport(rAnnotation, scriptParent)) {
                        LOGGER.debug("Registered import as resource for algorithm {} based on annotation: {}",
                                algorithm_wkn,
                                rAnnotation);
                    } else {
                        LOGGER.warn("Could not register resources based on annotation {}", rAnnotation);
                    }
                });
    }

    private List<RAnnotation> getImportAnnotations(GenericRProcess process) {
        try {
            return RAnnotation.filterAnnotations(process.getAnnotations(), RAnnotationType.IMPORT);
        }
        catch (RAnnotationException e) {
            LOGGER.error("Could not get import annotations for algorithm  {}", process.getWellKnownName(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<IAlgorithm> getAlgorithm(String algorithmName) {
        if ( !this.config.isCacheProcesses()) {
            LOGGER.debug("Process cache disabled, creating new process for id '{}'", algorithmName);
            if ( !addAlgorithm(algorithmName)) {
                LOGGER.warn("Problem adding algorithm for deactivated cache.");
            }
        }

        if ( !containsAlgorithm(algorithmName)) {
            throw new RuntimeException("This repository does not contain an algorithm '" + algorithmName + "'");
        }

        return Optional.ofNullable(this.rProcesses.get(algorithmName));
    }

    public boolean containsAlgorithm(String processID) {
        return this.rProcesses.containsKey(processID);
    }

    public boolean removeAlgorithm(Object item) {
        if ( !canHandleItem(item)) {
            LOGGER.debug("Ignore removing of unsupported item '{}' of class '{}'", item, item.getClass());
            return false;
        }

        String id;
        if (item instanceof File) {
            File file = (File) item;
            try {
                id = scriptRepo.getWKNForScriptFile(file);
            } catch (OwsExceptionReport | RAnnotationException e) {
                LOGGER.error("Could remove R Algorithm '{}'", file.getAbsolutePath(), e);
                return false;
            }
        } else {
            id = (String) item;
        }
        if (this.rProcesses.containsKey(id)) {
            this.rProcesses.remove(id);

            // TODO remove scripts from script repo

        }
        LOGGER.info("Removed algorithm: {}", id);
        return true;
    }

    public RProcessInfo getProcessInfo(String processID) {
        return this.processInfos.get(processID);
    }

    public void shutdown() {
        LOGGER.info("Shutting down ...");
        this.rProcesses.clear();
        this.processInfos.clear();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RAlgorithmRepository [");
        if (rProcesses != null){
            builder.append("algorithm count=").append(rProcesses.size()).append(", ");
        }
        if (config != null){
            builder.append("config=").append(config).append(", ");
        }
        // if (changeManager != null)
        // builder.append("changeManager=").append(changeManager).append(", ");
        // if (repo != null)
        // builder.append("repo=").append(repo).append(", ");
        // if (parser != null)
        // builder.append("parser=").append(parser).append(", ");
        // if (processInfos != null)
        // builder.append("processInfos=").append(processInfos).append(", ");
        return builder.toString();
    }

    @Override
    public void destroy() {
    }

    @Override
    public Set<OwsCode> getAlgorithmNames() {
        return this.rProcesses.keySet();
    }

    @Override
    public Optional<IAlgorithm> getAlgorithm(OwsCode id) {
        return Optional.ofNullable(this.rProcesses.get(id));
    }

    @Override
    public Optional<TypedProcessDescription> getProcessDescription(OwsCode id) {
        return Optional.ofNullable(getAlgorithm(id).get().getDescription());
    }

    @Override
    public boolean containsAlgorithm(OwsCode id) {
        return this.rProcesses.containsKey(id);
    }


}
