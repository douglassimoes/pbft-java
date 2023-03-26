package pbft;

public enum PBFTMessageType {
	REQUEST(0),
    PREPREPARE(1),
    PREPARE(2),
    COMMIT(3),
    REPLY(4),
    VIEW_CHANGE(5),
    NEW_VIEW(6),
    CHECKPOINT(7);

    private final int value;

    private PBFTMessageType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static PBFTMessageType fromValue(String msgValue) {
    	int value = Integer.valueOf(msgValue);
        for (PBFTMessageType messageType : PBFTMessageType.values()) {
            if (messageType.getValue() == value) {
                return messageType;
            }
        }
        throw new IllegalArgumentException("Invalid message type value: " + value);
    }
}