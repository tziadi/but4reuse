package org.but4reuse.adapters.eclipse.generator;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.but4reuse.adapters.IElement;
import org.but4reuse.adapters.eclipse.EclipseAdapter;
import org.but4reuse.adapters.eclipse.FileElement;
import org.but4reuse.adapters.eclipse.PluginElement;
import org.but4reuse.adapters.eclipse.benchmark.ActualFeature;
import org.but4reuse.adapters.eclipse.benchmark.FeatureHelper;
import org.but4reuse.adapters.eclipse.generator.dependencies.DependencyAnalyzer;
import org.but4reuse.adapters.eclipse.generator.interfaces.IListener;
import org.but4reuse.adapters.eclipse.generator.interfaces.ISender;
import org.but4reuse.adapters.eclipse.generator.interfaces.IVariantsGenerator;
import org.but4reuse.adapters.eclipse.generator.utils.EclipseKeepOnlyMetadata;
import org.but4reuse.adapters.eclipse.generator.utils.FileAndDirectoryUtils;
import org.but4reuse.adapters.eclipse.generator.utils.PluginElementGenerator;
import org.but4reuse.adapters.eclipse.generator.utils.SplotUtils;
import org.but4reuse.adapters.eclipse.generator.utils.VariantsUtils;
import org.but4reuse.utils.files.FileUtils;
import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * Variants generation using Random selection with option to create dissimilar
 * variants
 * 
 * @author Julien Margarido
 * @author Felix Lima Gorito
 * @author jabier.martinez
 */
public class VariantsRandomAndDissimilarGenerator implements IVariantsGenerator, ISender {

	private String input;
	private String output;
	private String generator;
	private int nbVariants;
	private int time;
	private boolean keepOnlyMetadata;
	private boolean noOutputOnlyStatistics;

	String generatorSummary;
	List<IListener> listeners;
	EclipseAdapter adapter;

	public VariantsRandomAndDissimilarGenerator(String input, String output, String generator, int nbVariants, int time,
			boolean keepOnlyMetadata, boolean noOutputOnlyStatistics) {
		this.input = input;
		this.output = output;
		this.generator = generator;
		this.nbVariants = nbVariants;
		this.time = time;
		this.keepOnlyMetadata = keepOnlyMetadata;
		this.noOutputOnlyStatistics = noOutputOnlyStatistics;
		adapter = new EclipseAdapter();
	}

	public void generate() {
		long startTime = System.currentTimeMillis();

		sendToAll("Starting generation with :");
		sendToAll("-input = " + input);
		sendToAll("-output = " + output);
		sendToAll("-generator = " + generator);
		sendToAll("-variants number = " + nbVariants);
		sendToAll("-time = " + time);
		sendToAll("-keepOnlyMetadata = " + keepOnlyMetadata);
		sendToAll("-onlyStatistics= " + noOutputOnlyStatistics + "\n");

		sendToAll("Please wait until Generation finished\n");

		File eclipse = new File(input);

		if (!eclipse.exists()) {
			sendToAll(input + " not exists !");
			return;
		}

		// if the eclipse dir is inside the input
		if (eclipse.list().length == 1 && eclipse.listFiles()[0].getName().equals("eclipse")) {
			if (input.endsWith(File.separator)) {
				input += "eclipse" + File.separator;
			} else {
				input += File.separator + "eclipse" + File.separator;
			}
			eclipse = new File(input);
		}

		// check if it's an eclipse directory
		if (!VariantsUtils.isEclipseDir(eclipse)) {
			sendToAll(input + " is not an eclipse !");
			return;
		}

		URI inputURI = new File(input).toURI();
		List<ActualFeature> allFeatures;
		try {
			allFeatures = FeatureHelper.getFeaturesOfEclipse(inputURI.toString());
		} catch (Exception e) {
			sendToAll("Error in generator : Impossible to get all features.");
			e.printStackTrace();
			return;
		}

		// ignore epp package
		List<ActualFeature> toRemove = new ArrayList<ActualFeature>();
		for (ActualFeature f : allFeatures) {
			if (f.getId().startsWith("org.eclipse.epp.package.")) {
				toRemove.add(f);
			}
		}
		allFeatures.removeAll(toRemove);

		List<PluginElement> allPlugins = new ArrayList<PluginElement>();
		List<FileElement> allFileElements = new ArrayList<FileElement>();

		List<IElement> iElems = adapter.adapt(inputURI, new NullProgressMonitor());
		for (IElement elem : iElems) {
			if (elem instanceof PluginElement)
				allPlugins.add((PluginElement) elem);
			else if (elem instanceof FileElement)
				allFileElements.add((FileElement) elem);
		}

		// Permits to use PluginElement without launch an Eclipse Application
		List<PluginElementGenerator> allPluginsGen = PluginElementGenerator.transformInto(allPlugins);

		sendToAll("Total features number in the input = " + allFeatures.size());
		sendToAll("Total plugins number in the input = " + allPluginsGen.size() + "\n");

		File outputFile = new File(output + File.separator + "SPLOTFeatureModel.xml");
		SplotUtils.exportToSPLOT(outputFile, allFeatures);

		File generatedConfigsFile = new File(output + File.separator + "generatedConfigs.txt");

		ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", generator, "generate_products", "-fm",
				outputFile.getAbsolutePath(), "-nbProds", Integer.toString(nbVariants), "-timeAllowedMS",
				Integer.toString(time * 1000), "-o", generatedConfigsFile.getAbsolutePath());

		try {
			Process p = processBuilder.start();
			while (p.isAlive()) {
				// loop
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		long stopTimePreparation = System.currentTimeMillis();
		long elapsedTimePreparation = stopTimePreparation - startTime;
		sendToAll("Preparation time (milliseconds): " + elapsedTimePreparation + "\n");

		sendToAll("\"Variant\";\"Name\";\"Selectedfeatures\";\"Plugins\";\"Milliseconds\"");

		List<String> generatedConfigs = FileUtils.getLinesOfFile(generatedConfigsFile);
		// remove headers and empty line
		List<String> linesToRemove = new ArrayList<String>();
		for (String line : generatedConfigs) {
			if (line.contains("->") || line.isEmpty()) {
				linesToRemove.add(line);
			}
		}
		generatedConfigs.removeAll(linesToRemove);

		// Variants loop
		DependencyAnalyzer depAnalyzer = new DependencyAnalyzer(allFeatures, allPluginsGen, inputURI.toString());
		for (int i = 1; i <= nbVariants; i++) {
			long startTimeThisVariant = System.currentTimeMillis();
			String output_variant = output + File.separator + VariantsUtils.VARIANT + "_" + i;

			List<PluginElement> pluginsList = new ArrayList<PluginElement>();
			List<ActualFeature> chosenFeatures = new ArrayList<ActualFeature>();

			// A config is an array of integers. 2 means that feature 2 is
			// selected, -4 means that feature 4 is not selected
			String c = generatedConfigs.get(i - 1);
			String[] numbers = c.split(";");

			for (String x : numbers) {
				int j = Integer.parseInt(x);
				// positive number means that it was selected
				if (j > 0) {
					// the file creates a fake root and it starts from 1
					j = j - 2;
					// do not consider the fake root (1st feature)
					if (j != -1) {
						String id = allFeatures.get(j).getId();
						for (ActualFeature oneFeat : allFeatures) {
							if (oneFeat.getId().equals(id)) {
								chosenFeatures.add(oneFeat);
								break;
							}
						}
					}
				}
			} // end of iterate through allFeatures

			for (ActualFeature one_manda : depAnalyzer.getMandatoryFeaturesForThisInput()) {
				if (!chosenFeatures.contains(one_manda)) {
					chosenFeatures.add(one_manda);
				}
			}

			// Get all plugins from chosen features
			for (ActualFeature chosenFeature : chosenFeatures) {
				List<PluginElementGenerator> allPluginsDependencies = depAnalyzer.getPluginsDependencies(chosenFeature);
				if (allPluginsDependencies != null) {
					for (PluginElementGenerator depPlugin : allPluginsDependencies) {
						// Avoid duplicates dependencies in the plugins list
						if (!pluginsList.contains(depPlugin)) {
							pluginsList.add(depPlugin);
						}
					}
				}
			}

			List<PluginElementGenerator> pluginsWithoutAnyFeatureDependencies = depAnalyzer
					.getPluginsWithoutAnyFeaturesDependencies();
			pluginsList.addAll(pluginsWithoutAnyFeatureDependencies);

			if (!noOutputOnlyStatistics) {
				try {
					// Create all dirs and copy features and plugins
					File output_variantFile = new File(output_variant);
					output_variantFile.mkdirs();

					for (File file_eclipse : eclipse.listFiles()) {
						// Copy eclipse files & dirs (except features & plugins)
						if (!file_eclipse.getName().equals(VariantsUtils.FEATURES)
								&& !file_eclipse.getName().equals(VariantsUtils.PLUGINS)) {
							FileAndDirectoryUtils.copyFilesAndDirectories(output_variantFile, file_eclipse);
						}
					}

					// features copy
					File[] allFilesFeatures = new File[chosenFeatures.size()];
					for (int j = 0; j < chosenFeatures.size(); j++) {
						allFilesFeatures[j] = new File(depAnalyzer.getPathFromFeature(chosenFeatures.get(j)));
					}
					FileAndDirectoryUtils.copyFilesAndDirectories(new File(output_variantFile, VariantsUtils.FEATURES),
							allFilesFeatures);

					// plugins copy
					File[] allFilesPlugins = new File[pluginsList.size()];
					for (int j = 0; j < pluginsList.size(); j++) {
						allFilesPlugins[j] = new File(pluginsList.get(j).getAbsolutePath());
					}
					FileAndDirectoryUtils.copyFilesAndDirectories(new File(output_variant, VariantsUtils.PLUGINS),
							allFilesPlugins);
				} catch (Exception e) {
					e.printStackTrace();
				}

				File output_VariantFile = new File(output_variant);

				if (!keepOnlyMetadata) {
					// This call adapter construct mainly to fix the bundle.info
					// configuration file to have a functional eclipse
					List<IElement> allElements = new ArrayList<IElement>();
					allElements.addAll(allFileElements);
					allElements.addAll(pluginsList);

					URI outputUri = output_VariantFile.toURI();
					adapter.construct(outputUri, allElements, new NullProgressMonitor());
				}

				if (keepOnlyMetadata) {
					// We keep only manifests, properties and xmls
					EclipseKeepOnlyMetadata.cleanAndKeepOnlyMetadata(output_VariantFile);
				}
			}

			long stopTimeThisVariant = System.currentTimeMillis();
			long elapsedTimeThisVariant = stopTimeThisVariant - startTimeThisVariant;

			sendToAll(i + ";Variant_" + i + ";" + chosenFeatures.size() + ";" + pluginsList.size() + ";"
					+ elapsedTimeThisVariant);

		} // end of variants loop

		long stopTime = System.currentTimeMillis();
		long elapsedTime = stopTime - startTime;
		sendToAll("\nGeneration finished ! Miliseconds: " + elapsedTime);
	}

	@Override
	public void addListener(IListener listener) {
		if (listeners == null) {
			listeners = new ArrayList<IListener>();
		}
		listeners.add(listener);
	}

	@Override
	public void sendToAll(String msg) {
		if (msg != null && listeners != null && !listeners.isEmpty()) {
			for (IListener oneListener : listeners) {
				oneListener.receive(msg);
			}
		}
	}

}
