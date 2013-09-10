import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

public class Overseer extends UnicastRemoteObject implements IOverseer{
	private static final long serialVersionUID = -718334084826807208L;
	private static int counter = 0;
	static int REG_PORT = 3334; 
	private static int numNodes;
	private ArrayList<String> nodes = new ArrayList<String>();
	private Registry registry;
	
	public static void main(String argsp[]){
		try {
//			Overseer.numNodes = 4;
		    Overseer.numNodes = Integer.parseInt(argsp[0]);
			new Overseer();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public Overseer()throws RemoteException{
		try {
			LocateRegistry.createRegistry(REG_PORT);
			registry = LocateRegistry.getRegistry(REG_PORT);
			registry.rebind("overseer", this);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		for(int i=1;i<=numNodes;i++){
			nodes.add("P"+i);
		}
		createNodes();
	}

	public void createNodes(){
		try {
			for(int i=1;i<=numNodes;i++){
			   	Process p= Runtime.getRuntime().exec("cmd /c CreateProcessNode.bat P"+Integer.toString(i));
				p.waitFor();
				System.out.println("Node Process: "+i+" created.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void nodeCreated(String nodeName) throws RemoteException {
		System.out.println("Node created: "+nodeName);
		counter++;
	}

	@Override
	public void nodeDead(String nodeName) throws RemoteException {
		System.out.println("Node Dead: "+nodeName);
		counter--;
		nodes.remove(nodeName);
		if(counter==0)
			System.exit(0);
	}

	@Override
	public ArrayList<String> getbNodes(String caller) throws RemoteException {
		ArrayList<String> bNodes = new ArrayList<String>();
		for(String n : nodes){
			if(!n.equals(caller))
				bNodes.add(n);
		}
		return bNodes;
	}
}
