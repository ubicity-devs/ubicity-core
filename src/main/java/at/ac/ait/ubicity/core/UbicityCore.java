/**
    Copyright (C) 2014  AIT / Austrian Institute of Technology
    http://www.ait.ac.at

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see http://www.gnu.org/licenses/agpl-3.0.html
 */
package at.ac.ait.ubicity.core;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.xeoh.plugins.base.Plugin;
import net.xeoh.plugins.base.impl.PluginManagerFactory;
import net.xeoh.plugins.base.util.PluginManagerUtil;

import org.apache.log4j.Logger;

import at.ac.ait.ubicity.commons.broker.JiTBroker;
import at.ac.ait.ubicity.commons.interfaces.JiTConsumerPlugin;
import at.ac.ait.ubicity.commons.interfaces.JiTDispatcher;
import at.ac.ait.ubicity.commons.interfaces.UbicityPlugin;
import at.ac.ait.ubicity.commons.jit.Action;
import at.ac.ait.ubicity.commons.jit.Answer;
import at.ac.ait.ubicity.commons.jit.Answer.Status;
import at.ac.ait.ubicity.commons.util.PropertyLoader;

public class UbicityCore implements JiTDispatcher {

	private static final Logger logger = Logger.getLogger(UbicityCore.class);

	protected final static PluginManagerUtil pluginManager = PluginManagerFactory.createPluginManagerX();

	protected static List<URI> pluginURIList = new ArrayList<URI>();

	private UbicityCore() {
		PropertyLoader config = new PropertyLoader(UbicityCore.class.getResource("/core.cfg"));

		String[] pluginPath = config.getStringArray("core.plugins.directory");

		for (String path : pluginPath) {
			pluginURIList.add(new File(path).toURI());
		}

		// set Core as dispatcher
		JiTBroker.registerDispatcher(this);

		// register a shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(new CoreShutdownHook()));
	}

	public void start() {
		for (URI pluginURI : pluginURIList) {
			loadNewPlugins(pluginURI);
		}
	}

	@Override
	public Answer distribute(Action action) {

		// Handle commands with core functionality
		if (action.getReceiver().equalsIgnoreCase("ubicity-core")) {
			return process(action);
		} else {
			for (UbicityPlugin p : getPlugins(JiTConsumerPlugin.class)) {
				// Check if the intendend receiver matches the plugin name
				if (action.getReceiver().equalsIgnoreCase(p.getName())) {
					return ((JiTConsumerPlugin) p).process(action);
				}
			}
		}

		return new Answer(action, Status.COMMAND_NOT_RECOGNIZED);
	}

	/**
	 * JiT functionality on core level.
	 * 
	 * @param action
	 * @return
	 */
	private Answer process(Action action) {

		if ("list".equalsIgnoreCase(action.getCommand())) {
			List<String> pluginNames = new ArrayList<String>();
			for (UbicityPlugin p : getPlugins(UbicityPlugin.class)) {
				pluginNames.add(p.getName());
			}
			return new Answer(action, Status.PROCESSED, pluginNames.toString());

		}
		return new Answer(action, Status.COMMAND_NOT_RECOGNIZED);
	}

	/**
	 * Checks URI for new Plugins and set manatory fields.
	 * 
	 * @return
	 */
	private void loadNewPlugins(URI path) {
		pluginManager.addPluginsFrom(path);
	}

	/**
	 * Returns all Plugins which are loaded.
	 * 
	 * @return
	 */
	private static List<UbicityPlugin> getPlugins(Class<?> inst) {

		List<UbicityPlugin> plugList = new ArrayList<UbicityPlugin>();
		for (Plugin plugin : pluginManager.getPlugins()) {
			if (inst.isAssignableFrom(plugin.getClass())) {
				plugList.add((UbicityPlugin) plugin);
			}
		}

		return plugList;
	}

	/**
	 * Mainly here for debugging purposes.
	 * 
	 * @param args
	 */
	public final static void main(String[] args) {
		UbicityCore c = new UbicityCore();
		c.start();
	}

	/**
	 * Can only be called by an instance of CoreShutdownHook. Prepares an orderly Core exit.
	 * 
	 * @param _caller
	 *            the CoreShutdownHook calling us.
	 */
	static synchronized void prepareShutdown(final CoreShutdownHook _caller) {
		if (_caller != null) {
			try {
				for (UbicityPlugin p : getPlugins(UbicityPlugin.class)) {
					p.shutdown();
				}

				pluginManager.shutdown();

			} catch (Exception | Error e) {
				logger.fatal("caught some problem while preparing shutdown", e);
			}
		}
	}
}

final class CoreShutdownHook implements Runnable {
	private static final Logger logger = Logger.getLogger(CoreShutdownHook.class.getName());

	@Override
	public void run() {
		logger.warn("Shutting down core");
		UbicityCore.prepareShutdown(this);
	}
}
