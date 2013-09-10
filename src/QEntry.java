
public class QEntry {
	int M;
	int tag;
	String sender;
	int ts;
	boolean deliverable;
	
	public QEntry(int m, int tag, String sender, int ts, boolean deliverable) {
		M = m;
		this.tag = tag;
		this.sender = sender;
		this.ts = ts;
		this.deliverable = deliverable;
	}
	
	private String getString(int i){
		return Integer.toString(i);
	}
	
	public String getStringForm(){
		return new StringBuffer().append(getString(M)).append(",")
		.append(getString(tag)).append(",")
		.append(sender).append(",")
		.append(getString(ts))
		.append(",").append(deliverable).toString();
	}
}
