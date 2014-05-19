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
package at.ac.ait.ubicity.core.jit;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.logging.Logger;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

/**
 *
 * @author jan
 */
public final class JitIndexingController implements Runnable {

	private static final Logger logger = Logger
			.getLogger(JitIndexingController.class.getName());

	private static int PORT;
	static {
		try {
			// set necessary stuff for us to ueberhaupt be able to work
			Configuration config = new PropertiesConfiguration("commons.cfg");
			PORT = config.getInt("commons.plugins.reverse_cac_port");

		} catch (ConfigurationException noConfig) {
			logger.severe("could not configure from commons.cfg file");
		}
	}

	protected ServerSocket listenSocket;

	protected ThreadGroup threadGroup;

	protected Vector<Connection> connections;

	protected Vulture vulture;

	public final static void fail(Throwable e, String msg) {
		logger.severe(msg + ":" + e);
	}

	public JitIndexingController(int _port) {

		if (_port == 0)
			_port = PORT;
		try {
			listenSocket = new ServerSocket(_port);
		} catch (Exception | Error e) {
			fail(e, "Could not create server-side socket on ubicity core");
		}
		threadGroup = new ThreadGroup(
				"ubicity JitIndexingController connections");
		connections = new Vector<Connection>();
		vulture = new Vulture(this);

	}

	public JitIndexingController() {
		this(PORT);
	}

	/**
	 * Loop forever, listening for and acceptino connections from clients. For
	 * each connection, create a Connection object to handle communication
	 * through the new Socket. When we create a new connection, add it to the
	 * Vector of connections. The Vulture will dispose of dead connections.
	 */
	@Override
	public void run() {
		try {
			while (true) {
				Socket client = listenSocket.accept();
				Connection c = new Connection(client, threadGroup, 3, vulture);
				synchronized (connections) {
					connections.addElement(c);
				}
			}
		} catch (IOException e) {
			fail(e, "Exception while listening for connections");
		}
	}

	public final static void main(String... args) {

		Thread t;

		if (args.length == 1) {
			int port = Integer.parseInt(args[0]);
			t = new Thread(new JitIndexingController(port));
		} else {
			t = new Thread(new JitIndexingController());
		}

		t.start();
	}
}
