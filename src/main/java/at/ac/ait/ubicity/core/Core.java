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
import java.util.concurrent.ConcurrentHashMap;

import net.xeoh.plugins.base.PluginManager;
import net.xeoh.plugins.base.impl.PluginManagerFactory;

import org.apache.log4j.Logger;

import at.ac.ait.ubicity.commons.broker.BrokerConsumer;
import at.ac.ait.ubicity.commons.broker.events.EventEntry;
import at.ac.ait.ubicity.commons.interfaces.BaseUbicityPlugin;
import at.ac.ait.ubicity.commons.interfaces.ReverseControllableMediumPlugin;
import at.ac.ait.ubicity.commons.interfaces.UbicityAddOn;
import at.ac.ait.ubicity.commons.interfaces.UbicityPlugin;
import at.ac.ait.ubicity.commons.protocol.Answer;
import at.ac.ait.ubicity.commons.protocol.Command;
import at.ac.ait.ubicity.commons.protocol.Medium;
import at.ac.ait.ubicity.commons.util.PropertyLoader;
import at.ac.ait.ubicity.core.jit.JitIndexingController;

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
public final class Core implements Runnable {

	private static final Logger logger = Logger.getLogger(Core.class);

	protected final ConcurrentHashMap<Integer, BaseUbicityPlugin> plugins = new ConcurrentHashMap<Integer, BaseUbicityPlugin>();

	private static Core singleton;

	protected final PluginManager pluginManager = PluginManagerFactory
			.createPluginManager();

	protected List<URI> pluginURIList = new ArrayList<URI>();

	private Core() {

		PropertyLoader config = new PropertyLoader(
				Core.class.getResource("/core.cfg"));

		String[] pluginPath = config.getStringArray("core.plugins.directory");

		for (String path : pluginPath) {
			pluginURIList.add(new File(path).toURI());
		}

		singleton = this;
		// register a shutdown hook
		Runtime.getRuntime().addShutdownHook(
				new Thread(new CoreShutdownHook(this)));

		// start the jit controller in order for the back end to react upon JIT
		// indexing requests
		logger.info("starting JitIndexingController");
		JitIndexingController jitController = new JitIndexingController();
		Thread jitThread = new Thread(jitController);
		jitThread.setPriority(Thread.MAX_PRIORITY - 1);
		jitThread.start();
		logger.info("successfully started JitIndexingController");

	}

	public static Core getInstance() {

		if (singleton == null)
			singleton = new Core();
		return singleton;
	}

	@Override
	/**
	 *  Cycle for ever, with three-second sleeping periods, and look for new plugins
	 *  @TODO : solve a recurring cross-platform problem: this method may throw DuplicateRealmException 
	 *  on one platform, but not on the other ( e.g. Windows: fine, Linux: DRE ) 
	 */
	public void run() {
		while (true) {
			try {

				for (URI pluginURI : pluginURIList) {
					pluginManager.addPluginsFrom(pluginURI);
				}

				Thread.sleep(3000);
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

	/**
	 * 
	 * @param plugin
	 *            The plugin to register. A Plugin **MUST** call this method in
	 *            order to be recognized by the Core and benefit from its access
	 *            to elasticsearch. It **IS** possible to run as a plugin
	 *            without calling this method; the plugin is then, basically, an
	 *            isolated POJO or - if it wishes to implement the Runnable
	 *            interface - an isolated Thread with the same JVM as the Core.
	 * @return true if registering went OK, false in case of a problem.
	 */
	public synchronized final boolean register(BaseUbicityPlugin plugin) {
		try {

			plugins.put(plugin.hashCode(), plugin);

			if (UbicityAddOn.class.isInstance(plugin)) {
				UbicityBroker.register(((BrokerConsumer) plugin));
			}

			logger.info(plugin.getName() + " registered");

			return true;
		} catch (Exception e) {
			logger.warn("caught an exception while trying to register plugin "
					+ plugin.getName() + " : " + e.toString());
			return false;
		} catch (Error ee) {
			logger.warn("caught an error while trying to register plugin "
					+ plugin.getName() + ": " + ee.toString());
			return false;
		}
	}

	/**
	 * 
	 * @param plugin
	 *            The plugin to deregister
	 * @return boolean - in principle, always return true;
	 * 
	 */
	public final void deRegister(BaseUbicityPlugin plugin) {
		try {
			if (plugins.containsKey(plugin.hashCode())) {
				logger.info(plugin.getName() + " deregistered");

				plugins.remove(plugin.hashCode());

				if (UbicityAddOn.class.isInstance(plugin)) {
					UbicityBroker.deRegister((BrokerConsumer) plugin);
				}

				plugin.shutdown();
			}
		} catch (Error ee) {
			logger.fatal(
					"caught an Error while trying to deregister "
							+ plugin.getName(), ee);
		}
	}

	public Answer forward(Command _command) {
		logger.info("got a Command forwarded::" + _command.toRESTString());
		List<Medium> list = _command.getMedia().get();

		for (int i = 0; i < list.size(); i++) {
			Medium m = list.get(i);
			for (BaseUbicityPlugin p : plugins.values()) {
				if (p instanceof ReverseControllableMediumPlugin)
					return ((ReverseControllableMediumPlugin) p)
							.execute(_command);
			}
		}
		return Answer.FAIL;
	}

	/**
	 * Mainly here for debugging purposes.
	 * 
	 * @param args
	 */
	public final static void main(String[] args) {
		Core c = Core.getInstance();
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

				// first deregister all plugins
				for (BaseUbicityPlugin p : plugins.values()) {
					if (UbicityPlugin.class.isInstance(p)) {
						deRegister(p);
					}
				}

				// Deregister all addOns
				for (BaseUbicityPlugin p : plugins.values()) {
					if (UbicityAddOn.class.isInstance(p)) {
						deRegister(p);
					}
				}

			} catch (Exception | Error e) {
				logger.fatal(
						"caught some problem while preparing shutdown :: ", e);
			}
		}
	}

	/**
	 * Send EventEntry to configured consumers
	 * 
	 * @param event
	 * @throws UbicityBrokerException
	 */
	public void publish(EventEntry event) throws UbicityBrokerException {
		UbicityBroker.publish(event);
	}
}

final class CoreShutdownHook implements Runnable {

	// although the Core is accessible over getInstance(), pre-register in order
	// to speed up operations;
	// also, we are not certain that getInstance() is still replying under
	// certain apocalyptic cirumstances...
	private final Core core;

	private static final Logger logger = Logger
			.getLogger(CoreShutdownHook.class.getName());

	public CoreShutdownHook(Core _core) {
		core = _core;
	}

	@Override
	public void run() {
		logger.warn(this.getClass().getName()
				+ " :  calling prepareShutdown() on Core ");
		core.prepareShutdown(this);
	}
}
