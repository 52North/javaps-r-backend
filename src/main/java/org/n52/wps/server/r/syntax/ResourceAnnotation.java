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
package org.n52.wps.server.r.syntax;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.wps.server.r.data.RDataTypeRegistry;
import org.n52.wps.server.r.data.R_Resource;
import org.n52.wps.server.r.util.ResourceUrlGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Matthias Hinz, Daniel Nüst
 *
 */
public class ResourceAnnotation extends RAnnotation {

    private static final Logger log = LoggerFactory.getLogger(ResourceAnnotation.class);

    private List<R_Resource> resources = new ArrayList<>();

    private final ResourceUrlGenerator urlGenerator;

    public ResourceAnnotation(List<R_Resource> resources, RDataTypeRegistry dataTypeRegistry, ResourceUrlGenerator urlGenerator) throws IOException,
            RAnnotationException {
        super(RAnnotationType.RESOURCE, new HashMap<>(), dataTypeRegistry);
        this.resources.addAll(resources);
        this.urlGenerator = urlGenerator;
        log.trace("NEW {}", this);
    }

    @Override
    public Object getObjectValue(RAttribute attr) throws RAnnotationException {
        if (attr.equals(RAttribute.NAMED_LIST)) {
            return getResources();
        }
        else if (attr.equals(RAttribute.NAMED_LIST_R_SYNTAX)) {
            StringBuilder namedList = new StringBuilder();
            namedList.append("list(");
            boolean startloop = true;
            // have to process the resources to get full URLs to the files
            for (R_Resource resource : this.resources) {
                // String fullResourceURL = resource.getFullResourceURL(this.resourceDirUrl).toExternalForm();
                String fullResourceURL;
                try {
                    fullResourceURL = urlGenerator.getResourceURL(resource).toExternalForm();
                }
                catch (OwsExceptionReport e) {
                    log.error("Could not create full resource URL for {}", resource);
                    continue;
                }

                if (startloop){
                    startloop = false;
                }
                else{
                    namedList.append(", ");
                }

                String resourceName = resource.getResourceValue();

                if (fullResourceURL != null) {
                    namedList.append("\"").append(resourceName).append("\"");
                    namedList.append(" = ");
                    namedList.append("\"").append(fullResourceURL).append("\"");
                }
                else {
                    namedList.append("\"").append(resourceName).append("\"");
                    namedList.append(" = ");
                    namedList.append("\"").append(resourceName).append("\"");
                }
            }
            namedList.append(")");

            log.trace("Created resource list for usage in R: {}", namedList);
            return namedList.toString();
        }
        else{
            throw new RAnnotationException("Attribute '{}' not defined for this annotation: {}", attr, this);
        }
    }

    protected Collection<R_Resource> getResources() {
        if (this.resources == null){
            this.resources = new ArrayList<>();
        }

        return this.resources;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ResourceAnnotation [resources=");
        if (this.resources != null){
            builder.append(Arrays.toString(this.resources.toArray()));
        }
        else{
            builder.append("<null>");
        }
        builder.append("]");
        return builder.toString();
    }

}
