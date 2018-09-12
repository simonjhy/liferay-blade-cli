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

import com.liferay.blade.cli.BladeCLI;
import com.liferay.blade.cli.util.BladeUtil;
import com.liferay.blade.cli.util.ServerUtil;

import java.io.PrintStream;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.output.NullOutputStream;

/**
 * @author David Truong
 * @author Simon Jiang
 */
public class ServerStartCommand extends AbstractServerCommand<ServerStartArgs> {

	public ServerStartCommand() {
	}

	@Override
	public Class<ServerStartArgs> getArgsClass() {
		return ServerStartArgs.class;
	}

	@Override
	protected void commandJBossWildfly(Path dir) throws Exception {
		BladeCLI bladeCLI = getBladeCLI();
		ServerStartArgs serverStartArgs = getArgs();

		Map<String, String> enviroment = new HashMap<>();

		String executable = ServerUtil.getJBossWildflyStartExecutable();

		Path binPath = dir.resolve("bin");

		final StringBuilder startCommand = new StringBuilder("");

		if (serverStartArgs.isDebug()) {
			startCommand.append(" --debug");

			if (!BladeUtil.isEmpty(serverStartArgs.getPort())) {
				startCommand.append(" " + serverStartArgs.getPort());
			}
		}

		PrintStream out = bladeCLI.out();
		PrintStream error = bladeCLI.error();

		if (serverStartArgs.isBackground()) {
			enviroment.put("JBOSS_PIDFILE", "jboss.pid");
			enviroment.put("LAUNCH_JBOSS_IN_BACKGROUND", "1");

			if (!serverStartArgs.isCommandLine()) {
				out = new PrintStream(NullOutputStream.NULL_OUTPUT_STREAM);
				error = new PrintStream(NullOutputStream.NULL_OUTPUT_STREAM);
			}
		}

		if (serverStartArgs.isCommandLine() && serverStartArgs.isBackground()) {
			bladeCLI.error("JBoss does not support run backroud mode in command line");
		}

		Process process = BladeUtil.startProcess(
			executable + startCommand.toString(), binPath.toFile(), enviroment, out, error);

		processes.add(process);

		Runtime runtime = Runtime.getRuntime();

		runtime.addShutdownHook(
			new Thread(
				() -> {
					try {
						process.waitFor();
					}
					catch (InterruptedException ie) {
						bladeCLI.error("Could not wait for process to end before shutting down");
					}
				}));
	}

	@Override
	protected void commandTomcat(Path dir) throws Exception {
		BladeCLI bladeCLI = getBladeCLI();
		ServerStartArgs serverStartArgs = getArgs();

		Map<String, String> enviroment = new HashMap<>();

		enviroment.put("CATALINA_PID", "catalina.pid");

		String executable = ServerUtil.getTomcatExecutable();

		String startCommand = " run";

		PrintStream out = bladeCLI.out();
		PrintStream error = bladeCLI.error();

		if (serverStartArgs.isBackground()) {
			startCommand = " start";

			out = new PrintStream(NullOutputStream.NULL_OUTPUT_STREAM);
			error = new PrintStream(NullOutputStream.NULL_OUTPUT_STREAM);
		}

		if (serverStartArgs.isDebug()) {
			startCommand = " jpda " + startCommand;

			if (!BladeUtil.isEmpty(serverStartArgs.getPort())) {
				enviroment.put("JPDA_ADDRESS", "localhost:" + serverStartArgs.getPort());
			}
		}

		Path logsPath = dir.resolve("logs");

		if (!Files.exists(logsPath)) {
			Files.createDirectory(logsPath);
		}

		Path catalinaOutPath = logsPath.resolve("catalina.out");

		if (!Files.exists(catalinaOutPath)) {
			Files.createFile(catalinaOutPath);
		}

		Path binPath = dir.resolve("bin");

		final Process process = BladeUtil.startProcess(
			executable + startCommand, binPath.toFile(), enviroment, out, error);

		processes.add(process);

		Runtime runtime = Runtime.getRuntime();

		runtime.addShutdownHook(
			new Thread(
				() -> {
					try {
						process.waitFor();
					}
					catch (InterruptedException ie) {
						bladeCLI.error("Could not wait for process to end before shutting down");
					}
				}));

		if (serverStartArgs.isBackground() && serverStartArgs.isTail()) {
			Process tailProcess = BladeUtil.startProcess("tail -f catalina.out", logsPath.toFile(), enviroment);

			processes.add(tailProcess);

			tailProcess.waitFor();
		}
	}

}