import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface IOverseer extends Remote {
	void nodeCreated(String nodeName) throws RemoteException;
	void nodeDead(String nodeName) throws RemoteException;
	ArrayList<String> getbNodes(String caller) throws RemoteException;
}
