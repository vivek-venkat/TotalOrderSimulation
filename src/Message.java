import java.io.Serializable;


public class Message implements Serializable{
	private static final long serialVersionUID = 212086067017050020L;
	String sid;
	int tag;
	String oid;
	int ts;
	int m;
	MessageType message;
	
	public Message(String sid, int tag) {
		this.sid = sid;
		this.tag = tag;
	}
	
	public void createFINALMessage(int ts){
		this.ts=ts;
		message = MessageType.FINAL;
	}
	
	public void createREVISEMessage(int m,int ts){
		this.m=m;
		this.ts=ts;
		message = MessageType.REVISE;
	}
	
	public void createPROPOSEMessage(String oid,int ts){
		this.oid=oid;
		this.ts=ts;
		message = MessageType.PROPOSED;
	}
}
