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

import static com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT;

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.UpdateSet;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Handles enumeration for vsphere endpoints. It supports up to {@link #MAX_CONCURRENT_ENUM_PROCESSES}
 * concurrent long-running enumeration processes. Attempts to start more processes than that will result
 * in error.
 */
public class VSphereAdapterResourceEnumerationService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.ENUMERATION_SERVICE;

    private static final int MAX_CONCURRENT_ENUM_PROCESSES = 10;

    /**
     * Stores currently running enumeration processes.
     */
    private final ConcurrentMap<String, ComputeEnumerateResourceRequest> startedEnumProcessesByHost = new ConcurrentHashMap<>();

    /**
     * Bounded theadpool executing the currently running enumeration processes.
     */
    private final ExecutorService enumerationThreadPool;

    public VSphereAdapterResourceEnumerationService() {
        this.enumerationThreadPool = new ThreadPoolExecutor(MAX_CONCURRENT_ENUM_PROCESSES,
                MAX_CONCURRENT_ENUM_PROCESSES,
                0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new AbortPolicy());
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ComputeEnumerateResourceRequest request = op.getBody(ComputeEnumerateResourceRequest.class);

        validate(request);

        TaskManager mgr = new TaskManager(this, request.taskReference);
        mgr.patchTask(TaskStage.STARTED);

        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        if (request.isMockRequest) {
            mgr.patchTask(TaskStage.FINISHED);
            return;
        }

        URI parentUri = ComputeStateWithDescription.buildUri(request.resourceReference);

        Operation.createGet(parentUri)
                .setCompletion(o -> {
                    thenWithParentState(request, o.getBody(ComputeStateWithDescription.class), mgr);
                }, mgr)
                .sendWith(this);
    }

    private void thenWithParentState(ComputeEnumerateResourceRequest request,
            ComputeStateWithDescription parent, TaskManager mgr) {

        if (request.enumerationAction == EnumerationAction.STOP) {
            endEnumerationProcess(parent, mgr);
            return;
        }

        VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(this);

        pool.submit(this, parent.adapterManagementReference,
                parent.description.authCredentialsLink,
                (connection, e) -> {
                    if (e != null) {
                        String msg = String.format("Cannot establish connection to %s",
                                parent.adapterManagementReference);
                        logInfo(msg);
                        mgr.patchTaskToFailure(msg, e);
                    } else {
                        if (request.enumerationAction == EnumerationAction.REFRESH) {
                            refreshResourcesOnce(request, connection, parent, mgr);
                        } else if (request.enumerationAction == EnumerationAction.START) {
                            startEnumerationProcess(
                                    connection.createUnmanagedCopy(),
                                    parent,
                                    request,
                                    mgr);
                        }
                    }
                });
    }

    private void endEnumerationProcess(ComputeStateWithDescription parent, TaskManager mgr) {
        // just remove from map, enumeration process checks if it should continue at every step
        ComputeEnumerateResourceRequest old = this.startedEnumProcessesByHost
                .remove(parent.documentSelfLink);

        if (old == null) {
            logInfo("No running enumeration process for %s was found", parent.documentSelfLink);
        }

        mgr.patchTask(TaskStage.FINISHED);
    }

    private void startEnumerationProcess(
            Connection connection,
            ComputeStateWithDescription parent,
            ComputeEnumerateResourceRequest request, TaskManager mgr) {

        ComputeEnumerateResourceRequest old = this.startedEnumProcessesByHost
                .putIfAbsent(parent.documentSelfLink, request);

        if (old != null) {
            logInfo("Enumeration process for %s already started, not starting a new one",
                    parent.documentSelfLink);
            return;
        }

        EnumerationClient client;
        try {
            client = new EnumerationClient(connection, parent);
        } catch (Exception e) {
            String msg = String
                    .format("Error connecting to %s while starting enumeration process for %s",
                            parent.adapterManagementReference,
                            parent.documentSelfLink);
            logInfo(msg);
            mgr.patchTaskToFailure(msg, e);
            return;
        }

        try {
            this.enumerationThreadPool.execute(() -> {
                try {
                    startEnumerationProcess(parent, client);
                } catch (Exception e) {
                    String msg = String.format("Error during enumeration process %s, aborting",
                            parent.documentSelfLink);
                    log(Level.FINE, msg);
                    mgr.patchTaskToFailure(msg, e);
                }
            });
        } catch (RejectedExecutionException e) {
            String msg = String
                    .format("Max number of resource enumeration processes reached: will not start one for %s",
                            parent.documentSelfLink);
            logInfo(msg);
            mgr.patchTaskToFailure(msg, e);
        }
    }

    /**
     * This method executes in a thread managed by {@link #enumerationThreadPool}.
     *
     * @param client
     * @throws Exception
     */
    private void startEnumerationProcess(ComputeStateWithDescription parent,
            EnumerationClient client)
            throws Exception {
        PropertyFilterSpec spec = client.createFullFilterSpec();

        try {
            for (UpdateSet updateSet : client.pollForUpdates(spec)) {
                processUpdates(updateSet);
                if (!this.startedEnumProcessesByHost.containsKey(parent.documentSelfLink)) {
                    break;
                }
            }
        } catch (Exception e) {
            // destroy connection and let global error handler process it further
            client.close();
            throw e;
        }
    }

    /**
     * This method executes in a thread managed by {@link VSphereIOThreadPoolAllocator}
     *
     * @param request
     * @param connection
     * @param parent
     * @param mgr
     */
    private void refreshResourcesOnce(
            ComputeEnumerateResourceRequest request,
            Connection connection,
            ComputeStateWithDescription parent,
            TaskManager mgr) {

        EnumerationClient client;
        try {
            client = new EnumerationClient(connection, parent);
        } catch (Exception e) {
            mgr.patchTaskToFailure(e);
            return;
        }

        PropertyFilterSpec spec = client.createFullFilterSpec();

        try {
            for (List<ObjectContent> page : client.retrieveObjects(spec)) {
                processFoundObjects(request, page);
            }
        } catch (Exception e) {
            String msg = "Error processing PropertyCollector results";
            logWarning(msg);
            mgr.patchTaskToFailure(msg, e);
        }

        mgr.patchTask(TaskStage.FINISHED);
    }

    private void processFoundObjects(ComputeEnumerateResourceRequest request,
            List<ObjectContent> objects) {
        for (ObjectContent cont : objects) {
            System.out.println(VimUtils.convertMoRefToString(cont.getObj()));
            if (VimUtils.isVirtualMachine(cont.getObj())) {
                VmOverlay vm = new VmOverlay(cont);
                processFoundVm(request, vm);
            } else if (VimUtils.isResourcePool(cont.getObj())) {
                ResourcePoolOverlay rp = new ResourcePoolOverlay(cont);
                processFoundResourcePool(request, rp);
            } else if (VimUtils.isHost(cont.getObj())) {
                HostSystemOverlay hs = new HostSystemOverlay(cont);
                processFoundHostSystem(request, hs);
            }
        }
    }

    private void processFoundHostSystem(ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs) {
        QueryTask task = createHostSystemQueryTask(request.resourceLink(),
                hs.getHardwareUuid());
        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewHostSystem(request, hs);
            } else {
                ComputeState oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(), ComputeState.class);
                updateHostSystem(oldDocument, request, hs);
            }
        });
    }

    private void updateHostSystem(ComputeState oldDocument, ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs) {
        ComputeState state = createComputeFromResults(request, hs);
        state.documentSelfLink = oldDocument.documentSelfLink;

        logFine("Syncing HostSystem %s", oldDocument.documentSelfLink);
        Operation.createPatch(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .sendWith(this);

        ComputeDescription desc = createDescriptionForHost(request, hs);
        desc.documentSelfLink = oldDocument.descriptionLink;
        Operation.createPatch(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);
    }

    private void createNewHostSystem(ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs) {

        ComputeDescription desc = createDescriptionForHost(request, hs);
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = createComputeFromResults(request, hs);
        state.descriptionLink = desc.documentSelfLink;

        logFine("Found new HostSystem %s", hs.getName());
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .sendWith(this);
    }

    private ComputeDescription createDescriptionForHost(ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs) {
        ComputeDescription res = new ComputeDescription();
        res.documentSelfLink = UriUtils
                .buildUriPath(ComputeService.FACTORY_LINK, UUID.randomUUID().toString());
        res.cpuCount = hs.getCoreCount();
        res.cpuMhzPerCore = hs.getCpuMhz();
        res.totalMemoryBytes = hs.getTotalMemoryBytes();
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        return res;
    }

    private ComputeState createComputeFromResults(ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs) {
        ComputeState state = new ComputeState();
        state.id = hs.getHardwareUuid();
        state.adapterManagementReference = request.adapterManagementReference;
        state.parentLink = request.resourceLink();
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, hs.getId())
                .put(ComputeProperties.CUSTOM_DISPLAY_NAME, hs.getName());
        return state;
    }

    private void processFoundResourcePool(ComputeEnumerateResourceRequest request,
            ResourcePoolOverlay rp) {
        QueryTask task = createResourcePoolQueryTask(request.adapterManagementReference.toString(),
                rp.getId().getValue());
        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewResourcePool(request, rp);
            } else {
                updateResourcePool(result.documentLinks.get(0), request, rp);
            }
        });
    }

    private void updateResourcePool(String selfLink, ComputeEnumerateResourceRequest request,
            ResourcePoolOverlay rp) {
        ResourcePoolState state = createResourcePoolFromResults(request, rp);
        state.documentSelfLink = selfLink;

        Operation.createPatch(UriUtils.buildUri(getHost(), selfLink))
                .setBody(state)
                .sendWith(this);
    }

    private void createNewResourcePool(ComputeEnumerateResourceRequest request,
            ResourcePoolOverlay rp) {
        ResourcePoolState state = createResourcePoolFromResults(request, rp);

        logFine("Found new ResourcePool %s", rp.getName());
        Operation.createPost(this, ResourcePoolService.FACTORY_LINK)
                .setBody(state)
                .sendWith(this);
    }

    private ResourcePoolState createResourcePoolFromResults(ComputeEnumerateResourceRequest request,
            ResourcePoolOverlay rp) {
        ResourcePoolState state = new ResourcePoolState();
        state.id = rp.getId().getValue();
        state.name = rp.getName();
        state.maxMemoryBytes = rp.getMemoryLimitBytes();
        state.minMemoryBytes = rp.getMemoryReservationBytes();

        CustomProperties.of(state)
                .put(CustomProperties.ADAPTER_REFERENCE,
                        request.adapterManagementReference.toString());

        return state;
    }

    private QueryTask createResourcePoolQueryTask(String adapterReference,
            String moId) {
        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create().addFieldClause(ComputeState.FIELD_NAME_ID, moId)
                        .build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                ResourcePoolState.FIELD_NAME_CUSTOM_PROPERTIES,
                                CustomProperties.ADAPTER_REFERENCE),
                        adapterReference).build());

        return QueryTask
                .create(qs)
                .setDirect(true);
    }

    private void processFoundVm(ComputeEnumerateResourceRequest request, VmOverlay vm) {
        QueryTask task = createVmQueryTask(request.resourceLink(), vm.getInstanceUuid());
        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewCompute(request, vm);
            } else {
                updateCompute(result.documentLinks.get(0), request, vm);
            }
        });
    }

    private void updateCompute(String computeLink, ComputeEnumerateResourceRequest request,
            VmOverlay vm) {
        ComputeState state = createComputeFromResults(request, vm);
        state.documentSelfLink = computeLink;

        logFine("Syncing ComputeState %s", computeLink);

        Operation.createPatch(UriUtils.buildUri(getHost(), computeLink))
                .setBody(state)
                .sendWith(this);
    }

    private void createNewCompute(ComputeEnumerateResourceRequest request, VmOverlay vm) {
        ComputeState state = createComputeFromResults(request, vm);

        logFine("Found new ComputeState %s", vm.getInstanceUuid());
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .sendWith(this);
    }

    /**
     * Make a ComputeState from the request and a vm found in vsphere.
     *
     * @param request
     * @param vm
     * @return
     */
    private ComputeState createComputeFromResults(ComputeEnumerateResourceRequest request,
            VmOverlay vm) {
        ComputeState state = new ComputeState();
        state.adapterManagementReference = request.adapterManagementReference;
        state.parentLink = request.resourceLink();
        state.descriptionLink = request.computeDescriptionLink;
        state.resourcePoolLink = request.resourcePoolLink;

        state.powerState = vm.getPowerState();
        state.primaryMAC = vm.getPrimaryMac();
        state.id = vm.getInstanceUuid();

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, vm.getId())
                .put(CustomProperties.TEMPLATE, vm.isTempalte())
                .put(ComputeProperties.CUSTOM_DISPLAY_NAME, vm.getName());
        return state;
    }

    /**
     * Executes a direct query and invokes the provided handler with the results.
     *
     * @param task
     * @param handler
     */
    private void withTaskResults(QueryTask task, Consumer<ServiceDocumentQueryResult> handler) {
        Operation.createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(task)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error processing task %s", task.documentSelfLink);
                        return;
                    }

                    QueryTask body = o.getBody(QueryTask.class);
                    handler.accept(body.results);
                })
                .sendWith(this);
    }

    /**
     * Builds a query for finding a ComputeState by instanceUuid from vsphere and parent compute link.
     *
     * @param parentComputeLink
     * @param instanceUuid
     * @return
     */
    private QueryTask createVmQueryTask(String parentComputeLink, String instanceUuid) {
        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create().addFieldClause(ComputeState.FIELD_NAME_ID, instanceUuid)
                        .build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink).build());

        return QueryTask
                .create(qs)
                .setDirect(true);
    }

    /**
     * Builds a query for finding a HostSystems by their hardwareUuid.
     *
     * @param parentComputeLink
     * @param hardwareUuid
     * @return
     */
    private QueryTask createHostSystemQueryTask(String parentComputeLink, String hardwareUuid) {
        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create().addFieldClause(ComputeState.FIELD_NAME_ID, hardwareUuid)
                        .build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink).build());

        // fetch the whole document to extract the description link

        qs.options = EnumSet.of(EXPAND_CONTENT);
        return QueryTask
                .create(qs)
                .setDirect(true);
    }

    private void processUpdates(UpdateSet updateSet) {
        // handle PC updates
        // https://jira-hzn.eng.vmware.com/browse/VCOM-17
    }

    private void validate(ComputeEnumerateResourceRequest request) {
        // assume all request are REFRESH requests
        if (request.enumerationAction == null) {
            request.enumerationAction = EnumerationAction.REFRESH;
        }
    }
}
