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

package com.liferay.blade.cli.maven;

import com.liferay.blade.cli.WorkspaceProvider;
import com.liferay.blade.cli.util.MavenUtil;

import java.io.File;

import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;

/**
 * @author Christopher Bryan Boyd
 * @author Simon Jiang
 */
public class MavenWorkspaceProvider implements WorkspaceProvider {

	@Override
	public File getWorkspaceDir(File dir) {
		return MavenUtil.getWorkspaceDir(dir);
	}

	public boolean isDependencyManagementEnabled(File dir) {
		if (!isWorkspace(dir)) {
			return false;
		}

		Properties properties = MavenUtil.getMavenProperties(dir);
		String targetPlatformVersionKey = "targetPlatformVersion";

		boolean targetPlatformEnabled = properties.containsKey(targetPlatformVersionKey);

		try {
			Model mavenModel = MavenUtil.getMavenModel(dir);

			DependencyManagement dependencyManagement = mavenModel.getDependencyManagement();

			List<Dependency> dependencies = dependencyManagement.getDependencies();

			Stream<Dependency> dependencyStream = dependencies.stream();

			boolean hasTargetPlatform = dependencyStream.allMatch(
				dependency ->
					_portal_bom_group.equals(dependency.getGroupId()) &&
					(_portal_bom_artifact.equals(dependency.getArtifactId()) ||
					_portal_bom_compile_artifact.equals(dependency.getArtifactId()) ||
					_portal_bom_third_party_artifact.equals(dependency.getArtifactId())));

			if (targetPlatformEnabled && hasTargetPlatform) {
				return true;
			}

			return false;
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	@Override
	public boolean isWorkspace(File dir) {
		return MavenUtil.isWorkspace(dir);
	}

	private static String _portal_bom_artifact = "release.portal.bom";
	private static String _portal_bom_compile_artifact = "release.portal.bom.compile.only";
	private static String _portal_bom_group = "com.liferay.portal";
	private static String _portal_bom_third_party_artifact = "release.portal.bom.third.party";

}