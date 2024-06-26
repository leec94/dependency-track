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
package org.dependencytrack.tasks;

import alpine.common.logging.Logger;
import alpine.event.framework.Event;
import org.dependencytrack.event.DefectDojoUploadEventAbstract;
import org.dependencytrack.integrations.defectdojo.DefectDojoUploader;

public class DefectDojoUploadTask extends VulnerabilityManagementUploadTask {

    private static final Logger LOGGER = Logger.getLogger(DefectDojoUploadTask.class);

    /**
     * {@inheritDoc}
     */
    public void inform(final Event e) {
        if (e instanceof DefectDojoUploadEventAbstract) {
            final DefectDojoUploadEventAbstract event = (DefectDojoUploadEventAbstract) e;
            LOGGER.debug("Starting DefectDojo upload task");
            super.inform(event, new DefectDojoUploader());
            LOGGER.debug("DefectDojo upload complete");
        }
    }
}
