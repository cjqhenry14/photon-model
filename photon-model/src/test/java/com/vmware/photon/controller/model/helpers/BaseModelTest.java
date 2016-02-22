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

package com.vmware.photon.controller.model.helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;

import com.vmware.xenon.common.Service;

/**
 * Abstract base class that creates a DCP ServiceHost running all the model DCP
 * services for unit-tests.
 */
public abstract class BaseModelTest {

    private static final int HOST_PORT = 0;
    private static final Logger logger = Logger.getLogger(BaseModelTest.class.getName());

    protected TestHost host;
    private Path sandboxDirectory;

    protected abstract Class<? extends Service>[] getFactoryServices();

    @Before
    public void setUpClass() throws Throwable {
        if (this.host == null) {
            this.sandboxDirectory = Files.createTempDirectory(null);
            this.host = new TestHost(HOST_PORT, this.sandboxDirectory,
                    getFactoryServices());
            this.host.start();
        }
    }

    @After
    public void tearDownClass() throws Throwable {
        if (this.host != null) {
            this.host.stop();
            this.host = null;
        }
        File sandbox = new File(this.sandboxDirectory.toUri());
        if (sandbox.exists()) {
            try {
                FileUtils.forceDelete(sandbox);
            } catch (FileNotFoundException | IllegalArgumentException ex) {
                logger.log(Level.FINE, "Sandbox file was not found");
            } catch (IOException ex) {
                FileUtils.forceDeleteOnExit(sandbox);
            }
        }
    }
}
