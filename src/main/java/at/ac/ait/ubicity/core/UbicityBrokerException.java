package at.ac.ait.ubicity.core;

public class UbicityBrokerException extends Exception {

	private static final long serialVersionUID = 1L;

	public enum BrokerMsg {
		PRODUCER_NOT_EXISTENT
	}

	private final BrokerMsg msg;

	public UbicityBrokerException(BrokerMsg msg) {
		this.msg = msg;
	}

	public BrokerMsg getBrokerMessage() {
		return this.msg;
	}

}
