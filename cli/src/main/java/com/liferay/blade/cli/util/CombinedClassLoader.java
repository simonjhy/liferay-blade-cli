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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.lang.reflect.Field;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Christopher Bryan Boyd
 * @author Seiphon Wang
 */
public class CombinedClassLoader extends ClassLoader implements AutoCloseable {

	public CombinedClassLoader(ClassLoader... classLoaders) {
		for (ClassLoader classLoader : classLoaders) {
			_add(classLoader);
		}
	}

	@Override
	public void close() throws Exception {
		for (ClassLoader classLoader : _classLoaders) {
			try {
				@SuppressWarnings("static-access")
				final ClassLoader systemClassLoader = classLoader.getSystemClassLoader();

				if (classLoader.equals(systemClassLoader)) {
					continue;
				}

				if (classLoader instanceof URLClassLoader) {
					try {
						Class<URLClassLoader> urlClassLoader = URLClassLoader.class;

						Field ucp = urlClassLoader.getDeclaredField("ucp");

						ucp.setAccessible(true);

						Object urlClassPath = ucp.get(classLoader);

						Class<? extends Object> urlClass = urlClassPath.getClass();

						Field loaders = urlClass.getDeclaredField("loaders");

						loaders.setAccessible(true);

						Object urlClasspathLoaders = loaders.get(urlClassPath);

						Collection<?> loaderCollection = (Collection<?>)urlClasspathLoaders;

						Object[] jarLoaders = loaderCollection.toArray();

						for (Object jarLoader : jarLoaders) {
							try {
								Class<? extends Object> jarLoaderClazz = jarLoader.getClass();

								Field jarField = jarLoaderClazz.getDeclaredField("jar");

								jarField.setAccessible(true);

								Object jarFileObject = jarField.get(jarLoader);

								JarFile jarFile = (JarFile)jarFileObject;

								_closableJarFiles.add(jarFile.getName());

								jarFile.close();
							}
							catch (Throwable th) {
							}
						}
					}
					catch (Throwable th) {
					}
				}

				if (classLoader instanceof AutoCloseable) {
					AutoCloseable closeable = (AutoCloseable)classLoader;

					closeable.close();
				}
			}
			catch (Throwable th) {
			}
		}

		_cleanupJarFileFactory();
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		String path = name.replace('.', '/');

		path = path + ".class";

		URL url = findResource(path);

		if (url == null) {
			throw new ClassNotFoundException(name);
		}

		try {
			ByteBuffer byteCode = _loadResourceFromClasspath(url);

			return defineClass(name, byteCode, null);
		}
		catch (IOException ioe) {
			throw new ClassNotFoundException(name, ioe);
		}
	}

	@Override
	protected URL findResource(String name) {
		Stream<ClassLoader> urlStream = _classLoaders.stream();

		return urlStream.map(
			c -> c.getResource(name)
		).filter(
			Objects::nonNull
		).findAny(
		).orElse(
			null
		);
	}

	@Override
	protected Enumeration<URL> findResources(String name) throws IOException {
		Stream<ClassLoader> urlStream = _classLoaders.stream();

		return Collections.enumeration(
			urlStream.map(
				c -> _getResources(c, name)
			).map(
				Collections::list
			).flatMap(
				Collection::stream
			).collect(
				Collectors.toList()
			));
	}

	private static Enumeration<URL> _getResources(ClassLoader classLoader, String name) {
		try {
			return classLoader.getResources(name);
		}
		catch (IOException ioe) {
		}

		return null;
	}

	private static ByteBuffer _loadResourceFromClasspath(URL url) throws IOException {
		byte[] buffer = new byte[1024];

		URLConnection urlConnection = url.openConnection();

		try (InputStream inputStream = urlConnection.getInputStream();
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(buffer.length)) {

			int bytesCount = -1;

			while ((bytesCount = inputStream.read(buffer)) != -1) {
				byteArrayOutputStream.write(buffer, 0, bytesCount);
			}

			byte[] output = byteArrayOutputStream.toByteArray();

			return ByteBuffer.wrap(output);
		}
	}

	private void _add(ClassLoader classLoader) {
		_classLoaders.add(classLoader);
	}

	private void _cleanupJarFileFactory() {
		Class<?> classJarURLConnection = null;

		try {
			classJarURLConnection = Class.forName("sun.net.www.protocol.jar.JarURLConnection");

			if (classJarURLConnection == null) {
				return;
			}

			Field factoryField = classJarURLConnection.getDeclaredField("factory");

			if (factoryField == null) {
				return;
			}

			factoryField.setAccessible(true);

			Object factoryObject = factoryField.get(null);

			if (factoryObject == null) {
				return;
			}

			Class<? extends Object> classJarFileFactory = factoryObject.getClass();

			HashMap<?, ?> fileCache = null;

			try {
				Field fileCacheField = classJarFileFactory.getDeclaredField("fileCache");

				fileCacheField.setAccessible(true);

				Object fileCacheObject = fileCacheField.get(null);

				if (fileCacheObject instanceof HashMap) {
					fileCache = (HashMap<?, ?>)fileCacheObject;
				}
			}
			catch (IllegalAccessException | NoSuchFieldException e) {
			}

			HashMap<?, ?> urlCache = null;

			try {
				Field urlCacheField = classJarFileFactory.getDeclaredField("urlCache");

				urlCacheField.setAccessible(true);

				Object urlCacheObject = urlCacheField.get(null);

				if (urlCacheObject instanceof HashMap) {
					urlCache = (HashMap<?, ?>)urlCacheObject;
				}
			}
			catch (IllegalAccessException | NoSuchFieldException e) {
			}

			if (urlCache != null) {
				HashMap<?, ?> urlCacheClone = (HashMap<?, ?>)urlCache.clone();

				Iterator<?> urlCacheIterator = urlCacheClone.keySet(
				).iterator();

				while (urlCacheIterator.hasNext()) {
					Object urlCacheIteratorObject = urlCacheIterator.next();

					if (!(urlCacheIteratorObject instanceof JarFile)) {
						continue;
					}

					JarFile jarFile = (JarFile)urlCacheIteratorObject;

					if (_closableJarFiles.contains(jarFile.getName())) {
						try {
							jarFile.close();
						}
						catch (IOException e) {
						}

						urlCache.remove(jarFile);
					}
				}
			}

			if (fileCache != null) {
				HashMap<?, ?> fileCacheClone = (HashMap<?, ?>)fileCache.clone();

				Iterator<?> fileCacheIterator = fileCacheClone.keySet(
				).iterator();

				while (fileCacheIterator.hasNext()) {
					Object fileCacheIteratorKey = fileCacheIterator.next();

					Object fileCacheIteratorObject = fileCache.get(fileCacheIteratorKey);

					if (!(fileCacheIteratorObject instanceof JarFile)) {
						continue;
					}

					JarFile jarFile = (JarFile)fileCacheIteratorObject;

					if (_closableJarFiles.contains(jarFile.getName())) {
						try {
							jarFile.close();
						}
						catch (IOException e) {
						}

						fileCache.remove(jarFile);
					}
				}
			}
		}
		catch (Exception e) {
		}
		finally {
			_closableJarFiles.clear();
		}
	}

	private Collection<ClassLoader> _classLoaders = new ArrayList<>();
	private HashSet<String> _closableJarFiles = new HashSet<>();

}