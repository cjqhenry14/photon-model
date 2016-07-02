/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.adapters.vsphere;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 */
public class ProvisionContext {
    public final URI computeReference;
    public final URI provisioningTaskReference;

    public ComputeStateWithDescription parent;
    public ComputeStateWithDescription child;

    public ManagedObjectReference templateMoRef;

    public ServiceDocument task;
    public List<DiskState> disks;
    public AuthCredentialsServiceState vSphereCredentials;
    public ResourcePoolState resourcePool;

    public VSphereIOThreadPool pool;
    public Consumer<Throwable> errorHandler;

    private final InstanceRequestType instanceRequestType;

    public ProvisionContext(ComputeInstanceRequest req) {
        this.instanceRequestType = req.requestType;
        this.computeReference = req.resourceReference;
        this.provisioningTaskReference = req.taskReference;
    }

    public ProvisionContext(ComputePowerRequest req) {
        this.instanceRequestType = null;
        this.computeReference = req.resourceReference;
        this.provisioningTaskReference = req.taskReference;
    }

    /**
     * Populates the given initial context and invoke the onSuccess handler when built. At every step,
     * if failure occurs the ProvisionContext's errorHandler is invoked to cleanup.
     * @param ctx
     * @param onSuccess
     */
    public static void populateContextThen(Service service, ProvisionContext ctx,
            Consumer<ProvisionContext> onSuccess) {
        // TODO fetch all required state in parallel using OperationJoin.
        if (ctx.child == null) {
            URI computeUri = UriUtils
                    .extendUriWithQuery(ctx.computeReference,
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());
            AdapterUtils.getServiceState(service, computeUri, op -> {
                ctx.child = op.getBody(ComputeStateWithDescription.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        String templateLink = VimUtils.<String>firstNonNull(
                CustomProperties.of(ctx.child).getString(CustomProperties.TEMPLATE_LINK),
                CustomProperties.of(ctx.child.description).getString(CustomProperties.TEMPLATE_LINK)
        );

        // if it is create request and there is template link, fetch the template
        // in all other cases ignore the presence of the template
        if (templateLink != null && ctx.templateMoRef == null && ctx.instanceRequestType == InstanceRequestType.CREATE) {
            URI computeUri = UriUtils.buildUri(service.getHost(), templateLink);

            AdapterUtils.getServiceState(service, computeUri, op -> {
                ComputeStateWithDescription body = op.getBody(ComputeStateWithDescription.class);
                ctx.templateMoRef = CustomProperties.of(body).getMoRef(CustomProperties.MOREF);
                if (ctx.templateMoRef == null) {
                    String msg = String
                            .format("The linked template %s does not contain a MoRef in its custom properties",
                                    templateLink);
                    ctx.fail(new IllegalStateException(msg));
                } else {
                    populateContextThen(service, ctx, onSuccess);
                }
            }, ctx.errorHandler);
            return;
        }

        if (ctx.resourcePool == null) {
            if (ctx.child.resourcePoolLink == null) {
                ctx.fail(new IllegalStateException(
                        "resourcePoolLink is not defined for resource "
                                + ctx.child.documentSelfLink));
                return;
            }

            URI rpUri = UriUtils.buildUri(service.getHost(), ctx.child.resourcePoolLink);
            AdapterUtils.getServiceState(service, rpUri, op -> {
                ctx.resourcePool = op.getBody(ResourcePoolState.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.parent == null) {
            if (ctx.child.parentLink == null) {
                ctx.fail(new IllegalStateException(
                        "parentLink is not defined for resource "
                                + ctx.child.documentSelfLink));
                return;
            }

            URI computeUri = UriUtils
                    .extendUriWithQuery(
                            UriUtils.buildUri(service.getHost(), ctx.child.parentLink),
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());

            AdapterUtils.getServiceState(service, computeUri, op -> {
                ctx.parent = op.getBody(ComputeStateWithDescription.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.vSphereCredentials == null) {
            URI credUri = UriUtils
                    .buildUri(service.getHost(), ctx.parent.description.authCredentialsLink);
            AdapterUtils.getServiceState(service, credUri, op -> {
                ctx.vSphereCredentials = op.getBody(AuthCredentialsServiceState.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.task == null) {
            AdapterUtils.getServiceState(service, ctx.provisioningTaskReference, op -> {
                ctx.task = op.getBody(ServiceDocument.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.disks == null) {
            // no disks attached
            if (ctx.child.diskLinks == null || ctx.child.diskLinks
                    .isEmpty()) {
                ctx.disks = Collections.emptyList();
                populateContextThen(service, ctx, onSuccess);
                return;
            }

            ctx.disks = new ArrayList<>(ctx.child.diskLinks.size());

            // collect disks in parallel
            Stream<Operation> opsGetDisk = ctx.child.diskLinks.stream()
                    .map(link -> Operation.createGet(service, link));

            OperationJoin join = OperationJoin.create(opsGetDisk)
                    .setCompletion((os, errors) -> {
                        if (errors != null && !errors.isEmpty()) {
                            // fail on first error
                            ctx.errorHandler
                                    .accept(new IllegalStateException("Cannot get disk state",
                                            errors.values().iterator().next()));
                            return;
                        }

                        os.values().forEach(op -> ctx.disks.add(op.getBody(DiskState.class)));

                        onSuccess.accept(ctx);
                    });

            join.sendWith(service);
        }
    }

    /**
     * The returned JoinedCompletionHandler fails this context by invoking the error handler if any
     * error is found in {@link JoinedCompletionHandler#handle(java.util.Map, java.util.Map) error map}.
     */
    public JoinedCompletionHandler failOnError() {
        return (ops, failures) -> {
            if (failures != null && !failures.isEmpty()) {
                this.fail(failures.values().iterator().next());
            }
        };
    }

    /**
     * Fails the provisioning by invoking the errorHandler.
     * @return tre if t is defined, false otherwise
     * @param t
     */
    public boolean fail(Throwable t) {
        if (t != null) {
            this.errorHandler.accept(t);
            return true;
        } else {
            return false;
        }
    }

    public void failWithMessage(String msg, Throwable t) {
        this.fail(new Exception(msg, t));
    }

    public URI getAdapterManagementReference() {
        if (this.child.adapterManagementReference != null) {
            return this.child.adapterManagementReference;
        } else {
            return this.parent.adapterManagementReference;
        }
    }

    /**
     * zoneID is interpreted as a resource pool. Specific zoneId is preferred, else the parents zone
     * is used.
     * @return
     */
    public String getResourcePoolId() {
        if (this.child.description.zoneId != null) {
            return this.child.description.zoneId;
        } else {
            return this.parent.description.zoneId;
        }
    }

    public void failWithMessage(String msg) {
        fail(new IllegalStateException(msg));
    }
}
