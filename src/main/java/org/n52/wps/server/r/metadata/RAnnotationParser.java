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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import javax.inject.Inject;

import org.apache.xmlbeans.XmlOptions;
import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.shetland.ogc.wps.description.ProcessDescription;
import org.n52.wps.server.r.R_Config;
import org.n52.wps.server.r.data.RDataTypeRegistry;
import org.n52.wps.server.r.data.R_Resource;
import org.n52.wps.server.r.syntax.ImportAnnotation;
import org.n52.wps.server.r.syntax.RAnnotation;
import org.n52.wps.server.r.syntax.RAnnotationException;
import org.n52.wps.server.r.syntax.RAnnotationType;
import org.n52.wps.server.r.syntax.RAttribute;
import org.n52.wps.server.r.syntax.RSeperator;
import org.n52.wps.server.r.syntax.ResourceAnnotation;
import org.n52.wps.server.r.util.ResourceUrlGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RAnnotationParser {

    private static final String ANNOTATION_CHARACTER = "#";

    private static final String COMMENTED_ANNOTATION_CHARACTER = "##";

    private static final boolean DEFAULT_RESOURCE_VISIBILITY = true;

    private static final boolean DEFAULT_IMPORT_VISIBILITY = true;

    private static Logger LOGGER = LoggerFactory.getLogger(RAnnotationParser.class);

    @Inject
    private RDataTypeRegistry dataTypeRegistry;

    @Inject
    private R_Config config;

    @Inject
    private RProcessDescriptionCreator descriptionCreator;

    @Inject
    private ResourceUrlGenerator urlGenerator;

    public RAnnotationParser() {
        // FIXME use setting mechanism to get the base url
        LOGGER.debug("New {}", this);
    }

    public RAnnotationParser(RDataTypeRegistry dataTypeRegistry, R_Config config, ResourceUrlGenerator urlGenerator) {
        this();
        this.dataTypeRegistry = dataTypeRegistry;
        this.config = config;
        LOGGER.debug("New {} and set registry and config manually to\n\t{}\n\t{}",
                this,
                this.dataTypeRegistry,
                this.config);
    }

    /**
     *
     * @param script
     *        the script to validate
     * @param identifier
     *        the script id to use for generating a test-process description
     * @return false if the process description is invalid
     * @throws RAnnotationException
     */
    public boolean validateScript(InputStream script, String identifier) throws RAnnotationException {
        return validateScriptWithErrors(script, identifier).isEmpty();
    }

    /**
     *
     * @param script
     * @param identifier
     * @return a list of the XML validation errors or the exceptions during validation
     * @throws RAnnotationException
     */
    public Collection<Exception> validateScriptWithErrors(InputStream script, String identifier) throws RAnnotationException {
        ArrayList<Exception> validationErrors = new ArrayList<>();
        XmlOptions validationOptions = new XmlOptions();
        validationOptions.setErrorListener(validationErrors);

        // try to parse annotations:
        List<RAnnotation> annotations = null;
        try {
            annotations = parse(script, true);
        } catch (RAnnotationException e) {
            LOGGER.error("Error parsing annotations during validation", e);
            validationErrors.add(e);
        }

        if (annotations != null) {
            if (annotations.isEmpty()) {
                validationErrors.add(new RAnnotationException("No annotations found"));
            } else {
                // check for exactly one description
                hasOneDescription(identifier, validationErrors, annotations);

                validateMetadataAnnotations(validationErrors, annotations, identifier);
            }
        }

        return validationErrors;
    }

    @SuppressWarnings("unused")
    private void validateMetadataAnnotations(ArrayList<Exception> validationErrors,
            List<RAnnotation> annotations,
            String scriptId) throws RAnnotationException {
        List<RAnnotation> metadata = RAnnotation.filterAnnotations(annotations, RAnnotationType.METADATA);
        for (RAnnotation annotation : metadata) {
            if (annotation.getType().equals(RAnnotationType.METADATA)) {
                LOGGER.trace("Validating metadata annotation: {}", annotation);

                // title is set
                String title = annotation.getStringValue(RAttribute.TITLE);
                if (title == null || title.isEmpty()) {
                    StringBuilder sb = new StringBuilder()
                            .append("Annotation of type '")
                            .append(RAnnotationType.METADATA.getStartKey())
                            .append("' in the script '")
                            .append(scriptId)
                            .append("' is not valid: ")
                            .append(RAttribute.TITLE)
                            .append(" must be set in ")
                            .append(annotation);
                    validationErrors.add(new Exception(sb.toString()));
                }

                // href is set and valid URL
                String href = annotation.getStringValue(RAttribute.HREF);
                if (href == null || href.isEmpty()) {
                    StringBuilder sb = new StringBuilder()
                            .append("Annotation of type '")
                            .append(RAnnotationType.METADATA.getStartKey())
                            .append("' in the script '")
                            .append(scriptId)
                            .append("' is not valid: ")
                            .append(RAttribute.HREF)
                            .append(" must be set, but annotation is ")
                            .append(annotation);
                    validationErrors.add(new Exception(sb.toString()));
                }
                try {
                    URL url = new URL(href);
                    url.toURI();
                } catch (MalformedURLException | URISyntaxException e) {
                    StringBuilder sb = new StringBuilder()
                            .append("Annotation of type '")
                            .append(RAnnotationType.METADATA.getStartKey())
                            .append("' in the script '")
                            .append(scriptId)
                            .append("' is not valid: ")
                            .append(RAttribute.HREF)
                            .append(" must be a well-formed URL, but annotation is ")
                            .append(annotation);
                    validationErrors.add(new Exception(sb.toString()));
                }
            }
        }
    }

    //TODO check how process description could be validated
    private void hasOneDescription(String identifier,
            ArrayList<Exception> validationErrors,
            List<RAnnotation> annotations) throws RAnnotationException {
        List<RAnnotation> descriptions = RAnnotation.filterAnnotations(annotations, RAnnotationType.DESCRIPTION);
        if (descriptions.size() != 1) {
            validationErrors.add(new RAnnotationException("Exactly one description annotation required, but found "
                    + descriptions.size()));
        }

        try {
            // try to create process description from annotations
            ProcessDescription processType = descriptionCreator.createDescribeProcessType(annotations,
                    identifier);

            boolean valid = true;
            if (!valid) {
                LOGGER.warn("Invalid R algorithm '{}'. The process description created from the script is not valid. Validation errors: \n{}",
                        identifier,
                        Arrays.toString(validationErrors.toArray()));

                // save process description in output errors
                StringBuilder sb = new StringBuilder()
                        .append("Errorenous XML Process Description, so the script '")
                        .append(identifier)
                        .append("' is not valid: ")
//                        .append(processType.xmlText())
                        ;
                validationErrors.add(new Exception(sb.toString())); // TODO name exception
            }

        } catch (OwsExceptionReport | RAnnotationException e) {
            LOGGER.error("Invalid R algorithm '{}'. Script validation failed when executing process description creator.",
                    identifier,
                    e);
            validationErrors.add(e);
        }
    }

    public List<RAnnotation> parseAnnotationsfromScript(InputStream inputScript) throws RAnnotationException,
            IOException {
        LOGGER.debug("Starting to parse annotations from script " + inputScript);

        return parse(inputScript, false);
    }

    private ArrayList<RAnnotation> parse(InputStream inputScript, boolean validationOnly) throws RAnnotationException {
        try {
            BufferedReader lineReader = new BufferedReader(new InputStreamReader(inputScript));
            int lineCounter = 0;
            boolean isCurrentlyParsingAnnotation = false;
            StringBuilder annotationString = null;
            RAnnotationType annotationType = null;
            ArrayList<RAnnotation> annotations = new ArrayList<>();
            String scriptId = null;

            while (lineReader.ready()) {
                String line = lineReader.readLine();
                lineCounter++;

                if (line.trim().startsWith(ANNOTATION_CHARACTER)
                        && !line.trim().startsWith(COMMENTED_ANNOTATION_CHARACTER)) {
                    line = line.split("#", 2)[1];
                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    LOGGER.trace("Parsing annotation line '{}'", line);
                    if (!isCurrentlyParsingAnnotation) // searches for startKey - expressions in a line
                    {
                        for (RAnnotationType anot : RAnnotationType.values()) {
                            String startKey = anot.getStartKey().getKey();
                            if (line.contains(startKey)) {
                                LOGGER.trace("Parsing annotation of type {}", startKey);

                                // start parsing an annotation, which might
                                // spread several lines
                                line = line.split(RSeperator.STARTKEY_SEPARATOR.getKey(), 2)[1];
                                annotationString = new StringBuilder();
                                annotationType = anot;
                                isCurrentlyParsingAnnotation = true;

                                break;
                            }
                        }
                    }
                    try {
                        if (isCurrentlyParsingAnnotation) {
                            String endKey = RSeperator.ANNOTATION_END.getKey();
                            if (line.contains(endKey)) {
                                line = line.split(endKey, 2)[0];
                                isCurrentlyParsingAnnotation = false;
                                // last line for multiline annotation
                            }

                            annotationString.append(line);
                            if (!isCurrentlyParsingAnnotation) {
                                RAnnotation newAnnotation = null;
                                if (annotationType.equals(RAnnotationType.RESOURCE)) {
                                    newAnnotation = createResourceAnnotation(scriptId, annotationString.toString());
                                } else if (annotationType.equals(RAnnotationType.IMPORT)) {
                                    newAnnotation = createImportAnnotation(scriptId, annotationString.toString());
                                } else {
                                    HashMap<RAttribute, Object> attrHash = hashAttributes(annotationType,
                                            annotationString.toString());
                                    newAnnotation = new RAnnotation(annotationType, attrHash, dataTypeRegistry);
                                    if (scriptId == null && annotationType.equals(RAnnotationType.DESCRIPTION)) {
                                        scriptId = (String) newAnnotation.getObjectValue(RAttribute.IDENTIFIER);
                                    }
                                }
                                LOGGER.trace("Done parsing annotation {} for script {}", newAnnotation, scriptId);
                                annotations.add(newAnnotation);
                            }
                        }
                    } catch (RAnnotationException e) {
                        LOGGER.error("Invalid R script with wrong annotation in Line {}: {}",
                                lineCounter,
                                e.getMessage());
                        throw e;
                    }
                }
            }

            LOGGER.debug("Finished parsing {} annotations from script (set debug to TRACE to see details):\n\t\t{}",
                    annotations.size(),
                    Arrays.deepToString(annotations.toArray()));
            return annotations;

        } catch (RuntimeException | IOException e) {
            LOGGER.error("Error parsing annotations.", e);
            throw new RAnnotationException("Error parsing annotations.", e);
        }
    }

    private HashMap<RAttribute, Object> hashAttributes(RAnnotationType anotType, String attributeString) throws RAnnotationException {

        HashMap<RAttribute, Object> attrHash = new HashMap<>();
        StringTokenizer attrValueTokenizer = new StringTokenizer(attributeString,
                RSeperator.ATTRIBUTE_SEPARATOR.getKey());
        boolean iterableOrder = true;
        // iterates over the attribute sequence of an Annotation
        Iterator<RAttribute> attrKeyIterator = anotType.getAttributeSequence().iterator();

        // Important for sequential order: start attribute contains no value,
        // iteration starts from the second key
        attrKeyIterator.next();

        while (attrValueTokenizer.hasMoreElements()) {
            String attrValue = attrValueTokenizer.nextToken();
            if (attrValue.trim().startsWith("\"")) {

                for (; attrValueTokenizer.hasMoreElements() && !attrValue.trim().endsWith("\"");) {
                    attrValue += RSeperator.ATTRIBUTE_SEPARATOR + attrValueTokenizer.nextToken();
                }

                attrValue = attrValue.substring(attrValue.indexOf("\"") + 1, attrValue.lastIndexOf("\""));
            }

            if (attrValue.contains(RSeperator.ATTRIBUTE_VALUE_SEPARATOR.getKey())) {
                iterableOrder = false;

                // in the following case, the annotation contains no sequential
                // order and
                // lacks an explicit attribute declaration --> Annotation cannot
                // be interpreted
                // e.g. value1, value2, attribute9 = value9, value4 --> parser
                // error for "value4"
            } else if (!iterableOrder) {
                throw new RAnnotationException("Annotation contains no valid order: " + "\""
                        + anotType.getStartKey().getKey() + " " + attributeString + "\"");
            }

            // Valid annotations:
            // 1) Annotation with a sequential attribute order:
            // wps.in: name,description,0,1;
            // 2) Annotation with a partially sequential attribute order:
            // wps.in: name,description, maxOccurs = 1;
            // 3) Annotations without sequential order:
            // wps.des: abstract = example process, title = Example1;
            if (iterableOrder) {
                attrHash.put(attrKeyIterator.next(), attrValue.trim());

            } else {
                String[] keyValue = attrValue.split(RSeperator.ATTRIBUTE_VALUE_SEPARATOR.getKey());
                RAttribute attribute = anotType.getAttribute(keyValue[0].trim());
                String value = keyValue[1].trim();
                if (value.startsWith("\"")) {

                    for (; attrValueTokenizer.hasMoreElements() && !value.trim().endsWith("\"");) {
                        value += RSeperator.ATTRIBUTE_SEPARATOR + attrValueTokenizer.nextToken();
                    }

                    value = value.substring(value.indexOf("\"") + 1, value.lastIndexOf("\""));
                }

                attrHash.put(attribute, value);
            }
        }
        return attrHash;
    }

    private RAnnotation createResourceAnnotation(String scriptId, String attributeString) throws IOException,
            RAnnotationException {
        List<R_Resource> resources = getResourcesFromAnnotation(scriptId, attributeString, DEFAULT_RESOURCE_VISIBILITY);

        ResourceAnnotation resourceAnnotation = new ResourceAnnotation(resources, dataTypeRegistry, urlGenerator);

        return resourceAnnotation;
    }

    private RAnnotation createImportAnnotation(String scriptId, String attributeString) throws IOException,
            RAnnotationException {
        List<R_Resource> resources = getResourcesFromAnnotation(scriptId, attributeString, DEFAULT_IMPORT_VISIBILITY);

        ImportAnnotation importAnnotation = new ImportAnnotation(resources, dataTypeRegistry);

        return importAnnotation;
    }

    private List<R_Resource> getResourcesFromAnnotation(String scriptId,
            String attributeString,
            boolean defaultVisibility) {
        List<R_Resource> resources = new ArrayList<>();

        StringTokenizer attrValueTokenizer = new StringTokenizer(attributeString,
                RSeperator.ATTRIBUTE_SEPARATOR.getKey());

        while (attrValueTokenizer.hasMoreElements()) {
            String resourceValue = attrValueTokenizer.nextToken().trim();
            R_Resource r_resource = new R_Resource(config.getPublicScriptId(scriptId), resourceValue, defaultVisibility);
            resources.add(r_resource);

            LOGGER.trace("Found new resource in annotation: {}", r_resource);
        }
        return resources;
    }

}
