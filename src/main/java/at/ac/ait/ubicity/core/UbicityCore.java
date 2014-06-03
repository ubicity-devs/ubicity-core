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

import at.ac.ait.ubicity.commons.interfaces.BaseUbicityPlugin;
import at.ac.ait.ubicity.commons.interfaces.ReverseControllableMediumPlugin;
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

	private static UbicityCore singleton;

	protected final PluginManagerUtil pluginManager = PluginManagerFactory
			.createPluginManagerX();

	protected List<URI> pluginURIList = new ArrayList<URI>();

	private UbicityCore() {

		PropertyLoader config = new PropertyLoader(
				UbicityCore.class.getResource("/core.cfg"));

		String[] pluginPath = config.getStringArray("core.plugins.directory");

		for (String path : pluginPath) {
			pluginURIList.add(new File(path).toURI());
		}

		singleton = this;
		// register a shutdown hook
		Runtime.getRuntime().addShutdownHook(
				new Thread(new CoreShutdownHook(this)));
	}

	public static UbicityCore getInstance() {
		if (singleton == null)
			singleton = new UbicityCore();
		return singleton;
	}

	@Override
	public void run() {
		while (true) {
			try {
				for (URI pluginURI : pluginURIList) {
					pluginManager.addPluginsFrom(pluginURI);
				}
				Thread.sleep(2000);

			} catch (InterruptedException _interrupt) {
				Thread.interrupted();
			} catch (Error ee) {
				logger.fatal("caught an Error while running : " + ee.toString());
			} catch (RuntimeException prettyBad) {
				logger.fatal("caught a runtime exception while running :: "
						+ prettyBad.toString());
			}
		}
	}

	public Answer forward(Command _command) {
		List<Medium> list = _command.getMedia().get();

		for (int i = 0; i < list.size(); i++) {
			Medium m = list.get(i);
			for (BaseUbicityPlugin p : getAllPlugins()) {
				if (p instanceof ReverseControllableMediumPlugin)
					return ((ReverseControllableMediumPlugin) p)
							.execute(_command);
			}
		}
		return Answer.FAIL;
	}

	/**
	 * Returns all Plugins which are loaded.
	 * 
	 * @return
	 */
	private List<BaseUbicityPlugin> getAllPlugins() {

		List<BaseUbicityPlugin> plugList = new ArrayList<BaseUbicityPlugin>();

		for (Plugin plugin : pluginManager.getPlugins()) {

			if (plugin instanceof BaseUbicityPlugin) {
				plugList.add((BaseUbicityPlugin) plugin);
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
		UbicityCore c = UbicityCore.getInstance();
		Thread coreThread = new Thread(c);
		coreThread.setPriority(Thread.MIN_PRIORITY + 1);
		coreThread.setName("ubicity core execution context");
		coreThread.start();
	}

	/**
	 * Can only be called by an instance of CoreShutdownHook. Prepares an
	 * orderly Core exit.
	 * 
	 * @param _caller
	 *            the CoreShutdownHook calling us.
	 */
	final synchronized void prepareShutdown(final CoreShutdownHook _caller) {
		if (_caller != null) {
			try {
				for (BaseUbicityPlugin p : getAllPlugins()) {
					if (UbicityPlugin.class.isInstance(p)) {
						p.shutdown();
					}
				}

				pluginManager.shutdown();

			} catch (Exception | Error e) {
				logger.fatal(
						"caught some problem while preparing shutdown :: ", e);
			}
		}
	}
}

final class CoreShutdownHook implements Runnable {

	private final UbicityCore core;

	private static final Logger logger = Logger
			.getLogger(CoreShutdownHook.class.getName());

	public CoreShutdownHook(UbicityCore _core) {
		core = _core;
	}

	@Override
	public void run() {
		logger.warn("Shutting down core");
		core.prepareShutdown(this);
	}
}
