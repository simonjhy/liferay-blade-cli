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

import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Domain;
import aQute.bnd.osgi.Jar;

import aQute.lib.io.IO;

import com.liferay.blade.cli.BladeTest;
import com.liferay.blade.cli.BladeTestResults;
import com.liferay.blade.cli.GradleRunnerUtil;
import com.liferay.blade.cli.TestUtil;
import com.liferay.blade.cli.util.FileUtil;
import com.liferay.blade.cli.util.WorkspaceUtil;
import com.liferay.project.templates.ProjectTemplates;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;

import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import org.gradle.testkit.runner.BuildTask;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author Gregory Amerson
 */
public class CreateCommandTest {

	@Before
	public void setUp() throws Exception {
		_bladeTest = new BladeTest(temporaryFolder.getRoot());
	}

	@Test
	public void testCreateActivator() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] gradleArgs = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "activator", "bar-activator"};

		String projectPath = new File(tempRoot, "bar-activator").getAbsolutePath();

		_bladeTest.run(gradleArgs);

		_checkGradleBuildFiles(projectPath);

		_contains(
			_checkFileExists(projectPath + "/src/main/java/bar/activator/BarActivator.java"),
			".*^public class BarActivator implements BundleActivator.*$");

		TestUtil.verifyBuild(projectPath, "bar.activator-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "build/libs/bar.activator-1.0.0.jar"));

		FileUtil.deleteDir(Paths.get(projectPath));
	}

	@Test
	public void testCreateApi() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] gradleArgs = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "api", "foo"};

		String projectPath = new File(tempRoot, "foo").getAbsolutePath();

		_bladeTest.run(gradleArgs);

		_checkGradleBuildFiles(projectPath);

		_contains(_checkFileExists(projectPath + "/src/main/java/foo/api/Foo.java"), ".*^public interface Foo.*");

		_contains(_checkFileExists(projectPath + "/src/main/resources/foo/api/packageinfo"), "version 1.0.0");

		TestUtil.verifyBuild(projectPath, "foo-1.0.0.jar");

		try (Jar jar = new Jar(new File(projectPath, "build/libs/foo-1.0.0.jar"))) {
			Manifest manifest = jar.getManifest();

			Attributes mainAttributes = manifest.getMainAttributes();

			Assert.assertEquals("foo.api;version=\"1.0.0\"", mainAttributes.getValue("Export-Package"));
		}
	}

	@Test
	public void testCreateExtModule() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] gradleArgs = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "modules-ext", "-m", "com.liferay.login.web", "-M",
			"1.0.0", "loginExt"
		};

		String projectPath = new File(tempRoot, "loginExt").getAbsolutePath();

		_bladeTest.run(gradleArgs);

		_contains(
			_checkFileExists(projectPath + "/build.gradle"),
			new String[] {
				".*^apply plugin: \"com.liferay.osgi.ext.plugin\".*$",
				"^.*originalModule group: \"com.liferay\", name: \"com.liferay.login.web\", version: \"1.0.0\".*$"
			});

		String buildJarName = "com.liferay.login.web-1.0.0.ext.jar";

		TestUtil.verifyBuild(projectPath, buildJarName);

		_verifyImportPackage(new File(projectPath, "build/libs/" + buildJarName));
	}

	@Test
	public void testCreateExtModuleWithoutOriginalModuleOptions() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "modules-ext", "loginExt"};

		BladeTestResults bladeTestResults = TestUtil.runBlade(args);

		String output = bladeTestResults.getOutput();

		Assert.assertTrue(output, output.contains("modules-ext options"));

		args =
			new String[] {"create", "-d", tempRoot.getAbsolutePath(), "-t", "modules-ext", "-M", "1.0.0", "loginExt"};

		output = TestUtil.runBlade(tempRoot, args).getOutput();

		Assert.assertTrue(output, output.contains("modules-ext options"));
	}

	@Test
	public void testCreateFragment() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] gradleArgs = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "fragment", "-h", "com.liferay.login.web", "-H", "1.0.0",
			"loginHook"
		};

		String projectPath = new File(tempRoot, "loginHook").getAbsolutePath();

		_bladeTest.run(gradleArgs);

		_checkGradleBuildFiles(projectPath);

		_contains(
			_checkFileExists(projectPath + "/bnd.bnd"),
			new String[] {
				".*^Bundle-SymbolicName: loginhook.*$",
				".*^Fragment-Host: com.liferay.login.web;bundle-version=\"1.0.0\".*$"
			});

		_contains(_checkFileExists(projectPath + "/build.gradle"), ".*^apply plugin: \"com.liferay.plugin\".*");

		TestUtil.verifyBuild(projectPath, "loginhook-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "build/libs/loginhook-1.0.0.jar"));
	}

	@Test
	public void testCreateFragmentWithoutHostOptions() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "fragment", "loginHook"};

		BladeTestResults bladeTestResults = TestUtil.runBlade(args);

		String output = bladeTestResults.getOutput();

		Assert.assertTrue(output, output.contains("\"-t fragment\" options missing"));

		args = new String[]
			{"create", "-d", tempRoot.getAbsolutePath(), "-t", "fragment", "-h", "com.liferay.login.web", "loginHook"};

		output = bladeTestResults.getOutput();

		Assert.assertTrue(output, output.contains("\"-t fragment\" options missing"));

		args = new String[] {"create", "-d", tempRoot.getAbsolutePath(), "-t", "fragment", "-H", "1.0.0", "loginHook"};

		output = TestUtil.runBlade(tempRoot, args).getOutput();

		Assert.assertTrue(output, output.contains("\"-t fragment\" options missing"));
	}

	@Test
	public void testCreateGradleMVCPortletProjectWithPackage() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args =
			{"create", "-d", tempRoot.getAbsolutePath(), "-t", "mvc-portlet", "-p", "com.liferay.test", "foo"};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "foo").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(projectPath + "/bnd.bnd");

		_contains(
			_checkFileExists(projectPath + "/src/main/java/com/liferay/test/portlet/FooPortlet.java"),
			".*^public class FooPortlet extends MVCPortlet.*$");

		_contains(
			_checkFileExists(new File(projectPath, "build.gradle").getAbsolutePath()),
			".*^apply plugin: \"com.liferay.plugin\".*");

		_checkFileExists(projectPath + "/src/main/resources/META-INF/resources/view.jsp");

		_checkFileExists(projectPath + "/src/main/resources/META-INF/resources/init.jsp");

		TestUtil.verifyBuild(projectPath, "com.liferay.test-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "/build/libs/com.liferay.test-1.0.0.jar"));
	}

	@Test
	public void testCreateGradleMVCPortletProjectWithPortletSuffix() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "mvc-portlet", "portlet-portlet"};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "portlet-portlet").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(projectPath + "/bnd.bnd");

		_contains(
			_checkFileExists(projectPath + "/src/main/java/portlet/portlet/portlet/PortletPortlet.java"),
			".*^public class PortletPortlet extends MVCPortlet.*$");

		_contains(_checkFileExists(projectPath + "/build.gradle"), ".*^apply plugin: \"com.liferay.plugin\".*");

		_checkFileExists(projectPath + "/src/main/resources/META-INF/resources/view.jsp");

		_checkFileExists(projectPath + "/src/main/resources/META-INF/resources/init.jsp");

		TestUtil.verifyBuild(projectPath, "portlet.portlet-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "/build/libs/portlet.portlet-1.0.0.jar"));
	}

	@Test
	public void testCreateGradlePortletProject() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "portlet", "-c", "Foo", "gradle.test"};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "gradle.test").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(projectPath + "/build.gradle");

		_contains(
			_checkFileExists(projectPath + "/src/main/java/gradle/test/portlet/FooPortlet.java"),
			new String[] {
				"^package gradle.test.portlet;.*", ".*javax.portlet.display-name=Foo.*",
				".*^public class FooPortlet .*", ".*Hello from Foo!.*"
			});

		TestUtil.verifyBuild(projectPath, "gradle.test-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "/build/libs/gradle.test-1.0.0.jar"));
	}

	@Test
	public void testCreateGradleService() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "service", "-s",
			"com.liferay.portal.kernel.events.LifecycleAction", "-c", "FooAction", "servicepreaction"
		};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "servicepreaction").getAbsolutePath();

		_checkFileExists(projectPath + "/build.gradle");

		File file = new File(projectPath, "src/main/java/servicepreaction/FooAction.java");

		_contains(
			_checkFileExists(file.getPath()),
			new String[] {
				"^package servicepreaction;.*", ".*^import com.liferay.portal.kernel.events.LifecycleAction;$.*",
				".*service = LifecycleAction.class.*", ".*^public class FooAction implements LifecycleAction \\{.*"
			});

		List<String> lines = new ArrayList<>();
		String line = null;

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			while ((line = reader.readLine()) != null) {
				lines.add(line);

				if (line.equals("import com.liferay.portal.kernel.events.LifecycleAction;")) {
					lines.add("import com.liferay.portal.kernel.events.LifecycleEvent;");
					lines.add("import com.liferay.portal.kernel.events.ActionException;");
				}

				if (line.equals("public class FooAction implements LifecycleAction {")) {
					StringBuilder sb = new StringBuilder();

					sb.append("@Override\n");
					sb.append("public void processLifecycleEvent(LifecycleEvent lifecycleEvent)\n");
					sb.append("throws ActionException {\n");
					sb.append("System.out.println(\"login.event.pre=\" + lifecycleEvent);\n");
					sb.append("}\n");

					lines.add(sb.toString());
				}
			}
		}

		try (Writer writer = new FileWriter(file)) {
			for (String string : lines) {
				writer.write(string + "\n");
			}
		}

		TestUtil.verifyBuild(projectPath, "servicepreaction-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "build/libs/servicepreaction-1.0.0.jar"));
	}

	@Test
	public void testCreateGradleServiceBuilderDashes() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "service-builder", "-p",
			"com.liferay.backend.integration", "backend-integration"
		};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "backend-integration").getAbsolutePath();

		_contains(
			_checkFileExists(projectPath + "/settings.gradle"),
			"include \"backend-integration-api\", \"backend-integration-service\"");

		_contains(
			_checkFileExists(projectPath + "/backend-integration-api/bnd.bnd"),
			new String[] {
				".*Export-Package:\\\\.*", ".*com.liferay.backend.integration.exception,\\\\.*",
				".*com.liferay.backend.integration.model,\\\\.*", ".*com.liferay.backend.integration.service,\\\\.*",
				".*com.liferay.backend.integration.service.persistence.*"
			});

		_contains(_checkFileExists(projectPath + "/backend-integration-service/bnd.bnd"), ".*Liferay-Service: true.*");

		BuildTask buildServiceTask = GradleRunnerUtil.executeGradleRunner(projectPath, "buildService");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildServiceTask);

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(projectPath, "build");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/backend-integration-api", "com.liferay.backend.integration.api-1.0.0.jar");
		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/backend-integration-service", "com.liferay.backend.integration.service-1.0.0.jar");

		_verifyImportPackage(
			new File(
				projectPath,
				"backend-integration-service/build/libs/com.liferay.backend.integration.service-1.0.0.jar"));
	}

	@Test
	public void testCreateGradleServiceBuilderDefault() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "service-builder", "-p", "com.liferay.docs.guestbook",
			"guestbook"
		};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "guestbook").getAbsolutePath();

		_contains(
			_checkFileExists(projectPath + "/settings.gradle"), "include \"guestbook-api\", \"guestbook-service\"");

		_contains(
			_checkFileExists(projectPath + "/guestbook-api/bnd.bnd"),
			new String[] {
				".*Export-Package:\\\\.*", ".*com.liferay.docs.guestbook.exception,\\\\.*",
				".*com.liferay.docs.guestbook.model,\\\\.*", ".*com.liferay.docs.guestbook.service,\\\\.*",
				".*com.liferay.docs.guestbook.service.persistence.*"
			});

		_contains(_checkFileExists(projectPath + "/guestbook-service/bnd.bnd"), ".*Liferay-Service: true.*");

		File file = _checkFileExists(projectPath + "/guestbook-service/build.gradle");

		_contains(file, ".*compileOnly project\\(\":guestbook-api\"\\).*");

		BuildTask buildService = GradleRunnerUtil.executeGradleRunner(projectPath, "buildService");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildService);

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(projectPath, "build");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(projectPath + "/guestbook-api", "com.liferay.docs.guestbook.api-1.0.0.jar");
		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/guestbook-service", "com.liferay.docs.guestbook.service-1.0.0.jar");

		File serviceJar = new File(
			projectPath, "guestbook-service/build/libs/com.liferay.docs.guestbook.service-1.0.0.jar");

		_verifyImportPackage(serviceJar);

		try (JarFile serviceJarFile = new JarFile(serviceJar)) {
			Manifest manifest = serviceJarFile.getManifest();

			Attributes mainAttributes = manifest.getMainAttributes();

			String springContext = mainAttributes.getValue("Liferay-Spring-Context");

			Assert.assertTrue(springContext.equals("META-INF/spring"));
		}
	}

	@Test
	public void testCreateGradleServiceBuilderDots() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "service-builder", "-p", "com.liferay.docs.guestbook",
			"com.liferay.docs.guestbook"
		};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "com.liferay.docs.guestbook").getAbsolutePath();

		_contains(
			_checkFileExists(projectPath + "/settings.gradle"),
			"include \"com.liferay.docs.guestbook-api\", \"com.liferay.docs.guestbook-service\"");

		_contains(
			_checkFileExists(projectPath + "/com.liferay.docs.guestbook-api/bnd.bnd"),
			new String[] {
				".*Export-Package:\\\\.*", ".*com.liferay.docs.guestbook.exception,\\\\.*",
				".*com.liferay.docs.guestbook.model,\\\\.*", ".*com.liferay.docs.guestbook.service,\\\\.*",
				".*com.liferay.docs.guestbook.service.persistence.*"
			});

		_contains(
			_checkFileExists(projectPath + "/com.liferay.docs.guestbook-service/bnd.bnd"), ".*Liferay-Service: true.*");

		BuildTask buildService = GradleRunnerUtil.executeGradleRunner(projectPath, "buildService");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildService);

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(projectPath, "build");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/com.liferay.docs.guestbook-api", "com.liferay.docs.guestbook.api-1.0.0.jar");
		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/com.liferay.docs.guestbook-service", "com.liferay.docs.guestbook.service-1.0.0.jar");

		_verifyImportPackage(
			new File(
				projectPath,
				"com.liferay.docs.guestbook-service/build/libs/com.liferay.docs.guestbook.service-1.0.0.jar"));
	}

	@Test
	public void testCreateGradleServiceWrapper() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "service-wrapper", "-s",
			"com.liferay.portal.kernel.service.UserLocalServiceWrapper", "serviceoverride"
		};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "serviceoverride").getAbsolutePath();

		_checkFileExists(projectPath + "/build.gradle");

		_contains(
			_checkFileExists(projectPath + "/src/main/java/serviceoverride/Serviceoverride.java"),
			new String[] {
				"^package serviceoverride;.*",
				".*^import com.liferay.portal.kernel.service.UserLocalServiceWrapper;$.*",
				".*service = ServiceWrapper.class.*",
				".*^public class Serviceoverride extends UserLocalServiceWrapper \\{.*",
				".*public Serviceoverride\\(\\) \\{.*"
			});

		TestUtil.verifyBuild(projectPath, "serviceoverride-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "build/libs/serviceoverride-1.0.0.jar"));
	}

	@Test
	public void testCreateGradleSymbolicName() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-t", "mvc-portlet", "-d", tempRoot.getAbsolutePath(), "-p", "foo.bar", "barfoo"};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "barfoo").getAbsolutePath();

		_checkFileExists(projectPath + "/build.gradle");

		_contains(_checkFileExists(projectPath + "/bnd.bnd"), ".*Bundle-SymbolicName: foo.bar.*");

		TestUtil.verifyBuild(projectPath, "foo.bar-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "build/libs/foo.bar-1.0.0.jar"));
	}

	@Test
	public void testCreateMissingArgument() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "foobar", "-d", tempRoot.getAbsolutePath()};

		String output = null;

		try {
			BladeTestResults bladeTestResults = TestUtil.runBlade(args);

			output = bladeTestResults.getOutput();
		}
		catch (Throwable t) {
			output = t.getMessage();
		}

		Assert.assertNotNull(output);

		boolean containsError = output.contains("The following option is required");

		Assert.assertTrue(containsError);
	}

	@Test
	public void testCreateMVCPortlet() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] gradleArgs = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "mvc-portlet", "foo"};

		String projectPath = new File(tempRoot, "foo").getAbsolutePath();

		_bladeTest.run(gradleArgs);

		_checkGradleBuildFiles(projectPath);

		_contains(
			_checkFileExists(projectPath + "/src/main/java/foo/portlet/FooPortlet.java"),
			".*^public class FooPortlet extends MVCPortlet.*$");

		_contains(_checkFileExists(projectPath + "/build.gradle"), ".*^apply plugin: \"com.liferay.plugin\".*");

		_checkFileExists(projectPath + "/src/main/resources/META-INF/resources/view.jsp");

		_checkFileExists(projectPath + "/src/main/resources/META-INF/resources/init.jsp");

		TestUtil.verifyBuild(projectPath, "foo-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "build/libs/foo-1.0.0.jar"));
	}

	@Test
	public void testCreateNpmAngular() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "npm-angular-portlet", "npmangular"};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "npmangular").getAbsolutePath();

		_checkFileExists(projectPath + "/build.gradle");

		File jsp = _checkFileExists(projectPath + "/src/main/resources/META-INF/resources/view.jsp");

		_contains(jsp, ".*<aui:script require=\"<%= bootstrapRequire %>\">.*");

		_contains(jsp, ".*bootstrapRequire.default.*");
	}

	@Test
	public void testCreateOnExistFolder() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "activator", "exist"};

		File existFile = new File(tempRoot, "exist/file.txt");

		if (!existFile.exists()) {
			existFile.getParentFile().mkdirs();

			Assert.assertTrue(existFile.createNewFile());
		}

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "exist").getAbsolutePath();

		_checkFileDoesNotExists(projectPath + "/bnd.bnd");
	}

	@Test
	public void testCreatePortletConfigurationIcon() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "portlet-configuration-icon", "-p", "blade.test",
			"icontest"
		};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "icontest").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(projectPath + "/bnd.bnd");

		File componentFile = _checkFileExists(
			projectPath + "/src/main/java/blade/test/portlet/configuration/icon/IcontestPortletConfigurationIcon.java");

		_contains(
			componentFile, ".*^public class IcontestPortletConfigurationIcon.*extends BasePortletConfigurationIcon.*$");

		File gradleBuildFile = _checkFileExists(projectPath + "/build.gradle");

		_contains(gradleBuildFile, ".*^apply plugin: \"com.liferay.plugin\".*");

		TestUtil.verifyBuild(projectPath, "blade.test-1.0.0.jar");
	}

	@Test
	public void testCreatePortletToolbarContributor() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "portlet-toolbar-contributor", "-p", "blade.test",
			"toolbartest"
		};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "toolbartest").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(projectPath + "/bnd.bnd");

		File componentFile = _checkFileExists(
			projectPath + "/src/main/java/blade/test/portlet/toolbar/contributor" +
				"/ToolbartestPortletToolbarContributor.java");

		_contains(
			componentFile,
			".*^public class ToolbartestPortletToolbarContributor.*implements PortletToolbarContributor.*$");

		File gradleBuildFile = _checkFileExists(projectPath + "/build.gradle");

		_contains(gradleBuildFile, ".*^apply plugin: \"com.liferay.plugin\".*");

		TestUtil.verifyBuild(projectPath, "blade.test-1.0.0.jar");
	}

	@Test
	public void testCreateProjectAllDefaults() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "mvc-portlet", "hello-world-portlet"};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "hello-world-portlet").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(projectPath + "/bnd.bnd");

		File portletFile = _checkFileExists(
			projectPath + "/src/main/java/hello/world/portlet/portlet/HelloWorldPortlet.java");

		_contains(portletFile, ".*^public class HelloWorldPortlet extends MVCPortlet.*$");

		File gradleBuildFile = _checkFileExists(projectPath + "/build.gradle");

		_contains(gradleBuildFile, ".*^apply plugin: \"com.liferay.plugin\".*");

		_checkFileExists(projectPath + "/src/main/resources/META-INF/resources/view.jsp");

		_checkFileExists(projectPath + "/src/main/resources/META-INF/resources/init.jsp");

		TestUtil.verifyBuild(projectPath, "hello.world.portlet-1.0.0.jar");

		_verifyImportPackage(new File(projectPath + "/build/libs/hello.world.portlet-1.0.0.jar"));
	}

	@Test
	public void testCreateProjectWithRefresh() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "mvc-portlet", "hello-world-refresh"};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "hello-world-refresh").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(projectPath + "/bnd.bnd");

		File portletFile = _checkFileExists(
			projectPath + "/src/main/java/hello/world/refresh/portlet/HelloWorldRefreshPortlet.java");

		_contains(portletFile, ".*^public class HelloWorldRefreshPortlet extends MVCPortlet.*$");

		File gradleBuildFile = _checkFileExists(projectPath + "/build.gradle");

		_contains(gradleBuildFile, ".*^apply plugin: \"com.liferay.plugin\".*");

		_checkFileExists(projectPath + "/src/main/resources/META-INF/resources/view.jsp");

		_checkFileExists(projectPath + "/src/main/resources/META-INF/resources/init.jsp");

		TestUtil.verifyBuild(projectPath, "hello.world.refresh-1.0.0.jar");

		_verifyImportPackage(new File(projectPath + "/build/libs/hello.world.refresh-1.0.0.jar"));
	}

	@Test
	public void testCreateServiceTemplateServiceParameterRequired() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "service", "foo"};

		String errors = null;

		BladeTestResults bladeTestResults = TestUtil.runBlade(false, args);

		try {
			errors = bladeTestResults.getErrors();
		}
		catch (Throwable t) {
			errors = t.getMessage();
		}

		Assert.assertNotNull(errors);

		Assert.assertTrue(errors, errors.contains("Usage:"));

		args = new String[] {"create", "-t", "service", "-s com.test.Foo", "foo"};

		bladeTestResults = TestUtil.runBlade(false, args);

		try {
			errors = bladeTestResults.getErrors();
		}
		catch (Throwable t) {
			errors = t.getMessage();
		}

		Assert.assertFalse(errors, errors.contains("Usage:"));
	}

	@Test
	public void testCreateSimulationPanelEntry() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "simulation-panel-entry", "-p", "test.simulator",
			"simulator"
		};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "simulator").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(projectPath + "/bnd.bnd");

		File componentFile = _checkFileExists(
			projectPath + "/src/main/java/test/simulator/application/list/SimulatorSimulationPanelApp.java");

		_contains(componentFile, ".*^public class SimulatorSimulationPanelApp.*extends BaseJSPPanelApp.*$");

		File gradleBuildFile = _checkFileExists(projectPath + "/build.gradle");

		_contains(gradleBuildFile, ".*^apply plugin: \"com.liferay.plugin\".*");

		TestUtil.verifyBuild(projectPath, "test.simulator-1.0.0.jar");
	}

	@Test
	public void testCreateSpringMvcPortlet() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "spring-mvc-portlet", "-p", "test.spring.portlet",
			"spring-test"
		};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "spring-test").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(
			projectPath + "/src/main/java/test/spring/portlet/portlet/SpringTestPortletViewController.java");

		_checkFileExists(projectPath + "/build.gradle");

		TestUtil.verifyBuild(projectPath, "spring-test.war");
	}

	@Test
	public void testCreateTemplateContextContributor() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args =
			{"create", "-d", tempRoot.getAbsolutePath(), "-t", "template-context-contributor", "blade-test"};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "blade-test").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(projectPath + "/bnd.bnd");

		File componentFile = _checkFileExists(
			projectPath + "/src/main/java/blade/test/context/contributor/BladeTestTemplateContextContributor.java");

		_contains(
			componentFile,
			".*^public class BladeTestTemplateContextContributor.*implements TemplateContextContributor.*$");

		File gradleBuildFile = _checkFileExists(projectPath + "/build.gradle");

		_contains(gradleBuildFile, ".*^apply plugin: \"com.liferay.plugin\".*");

		TestUtil.verifyBuild(projectPath, "blade.test-1.0.0.jar");
	}

	@Test
	public void testCreateTheme() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "theme", "theme-test"};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "theme-test").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileDoesNotExists(projectPath + "/bnd.bnd");

		_checkFileExists(projectPath + "/src/main/webapp/css/_custom.scss");

		File properties = _checkFileExists(projectPath + "/src/main/webapp/WEB-INF/liferay-plugin-package.properties");

		_contains(properties, ".*^name=theme-test.*");

		TestUtil.verifyBuild(projectPath, "theme-test.war");
	}

	@Test
	public void testCreateThemeContributor() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {
			"create", "-d", tempRoot.getAbsolutePath(), "-t", "theme-contributor", "-C", "foobar",
			"theme-contributor-test"
		};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "theme-contributor-test").getAbsolutePath();

		_checkFileExists(projectPath);

		File bnd = _checkFileExists(projectPath + "/bnd.bnd");

		_contains(bnd, ".*Liferay-Theme-Contributor-Type: foobar.*");

		TestUtil.verifyBuild(projectPath, "theme.contributor.test-1.0.0.jar");
	}

	@Test
	public void testCreateWarHookLocation() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		_makeWorkspace(workspace);

		_testCreateWar(workspace, "war-hook", "war-hook-test");
	}

	@Test
	public void testCreateWarMVCPortletLocation() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		_makeWorkspace(workspace);

		_testCreateWar(workspace, "war-mvc-portlet", "war-portlet-test");
	}

	@Test
	public void testCreateWorkspaceGradleExtModule() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File extDir = new File(workspace, "ext");

		_makeWorkspace(workspace);

		String[] gradleArgs = {
			"create", "-d", extDir.getAbsolutePath(), "-t", "modules-ext", "-m", "com.liferay.login.web", "-M", "1.0.0",
			"loginExt"
		};

		_bladeTest.run(gradleArgs);

		String projectPath = extDir.getAbsolutePath();

		_checkFileExists(projectPath + "/loginExt");

		_contains(
			_checkFileExists(projectPath + "/loginExt/build.gradle"),
			new String[] {
				"^.*originalModule group: \"com.liferay\", name: \"com.liferay.login.web\", version: \"1.0.0\".*$"
			});

		_lacks(
			_checkFileExists(projectPath + "/loginExt/build.gradle"),
			".*^apply plugin: \"com.liferay.osgi.ext.plugin\".*$");

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "jar");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		String extJarName = "com.liferay.login.web-1.0.0.ext.jar";

		GradleRunnerUtil.verifyBuildOutput(projectPath + "/loginExt", extJarName);

		_verifyImportPackage(new File(projectPath, "loginExt/build/libs/" + extJarName));
	}

	@Test
	public void testCreateWorkspaceGradleFragment() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File extensionsDir = new File(workspace, "modules/extensions");

		String[] args = {
			"create", "-d", extensionsDir.getAbsolutePath(), "-t", "fragment", "-h", "com.liferay.login.web", "-H",
			"1.0.0", "loginHook"
		};

		_makeWorkspace(workspace);

		_bladeTest.run(args);

		String projectPath = extensionsDir.getAbsolutePath();

		_checkFileExists(projectPath + "/loginHook");

		_contains(
			_checkFileExists(projectPath + "/loginHook/bnd.bnd"),
			new String[] {
				".*^Bundle-SymbolicName: loginhook.*$",
				".*^Fragment-Host: com.liferay.login.web;bundle-version=\"1.0.0\".*$"
			});

		_checkFileExists(projectPath + "/loginHook/build.gradle");

		_lacks(_checkFileExists(projectPath + "/loginHook/build.gradle"), ".*^apply plugin: \"com.liferay.plugin\".*");

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "jar");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(projectPath + "/loginHook", "loginhook-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "loginHook/build/libs/loginhook-1.0.0.jar"));
	}

	@Test
	public void testCreateWorkspaceGradlePortletProject() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File appsDir = new File(workspace, "modules/apps");

		String projectPath = appsDir.getAbsolutePath();

		String[] args = {"create", "-d", projectPath, "-t", "portlet", "-c", "Foo", "gradle.test"};

		_makeWorkspace(workspace);

		_bladeTest.run(args);

		_checkFileExists(projectPath + "/gradle.test/build.gradle");

		_checkFileDoesNotExists(projectPath + "/gradle.test/gradlew");

		_contains(
			_checkFileExists(projectPath + "/gradle.test/src/main/java/gradle/test/portlet/FooPortlet.java"),
			new String[] {
				"^package gradle.test.portlet;.*", ".*javax.portlet.display-name=Foo.*",
				".*^public class FooPortlet .*", ".*Hello from Foo!.*"
			});

		_lacks(
			_checkFileExists(projectPath + "/gradle.test/build.gradle"), ".*^apply plugin: \"com.liferay.plugin\".*");

		TestUtil.verifyBuild(workspace.getPath(), "jar", "gradle.test-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "gradle.test/build/libs/gradle.test-1.0.0.jar"));
	}

	@Test
	public void testCreateWorkspaceGradleServiceBuilderProjectApiPath() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File nestedDir = new File(workspace, "modules/nested/path");

		String[] args = {
			"create", "-d", nestedDir.getAbsolutePath(), "-t", "service-builder", "-p", "com.liferay.sample", "sample"
		};

		_makeWorkspace(workspace);

		Assert.assertTrue(nestedDir.mkdirs());

		_bladeTest.run(args);

		String projectPath = nestedDir.getAbsolutePath();

		_checkFileExists(projectPath + "/sample/build.gradle");

		_checkFileDoesNotExists(projectPath + "/sample/settings.gradle");

		_checkFileExists(projectPath + "/sample/sample-api/build.gradle");

		_checkFileExists(projectPath + "/sample/sample-service/build.gradle");

		File file = _checkFileExists(projectPath + "/sample/sample-service/build.gradle");

		_contains(file, ".*compileOnly project\\(\":modules:nested:path:sample:sample-api\"\\).*");

		BuildTask buildService = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "buildService");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildService);

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "jar");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(projectPath + "/sample/sample-api", "com.liferay.sample.api-1.0.0.jar");
		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/sample/sample-service", "com.liferay.sample.service-1.0.0.jar");

		_verifyImportPackage(
			new File(projectPath, "sample/sample-service/build/libs/com.liferay.sample.service-1.0.0.jar"));
	}

	@Test
	public void testCreateWorkspaceGradleServiceBuilderProjectDashes() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File modulesDir = new File(workspace, "modules");

		String projectPath = modulesDir.getAbsolutePath();

		String[] args = {"create", "-d", projectPath, "-t", "service-builder", "-p", "com.sample", "workspace-sample"};

		_makeWorkspace(workspace);

		_bladeTest.run(args);

		_checkFileExists(projectPath + "/workspace-sample/build.gradle");

		_checkFileDoesNotExists(projectPath + "/workspace-sample/settings.gradle");

		_checkFileExists(projectPath + "/workspace-sample/workspace-sample-api/build.gradle");

		_checkFileExists(projectPath + "/workspace-sample/workspace-sample-service/build.gradle");

		BuildTask buildService = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "buildService");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildService);

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "jar");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/workspace-sample/workspace-sample-api", "com.sample.api-1.0.0.jar");
		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/workspace-sample/workspace-sample-service", "com.sample.service-1.0.0.jar");

		_verifyImportPackage(
			new File(projectPath, "workspace-sample/workspace-sample-service/build/libs/com.sample.service-1.0.0.jar"));
	}

	@Test
	public void testCreateWorkspaceGradleServiceBuilderProjectDefault() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File modulesDir = new File(workspace, "modules");

		String projectPath = modulesDir.getAbsolutePath();

		String[] args = {"create", "-d", projectPath, "-t", "service-builder", "-p", "com.liferay.sample", "sample"};

		_makeWorkspace(workspace);

		_bladeTest.run(args);

		_checkFileExists(projectPath + "/sample/build.gradle");

		_checkFileDoesNotExists(projectPath + "/sample/settings.gradle");

		_checkFileExists(projectPath + "/sample/sample-api/build.gradle");

		_checkFileExists(projectPath + "/sample/sample-service/build.gradle");

		File file = _checkFileExists(projectPath + "/sample/sample-service/build.gradle");

		_contains(file, ".*compileOnly project\\(\":modules:sample:sample-api\"\\).*");

		BuildTask buildService = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "buildService");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildService);

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "jar");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(projectPath + "/sample/sample-api", "com.liferay.sample.api-1.0.0.jar");
		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/sample/sample-service", "com.liferay.sample.service-1.0.0.jar");

		File serviceJar = new File(
			projectPath, "sample/sample-service/build/libs/com.liferay.sample.service-1.0.0.jar");

		_verifyImportPackage(serviceJar);

		try (JarFile serviceJarFile = new JarFile(serviceJar)) {
			Manifest manifest = serviceJarFile.getManifest();

			Attributes mainAttributes = manifest.getMainAttributes();

			String springContext = mainAttributes.getValue("Liferay-Spring-Context");

			Assert.assertTrue(springContext.equals("META-INF/spring"));
		}
	}

	@Test
	public void testCreateWorkspaceGradleServiceBuilderProjectDots() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File modulesDir = new File(workspace, "modules");

		String projectPath = modulesDir.getAbsolutePath();

		String[] args = {"create", "-d", projectPath, "-t", "service-builder", "-p", "com.sample", "workspace.sample"};

		_makeWorkspace(workspace);

		_bladeTest.run(args);

		_checkFileExists(projectPath + "/workspace.sample/build.gradle");

		_checkFileDoesNotExists(projectPath + "/workspace.sample/settings.gradle");

		_checkFileExists(projectPath + "/workspace.sample/workspace.sample-api/build.gradle");

		_checkFileExists(projectPath + "/workspace.sample/workspace.sample-service/build.gradle");

		BuildTask buildService = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "buildService");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildService);

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "jar");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/workspace.sample/workspace.sample-api", "com.sample.api-1.0.0.jar");
		GradleRunnerUtil.verifyBuildOutput(
			projectPath + "/workspace.sample/workspace.sample-service", "com.sample.service-1.0.0.jar");

		_verifyImportPackage(
			new File(projectPath, "workspace.sample/workspace.sample-service/build/libs/com.sample.service-1.0.0.jar"));
	}

	@Test
	public void testCreateWorkspaceModuleLocation() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File modulesDir = new File(workspace, "modules");

		String projectPath = modulesDir.getAbsolutePath();

		String[] args = {"--base", workspace.getAbsolutePath(), "create", "-t", "mvc-portlet", "foo"};

		_makeWorkspace(workspace);

		_bladeTest.run(args);

		_checkFileExists(projectPath + "/foo");

		_checkFileExists(projectPath + "/foo/bnd.bnd");

		File portletFile = _checkFileExists(projectPath + "/foo/src/main/java/foo/portlet/FooPortlet.java");

		_contains(portletFile, ".*^public class FooPortlet extends MVCPortlet.*$");

		File gradleBuildFile = _checkFileExists(projectPath + "/foo/build.gradle");

		_lacks(gradleBuildFile, ".*^apply plugin: \"com.liferay.plugin\".*");

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "jar");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(projectPath + "/foo", "foo-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "foo/build/libs/foo-1.0.0.jar"));
	}

	@Test
	public void testCreateWorkspaceProjectAllDefaults() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File appsDir = new File(workspace, "modules/apps");

		String projectPath = appsDir.getAbsolutePath();

		String[] args = {"create", "-d", projectPath, "-t", "mvc-portlet", "foo"};

		_makeWorkspace(workspace);

		_bladeTest.run(args);

		_checkFileExists(projectPath + "/foo");

		_checkFileExists(projectPath + "/foo/bnd.bnd");

		File portletFile = _checkFileExists(projectPath + "/foo/src/main/java/foo/portlet/FooPortlet.java");

		_contains(portletFile, ".*^public class FooPortlet extends MVCPortlet.*$");

		File gradleBuildFile = _checkFileExists(projectPath + "/foo/build.gradle");

		_lacks(gradleBuildFile, ".*^apply plugin: \"com.liferay.plugin\".*");

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "jar");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(projectPath + "/foo", "foo-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "foo/build/libs/foo-1.0.0.jar"));
	}

	@Test
	public void testCreateWorkspaceProjectWithRefresh() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File appsDir = new File(workspace, "modules/apps");

		String appsPath = appsDir.getAbsolutePath();

		String[] args = {"create", "-d", appsPath, "-t", "mvc-portlet", "foo-refresh"};

		_makeWorkspace(workspace);

		_bladeTest.run(args);

		String projectPath = new File(appsDir, "foo-refresh").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileExists(projectPath + "/bnd.bnd");

		File portletFile = _checkFileExists(projectPath + "/src/main/java/foo/refresh/portlet/FooRefreshPortlet.java");

		_contains(portletFile, ".*^public class FooRefreshPortlet extends MVCPortlet.*$");

		File gradleBuildFile = _checkFileExists(projectPath + "/build.gradle");

		_lacks(gradleBuildFile, ".*^apply plugin: \"com.liferay.plugin\".*");

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "jar");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(projectPath, "foo.refresh-1.0.0.jar");

		_verifyImportPackage(new File(projectPath, "build/libs/foo.refresh-1.0.0.jar"));
	}

	@Test
	public void testCreateWorkspaceThemeLocation() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		String[] args = {"--base", workspace.getAbsolutePath(), "create", "-t", "theme", "theme-test"};

		_makeWorkspace(workspace);

		_bladeTest.run(args);

		String projectPath = new File(workspace, "wars/theme-test").getAbsolutePath();

		_checkFileExists(projectPath);

		_checkFileDoesNotExists(projectPath + "/bnd.bnd");

		_checkFileExists(projectPath + "/src/main/webapp/css/_custom.scss");

		File properties = _checkFileExists(projectPath + "/src/main/webapp/WEB-INF/liferay-plugin-package.properties");

		_contains(properties, ".*^name=theme-test.*");

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getPath(), "war");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(projectPath, "theme-test.war");
	}

	@Test
	public void testCreateWorkspaceTypeValid() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		File workspace = new File(tempRoot, "workspace");

		File modulesDir = new File(workspace, "modules");

		String[] args = {"--base", modulesDir.getAbsolutePath(), "create", "-t", "soy-portlet", "foo"};

		_makeWorkspace(workspace);

		_bladeTest.run(args);

		File buildGradle = new File(modulesDir, "foo/build.gradle");

		_checkFileExists(buildGradle.getAbsolutePath());

		String content = new String(IO.read(buildGradle));

		Assert.assertEquals(1, StringUtils.countMatches(content, '{'));

		Assert.assertEquals(1, StringUtils.countMatches(content, '}'));
	}

	@Test
	public void testLiferayVersion() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] sevenZeroArgs =
			{"--base", tempRoot.getAbsolutePath(), "create", "-t", "npm-angular-portlet", "-v", "7.0", "seven-zero"};

		_bladeTest.run(sevenZeroArgs);

		File npmbundlerrc = new File(tempRoot, "seven-zero/build.gradle");

		String content = new String(IO.read(npmbundlerrc));

		Assert.assertFalse(content.contains("js.loader.modules.extender.api"));

		String[] sevenOneArgs =
			{"--base", tempRoot.getAbsolutePath(), "create", "-t", "npm-angular-portlet", "seven-one"};

		_bladeTest.run(sevenOneArgs);

		npmbundlerrc = new File(tempRoot, "seven-one/build.gradle");

		content = new String(IO.read(npmbundlerrc));

		Assert.assertTrue(content.contains("js.loader.modules.extender.api"));
	}

	@Test
	public void testListTemplates() throws Exception {
		String[] args = {"create", "-l"};

		BladeTestResults bladeTestResults = TestUtil.runBlade(args);

		String output = bladeTestResults.getOutput();

		Map<String, String> templates = ProjectTemplates.getTemplates();

		List<String> templateNames = new ArrayList<>(templates.keySet());

		for (String templateName : templateNames) {
			Assert.assertTrue(output.contains(templateName));
		}
	}

	@Test
	public void testWrongTemplateTyping() throws Exception {
		File tempRoot = temporaryFolder.getRoot();

		String[] args = {"create", "-d", tempRoot.getAbsolutePath(), "-t", "activatorXXX", "wrong-activator"};

		_bladeTest.run(args);

		String projectPath = new File(tempRoot, "wrong-activator").getAbsolutePath();

		_checkFileDoesNotExists(projectPath);
	}

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	private File _checkFileDoesNotExists(String path) {
		File file = IO.getFile(path);

		Assert.assertFalse(file.exists());

		return file;
	}

	private File _checkFileExists(String path) {
		File file = IO.getFile(path);

		Assert.assertTrue(file.exists());

		return file;
	}

	private void _checkGradleBuildFiles(String projectPath) {
		_checkFileExists(projectPath);
		_checkFileExists(projectPath + "/bnd.bnd");
		_checkFileExists(projectPath + "/build.gradle");
		_checkFileExists(projectPath + "/gradlew");
		_checkFileExists(projectPath + "/gradlew.bat");
	}

	private void _contains(File file, String pattern) throws Exception {
		String content = new String(IO.read(file));

		_contains(content, pattern);
	}

	private void _contains(File file, String[] patterns) throws Exception {
		String content = new String(IO.read(file));

		for (String pattern : patterns) {
			_contains(content, pattern);
		}
	}

	private void _contains(String content, String regex) throws Exception {
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);

		Assert.assertTrue(pattern.matcher(content).matches());
	}

	private void _lacks(File file, String regex) throws Exception {
		String content = new String(IO.read(file));

		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);

		Assert.assertFalse(pattern.matcher(content).matches());
	}

	private void _makeWorkspace(File workspace) throws Exception {
		String[] args = {"--base", workspace.getParentFile().getPath(), "init", workspace.getName()};

		_bladeTest.run(args);

		Assert.assertTrue(WorkspaceUtil.isWorkspace(workspace));
	}

	private void _testCreateWar(File workspace, String projectType, String projectName) throws Exception {
		String[] args = {"--base", workspace.toString(), "create", "-t", projectType, projectName};

		_bladeTest.run(args);

		String projectPath = new File(workspace, "wars/" + projectName).getAbsolutePath();

		_checkFileExists(projectPath);

		BuildTask buildTask = GradleRunnerUtil.executeGradleRunner(workspace.getAbsolutePath(), "war");

		GradleRunnerUtil.verifyGradleRunnerOutput(buildTask);

		GradleRunnerUtil.verifyBuildOutput(projectPath, projectName + ".war");
	}

	private void _verifyImportPackage(File serviceJar) throws Exception {
		try (Jar jar = new Jar(serviceJar)) {
			Manifest m = jar.getManifest();

			Domain domain = Domain.domain(m);

			Parameters imports = domain.getImportPackage();

			for (String key : imports.keySet()) {
				Assert.assertFalse(key.isEmpty());
			}
		}
	}

	private BladeTest _bladeTest;

}