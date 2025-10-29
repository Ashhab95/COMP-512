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




public class Paxos {
	GCL gcl;
	FailCheck failCheck;
	private Logger logger;
	private String myProcess;           // e.g., "localhost:4010"
	private String[] allProcesses;      // All processes in the group
	private int processId;              // My index: 0, 1, 2...
	private int majority;               // How many for majority? (n/2)+1

	private AtomicInteger currentPosition;     // Next position to propose
	private AtomicInteger proposalCounter;     // Counter for generating unique proposal #s
	private AtomicInteger nextDeliveryPosition;
	private ConcurrentHashMap<Integer, Object> pendingDeliveries;

	private ConcurrentHashMap<Integer, InstanceState> instances; 	// Per-position state (one entry per Paxos instance)

	private BlockingQueue<Object> deliveryQueue; // Values that have been accepted and ready for delivery (in order)
	private BlockingQueue<PendingProposal> pendingProposals;// Values waiting to be proposed

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

		this.nextDeliveryPosition = new AtomicInteger(1);  // For ordered delivery
		this.pendingDeliveries = new ConcurrentHashMap<>();


		this.running = true;

		this.receiverThread = new Thread(new ReceiverTask(), "Paxos-Receiver");
		this.proposerThread = new Thread(new ProposerTask(), "Paxos-Proposer");
		receiverThread.start();
		proposerThread.start();

		logger.info("Paxos threads started");
	}

	// This is what the application layer is going to call to send a message/value, such as the player and the move
	public void broadcastTOMsg(Object val) {
		logger.info("broadcastTOMsg called with value: " + val);

		// Create wrapper with completion latch
		PendingProposal proposal = new PendingProposal(val);

		// Queue for proposer thread
		try {
			pendingProposals.put(proposal);
			logger.fine("Queued proposal for consensus");
		} catch (InterruptedException e) {
			logger.warning("Interrupted while queuing proposal");
			return;
		}

		// BLOCK until majority accepts
		try {
			logger.fine("Waiting for consensus to complete...");
			proposal.completionLatch.await();
			logger.info("Consensus complete! Returning from broadcastTOMsg");
		} catch (InterruptedException e) {
			logger.warning("Interrupted while waiting for consensus");
		}
	}

	// This is what the application layer is calling to figure out what is the next message in the total order.
	// Messages delivered in ALL the processes in the group should deliver this in the same order.
	public Object acceptTOMsg() throws InterruptedException {
		// Simply read from the ordered delivery queue
		Object value = deliveryQueue.take();
		logger.fine("acceptTOMsg returning: " + value);
		return value;
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

		// Failure point: immediately when receiving propose
		failCheck.checkFailure(FailCheck.FailureType.RECEIVEPROPOSE);

		// Get state for this position
		InstanceState state = getInstanceState(msg.position);

		synchronized (state) {
			// Check if this proposal number is higher than any we've promised
			if (msg.proposalNum > state.promisedProposalNum) {
				// Accept this prepare - update our promise
				state.promisedProposalNum = msg.proposalNum;

				logger.info("Promising to proposal #" + msg.proposalNum +
						" for position " + msg.position);

				// Send PROMISE back with any previously accepted value
				PromiseMsg promise = new PromiseMsg(
						msg.position,
						msg.proposalNum,
						state.acceptedValue,           // null if we haven't accepted anything
						state.acceptedProposalNum      // -1 if we haven't accepted anything
				);

				gcl.sendMsg(promise, sender);

				// Failure point: after sending vote
				failCheck.checkFailure(FailCheck.FailureType.AFTERSENDVOTE);

				logger.fine("Sent PROMISE to " + sender + " for position " + msg.position);

			} else {
				// Reject - this proposal number is too low
				logger.fine("Rejecting PREPARE from " + sender +
						" (proposal #" + msg.proposalNum +
						" <= promised #" + state.promisedProposalNum + ")");
				// We just ignore it (don't send anything back)
			}
		}
	}

	private void handlePromise(PromiseMsg msg, String sender) {
		logger.fine("handlePromise from " + sender + ": " + msg);

		// Get state for this position
		InstanceState state = getInstanceState(msg.position);

		synchronized (state) {
			// Add this sender to our set of promises
			boolean isNew = state.promisesReceived.add(sender);

			if (!isNew) {
				// Already got a promise from this sender, ignore duplicate
				logger.fine("Duplicate promise from " + sender + ", ignoring");
				return;
			}

			logger.info("Received PROMISE from " + sender +
					" for position " + msg.position +
					" (total: " + state.promisesReceived.size() + "/" + allProcesses.length + ")");

			// Check if they had a previously accepted value
			if (msg.acceptedValue != null &&
					msg.acceptedProposalNum > state.acceptedProposalNum) {
				// This promise contains a value that was previously accepted
				// We MUST use this value instead of our own (Paxos rule!)
				state.acceptedValue = msg.acceptedValue;
				state.acceptedProposalNum = msg.acceptedProposalNum;

				logger.info("Promise contains previously accepted value: " +
						msg.acceptedValue +
						" (from proposal #" + msg.acceptedProposalNum + ")");
			}

			// Check if we have majority
			if (state.promisesReceived.size() >= majority) {
				logger.info("*** MAJORITY PROMISES RECEIVED for position " + msg.position +
						" (" + state.promisesReceived.size() + "/" + allProcesses.length + ") ***");

				// Signal runPhase1 that we got majority
				if (state.promiseLatch != null) {
					state.promiseLatch.countDown();
				}

				// Failure point: after becoming leader
				failCheck.checkFailure(FailCheck.FailureType.AFTERBECOMINGLEADER);
			}
		}
	}

	private void handlePropose(ProposeMsg msg, String sender) {
		logger.fine("handlePropose from " + sender + ": " + msg);

		InstanceState state = getInstanceState(msg.position);

		synchronized (state) {
			if (msg.proposalNum >= state.promisedProposalNum) {
				state.acceptedValue = msg.value;
				state.acceptedProposalNum = msg.proposalNum;

				logger.info("ACCEPTING value for position " + msg.position +
						" with proposal #" + msg.proposalNum +
						", value: " + msg.value);

				AcceptMsg accept = new AcceptMsg(msg.position, msg.proposalNum, msg.value);
				gcl.sendMsg(accept, sender);

				logger.fine("Sent ACCEPT to " + sender + " for position " + msg.position);

				if (!state.delivered) {
					state.delivered = true;
					deliverValue(msg.position, msg.value);

					// ✅ FIX: Update currentPosition when accepting from others
					currentPosition.updateAndGet(current -> Math.max(current, msg.position + 1));
					logger.fine("Updated currentPosition to " + currentPosition.get());
				}

			} else {
				logger.fine("Rejecting PROPOSE from " + sender +
						" (proposal #" + msg.proposalNum +
						" < promised #" + state.promisedProposalNum + ")");
			}
		}
	}

	private void handleAccept(AcceptMsg msg, String sender) {
		logger.fine("handleAccept from " + sender + ": " + msg);

		// Get state for this position
		InstanceState state = getInstanceState(msg.position);

		synchronized (state) {
			// Add this sender to our set of accepts
			boolean isNew = state.acceptsReceived.add(sender);

			if (!isNew) {
				// Already got an accept from this sender, ignore duplicate
				logger.fine("Duplicate accept from " + sender + ", ignoring");
				return;
			}

			logger.info("Received ACCEPT from " + sender +
					" for position " + msg.position +
					" (total: " + state.acceptsReceived.size() + "/" + allProcesses.length + ")");

			// Check if we have majority
			if (state.acceptsReceived.size() >= majority) {
				logger.info("*** MAJORITY ACCEPTS RECEIVED for position " + msg.position +
						" (" + state.acceptsReceived.size() + "/" + allProcesses.length + ") ***");

				// Signal runPhase2 that we got majority
				if (state.acceptLatch != null) {
					state.acceptLatch.countDown();
				}
			}
		}
	}

	private boolean runConsensus(int position, Object value) {
		int attempt = 0;

		// Keep trying until success (or shutdown)
		while (running) {
			attempt++;
			try {
				logger.fine("Consensus attempt " + attempt + " for position " + position);

				int proposalNum = generateProposalNumber();

				// PHASE 1: Prepare/Promise
				logger.fine("Phase 1: Sending PREPARE with proposal #" + proposalNum);

				boolean phase1Success = runPhase1(position, proposalNum);

				if (!phase1Success) {
					logger.fine("Phase 1 failed, retrying with higher proposal number");
					Thread.sleep(50 * Math.min(attempt, 10)); // Exponential backoff with cap
					continue;
				}

				// PHASE 2: Propose/Accept
				InstanceState state = getInstanceState(position);
				Object valueToPropose;

				synchronized (state) {
					if (state.acceptedValue != null) {
						valueToPropose = state.acceptedValue;
						logger.info("Using previously accepted value: " + valueToPropose);
					} else {
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
					Thread.sleep(50 * Math.min(attempt, 10));
				}

			} catch (InterruptedException e) {
				if (!running) {
					logger.warning("Consensus interrupted during shutdown");
					return false;
				}
				logger.warning("Consensus interrupted, continuing...");
			}
		}

		logger.warning("Consensus stopped due to shutdown for position " + position);
		return false;
	}

	private boolean runPhase1(int position, int proposalNum) {
		logger.fine("runPhase1: position=" + position + ", propNum=" + proposalNum);

		// Get or create state for this position
		InstanceState state = getInstanceState(position);

		// Reset state for this phase
		synchronized (state) {
			state.promisesReceived.clear();
			state.acceptedValue = null;      // Will be updated from promises
			state.acceptedProposalNum = -1;

			// Create latch to wait for majority
			state.promiseLatch = new CountDownLatch(1);
		}

		// Create and send PREPARE message to ALL processes
		PrepareMsg prepare = new PrepareMsg(position, proposalNum);
		gcl.broadcastMsg(prepare);

		// Failure point: after sending proposal to become leader
		failCheck.checkFailure(FailCheck.FailureType.AFTERSENDPROPOSE);

		logger.info("Sent PREPARE for position " + position + " with proposal #" + proposalNum);

		// Wait for majority (with timeout)
		try {
			boolean gotMajority = state.promiseLatch.await(3, TimeUnit.SECONDS);

			if (gotMajority) {
				logger.info("Phase 1 SUCCESS: Got majority promises for position " + position);
				return true;
			} else {
				logger.warning("Phase 1 TIMEOUT: Didn't get majority promises for position " + position);
				return false;
			}

		} catch (InterruptedException e) {
			logger.warning("Phase 1 interrupted");
			return false;
		}
	}

	private boolean runPhase2(int position, int proposalNum, Object value) {
		logger.fine("runPhase2: position=" + position +
				", propNum=" + proposalNum + ", value=" + value);

		// Get state for this position
		InstanceState state = getInstanceState(position);

		// Reset state for this phase
		synchronized (state) {
			state.acceptsReceived.clear();

			// Create latch to wait for majority
			state.acceptLatch = new CountDownLatch(1);
		}

		// Create and send PROPOSE message to ALL processes
		ProposeMsg propose = new ProposeMsg(position, proposalNum, value);
		gcl.broadcastMsg(propose);

		logger.info("Sent PROPOSE for position " + position +
				" with proposal #" + proposalNum + ", value: " + value);

		// Wait for majority (with timeout)
		try {
			boolean gotMajority = state.acceptLatch.await(3, TimeUnit.SECONDS);

			if (gotMajority) {
				logger.info("Phase 2 SUCCESS: Got majority accepts for position " + position);

				// Failure point: after majority accepted the value
				failCheck.checkFailure(FailCheck.FailureType.AFTERVALUEACCEPT);

				return true;
			} else {
				logger.warning("Phase 2 TIMEOUT: Didn't get majority accepts for position " + position);
				return false;
			}

		} catch (InterruptedException e) {
			logger.warning("Phase 2 interrupted");
			return false;
		}
	}

	private void deliverInOrder() {
		while (true) {
			int nextPos = nextDeliveryPosition.get();
			Object value = pendingDeliveries.get(nextPos);

			if (value == null) {
				// Gap in sequence - can't deliver yet
				logger.fine("Waiting for position " + nextPos + " before delivering more");
				break;
			}

			// We have the next value in sequence!
			try {
				deliveryQueue.put(value);
				pendingDeliveries.remove(nextPos);
				nextDeliveryPosition.incrementAndGet();

				logger.info("DELIVERED position " + nextPos + " to application: " + value);

			} catch (InterruptedException e) {
				logger.warning("Interrupted while delivering value");
				break;
			}
		}
	}
	private void deliverValue(int position, Object value) {
		logger.info("Value CHOSEN for position " + position + ": " + value);

		synchronized (pendingDeliveries) {
			// Store this value
			pendingDeliveries.put(position, value);

			// Try to deliver as many in-order values as possible
			deliverInOrder();
		}
	}

	private InstanceState getInstanceState(int position) {
		return instances.computeIfAbsent(position, k -> new InstanceState());
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
					PendingProposal proposal = pendingProposals.take();

					int position = currentPosition.get();
					logger.info("Starting consensus for position " + position +
							", value: " + proposal.value);

					// Run consensus - this now retries indefinitely until success
					boolean success = runConsensus(position, proposal.value);

					if (success) {
						logger.info("Consensus SUCCESS for position " + position);

						// Move to next position
						currentPosition.incrementAndGet();

						// Signal the waiting broadcastTOMsg call
						proposal.completionLatch.countDown();
					} else {
						// This only happens during shutdown
						logger.warning("Consensus stopped for position " + position);
						proposal.completionLatch.countDown(); // Unblock the caller
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
	private class PendingProposal {
		Object value;
		CountDownLatch completionLatch;

		PendingProposal(Object value) {
			this.value = value;
			this.completionLatch = new CountDownLatch(1);
		}
	}
}

