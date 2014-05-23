/**
 *  Abstract class for handling LMAX messages
 */
package at.ac.ait.ubicity.core;

import java.util.HashMap;
import java.util.UUID;

import at.ac.ait.ubicity.commons.broker.BrokerProducer;
import at.ac.ait.ubicity.commons.broker.events.ESMetadata;
import at.ac.ait.ubicity.commons.broker.events.EventEntry;
import at.ac.ait.ubicity.commons.broker.events.Metadata;
import at.ac.ait.ubicity.commons.broker.events.ESMetadata.Action;
import at.ac.ait.ubicity.commons.broker.events.ESMetadata.Properties;

public class SimpleProducerImpl implements BrokerProducer {

	private final String producerName;

	public SimpleProducerImpl(String producerName) {
		this.producerName = producerName;
	}

	@Override
	public String getName() {
		return producerName;
	}

	public EventEntry createEntry(String data) {
		String id = producerName + "-" + UUID.randomUUID();

		HashMap<Properties, String> props = new HashMap<ESMetadata.Properties, String>();
		props.put(Properties.ES_INDEX, "tweets");
		props.put(Properties.ES_TYPE, "ctype");
		Metadata meta = new ESMetadata(Action.INDEX, 1, props);

		return new EventEntry(id, meta, data);
	}
}
