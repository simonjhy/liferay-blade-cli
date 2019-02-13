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

package com.liferay.blade.cli.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;

import java.util.Objects;
import java.util.Properties;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

/**
 * @author Christopher Bryan Boyd
 */
public class MavenUtil {

	public static void executeGoals(String projectPath, String[] goals) {
		Objects.requireNonNull(goals, "Goals must be specified");

		if (!(goals.length > 0)) {
			throw new RuntimeException("Goals must be specified");
		}

		String os = System.getProperty("os.name");

		boolean windows = false;

		os = os.toLowerCase();

		if (os.startsWith("win")) {
			windows = true;
		}

		boolean buildSuccess = false;

		int exitValue = 1;

		StringBuilder stringBuilder = new StringBuilder();

		for (String goal : goals) {
			stringBuilder.append(goal + " ");
		}

		StringBuilder output = new StringBuilder();

		String command = null;

		try {
			Runtime runtime = Runtime.getRuntime();

			command = (windows ? "cmd.exe /c .\\mvnw.cmd" : "./mvnw") + " " + stringBuilder.toString();

			Process process = runtime.exec(command, null, new File(projectPath));

			BufferedReader processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
			BufferedReader processError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

			String line = null;

			while ((line = processOutput.readLine()) != null) {
				output.append(line);
				output.append(System.lineSeparator());

				if (line.contains("BUILD SUCCESS")) {
					buildSuccess = true;
				}
			}

			while ((line = processError.readLine()) != null) {
				output.append(line);
				output.append(System.lineSeparator());
			}

			exitValue = process.waitFor();
		}
		catch (Exception e) {
			StringBuilder sb = new StringBuilder();

			sb.append("Project path: " + projectPath + "\n");
			sb.append("maven command failed: " + command);
			sb.append(e.getMessage());

			throw new RuntimeException(sb.toString(), e);
		}

		boolean exitValueCorrect = false;

		if (exitValue == 0) {
			exitValueCorrect = true;
		}

		if (!exitValueCorrect) {
			throw new RuntimeException(
				"Maven goals " + goals[0] + " failed for project " + projectPath + System.lineSeparator() + output);
		}

		if (!buildSuccess) {
			throw new RuntimeException("Maven goals " + goals + " failed in project path " + projectPath);
		}
	}

	public static Model getMavenModel(File baseDir) {
		try {
			File pomXmlFile = getPomXMLFile(baseDir);
			MavenXpp3Reader reader = new MavenXpp3Reader();

			return reader.read(new FileReader(pomXmlFile));
		}
		catch (Throwable th) {
			throw new RuntimeException("Unable to get maven properties", th);
		}
	}

	public static Properties getMavenProperties(File baseDir) {
		try {
			File pomXmlFile = getPomXMLFile(baseDir);
			MavenXpp3Reader reader = new MavenXpp3Reader();

			Model model = reader.read(new FileReader(pomXmlFile));

			return model.getProperties();
		}
		catch (Throwable th) {
			throw new RuntimeException("Unable to get maven properties", th);
		}
	}

	public static File getPomXMLFile(File dir) {
		return new File(getWorkspaceDir(dir), _POM_XML_FILE_NAME);
	}

	public static File getWorkspaceDir(File dir) {
		File mavenParent = _findWorkspacePomFile(dir);

		if (_isWorkspacePomFile(new File(mavenParent, "pom.xml"))) {
			return mavenParent;
		}

		File mavenPom = new File(dir, "pom.xml");

		if (mavenPom.exists() && _isWorkspacePomFile(mavenPom)) {
			return dir;
		}

		return null;
	}

	public static boolean isWorkspace(File dir) {
		File workspaceDir = getWorkspaceDir(dir);

		if (Objects.isNull(dir) || Objects.isNull(workspaceDir)) {
			return false;
		}

		File pomFile = new File(workspaceDir, "pom.xml");

		if (_isWorkspacePomFile(pomFile)) {
			return true;
		}

		return false;
	}

	private static File _findWorkspacePomFile(File dir) {
		if (dir == null) {
			return null;
		}
		else if (".".equals(dir.toString()) || !dir.isAbsolute()) {
			try {
				dir = dir.getCanonicalFile();
			}
			catch (Exception e) {
				dir = dir.getAbsoluteFile();
			}
		}

		File file = new File(dir, "pom.xml");

		if (file.exists() && _isWorkspacePomFile(file)) {
			return dir;
		}

		return _findWorkspacePomFile(dir.getParentFile());
	}

	private static boolean _isWorkspacePomFile(File pomFile) {
		boolean pom = false;

		if ((pomFile != null) && "pom.xml".equals(pomFile.getName()) && pomFile.exists()) {
			pom = true;
		}

		if (pom) {
			try {
				String content = BladeUtil.read(pomFile);

				if (content.contains("portal.tools.bundle.support")) {
					return true;
				}
			}
			catch (Exception e) {
			}
		}

		return false;
	}

	private static final String _POM_XML_FILE_NAME = "pom.xml";

}