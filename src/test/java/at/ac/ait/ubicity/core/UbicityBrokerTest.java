package at.ac.ait.ubicity.core;

import org.junit.Ignore;
import org.junit.Test;

import at.ac.ait.ubicity.commons.broker.BrokerConsumer;

public class UbicityBrokerTest {

	@Test
	@Ignore
	public void ConsumerProducerTest() throws UbicityBrokerException {

		BrokerConsumer cons1 = new SimpleConsumer("elasticsearch");

		SimpleProducerImpl prod = new SimpleProducerImpl("Producer");

		UbicityBroker.register(cons1);
		UbicityBroker.publish(prod.createEntry(String.valueOf(1)));

		UbicityBroker.deRegister(cons1);
	}

	@Test
	@Ignore
	public void MultiConsumerProducerTest() throws UbicityBrokerException {

		BrokerConsumer cons1 = new SimpleConsumer("elasticsearch");
		BrokerConsumer cons2 = new SimpleConsumer("cons2");
		BrokerConsumer cons3 = new SimpleConsumer("cons3");

		SimpleProducerImpl prod = new SimpleProducerImpl("Producer");

		UbicityBroker.register(cons1);

		UbicityBroker.register(cons3);

		for (int i = 0; i < 10; i++) {
			UbicityBroker.publish(prod.createEntry(String.valueOf(i)));

			if (i == 50) {
				UbicityBroker.register(cons2);
			}
			if (i == 60) {
				UbicityBroker.deRegister(cons1);
			}
			if (i == 70) {
				UbicityBroker.register(cons3);
			}
		}

		UbicityBroker.deRegister(cons2);
		UbicityBroker.deRegister(cons3);
	}

}
