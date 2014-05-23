package at.ac.ait.ubicity.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;

import at.ac.ait.ubicity.commons.broker.BrokerConsumer;
import at.ac.ait.ubicity.commons.broker.events.ConsumerPoison;
import at.ac.ait.ubicity.commons.broker.events.EventEntry;
import at.ac.ait.ubicity.commons.broker.events.Metadata;
import at.ac.ait.ubicity.core.UbicityBrokerException.BrokerMsg;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

/**
 * Global instance for holding all UbicityBrokers and management of the
 * disruptor instances.
 * 
 * @author ruggenthalerc
 *
 */
class UbicityBroker {

	private static HashMap<String, Disruptor<EventEntry>> brokers = new HashMap<String, Disruptor<EventEntry>>();

	private static Executor executor = Executors.newCachedThreadPool();

	private static int QUEUE_SIZE;

	private static final Logger logger = Logger.getLogger(UbicityBroker.class);

	static {
		try {
			Configuration config = new PropertiesConfiguration("commons.cfg");
			QUEUE_SIZE = config.getInt("commons.lmax.queue_size");

		} catch (ConfigurationException noConfig) {
			logger.fatal("Configuration not found! " + noConfig.toString());
		}

	}

	/**
	 * Registers the consumer and creates an invidual broker instance for it.
	 * 
	 * @param consumer
	 */
	@SuppressWarnings("unchecked")
	public synchronized static void register(BrokerConsumer consumer) {

		if (!brokers.containsKey(consumer.getName())) {
			// Construct the Disruptor
			Disruptor<EventEntry> disruptor = new Disruptor<>(
					() -> new EventEntry(), QUEUE_SIZE, executor);

			disruptor.handleEventsWith(consumer);
			disruptor.start();
			brokers.put(consumer.getName(), disruptor);

			logger.info("Registered consumer: " + consumer.getName());
		}
	}

	/**
	 * Deregisters the consumer and deletes the disruptor entry.
	 * 
	 * @param consumer
	 */
	public synchronized static void deRegister(BrokerConsumer consumer) {

		if (brokers.containsKey(consumer.getName())) {

			Disruptor<EventEntry> disruptor = brokers.get(consumer.getName());

			brokers.remove(consumer.getName());

			// Publish end message to signal shutdown
			publishToBroker(disruptor.getRingBuffer(), new ConsumerPoison());

			logger.info("Deregistered consumer: " + consumer.getName());

			// Stored entries are processed before the disruptor is shut down.
			disruptor.shutdown();
		}
	}

	/**
	 * Returns a list with all registered Consumers or an empty list.
	 * 
	 * @return
	 */
	public static List<String> getConsumerNames() {
		List<String> names = new ArrayList<String>();
		names.addAll(brokers.keySet());
		return names;
	}

	/**
	 * Publishes the message to all specified consumers.
	 * 
	 * @param <T>
	 * 
	 * @param <T>
	 * 
	 * @param producer
	 */
	public static void publish(EventEntry event) throws UbicityBrokerException {

		// set to current sequence
		event.incSequence();

		for (int i = 0; i < event.getCurrentMetadata().size(); i++) {

			Metadata metadata = event.getCurrentMetadata().get(i);

			if (brokers.containsKey(metadata.getDestination())) {

				RingBuffer<EventEntry> buffer = brokers.get(
						metadata.getDestination()).getRingBuffer();

				publishToBroker(buffer, event);

			} else {
				logger.fatal("Failed to publish message '" + event.getId()
						+ "' to consumer '" + metadata.getDestination() + "'");

				throw new UbicityBrokerException(
						BrokerMsg.PRODUCER_NOT_EXISTENT);
			}
		}
	}

	private static void publishToBroker(RingBuffer<EventEntry> buffer,
			EventEntry event) {

		long sequence = buffer.next();
		try {
			EventEntry e = buffer.get(sequence);
			e.copy(event);
		} finally {
			buffer.publish(sequence);
		}
	}
}
