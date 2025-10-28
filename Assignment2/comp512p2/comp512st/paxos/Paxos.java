package comp512st.paxos;

// Access to the GCL layer
import comp512.gcl.*;

import comp512.utils.*;

// Any other imports that you may need.
import java.io.*;
import java.util.logging.*;
import java.net.UnknownHostException;
import java.util.concurrent.*;
import java.util.*;
import java.util.concurrent.atomic.*;




public class Paxos
{
	GCL gcl;
	FailCheck failCheck;
	private Logger logger;
	private String myProcess;           // e.g., "localhost:4010"
	private String[] allProcesses;      // All processes in the group
	private int processId;              // My index: 0, 1, 2...
	private int majority;               // How many for majority? (n/2)+1

	private AtomicInteger currentPosition;     // Next position to propose
	private AtomicInteger proposalCounter;     // Counter for generating unique proposal #s

	private ConcurrentHashMap<Integer, InstanceState> instances; 	// Per-position state (one entry per Paxos instance)

	private BlockingQueue<Object> deliveryQueue; // Values that have been accepted and ready for delivery (in order)
	private BlockingQueue<Object> pendingProposals; // Values waiting to be proposed

	private Thread receiverThread;      // Reads from GCL and handles messages
	private Thread proposerThread;      // Runs Paxos consensus
	private volatile boolean running;   // Control flag for threads



	public Paxos(String myProcess, String[] allGroupProcesses, Logger logger, FailCheck failCheck) throws IOException, UnknownHostException
	{
		this.failCheck = failCheck;
		this.logger = logger;
		this.myProcess = myProcess;
		this.allProcesses = allGroupProcesses;

		// Initialize the GCL communication system as well as anything else you need to.
		this.gcl = new GCL(myProcess, allGroupProcesses, null, logger) ;

		// Calculate my ProcessID
		this.processId = -1;
		for (int i = 0; i < allGroupProcesses.length; i++) {
			if (allGroupProcesses[i].equals(myProcess)) {
				this.processId = i;
				break;
			}
		}
		if (this.processId == -1) {
			throw new IllegalArgumentException("My process not found in group: " + myProcess);
		}
		//Calculate majority
		this.majority = (allGroupProcesses.length / 2) + 1;

		logger.info("Paxos initialized: processId=" + processId +
				", majority=" + majority + "/" + allGroupProcesses.length);

		this.currentPosition = new AtomicInteger(1);
		this.proposalCounter = new AtomicInteger(0);
		this.instances = new ConcurrentHashMap<>();
		this.deliveryQueue = new LinkedBlockingQueue<>();
		this.pendingProposals = new LinkedBlockingQueue<>();

		this.running = true;

		this.receiverThread = new Thread(new ReceiverTask(), "Paxos-Receiver");
		this.proposerThread = new Thread(new ProposerTask(), "Paxos-Proposer");
		receiverThread.start();
		proposerThread.start();

		logger.info("Paxos threads started");
	}

	private int generateProposalNumber() {
		int counter = proposalCounter.incrementAndGet();
		int propNum = (counter * allProcesses.length) + processId;

		logger.fine("Generated proposal number: " + propNum +
				" (counter=" + counter + ", processId=" + processId + ")");

		return propNum;
	}

	private void handlePrepare(PrepareMsg msg, String sender) {
		logger.fine("handlePrepare from " + sender + ": " + msg);

		// TODO: Implement in Step 6
		// For now, just log it
	}

	private void handlePromise(PromiseMsg msg, String sender) {
		logger.fine("handlePromise from " + sender + ": " + msg);

		// TODO: Implement in Step 6
		// For now, just log it
	}

	private void handlePropose(ProposeMsg msg, String sender) {
		logger.fine("handlePropose from " + sender + ": " + msg);

		// TODO: Implement in Step 7
		// For now, just log it
	}

	private void handleAccept(AcceptMsg msg, String sender) {
		logger.fine("handleAccept from " + sender + ": " + msg);

		// TODO: Implement in Step 7
		// For now, just log it
	}

	private boolean runConsensus(int position, Object value) {
		// Try up to 5 times with increasing proposal numbers
		for (int attempt = 1; attempt <= 5; attempt++) {
			try {
				logger.fine("Consensus attempt " + attempt + " for position " + position);

				// Generate unique proposal number
				int proposalNum = generateProposalNumber();


				// PHASE 1: Prepare/Promise
				logger.fine("Phase 1: Sending PREPARE with proposal #" + proposalNum);

				boolean phase1Success = runPhase1(position, proposalNum);

				if (!phase1Success) {
					logger.fine("Phase 1 failed, retrying with higher proposal number");
					Thread.sleep(50 * attempt); // Exponential backoff
					continue;
				}


				// PHASE 2: Propose/Accept
				// Check if we need to use a different value (from promises)
				InstanceState state = getInstanceState(position);
				Object valueToPropose;

				synchronized (state) {
					if (state.acceptedValue != null) {
						// Must use previously accepted value
						valueToPropose = state.acceptedValue;
						logger.info("Using previously accepted value: " + valueToPropose);
					} else {
						// Use our original value
						valueToPropose = value;
					}
				}

				logger.fine("Phase 2: Sending PROPOSE with value: " + valueToPropose);

				boolean phase2Success = runPhase2(position, proposalNum, valueToPropose);

				if (phase2Success) {
					logger.info("Both phases succeeded! Value chosen for position " + position);
					return true;
				} else {
					logger.fine("Phase 2 failed, retrying");
					Thread.sleep(50 * attempt); // Exponential backoff
				}

			} catch (InterruptedException e) {
				logger.warning("Consensus interrupted");
				return false;
			}
		}

		// After 5 attempts, give up for now
		logger.severe("Consensus failed after 5 attempts for position " + position);
		return false;
	}

	private boolean runPhase1(int position, int proposalNum) {
		logger.fine("runPhase1: position=" + position + ", propNum=" + proposalNum);

		// Get or create state for this position
		InstanceState state = getInstanceState(position);

		// TODO: Implement in Step 6
		// For now, just return false so we can test the structure
		logger.warning("Phase 1 not implemented yet - returning false");
		return false;
	}

	private boolean runPhase2(int position, int proposalNum, Object value) {
		logger.fine("runPhase2: position=" + position +
				", propNum=" + proposalNum + ", value=" + value);

		// Get state for this position
		InstanceState state = getInstanceState(position);

		// TODO: Implement in Step 7
		// For now, just return false so we can test the structure
		logger.warning("Phase 2 not implemented yet - returning false");
		return false;
	}

	private InstanceState getInstanceState(int position) {
		return instances.computeIfAbsent(position, k -> new InstanceState());
	}


	// This is what the application layer is going to call to send a message/value, such as the player and the move
	public void broadcastTOMsg(Object val)
	{
		// This is just a place holder.
		// Extend this to build whatever Paxos logic you need to make sure the messaging system is total order.
		// Here you will have to ensure that the CALL BLOCKS, and is returned ONLY when a majority (and immediately upon majority) of processes have accepted the value.
		gcl.broadcastMsg(val);
	}

	// This is what the application layer is calling to figure out what is the next message in the total order.
	// Messages delivered in ALL the processes in the group should deliver this in the same order.
	public Object acceptTOMsg() throws InterruptedException
	{
		// This is just a place holder.
		GCMessage gcmsg = gcl.readGCMessage();
		return gcmsg.val;
	}

	// Add any of your own shutdown code into this method.
	public void shutdownPaxos() {
		logger.info("Shutting down Paxos...");

		running = false;

		receiverThread.interrupt();
		proposerThread.interrupt();

		try {
			receiverThread.join(2000);
			proposerThread.join(2000);
		} catch (InterruptedException e) {
			logger.warning("Interrupted while waiting for threads to stop");
		}

		// Shutdown GCL
		gcl.shutdownGCL();

		logger.info("Paxos shutdown complete");
	}

	private class InstanceState {


		// ACCEPTOR ROLE: What have I promised/accepted?
		int promisedProposalNum = -1;        // Highest proposal # I've promised
		Object acceptedValue = null;         // Value I've accepted (null if none)
		int acceptedProposalNum = -1;        // Proposal # of accepted value

		// PROPOSER ROLE: Tracking responses from other processes
		Set<String> promisesReceived = new HashSet<>();   // Who sent me Promise
		Set<String> acceptsReceived = new HashSet<>();    // Who sent me Accept

		// Latches for blocking until we get majority
		CountDownLatch promiseLatch;
		CountDownLatch acceptLatch;

		// The value we're trying to propose in this instance
		Object valueToPropose;

		// Flag to ensure we only deliver once per position
		boolean delivered = false;
	}
	private class ReceiverTask implements Runnable {
		@Override
		public void run() {
			logger.info("Receiver thread started");

			while (running) {
				try {
					// Read next message from GCL (this blocks if no messages)
					GCMessage msg = gcl.readGCMessage();

					Object payload = msg.val;
					String sender = msg.senderProcess;

					logger.fine("Received message from " + sender + ": " + payload);

					// Route based on message type
					if (payload instanceof PrepareMsg) {
						handlePrepare((PrepareMsg) payload, sender);

					} else if (payload instanceof PromiseMsg) {
						handlePromise((PromiseMsg) payload, sender);

					} else if (payload instanceof ProposeMsg) {
						handlePropose((ProposeMsg) payload, sender);

					} else if (payload instanceof AcceptMsg) {
						handleAccept((AcceptMsg) payload, sender);

					} else {
						logger.warning("Unknown message type: " + payload.getClass().getName());
					}

				} catch (InterruptedException e) {
					if (running) {
						logger.warning("Receiver thread interrupted unexpectedly");
					} else {
						logger.info("Receiver thread interrupted during shutdown");
					}
					break;
				}
			}

			logger.info("Receiver thread stopped");
		}
	}
	private class ProposerTask implements Runnable {
		@Override
		public void run() {
			logger.info("Proposer thread started");

			while (running) {
				try {
					// Wait for next value to propose (blocks if queue is empty)
					Object value = pendingProposals.take();

					int position = currentPosition.get();
					logger.info("Starting consensus for position " + position +
							", value: " + value);

					// Run Paxos consensus for this position
					boolean success = runConsensus(position, value);

					if (success) {
						logger.info("Consensus SUCCESS for position " + position);

						// Move to next position
						currentPosition.incrementAndGet();
					} else {
						logger.warning("Consensus FAILED for position " + position +
								", will retry");
						pendingProposals.put(value);

						Thread.sleep(100);
					}

				} catch (InterruptedException e) {
					if (running) {
						logger.warning("Proposer thread interrupted unexpectedly");
					} else {
						logger.info("Proposer thread interrupted during shutdown");
					}
					break;
				}
			}

			logger.info("Proposer thread stopped");
		}
	}
}

