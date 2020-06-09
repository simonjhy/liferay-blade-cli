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

import java.util.Map;
import java.util.Set;

/**
 * @author Gregory Amerson
 * @author Simon Jiang
 */
public interface ProjectInfo {

	public String getAppServerTomcatVersion();

	public String getBundleUrl();

	public String getDeployDir();

	public String getDockerContainerId();

	public String getDockerImageId();

	public String getDockerImageLiferay();

	public String getLiferayHome();

	public Set<String> getPluginClassNames();

	public Map<String, Set<File>> getProjectOutputFiles();

	public String getTargetPlatformVersion();

	public boolean isLiferayProject();

}