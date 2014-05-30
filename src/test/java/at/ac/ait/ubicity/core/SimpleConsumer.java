package at.ac.ait.ubicity.core;

import at.ac.ait.ubicity.commons.broker.BrokerConsumer;
import at.ac.ait.ubicity.commons.broker.events.EventEntry;

public class SimpleConsumer extends BrokerConsumer {

	private final String name;

	public SimpleConsumer(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void onReceived(EventEntry event) {
		System.out.println(event.getId() + " - " + event.getData());
	}
}