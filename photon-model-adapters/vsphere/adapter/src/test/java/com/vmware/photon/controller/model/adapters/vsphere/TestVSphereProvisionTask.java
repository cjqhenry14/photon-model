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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.SnapshotTaskService;
import com.vmware.photon.controller.model.tasks.SnapshotTaskService.SnapshotTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class TestVSphereProvisionTask extends BaseVSphereAdapterTest {

    public URI cdromUri = getCdromUri();

    // fields that are used across method calls, stash them as private fields
    private ResourcePoolState resourcePool;

    private AuthCredentialsServiceState auth;
    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;
    private ComputeDescription vmDescription;
    private ComputeState vm;

    private ResourcePoolState createResourcePool()
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = "resourcePool-" + UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1;
        inPool.minMemoryBytes = 1024;

        ResourcePoolState returnPool =
                TestUtils.doPost(this.host, inPool, ResourcePoolState.class,
                        UriUtils.buildUri(this.host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    @Test
    public void testValidateCredentials() throws Throwable {
        // Create a resource pool where the VM will be housed
        resourcePool = createResourcePool();

        auth = createAuth();

        computeHostDescription = createComputeDescription();
        computeHost = createComputeHost();

        vmDescription = createVmDescription();
        vm = createVmState();

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = vm.documentSelfLink;
        provisionTask.isMockRequest = isMock();
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;

        ProvisionComputeTaskService.ProvisionComputeTaskState outTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionComputeTaskService.FACTORY_LINK));

        List<URI> uris = new ArrayList<>();
        uris.add(UriUtils.buildUri(this.host, outTask.documentSelfLink));
        ProvisioningUtils.waitForTaskCompletion(this.host, uris, ProvisionComputeTaskState.class);

        vm = host.getServiceState(null, ComputeState.class,
                UriUtils.buildUri(host, vm.documentSelfLink));

        createSnapshot();

        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        QuerySpecification resourceQuerySpec = new QueryTask.QuerySpecification();
        // query all ComputeState resources for the cluster
        resourceQuerySpec.query
                .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(vm.documentSelfLink);

        deletionState.resourceQuerySpec = resourceQuerySpec;
        deletionState.isMockRequest = isMock();
        ResourceRemovalTaskState outDelete = TestUtils.doPost(this.host,
                deletionState,
                ResourceRemovalTaskState.class,
                UriUtils.buildUri(this.host,
                        ResourceRemovalTaskService.FACTORY_LINK));

        uris = new ArrayList<>();
        uris.add(UriUtils.buildUri(this.host, outDelete.documentSelfLink));
        ProvisioningUtils.waitForTaskCompletion(this.host, uris, ResourceRemovalTaskState.class);

        if (!isMock()) {
            BasicConnection connection = createConnection();

            GetMoRef get = new GetMoRef(connection);
            ManagedObjectReference moref = CustomProperties.of(vm)
                    .getMoRef(CustomProperties.MOREF);

            // try getting a property of vm: this must fail because vm is deleted
            try {
                get.entityProp(moref, "name");
                fail("VM must have been deleted");
            } catch (Exception e) {
            }
        }
    }

    private void createSnapshot() throws Throwable {
        SnapshotState snapshotState = createSnapshotState(vm);

        SnapshotTaskState sts = new SnapshotTaskState();
        sts.isMockRequest = isMock();
        sts.snapshotLink = snapshotState.documentSelfLink;
        sts.snapshotAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.SNAPSHOT_SERVICE);

        SnapshotTaskState outSts = TestUtils.doPost(this.host,
                sts,
                SnapshotTaskState.class,
                UriUtils.buildUri(this.host,
                        SnapshotTaskService.FACTORY_LINK));

        ProvisioningUtils.waitForTaskCompletion(this.host, Collections.singletonList(
                UriUtils.buildUri(this.host, outSts.documentSelfLink)),
                SnapshotTaskState.class);

        SnapshotState stateAfterTaskComplete = host.getServiceState(null, SnapshotState.class,
                UriUtils.buildUri(host, snapshotState.documentSelfLink));

        if (!isMock()) {
            assertNotNull(CustomProperties.of(stateAfterTaskComplete)
                    .getMoRef(CustomProperties.MOREF));

        }
    }

    private SnapshotState createSnapshotState(ComputeState vm) throws Throwable {
        SnapshotState state = new SnapshotState();
        state.id = "snapshot" + UUID.randomUUID();
        state.name = state.id;
        state.computeLink = vm.documentSelfLink;
        state.description = "description: " + state.name;

        return TestUtils.doPost(this.host, state,
                SnapshotState.class,
                UriUtils.buildUri(this.host, SnapshotService.FACTORY_LINK));

    }

    private ComputeState createVmState() throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = vmDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = vmDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = UriUtils.buildUri(vcUrl);

        computeState.powerState = PowerState.ON;

        computeState.parentLink = computeHost.documentSelfLink;

        computeState.diskLinks = new ArrayList<>(1);
        computeState.diskLinks.add(createDisk("boot", DiskType.HDD, getDiskUri()).documentSelfLink);

        computeState.diskLinks.add(createDisk("movies", DiskType.HDD, null).documentSelfLink);
        computeState.diskLinks.add(createDisk("A", DiskType.FLOPPY, null).documentSelfLink);
        computeState.diskLinks.add(createDisk("cd", DiskType.CDROM, cdromUri).documentSelfLink);

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, System.getProperty("vc.folder"));

        ComputeService.ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private DiskState createDisk(String alias, DiskType type, URI sourceImageReference)
            throws Throwable {
        DiskState res = new DiskState();
        res.capacityMBytes = 512;
        res.bootOrder = 1;
        res.type = type;
        res.id = res.name = "disk-" + alias;

        res.sourceImageReference = sourceImageReference;
        return TestUtils.doPost(this.host, res,
                DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }

    private ComputeDescription createVmDescription() throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = "vm-" + UUID.randomUUID().toString();
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.name = computeDesc.id;
        computeDesc.dataStoreId = dataStoreId;
        computeDesc.networkId = networkId;

        return TestUtils.doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    /**
     * Create a compute host representing a vcenter server
     */
    private ComputeService.ComputeState createComputeHost() throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = UUID.randomUUID().toString();
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = computeHostDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = UriUtils.buildUri(vcUrl);

        ComputeService.ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private AuthCredentialsServiceState createAuth() throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.privateKeyId = vcUsername;
        auth.privateKey = vcPassword;
        auth.documentSelfLink = UUID.randomUUID().toString();

        AuthCredentialsServiceState result = TestUtils
                .doPost(this.host, auth, AuthCredentialsServiceState.class,
                        UriUtils.buildUri(this.host, AuthCredentialsService.FACTORY_LINK));
        return result;
    }

    private ComputeDescription createComputeDescription() throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = UUID.randomUUID().toString();
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.supportedChildren.add(ComputeType.VM_GUEST.name());
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;

        computeDesc.zoneId = zoneId;

        return TestUtils.doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    public URI getCdromUri() {
        String cdromUri = System.getProperty("vc.cdromUri");
        if (cdromUri == null) {
            return null;
        } else {
            return URI.create(cdromUri);
        }
    }

    public URI getDiskUri() {
        String diskUri = System.getProperty("vc.diskUri");
        if (diskUri == null) {
            return null;
        } else {
            return URI.create(diskUri);
        }
    }
}