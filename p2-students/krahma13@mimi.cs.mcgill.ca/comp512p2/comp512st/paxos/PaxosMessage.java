package comp512st.paxos;

import java.io.Serial;
import java.io.Serializable;

class PaxosMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    int position;      // Which position in the total order
    int proposalNum;   // Unique proposal number

    public PaxosMessage(int position, int proposalNum) {
        this.position = position;
        this.proposalNum = proposalNum;
    }

    public PaxosMessage() {}
}

class PrepareMsg extends PaxosMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    public PrepareMsg(int position, int proposalNum) {
        super(position, proposalNum);
    }

    public PrepareMsg() {}

    @Override
    public String toString() {
        return "PREPARE{pos=" + position + ", prop#=" + proposalNum + "}";
    }
}

class PromiseMsg extends PaxosMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    Object acceptedValue;      // Previously accepted value (or null)
    int acceptedProposalNum;   // Proposal # of accepted value (or -1)

    public PromiseMsg(int position, int proposalNum,
                      Object acceptedValue, int acceptedProposalNum) {
        super(position, proposalNum);
        this.acceptedValue = acceptedValue;
        this.acceptedProposalNum = acceptedProposalNum;
    }

    public PromiseMsg() {}

    @Override
    public String toString() {
        return "PROMISE{pos=" + position + ", prop#=" + proposalNum +
                ", prevAccepted=" + (acceptedValue != null) + "}";
    }
}

class ProposeMsg extends PaxosMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    Object value;  // The actual value to agree on

    public ProposeMsg(int position, int proposalNum, Object value) {
        super(position, proposalNum);
        this.value = value;
    }

    public ProposeMsg() {}

    @Override
    public String toString() {
        return "PROPOSE{pos=" + position + ", prop#=" + proposalNum +
                ", val=" + value + "}";
    }
}

class AcceptMsg extends PaxosMessage {
    private static final long serialVersionUID = 1L;

    Object value;  // The value we're accepting

    public AcceptMsg(int position, int proposalNum, Object value) {
        super(position, proposalNum);
        this.value = value;
    }

    public AcceptMsg() {}

    @Override
    public String toString() {
        return "ACCEPT{pos=" + position + ", prop#=" + proposalNum +
                ", val=" + value + "}";
    }
}
