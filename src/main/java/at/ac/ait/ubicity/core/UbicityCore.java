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

import at.ac.ait.ubicity.commons.interfaces.JiTPlugin;
import at.ac.ait.ubicity.commons.interfaces.UbicityPlugin;
import at.ac.ait.ubicity.commons.protocol.Answer;
import at.ac.ait.ubicity.commons.protocol.Command;
import at.ac.ait.ubicity.commons.protocol.Medium;
import at.ac.ait.ubicity.commons.util.PropertyLoader;

/**
 *
 * @author Jan van Oort
 * @version 0.1
 * 
 *          This class is the core of the ubicity platform. It does not perform
 *          actual data production, nor does it process any data. Core is a
 *          rather dumb plugin platform, after the K.I.S.S. principle, where
 *          classes implementing the UbicityPlugin interface may register
 *          themselves and <b>offer</b> JSONObjects. These JSONObjects are then
 *          placed -- by a separate JSONProcuer thread dedicated to the plugin
 *          -- into an ArrayBlockingQueue, of which there is one per plugin. A
 *          JSONConsumer thread, also dedicated to the plugin, then takes the
 *          JSONObject and offers it to elasticsearch for indexing.
 *
 */
public final class UbicityCore implements Runnable {

	private static final Logger logger = Logger.getLogger(UbicityCore.class);

	protected final static PluginManagerUtil pluginManager = PluginManagerFactory
			.createPluginManagerX();

	protected static List<URI> pluginURIList = new ArrayList<URI>();

	private UbicityCore() {
		PropertyLoader config = new PropertyLoader(
				UbicityCore.class.getResource("/core.cfg"));

		String[] pluginPath = config.getStringArray("core.plugins.directory");

		for (String path : pluginPath) {
			pluginURIList.add(new File(path).toURI());
		}

		// register a shutdown hook
		Runtime.getRuntime()
				.addShutdownHook(new Thread(new CoreShutdownHook()));
	}

	@Override
	public void run() {
		while (true) {
			try {
				for (URI pluginURI : pluginURIList) {
					pluginManager.addPluginsFrom(pluginURI);
					Thread.sleep(1000);
				}

			} catch (InterruptedException _interrupt) {
				Thread.interrupted();
			} catch (Error ee) {
				logger.fatal("caught an Error while running : " + ee.toString());
			} catch (RuntimeException e) {
				logger.fatal("caught a runtime exception while running", e);
			}
		}
	}

	/**
	 * Forwards the command to responsible plugin.
	 * 
	 * @param _command
	 * @return
	 */
	public static Answer forward(Command _command) {
		List<Medium> list = _command.getMedia().get();

		for (int i = 0; i < list.size(); i++) {
			for (UbicityPlugin p : getAllPlugins()) {
				if (p instanceof JiTPlugin) {
					JiTPlugin plug = (JiTPlugin) p;
					if (plug.isResponsible(list.get(i))) {
						return plug.execute(_command);
					}
				}
			}
		}
		return Answer.FAIL;
	}

	/**
	 * Returns all Plugins which are loaded.
	 * 
	 * @return
	 */
	private static List<UbicityPlugin> getAllPlugins() {

		List<UbicityPlugin> plugList = new ArrayList<UbicityPlugin>();

		for (Plugin plugin : pluginManager.getPlugins()) {

			if (plugin instanceof UbicityPlugin) {
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
		Thread coreThread = new Thread(c);
		coreThread.start();
	}

	/**
	 * Can only be called by an instance of CoreShutdownHook. Prepares an
	 * orderly Core exit.
	 * 
	 * @param _caller
	 *            the CoreShutdownHook calling us.
	 */
	static synchronized void prepareShutdown(final CoreShutdownHook _caller) {
		if (_caller != null) {
			try {
				for (UbicityPlugin p : getAllPlugins()) {
					if (UbicityPlugin.class.isInstance(p)) {
						p.shutdown();
					}
				}

				pluginManager.shutdown();

			} catch (Exception | Error e) {
				logger.fatal("caught some problem while preparing shutdown", e);
			}
		}
	}
}

final class CoreShutdownHook implements Runnable {

	private static final Logger logger = Logger
			.getLogger(CoreShutdownHook.class.getName());

	@Override
	public void run() {
		logger.warn("Shutting down core");
		UbicityCore.prepareShutdown(this);
	}
}
