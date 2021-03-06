/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.jobmaster.failover;

import org.apache.flink.core.fs.Path;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

/**
 * Tests for the File system operation log store.
 */
public class FileSystemOperationLogStoreTest {
	@Rule
	public final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

	@Test
	public void testReadAndWriteOpLog() throws IOException {
		final Path rootPath = new Path(TEMPORARY_FOLDER.newFolder().getAbsolutePath());
		OperationLogStore store = new FileSystemOperationLogStore(rootPath);

		// Initial the store and test read write.
		store.start();

		OperationLog opLog0 = new TestingOperationLog(0);
		OperationLog opLog1 = new TestingOperationLog(1);
		OperationLog opLog2 = new TestingOperationLog(2);
		OperationLog opLog3 = new TestingOperationLog(3);
		OperationLog opLog4 = new TestingOperationLog(4);

		store.writeOpLog(opLog0);
		store.writeOpLog(opLog1);
		store.writeOpLog(opLog2);
		store.writeOpLog(opLog3);
		store.writeOpLog(opLog4);

		Assert.assertEquals(opLog0, store.readOpLog());
		Assert.assertEquals(opLog1, store.readOpLog());
		Assert.assertEquals(opLog2, store.readOpLog());
		Assert.assertEquals(opLog3, store.readOpLog());
		Assert.assertEquals(opLog4, store.readOpLog());
		Assert.assertTrue(null == store.readOpLog());

		store.stop();

		// generate two empty file
		store.start();
		store.stop();
		store.start();
		store.stop();

		// test restart a fs store can retrieve former logs
		store.start();

		Assert.assertEquals(opLog0, store.readOpLog());
		Assert.assertEquals(opLog1, store.readOpLog());
		Assert.assertEquals(opLog2, store.readOpLog());
		Assert.assertEquals(opLog3, store.readOpLog());
		Assert.assertEquals(opLog4, store.readOpLog());
		Assert.assertTrue(null == store.readOpLog());

		store.writeOpLog(opLog0);
		store.writeOpLog(opLog1);
		store.stop();

		// test restart a fs store can retrieve former logs
		store.start();

		Assert.assertEquals(opLog0, store.readOpLog());
		Assert.assertEquals(opLog1, store.readOpLog());
		Assert.assertEquals(opLog2, store.readOpLog());
		Assert.assertEquals(opLog3, store.readOpLog());
		Assert.assertEquals(opLog4, store.readOpLog());
		Assert.assertEquals(opLog0, store.readOpLog());
		Assert.assertEquals(opLog1, store.readOpLog());
		Assert.assertTrue(null == store.readOpLog());

		// test clear the store
		store.start();
		store.clear();

		store.start();

		Assert.assertTrue(null == store.readOpLog());

		store.stop();
	}
}
