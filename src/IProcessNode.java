import java.rmi.Remote;
import java.rmi.RemoteException;


public interface IProcessNode extends Remote{
	void sendMessage(Message message) throws RemoteException;
	public void broadcast() throws RemoteException;
}
