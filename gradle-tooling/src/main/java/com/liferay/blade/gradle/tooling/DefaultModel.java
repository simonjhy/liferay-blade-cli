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

package com.liferay.blade.gradle.tooling;

import java.io.File;
import java.io.Serializable;

import java.util.Map;
import java.util.Set;

/**
 * @author Gregory Amerson
 * @author Simon Jiang
 */
@SuppressWarnings("serial")
public class DefaultModel implements ProjectInfo, Serializable {

	public DefaultModel(
		Set<String> pluginClassNames, Map<String, Set<File>> projectOutputFiles, String deployDir, String liferayHome,
		String dockerImageLiferay, String dockerImageId, String dockerContainerId, String appServerTomcatVersion,
		String bundleUrl, String targetPlatformVersion) {

		_pluginClassNames = pluginClassNames;
		_projectOutputFiles = projectOutputFiles;
		_deployDir = deployDir;
		_liferayHome = liferayHome;
		_dockerImageLiferay = dockerImageLiferay;
		_dockerImageId = dockerImageId;
		_dockerContainerId = dockerContainerId;
		_appServerTomcatVersion = appServerTomcatVersion;
		_bundleUrl = bundleUrl;
		_targetPlatformVersion = targetPlatformVersion;
	}

	@Override
	public String getAppServerTomcatVersion() {
		return _appServerTomcatVersion;
	}

	@Override
	public String getBundleUrl() {
		return _bundleUrl;
	}

	@Override
	public String getDeployDir() {
		return _deployDir;
	}

	@Override
	public String getDockerContainerId() {
		return _dockerContainerId;
	}

	@Override
	public String getDockerImageId() {
		return _dockerImageId;
	}

	@Override
	public String getDockerImageLiferay() {
		return _dockerImageLiferay;
	}

	@Override
	public String getLiferayHome() {
		return _liferayHome;
	}

	@Override
	public Set<String> getPluginClassNames() {
		return _pluginClassNames;
	}

	@Override
	public Map<String, Set<File>> getProjectOutputFiles() {
		return _projectOutputFiles;
	}

	@Override
	public String getTargetPlatformVersion() {
		return _targetPlatformVersion;
	}

	@Override
	public boolean isLiferayProject() {
		if (_hasPlugin("aQute.bnd.gradle.BndBuilderPlugin") || _hasPlugin("com.liferay.gradle.plugins.LiferayPlugin") ||
			_hasPlugin("com.liferay.gradle.plugins.LiferayOSGiPlugin") ||
			_hasPlugin("com.liferay.gradle.plugins.gulp.GulpPlugin")) {

			return true;
		}

		return false;
	}

	private boolean _hasPlugin(String pluginClassName) {
		return _pluginClassNames.contains(pluginClassName);
	}

	private final String _appServerTomcatVersion;
	private final String _bundleUrl;
	private final String _deployDir;
	private final String _dockerContainerId;
	private final String _dockerImageId;
	private final String _dockerImageLiferay;
	private final String _liferayHome;
	private final Set<String> _pluginClassNames;
	private final Map<String, Set<File>> _projectOutputFiles;
	private final String _targetPlatformVersion;

}