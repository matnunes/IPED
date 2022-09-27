package iped.parsers.mail.win10.entries;

public class AttachmentEntry extends AbstractEntry {
    private int rowId;
    private long messageId;
    private int attachSize;
    private String fileName;
    private String attachFilePath;
    private Boolean received;

    public AttachmentEntry(int rowId, int messageId, int attachSize, String fileName, String filePath, Boolean received) {
        super(rowId);
        this.messageId = messageId;
        this.attachSize = attachSize;
        this.fileName = fileName;
        this.attachFilePath = filePath;
        this.received = received;
    }

	public int getRowId() {
		return this.rowId;
	}

	public void setRowId(int rowId) {
		this.rowId = rowId;
	}

	public long getMessageId() {
		return this.messageId;
	}

	public void setMessageId(long messageId) {
		this.messageId = messageId;
	}

	public int getAttachSize() {
		return this.attachSize;
	}

	public void setAttachSize(int attachSize) {
		this.attachSize = attachSize;
	}

	public String getFileName() {
		return this.fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	
    public String getAttachFilePath() {
		return this.attachFilePath;
	}

	public void setAttachFilePath(String filePath) {
		this.attachFilePath = filePath;
	}

	public void setReceived(Boolean received) {
		this.received = received;
	}

    public Boolean isReceived() {
        return this.received;
    }
}
