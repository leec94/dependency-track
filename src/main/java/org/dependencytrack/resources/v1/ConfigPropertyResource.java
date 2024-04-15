/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */
package org.dependencytrack.resources.v1;

import alpine.event.framework.EventService;
import alpine.model.ConfigProperty;
import alpine.server.auth.PermissionRequired;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.dependencytrack.auth.Permissions;
import org.dependencytrack.event.BomUploadEvent;
import org.dependencytrack.persistence.QueryManager;
import org.dependencytrack.tasks.BomUploadProcessingTask;
import org.dependencytrack.tasks.BomUploadProcessingTaskV2;

import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import alpine.common.logging.Logger;

/**
 * JAX-RS resources for processing ConfigProperties
 *
 * @author Steve Springett
 * @since 3.2.0
 */
@Path("/v1/configProperty")
@Api(value = "configProperty", authorizations = @Authorization(value = "X-Api-Key"))
public class ConfigPropertyResource extends AbstractConfigPropertyResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns a list of all ConfigProperties for the specified groupName",
            response = ConfigProperty.class,
            responseContainer = "List",
            notes = "<p>Requires permission <strong>SYSTEM_CONFIGURATION</strong></p>"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized")
    })
    @PermissionRequired(Permissions.Constants.SYSTEM_CONFIGURATION)
    public Response getConfigProperties() {
        try (QueryManager qm = new QueryManager(getAlpineRequest())) {
            final List<ConfigProperty> configProperties = qm.getConfigProperties();
            // Detaches the objects and closes the persistence manager so that if/when encrypted string
            // values are replaced by the placeholder, they are not erroneously persisted to the database.
            qm.getPersistenceManager().detachCopyAll(configProperties);
            qm.close();
            for (final ConfigProperty configProperty: configProperties) {
                // Replace the value of encrypted strings with the pre-defined placeholder
                if (ConfigProperty.PropertyType.ENCRYPTEDSTRING == configProperty.getPropertyType()) {
                    configProperty.setPropertyValue(ENCRYPTED_PLACEHOLDER);
                }
            }
            return Response.ok(configProperties).build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Updates a config property",
            response = ConfigProperty.class,
            notes = "<p>Requires permission <strong>SYSTEM_CONFIGURATION</strong></p>"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "The config property could not be found"),
    })
    @PermissionRequired(Permissions.Constants.SYSTEM_CONFIGURATION)
    public Response updateConfigProperty(ConfigProperty json) {
        final Validator validator = super.getValidator();
        failOnValidationError(
                validator.validateProperty(json, "groupName"),
                validator.validateProperty(json, "propertyName"),
                validator.validateProperty(json, "propertyValue")
        );
        try (QueryManager qm = new QueryManager()) {
            final ConfigProperty property = qm.getConfigProperty(json.getGroupName(), json.getPropertyName());
            return updatePropertyValue(qm, json, property);
        }
    }

    @POST
    @Path("aggregate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Updates an array of config properties",
            response = ConfigProperty.class,
            responseContainer = "List",
            notes = "<p>Requires permission <strong>SYSTEM_CONFIGURATION</strong></p>"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "One or more config properties could not be found"),
    })
    @PermissionRequired(Permissions.Constants.SYSTEM_CONFIGURATION)
    public Response updateConfigProperty(List<ConfigProperty> list) {
        final Validator validator = super.getValidator();
        for (ConfigProperty item: list) {
            failOnValidationError(
                    validator.validateProperty(item, "groupName"),
                    validator.validateProperty(item, "propertyName"),
                    validator.validateProperty(item, "propertyValue")
            );
        }
        List<Object> returnList = new ArrayList<>();
        try (QueryManager qm = new QueryManager()) {
            for (ConfigProperty item : list) {
                final ConfigProperty property = qm.getConfigProperty(item.getGroupName(), item.getPropertyName());
                returnList.add(updatePropertyValue(qm, item, property).getEntity());

                //EXPERIMENTAL: FUTURE RELEASES SHOULD REMOVE THIS BLOCK
                if (item.getGroupName().equals("experimental") &&
                 item.getPropertyName().equals("bom.processing.task.v2.enabled")) {
                    final EventService EVENT_SERVICE = EventService.getInstance();
                    final Logger LOGGER = Logger.getLogger(ConfigPropertyResource.class);

                    if (Boolean.parseBoolean(item.getPropertyValue())) {
                        LOGGER.info("Set V2");
                        EVENT_SERVICE.unsubscribe(BomUploadProcessingTask.class);
                        EVENT_SERVICE.subscribe(BomUploadEvent.class, BomUploadProcessingTaskV2.class);
                    } else {
                        LOGGER.info("Set V1");
                        EVENT_SERVICE.unsubscribe(BomUploadProcessingTaskV2.class);
                        EVENT_SERVICE.subscribe(BomUploadEvent.class, BomUploadProcessingTask.class);
                    }
                 }
                //EXPERIMENTAL
            }
        }
        return Response.ok(returnList).build();
    }


}
