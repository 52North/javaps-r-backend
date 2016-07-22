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
package org.n52.wps.server.r.metadata;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.n52.iceland.exception.ows.NoApplicableCodeException;
import org.n52.iceland.exception.ows.OwsExceptionReport;
import org.n52.iceland.ogc.ows.OwsAnyValue;
import org.n52.iceland.ogc.ows.OwsCode;
import org.n52.iceland.ogc.ows.OwsDomainMetadata;
import org.n52.iceland.ogc.ows.OwsKeyword;
import org.n52.iceland.ogc.ows.OwsLanguageString;
import org.n52.iceland.ogc.ows.OwsMetadata;
import org.n52.iceland.ogc.ows.OwsPossibleValues;
import org.n52.iceland.ogc.ows.OwsValue;
import org.n52.iceland.ogc.wps.Format;
import org.n52.iceland.ogc.wps.InputOccurence;
import org.n52.iceland.ogc.wps.description.LiteralDataDomain;
import org.n52.iceland.ogc.wps.description.impl.LiteralDataDomainImpl;
import org.n52.javaps.description.TypedComplexInputDescription;
import org.n52.javaps.description.TypedComplexOutputDescription;
import org.n52.javaps.description.TypedLiteralInputDescription;
import org.n52.javaps.description.TypedLiteralOutputDescription;
import org.n52.javaps.description.TypedProcessDescription;
import org.n52.javaps.description.TypedProcessInputDescription;
import org.n52.javaps.description.TypedProcessOutputDescription;
import org.n52.javaps.description.impl.TypedComplexInputDescriptionImpl;
import org.n52.javaps.description.impl.TypedComplexOutputDescriptionImpl;
import org.n52.javaps.description.impl.TypedLiteralInputDescriptionImpl;
import org.n52.javaps.description.impl.TypedLiteralOutputDescriptionImpl;
import org.n52.javaps.description.impl.TypedProcessDescriptionImpl;
import org.n52.javaps.io.Data;
import org.n52.javaps.io.InputHandler;
import org.n52.javaps.io.InputHandlerRepository;
import org.n52.javaps.io.OutputHandler;
import org.n52.javaps.io.OutputHandlerRepository;
import org.n52.javaps.io.complex.ComplexData;
import org.n52.javaps.io.data.binding.complex.GenericFileDataBinding;
import org.n52.javaps.io.literal.LiteralTypeRepository;
import org.n52.wps.server.r.data.RDataTypeRegistry;
import org.n52.wps.server.r.syntax.RAnnotation;
import org.n52.wps.server.r.syntax.RAnnotationException;
import org.n52.wps.server.r.syntax.RAnnotationType;
import org.n52.wps.server.r.syntax.RAttribute;
import org.n52.wps.server.r.util.ResourceUrlGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RProcessDescriptionCreator {

    public static final String SCRIPT_LINK_TITLE = "R Script";

    public static final String SESSION_INFO_TITLE = "R Session Info";

    public static final String RESOURCE_TITLE_PREFIX = "Resource: ";

    public static final String IMPORT_TITLE_PREFIX = "Import: ";

    private static final String DEFAULT_VERSION = "1.0.0";

    private static final Logger log = LoggerFactory.getLogger(RProcessDescriptionCreator.class);

    private final String id;

    private final boolean resourceDownloadEnabled;

    private final boolean importDownloadEnabled;

    private final boolean scriptDownloadEnabled;

    private final boolean sessionInfoLinkEnabled;

    private final ResourceUrlGenerator urlGenerator;
    
    
    @Inject
    InputHandlerRepository parserRepository;
    @Inject
    OutputHandlerRepository generatorRepository;
    @Inject
    LiteralTypeRepository literalTypeRepository;

    public RProcessDescriptionCreator(String publicProcessId,
                                      boolean resourceDownload,
                                      boolean importDownload,
                                      boolean scriptDownload,
                                      boolean sessionInfoLink,
                                      ResourceUrlGenerator urlGenerator) {
        this.id = publicProcessId;
        this.resourceDownloadEnabled = resourceDownload;
        this.importDownloadEnabled = importDownload;
        this.scriptDownloadEnabled = scriptDownload;
        this.sessionInfoLinkEnabled = sessionInfoLink;
        this.urlGenerator = urlGenerator;

        log.debug("NEW {}", this);
    }

    /**
     * Usually called from @GenericRProcess
     * 
     * @param annotations
     *        contain all process description information
     * @param identifier
     *        Process identifier
     * @param fileUrl
     * @return
     * @throws ExceptionReport
     * @throws RAnnotationException
     */
    public TypedProcessDescription createDescribeProcessType(List<RAnnotation> annotations, String identifier) throws OwsExceptionReport, RAnnotationException {
        log.debug("Creating Process Description for " + identifier);

        try {
            
            RAnnotation processAnnotation = getAnnotationOfType(annotations, RAnnotationType.DESCRIPTION);
            
            String abstr = processAnnotation.getStringValue(RAttribute.ABSTRACT);
            String title = processAnnotation.getStringValue(RAttribute.TITLE);
            String version = processAnnotation.getStringValue(RAttribute.VERSION);
            
            if (version == null || version.isEmpty()){
                version = DEFAULT_VERSION;
            }
            
            Set<OwsKeyword> keywords = new HashSet<>();
            Set<OwsMetadata> metadata = new HashSet<>();
            OwsCode owsCodeId = new OwsCode(identifier);
            OwsLanguageString titleLanguageString = new OwsLanguageString(title);
            
            OwsLanguageString abstractLanguageString = null;

            if(abstr != null && !abstr.isEmpty()){
                abstractLanguageString = new OwsLanguageString(abstr);
            }

            Set<TypedProcessInputDescription<?>> inputs = new HashSet<>();
            Set<TypedProcessOutputDescription<?>> outputs = new HashSet<>();
            
            for (RAnnotation annotation : annotations) {
                log.trace("Adding information to process description based on annotation {}", annotation);
                
                switch (annotation.getType()) {
                    case INPUT:
                        addInput(inputs, annotation);
                        break;
                    case OUTPUT:
                        addOutput(outputs, annotation);
                        break;
                    case DESCRIPTION:
                        //ignore
                        break;
//                    case RESOURCE:
//                        if (resourceDownloadEnabled)
//                            addProcessResources(pdt, annotation);
//                        else
//                            log.trace("Resource download is disabled, not adding elements to description.");
//                        break;
//                    case IMPORT:
//                        if (importDownloadEnabled)
//                            addImportProcessResources(pdt, annotation);
//                        else
//                            log.trace("Import download is disabled, not adding elements to description.");
//                        break;
//                    case METADATA:
//                        addMetadataResources(pdt, annotation);
//                        break;
                    default:
                        log.trace("Unhandled annotation: {}", annotation);
                        break;
                }
            }
            
            TypedProcessDescription tpdesc = new TypedProcessDescriptionImpl(owsCodeId, titleLanguageString, abstractLanguageString, keywords, metadata, inputs, outputs, version, true, true);
            
//            ProcessDescription pdt = new TypedProcessDescriptionImpl(id2, title2, abstr2, keywords, metadata, inputs, outputs, version, true, true);


//            if (scriptDownloadEnabled)
//                addScriptLink(urlGenerator.getScriptURL(identifier), pdt);
//            else
//                log.trace("Script download link disabled.");
//
//            if (sessionInfoLinkEnabled)
//                addSessionInfoLink(urlGenerator.getSessionInfoURL(), pdt);
//            else
//                log.trace("Session info download link disabled.");

//            ProcessOutputs outputs = pdt.addNewProcessOutputs();
//            DataInputs inputs = pdt.addNewDataInputs();
//
//            // Add SessionInfo-Output
//            OutputDescriptionType outdes = outputs.addNewOutput();
//            outdes.addNewIdentifier().setStringValue("sessionInfo");
//            outdes.addNewTitle().setStringValue("Information about the R session which has been used");
//            outdes.addNewAbstract().setStringValue("Output of the sessionInfo()-method after R-script execution");
//
//            SupportedComplexDataType scdt = outdes.addNewComplexOutput();
//            ComplexDataDescriptionType datatype = scdt.addNewDefault().addNewFormat();
//            datatype.setMimeType(GenericFileDataConstants.MIME_TYPE_PLAIN_TEXT);
//            datatype.setEncoding(IOHandler.DEFAULT_ENCODING);
//            datatype = scdt.addNewSupported().addNewFormat();
//            datatype.setMimeType(GenericFileDataConstants.MIME_TYPE_PLAIN_TEXT);
//            datatype.setEncoding(IOHandler.DEFAULT_ENCODING);
//
//            // Add Warnings-Output
//            outdes = outputs.addNewOutput();
//            outdes.addNewIdentifier().setStringValue("warnings");
//            outdes.addNewTitle().setStringValue("Warnings from R");
//            outdes.addNewAbstract().setStringValue("Output of the warnings()-method after R-script execution");
//
//            scdt = outdes.addNewComplexOutput();
//            datatype = scdt.addNewDefault().addNewFormat();
//            datatype.setMimeType(GenericFileDataConstants.MIME_TYPE_PLAIN_TEXT);
//            datatype.setEncoding(IOHandler.DEFAULT_ENCODING);
//            datatype = scdt.addNewSupported().addNewFormat();
//            datatype.setMimeType(GenericFileDataConstants.MIME_TYPE_PLAIN_TEXT);
//            datatype.setEncoding(IOHandler.DEFAULT_ENCODING);

            return tpdesc;
        }
        catch (RuntimeException e) {
            log.error("Error creating process description.", e);
//            throw new ExceptionReport("Error creating process description.",
//                                      "NA",
//                                      RProcessDescriptionCreator.class.getName(),
//                                      e);
            throw new NoApplicableCodeException();
        }
    }

    private RAnnotation getAnnotationOfType(List<RAnnotation> annotations, RAnnotationType type){

        // iterate over annotations, return annotation if type matches
        for (RAnnotation annotation : annotations) {
            if(annotation.getType().equals(type)){
                return annotation;
            }
        }
        
        return null;
    }
    
//    private void addMetadataResources(ProcessDescriptionType pdt, RAnnotation annotation) {
//        String title = null;
//        String href = null;
//        try {
//            title = annotation.getStringValue(RAttribute.TITLE);
//            href = annotation.getStringValue(RAttribute.HREF);
//        }
//        catch (RAnnotationException e) {
//            log.error("Problem adding process resources to process description", e);
//            return;
//        }
//
//        if (title != null && !title.isEmpty()) {
//            if (href != null && !href.isEmpty()) {
//                MetadataType mt = pdt.addNewMetadata();
//                mt.setTitle(title);
//                mt.setHref(href);
//            }
//            else
//                log.warn("Cannot add metadat resource, 'href' is null or empty");
//        }
//        else
//            log.warn("Cannot add metadat resource, 'title' is null or empty");
//    }
//
//    private void addScriptLink(URL fileUrl, ProcessDescriptionType pdt) {
//        // The "xlin:type"-argument, i.e. mt.setType(TypeType.RESOURCE); was
//        // not used for the resources
//        // because validation fails with the cause:
//        // "cvc-complex-type.3.1: Value 'resource' of attribute 'xlin:type'
//        // of element 'ows:Metadata' is not valid
//        // with respect to the corresponding attribute use. Attribute
//        // 'xlin:type' has a fixed value of 'simple'."
//        if (fileUrl != null) {
//            MetadataType mt = pdt.addNewMetadata();
//            mt.setTitle(SCRIPT_LINK_TITLE);
//            mt.setHref(fileUrl.toExternalForm());
//        }
//        else
//            log.warn("Cannot add url to script, is null");
//    }
//
//    private void addSessionInfoLink(URL sessionInfoUrl, ProcessDescriptionType pdt) {
//        if (sessionInfoUrl != null) {
//            MetadataType mt = pdt.addNewMetadata();
//            mt.setTitle(SESSION_INFO_TITLE);
//            mt.setHref(sessionInfoUrl.toExternalForm());
//        }
//        else
//            log.warn("Cannot add url to session info, is null");
//    }
//
//    private void addProcessResources(ProcessDescriptionType pdt, RAnnotation annotation) {
//        try {
//            Object obj = annotation.getObjectValue(RAttribute.NAMED_LIST);
//            if (obj instanceof Collection< ? >) {
//                Collection< ? > namedList = (Collection< ? >) obj;
//                for (Object object : namedList) {
//                    R_Resource resource = null;
//                    if (object instanceof R_Resource)
//                        resource = (R_Resource) object;
//                    else
//                        continue;
//
//                    if (resource.isPublic()) {
//                        MetadataType mt = pdt.addNewMetadata();
//                        mt.setTitle(RESOURCE_TITLE_PREFIX + resource.getResourceValue());
//
//                        // URL url = resource.getFullResourceURL(this.config.getResourceDirURL());
//                        URL url = urlGenerator.getResourceURL(resource);
//                        mt.setHref(url.toExternalForm());
//                        log.trace("Added resource URL to metadata document: {}", url);
//                    }
//                    else
//                        log.trace("Not adding resource because it is not public: {}", resource);
//                }
//            }
//        }
//        catch (RAnnotationException | ExceptionReport e) {
//            log.error("Problem adding process resources to process description", e);
//        }
//    }
//
//    private void addImportProcessResources(ProcessDescriptionType pdt, RAnnotation annotation) {
//        try {
//            Object obj = annotation.getObjectValue(RAttribute.NAMED_LIST);
//            if (obj instanceof Collection< ? >) {
//                Collection< ? > namedList = (Collection< ? >) obj;
//                for (Object object : namedList) {
//                    R_Resource resource = null;
//                    if (object instanceof R_Resource)
//                        resource = (R_Resource) object;
//                    else
//                        continue;
//
//                    if (resource.isPublic()) {
//                        MetadataType mt = pdt.addNewMetadata();
//                        mt.setTitle(IMPORT_TITLE_PREFIX + resource.getResourceValue());
//
//                        // URL url = resource.getFullResourceURL(this.config.getResourceDirURL());
//                        URL url = urlGenerator.getImportURL(resource);
//                        mt.setHref(url.toExternalForm());
//                        log.trace("Added resource URL to metadata document: {}", url);
//                    }
//                    else
//                        log.trace("Not adding resource because it is not public: {}", resource);
//                }
//            }
//        }
//        catch (RAnnotationException | ExceptionReport e) {
//            log.error("Problem adding process resources to process description", e);
//        }
//    }

    private void addInput(Set<TypedProcessInputDescription<?>> inputs, RAnnotation annotation) throws RAnnotationException {
        
        String identifier = annotation.getStringValue(RAttribute.IDENTIFIER);

        OwsCode owsCode = new OwsCode(identifier);
        
        // title is optional in the annotation, therefore it could be null, but it is required in the
        // description - then set to ID
        String title = annotation.getStringValue(RAttribute.TITLE);
        if (title == null){
            title = identifier;
        }

        OwsLanguageString titleLanguageString = new OwsLanguageString(title);
        
        OwsLanguageString abstractLanguageString = null;
        
        String abstr = annotation.getStringValue(RAttribute.ABSTRACT);

        if(abstr != null && !abstr.isEmpty()){
            abstractLanguageString = new OwsLanguageString(abstr);
        }
        
        Set<OwsKeyword> keywords = new HashSet<>();
        Set<OwsMetadata> metadata = new HashSet<>();
        
        String min = annotation.getStringValue(RAttribute.MIN_OCCURS);
        BigInteger minOccurs = BigInteger.valueOf(Long.parseLong(min));

        String max = annotation.getStringValue(RAttribute.MAX_OCCURS);
        BigInteger maxOccurs = BigInteger.valueOf(Long.parseLong(max));
        
        InputOccurence inputOccurence = new InputOccurence(minOccurs, maxOccurs);
        
        if (annotation.isComplex()) {            
            addComplexInput(owsCode, titleLanguageString, abstractLanguageString, keywords, metadata, inputOccurence, annotation, inputs);
        }
        else {
            addLiteralInput(owsCode, titleLanguageString, abstractLanguageString, keywords, metadata, inputOccurence, annotation, inputs);

        }
    }

    private void addLiteralInput(OwsCode id,OwsLanguageString title,
            OwsLanguageString abstrakt,
            Set<OwsKeyword> keywords,
            Set<OwsMetadata> metadata,
            InputOccurence occurence, RAnnotation annotation, Set<TypedProcessInputDescription<?>> inputs) throws RAnnotationException {    
        
        Set<LiteralDataDomain> supportedLiteralDataDomains = new HashSet<>();

        String dataTypeString = annotation.getProcessDescriptionType();
        
        OwsDomainMetadata dataType = new OwsDomainMetadata(dataTypeString);

        String def = annotation.getStringValue(RAttribute.DEFAULT_VALUE);
        OwsValue defaultValue = new OwsValue(def);
        
        OwsPossibleValues possibleValues = OwsAnyValue.instance();
        OwsDomainMetadata uom = new OwsDomainMetadata(" ");
        LiteralDataDomain defaultLiteralDataDomain = new LiteralDataDomainImpl(possibleValues, dataType, uom, defaultValue);
        
        TypedLiteralInputDescription literalInputDescription = new TypedLiteralInputDescriptionImpl(id, title, abstrakt, keywords, metadata, occurence, defaultLiteralDataDomain, supportedLiteralDataDomains, RDataTypeRegistry.getAbstractXSDLiteralTypeforLiteralRDataType(dataTypeString));
        
        inputs.add(literalInputDescription);
    }

    private void addComplexInput(OwsCode id,OwsLanguageString title,
            OwsLanguageString abstrakt,
            Set<OwsKeyword> keywords,
            Set<OwsMetadata> metadata,
            InputOccurence occurence, RAnnotation annotation, Set<TypedProcessInputDescription<?>> inputs) throws RAnnotationException {
                        
        String mimeType = annotation.getProcessDescriptionType();
        String encoding = annotation.getStringValue(RAttribute.ENCODING);
        String schema = annotation.getStringValue(RAttribute.SCHEMA);

        Format defaultFormat = new Format(mimeType, encoding, schema);
        
        Set<Format> supportedFormats = new HashSet<>();
        
        Class< ? extends Data<?>> iClass = annotation.getDataClass();
        if (iClass.equals(GenericFileDataBinding.class)) {
            String supportedMimeType = annotation.getProcessDescriptionType();
            String supportedEncoding = annotation.getStringValue(RAttribute.ENCODING);
            String supportedSchema = annotation.getStringValue(RAttribute.SCHEMA);

            Format supportedFormat = new Format(supportedMimeType, supportedEncoding, supportedSchema);
            
            supportedFormats.add(supportedFormat);
            
            //also add format with standard encoding
            if(encoding.equals("base64")){
                supportedFormat = new Format(supportedMimeType, "", supportedSchema);
                supportedFormats.add(supportedFormat);
            }
        }
        else {
            supportedFormats = addSupportedInputFormats(iClass);
        }
        
        TypedComplexInputDescription complexInputDescription = new TypedComplexInputDescriptionImpl(id, title, abstrakt, keywords, metadata, occurence, defaultFormat, supportedFormats, null, (Class<? extends ComplexData<?>>) iClass);
        
        inputs.add(complexInputDescription);
    }

    private void addOutput(Set<TypedProcessOutputDescription<?>> outputs, RAnnotation annotation) throws RAnnotationException {
        String identifier = annotation.getStringValue(RAttribute.IDENTIFIER);

        OwsCode owsCode = new OwsCode(identifier);
        
        // title is optional in the annotation, therefore it could be null, but it is required in the
        // description - then set to ID
        String title = annotation.getStringValue(RAttribute.TITLE);
        if (title == null){
            title = identifier;
        }

        OwsLanguageString titleLanguageString = new OwsLanguageString(title);
        
        OwsLanguageString abstractLanguageString = null;
        
        String abstr = annotation.getStringValue(RAttribute.ABSTRACT);

        if(abstr != null && !abstr.isEmpty()){
            abstractLanguageString = new OwsLanguageString(abstr);
        }
        
        Set<OwsKeyword> keywords = new HashSet<>();
        Set<OwsMetadata> metadata = new HashSet<>();

        if (annotation.isComplex()) {
            addComplexOutput(owsCode, titleLanguageString, abstractLanguageString, keywords, metadata, annotation, outputs);
        }
        else {
            addLiteralOutput(owsCode, titleLanguageString, abstractLanguageString, keywords, metadata, annotation, outputs);
        }
    }

    private void addLiteralOutput(OwsCode id,OwsLanguageString title,
            OwsLanguageString abstrakt,
            Set<OwsKeyword> keywords,
            Set<OwsMetadata> metadata, RAnnotation annotation, Set<TypedProcessOutputDescription<?>> outputs) throws RAnnotationException {
         
        Set<LiteralDataDomain> supportedLiteralDataDomains = new HashSet<>();
        
        String dataTypeString = annotation.getProcessDescriptionType();
        
        OwsDomainMetadata dataType = new OwsDomainMetadata(dataTypeString);

        OwsPossibleValues possibleValues = OwsAnyValue.instance();
        OwsDomainMetadata uom = new OwsDomainMetadata(" ");
        LiteralDataDomain defaultLiteralDataDomain = new LiteralDataDomainImpl(possibleValues, dataType, uom, null);
                
        TypedLiteralOutputDescription literalInputDescription = new TypedLiteralOutputDescriptionImpl(id, title, abstrakt, keywords, metadata, defaultLiteralDataDomain, supportedLiteralDataDomains, RDataTypeRegistry.getAbstractXSDLiteralTypeforLiteralRDataType(dataTypeString));
        
        outputs.add(literalInputDescription);        
        
    }

    private void addComplexOutput(OwsCode id,OwsLanguageString title,
            OwsLanguageString abstrakt,
            Set<OwsKeyword> keywords,
            Set<OwsMetadata> metadata, RAnnotation annotation, Set<TypedProcessOutputDescription<?>> outputs) throws RAnnotationException {
        
        String mimeType = annotation.getProcessDescriptionType();
        String encoding = annotation.getStringValue(RAttribute.ENCODING);
        String schema = annotation.getStringValue(RAttribute.SCHEMA);

        Format defaultFormat = new Format(mimeType, encoding, schema);
        
        Set<Format> supportedFormats = new HashSet<>();
        
        Class< ? extends Data<?>> iClass = annotation.getDataClass();
        if (iClass.equals(GenericFileDataBinding.class)) {
            String supportedMimeType = annotation.getProcessDescriptionType();
            String supportedEncoding = annotation.getStringValue(RAttribute.ENCODING);
            String supportedSchema = annotation.getStringValue(RAttribute.SCHEMA);

            Format supportedFormat = new Format(supportedMimeType, supportedEncoding, supportedSchema);
            
            supportedFormats.add(supportedFormat);
            
            //also add format with standard encoding
            if(encoding.equals("base64")){
                supportedFormat = new Format(supportedMimeType, "", supportedSchema);
                supportedFormats.add(supportedFormat);
            }
        }
        else {
            supportedFormats = addSupportedOutputFormats(iClass);
        }
        
        TypedComplexOutputDescription complexInputDescription = new TypedComplexOutputDescriptionImpl(id, title, abstrakt, keywords, metadata, defaultFormat, supportedFormats, null, (Class<? extends ComplexData<?>>) iClass);
        
        outputs.add(complexInputDescription);

    }

    /**
     * Searches all available datahandlers for supported formats
     */
    private Set<Format> addSupportedOutputFormats(Class< ? extends Data<?>> supportedClass) {
        List<OutputHandler> foundGenerators = new ArrayList<>();
        
        Set<Format> result = new HashSet<>();
        
        for (OutputHandler generator : generatorRepository.getOutputHandlers()) {
            Set<Class<? extends Data<?>>> supportedClasses = generator.getSupportedBindings();
            for (Class< ? > clazz : supportedClasses) {
                if (clazz.equals(supportedClass)) {
                    foundGenerators.add(generator);
                }
            }
        }

        // add properties for each parser which is found
        for (int i = 0; i < foundGenerators.size(); i++) {
            OutputHandler parser = foundGenerators.get(i);
            Set<Format> fullFormats = parser.getSupportedFormats();
            result.addAll(fullFormats);
        }

        return result;

    }

    /**
     * Searches all available datahandlers for supported formats
     */
    private Set<Format> addSupportedInputFormats(Class< ? extends Data<?>> supportedClass) {
        // retrieve a list of parsers which support the supportedClass-input  
        List<InputHandler> foundParsers = new ArrayList<>();
        
        Set<Format> result = new HashSet<>();
        
        for (InputHandler parser : parserRepository.getInputHandlers()) {
            Set<Class<? extends Data<?>>> supportedClasses = parser.getSupportedBindings();
            for (Class< ? > clazz : supportedClasses) {
                if (clazz.equals(supportedClass)) {
                    foundParsers.add(parser);
                }
            }
        }

        // add properties for each parser which is found
        for (int i = 0; i < foundParsers.size(); i++) {
            InputHandler parser = foundParsers.get(i);
            Set<Format> fullFormats = parser.getSupportedFormats();
            result.addAll(fullFormats);
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RProcessDescriptionCreator [");
        if (id != null)
            builder.append("id=").append(id).append(", ");
        builder.append("resourceDownloadEnabled=").append(resourceDownloadEnabled).append(", importDownloadEnabled=").append(importDownloadEnabled).append("]");
        return builder.toString();
    }

}
