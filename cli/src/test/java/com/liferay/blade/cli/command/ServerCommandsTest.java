/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liferay.blade.cli.command;

import com.jezhumble.javasysmon.JavaSysMon;
import com.jezhumble.javasysmon.ProcessInfo;

import com.liferay.blade.cli.BladeTest;
import com.liferay.blade.cli.StringPrintStream;
import com.liferay.blade.cli.TestUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.PidUtil;
import org.zeroturnaround.process.Processes;

/**
 * @author Christopher Bryan Boyd
 * @author Simon Jiang
 */
public class ServerCommandsTest {

	@Before
	public void setUp() throws Exception {
		_workspaceDir = temporaryFolder.newFolder("build", "test", "workspace");
	}

	@Test
	public void testServerInit() throws Exception {
		String[] args = {"--base", _workspaceDir.getPath(), "init"};

		new BladeTest().run(args);

		args = new String[] {"--base", _workspaceDir.getPath(), "server", "init"};

		File bundlesDirectory = new File(_workspaceDir.getPath(), "bundles");

		Assert.assertFalse(bundlesDirectory.exists());

		TestUtil.runBlade(args);

		Assert.assertTrue(bundlesDirectory.exists());
	}

	@Test
	public void testServerStartCommandExists() throws Exception {
		Assert.assertTrue(_commandExists("server", "start"));
		Assert.assertTrue(_commandExists("server start"));
		Assert.assertFalse(_commandExists("server", "startx"));
		Assert.assertFalse(_commandExists("server startx"));
		Assert.assertFalse(_commandExists("serverx", "start"));
		Assert.assertFalse(_commandExists("serverx start"));
	}

	@Test
	public void testServerStopCommandExists() throws Exception {
		Assert.assertTrue(_commandExists("server", "stop"));
		Assert.assertTrue(_commandExists("server stop"));
		Assert.assertFalse(_commandExists("server", "stopx"));
		Assert.assertFalse(_commandExists("server stopx"));
		Assert.assertFalse(_commandExists("serverx", "stopx"));
		Assert.assertFalse(_commandExists("serverx stop"));
	}

	@Test
	public void testTomcatServerCommandsStart() throws Exception {
		_initTomcatServer();

		String[] startArgs = {"--base", _workspaceDir.getPath(), "server", "start"};

		ServerStartCommand serverStartCommand = (ServerStartCommand)_runServerCommand(startArgs);

		_checkSeverProcess(serverStartCommand, "tomcat", true);
	}

	@Test
	public void testTomcatServerCommandsStartAndStop() throws Exception {
		_initTomcatServer();

		String[] startArgs = {"--base", _workspaceDir.getPath(), "server", "start"};

		ServerStartCommand serverStartCommand = (ServerStartCommand)_runServerCommand(startArgs);

		_checkSeverProcess(serverStartCommand, "tomcat", false);

		String[] stopArgs = {"--base", _workspaceDir.getPath(), "server", "stop"};

		ServerStopCommand serverStopCommand = (ServerStopCommand)_runServerCommand(stopArgs);

		Collection<Process> processes = serverStopCommand.getProcesses();

		Assert.assertTrue("Expected server stop process is empty.", processes.isEmpty());
	}

	@Test
	public void testWildflyServerCommandsStart() throws Exception {
		_initWildflyServer();

		String[] args = {"--base", _workspaceDir.getPath(), "server", "start"};

		ServerStartCommand serverStartCommand = (ServerStartCommand)_runServerCommand(args);

		_checkSeverProcess(serverStartCommand, "wildfly", true);
	}

	@Test
	public void testWildflyServerCommandsStartAndStop() throws Exception {
		_initWildflyServer();

		String[] startArgs = {"--base", _workspaceDir.getPath(), "server", "start"};

		ServerStartCommand serverStartCommand = (ServerStartCommand)_runServerCommand(startArgs);

		_checkSeverProcess(serverStartCommand, "wildfly", true);
	}

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	private static boolean _commandExists(String... args) {
		try {
			TestUtil.runBlade(args);
		}
		catch (Throwable throwable) {
			String message = throwable.getMessage();

			if (Objects.nonNull(message) && !message.contains("No such command")) {
				return true;
			}

			return false;
		}

		return false;
	}

	private void _checkSeverProcess(AbstractServerCommand<?> serverCommand, String keyword, boolean stopServer)
		throws Exception {

		Collection<Process> processes = serverCommand.getProcesses();

		Assert.assertFalse("Expected server start process to have started.", processes.isEmpty());

		Iterator<Process> iterator = processes.iterator();

		Process process = iterator.next();

		Assert.assertTrue("Expected server start process to be alive.", process.isAlive());

		if (stopServer) {
			int pid = PidUtil.getPid(process);

			PidProcess pidProcess = Processes.newPidProcess(pid);

			pidProcess.destroyForcefully();

			pidProcess.waitFor(5, TimeUnit.SECONDS);

			Assert.assertFalse("Expected server start process to be destroyed.", pidProcess.isAlive());

			JavaSysMon monitor = new JavaSysMon();

			ProcessInfo[] processTable = monitor.processTable();

			List<ProcessInfo> processInfoCollection = Arrays.asList(processTable);

			Collections.reverse(processInfoCollection);

			for (ProcessInfo pi : processInfoCollection) {
				if (pi.getParentPid() == pid) {
					String command = pi.getCommand();

					Path workspacePath = Paths.get("build", "test", "workspace");

					if (command.contains(workspacePath.toString()) && command.contains("java") &&
						command.contains(keyword)) {

						PidProcess serverPidProcess = Processes.newPidProcess(pi.getPid());

						serverPidProcess.destroyForcefully();

						Assert.assertFalse(
							"Expected server start subprocess to be destroyed.", serverPidProcess.isAlive());

						break;
					}
				}
				else {
					break;
				}
			}
		}
	}

	private void _initTomcatServer() throws Exception {
		String[] initArgs = {"--base", _workspaceDir.getPath(), "init", "-v", "7.1"};

		new BladeTest().run(initArgs);

		String[] gwArgs = {"--base", _workspaceDir.getPath(), "gw", "initBundle"};

		new BladeTest().run(gwArgs);

		File bundlesDirectory = new File(_workspaceDir.getPath(), "bundles");

		Assert.assertTrue(bundlesDirectory.exists());
	}

	private void _initWildflyServer() throws Exception {
		String[] initArgs = {"--base", _workspaceDir.getPath(), "init", "-v", "7.1"};

		new BladeTest().run(initArgs);

		Properties gradleProperties = new Properties();

		gradleProperties.load(new FileInputStream(new File(_workspaceDir, "gradle.properties")));

		gradleProperties.put(
			"liferay.workspace.bundle.url",
			"https://releases-cdn.liferay.com/portal/7.1.0-ga1/liferay-ce-portal-wildfly-7.1.0-ga1-" +
				"20180703012531655.zip");

		gradleProperties.store(new FileOutputStream(new File(_workspaceDir, "gradle.properties")), "");

		String[] gwArgs = {"--base", _workspaceDir.getPath(), "gw", "initBundle"};

		new BladeTest().run(gwArgs);

		File bundlesDirectory = new File(_workspaceDir.getPath(), "bundles");

		Assert.assertTrue(bundlesDirectory.exists());
	}

	private BaseCommand<?> _runServerCommand(String[] args) throws Exception {
		StringPrintStream outputPrintStream = StringPrintStream.newInstance();

		StringPrintStream errorPrintStream = StringPrintStream.newInstance();

		BladeTest bladeTest = new BladeTest(outputPrintStream, errorPrintStream);

		final List<Exception> exceptions = new ArrayList<>();

		Thread thread = new Thread(
			() -> {
				try {
					bladeTest.run(args);
				}
				catch (Exception e) {
					exceptions.add(e);
				}
			});

		thread.setDaemon(true);

		thread.run();

		Thread.sleep(5);

		if (!exceptions.isEmpty()) {
			Assert.fail("Unexpected exception: " + exceptions.get(0));
		}

		return bladeTest.getCommand();
	}

	private File _workspaceDir = null;

}