package org.but4reuse.adapters.eclipse.plugin_infos_extractor.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.but4reuse.adapters.eclipse.PluginElement;

public class PluginInfosExtractor {
	private static final String BUNDLE_VERSION = "Bundle-Version";
	private static final String REQUIRE_BUNDLE = "Require-Bundle";
	private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";

	/*
	 * FOLDERS
	 */
	/**
	 * Extracts the plugin infos from its MANIFEST.MF file
	 * 
	 * @param manifestFile
	 *            the absolute path to the manifest
	 * @return a PluginElement containing all the required informations
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static PluginElement getPluginInfosFromManifest(String manifestFile) throws Exception {
		try {
			PluginElement plugin = new PluginElement();
			plugin.setJar(false);
			File f = new File(manifestFile);
			f = f.getParentFile().getParentFile();
			plugin.setAbsolutePath(f.getAbsolutePath());
			InputStream ips = new FileInputStream(manifestFile);
			Manifest manifest = new Manifest(ips);
			fillPluginElementInfo(plugin, manifest);
			ips.close();
			return plugin;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	private static void fillPluginElementInfo(PluginElement plugin, Manifest manifest) {
		Attributes attributes = manifest.getMainAttributes();
		String value = attributes.getValue(BUNDLE_SYMBOLIC_NAME);
		int i = value.indexOf(';');
		if (i != -1)
			value = value.substring(0, i);
		plugin.setPluginSymbName(value);
		String version = attributes.getValue(BUNDLE_VERSION);
		plugin.setPluginVersion(version);
		value = attributes.getValue(REQUIRE_BUNDLE);
		if (value != null) {
			getRequireBundlesSymbNames(value, plugin);
		}
	}

	/*
	 * JARS
	 */

	/**
	 * Extracts the plugin infos, considering that it is a jar file
	 * 
	 * @param jarFile
	 *            the absolute path to the jar file
	 * @return the plugin element
	 * @throws IOException
	 */
	public static PluginElement getPluginInfosFromJar(String jarFile) throws IOException {
		File f = new File(jarFile);
		JarFile jar = new JarFile(f);
		PluginElement plugin = new PluginElement();
		plugin.setJar(true);
		plugin.setAbsolutePath(jarFile);
		try {
			fillPluginElementInfo(plugin,jar.getManifest());
			jar.close();
			return plugin;
		} catch (IOException e) {
			throw new IOException("Read exception for file " + jarFile + " !");
		}
	}

	/*
	 * BOTH
	 */

	/**
	 * Extracts all the required plugins' symbolic names from the Require-Bundle
	 * field's value
	 * 
	 * @param value
	 *            a String containing the whole Require-Bundle field's value
	 * @param plugin
	 *            the PluginElement for which we are searching the required
	 *            plugins
	 */
	private static void getRequireBundlesSymbNames(String value, PluginElement plugin) {
		String[] values = value.split(",");
		String previous = "";
		for (String val : values) {
			if (!val.matches("\\s*[0-9].*")) {
				int i = val.indexOf(';');
				if (i != -1)
					val = val.substring(0, i);
				val = val.replaceAll("\\s", "");
				previous = val;
				plugin.addRequire_bundle(val);
			} else if (val.contains("resolution:=optional")) {
				plugin.removeRequire_bundle(previous);
			}
		}
	}
}