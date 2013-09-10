import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.SynchronousQueue;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;

public class ProcessNode extends UnicastRemoteObject implements IProcessNode,
WindowListener {
	private static final long serialVersionUID = 8559914970264572874L;
	private Vector<QEntry> deliveryQueue = new Vector<QEntry>();
	private Vector<QEntry> tempQueue = new Vector<QEntry>();
	private Vector<QEntry> processedQueue = new Vector<QEntry>();
	private SynchronousQueue<Message> messageQueue = new SynchronousQueue<Message>();
	private Hashtable<Integer, Integer> proposeMessagesCount = new Hashtable<Integer, Integer>();
	private Random randomTag = new Random();
	private Random randomM = new Random();
	private Random randTime = new Random();
	private Registry registry;
	private ArrayList<String> bNodes;
	private IOverseer overseer;
	private String nodeName;
	private int priority = 0;
	private int clock = 0;
	private int tempts = 0;
	// UI
	private JFrame frame;
	private JTextField txtBaj;
	private DefaultListModel tempList = new DefaultListModel();
	private DefaultListModel deliveryList = new DefaultListModel();
	private DefaultListModel processedList = new DefaultListModel();
	private DefaultListModel log = new DefaultListModel();

	public static void main(String args[]) {
		try {
			new ProcessNode(args[0]);
			//			new ProcessNode("P2");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	ProcessNode(String nodeName) throws RemoteException {
		this.nodeName = nodeName;
		init();
	}

	private void init() {
		try {
			registry = LocateRegistry.getRegistry(Overseer.REG_PORT);
			registry.rebind(nodeName, this);
			overseer = (IOverseer) registry.lookup("overseer");
			new Thread(new RequestProcessor()).start();
			new Thread(new DeliveryChecker()).start();
			overseer.nodeCreated(nodeName);
			System.out.println("New Node : "+nodeName+" created.");
			getbNodes();
			initializeUI();
			createUI();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void broadcastMessage(int tag, int m) {
		tempts = 0;
		proposeMessagesCount.put(tag, 0);
		clock += 1;
		sendRevise(tag, m);
	}

	private void sendPropose(String sid, int tag) {
		try {
			addToLog("Sending Propose message back to " + sid + " with tag: "
					+ tag);
			Message message = new Message(nodeName, tag);
			message.createPROPOSEMessage(sid, priority);
			sendNodeMessage((IProcessNode)registry.lookup(sid), message);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendRevise(int tag, int m) {
		try {
			addToLog("Sending Revise Messages to all Nodes with tag: " + tag);
			Message message = new Message(nodeName, tag);
			message.createREVISEMessage(m, clock);
			for (String b : bNodes) {
				sendNodeMessage((IProcessNode) registry.lookup(b), message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendFinal(int tag) {
		try {
			addToLog("Sending Final Message to all Nodes with tag" + tag);
			Message message = new Message(nodeName, tag);
			message.createFINALMessage(tempts);
			for (String b : bNodes) {
				sendNodeMessage((IProcessNode) registry.lookup(b), message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void getbNodes() {
		try {
			bNodes = overseer.getbNodes(nodeName);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	private void deliverPendingMessages() {
		while (!deliveryQueue.isEmpty()) {
			try {
				Thread.sleep(550);
				QEntry qe = deliveryQueue.remove(0);
				removeFromList(deliveryList,false);
				clock = (clock > qe.ts) ? clock : qe.ts;
				clock += 1;
				processedQueue.add(qe);
				addToList(processedList, qe.getStringForm());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void sortTempQueue() {
		Collections.sort(tempQueue, new Comparator<Object>() {
			public int compare(Object a, Object b) {
				return (new Integer(((QEntry) a).ts)).compareTo(new Integer(
						((QEntry) b).ts));
			}
		});
		updateTempQueueUI();
	}

	private int genM() {
		return randomM.nextInt(1000);
	}

	private int genTag() {
		return randomTag.nextInt(1000)
		+ (Integer.parseInt(nodeName.replace("P", "")) * 1000);
	}

	private long waitTime() {
		return (randTime.nextInt(6 - 4 + 1) + 4) * 1000;
	}

	private void sendNodeMessage(final IProcessNode stub,final Message message){
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(waitTime());
					stub.sendMessage(message);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	
	private class DeliveryChecker implements Runnable{
		@Override
		public void run() {
			while(true){
				try {
					Thread.sleep(2000);
					deliverPendingMessages();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private class RequestProcessor implements Runnable {
		@Override
		public void run() {
			System.out.println("Request Processor Started");
			while (true) {
				try {
					final Message message = messageQueue.take();
					new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								synchronized (ProcessNode.class) {
									processMessage(message);
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		private void processMessage(final Message message)
		throws RemoteException {
			System.out.println("Request : " + message.message.name());
			updateClock();
			try {
				switch (message.message) {
				case REVISE:
					priority = (priority + 1 > message.ts) ? priority + 1
							: message.ts;
					final QEntry qe = new QEntry(message.m, message.tag,
							message.sid, priority, false);
					tempQueue.add(qe);
					addToList(tempList, qe.getStringForm());
					sendPropose(message.sid, message.tag);
					break;
				case PROPOSED:
					proposeMessagesCount
					.put(message.tag, proposeMessagesCount
							.get(message.tag) + 1);
					tempts = (tempts > message.ts) ? tempts
							: message.ts;
					System.out
					.println("Propose Message count: "
							+ proposeMessagesCount
							.get(message.tag));
					System.out.println("bNodes size: "
							+ bNodes.size());
					if (proposeMessagesCount.get(message.tag) == bNodes
							.size()) {
						sendFinal(message.tag);
						clock = (clock > tempts) ? clock : tempts;
						proposeMessagesCount.remove(message.tag);
					}
					break;
				case FINAL:
					System.out.println("Message = " + message.tag);
					for (QEntry q : tempQueue) {
						if (q.tag == message.tag
								&& q.sender.equals(message.sid)) {
							q.deliverable = true;
							q.ts = message.ts;
							break;
						}
					}
					sortTempQueue();
					//if (tempQueue.firstElement().tag == message.tag) {
					if (tempQueue.firstElement().deliverable) {
						QEntry qe1 = tempQueue.remove(0);
						deliveryQueue.add(qe1);
						addToList(deliveryList, qe1.getStringForm());
						removeFromList(tempList,false);
						while (!tempQueue.isEmpty() && tempQueue.firstElement().deliverable) {
							QEntry qe2 = tempQueue.remove(0);
							deliveryQueue.add(qe2);
							addToList(deliveryList, qe2.getStringForm());
							removeFromList(tempList,false);
						}
					}
					break;
				}
				updateClock();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void sendMessage(Message message) throws RemoteException {
		try {
			messageQueue.put(message);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void broadcast() throws RemoteException {
		broadcastMessage(genTag(), genM());
	}

	/**
	 * UI
	 */
	public void createUI() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					frame.setVisible(true);
					frame.setResizable(false);
					frame.setSize(450, 300);
					frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
					frame.setTitle("Process UI - " + nodeName);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Initialize the contents of the frame.
	 */
	@SuppressWarnings("serial")
	private void initializeUI() {
		frame = new JFrame();
		frame.getContentPane().setBackground(SystemColor.menu);
		frame.getContentPane().setLayout(new BorderLayout(0, 0));
		frame.addWindowListener(this);
		JPanel panel = new JPanel();
		panel.setBorder(new BevelBorder(BevelBorder.RAISED, null, null, null,
				null));
		frame.getContentPane().add(panel, BorderLayout.NORTH);
		panel.setLayout(new BorderLayout(0, 0));

		txtBaj = new JTextField();
		txtBaj.setEditable(false);
		txtBaj.setText("Clock: 0");
		panel.add(txtBaj, BorderLayout.CENTER);
		txtBaj.setColumns(5);

		JButton btnNewButton = new JButton("BROADCAST");
		btnNewButton.addActionListener(new BroadcastActionListener());
		panel.add(btnNewButton, BorderLayout.EAST);

		JPanel panel_1 = new JPanel();
		panel_1.setPreferredSize(new Dimension(150, 400));
		panel_1.setSize(new Dimension(50, 100));
		frame.getContentPane().add(panel_1, BorderLayout.WEST);
		panel_1.setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane = new JScrollPane();
		scrollPane
		.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane
		.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setAlignmentY(Component.BOTTOM_ALIGNMENT);
		scrollPane.setAlignmentX(Component.RIGHT_ALIGNMENT);
		scrollPane.setSize(new Dimension(50, 100));
		panel_1.add(scrollPane, BorderLayout.CENTER);

		JList list = new JList();
		list.setEnabled(true);
		list.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null,
				null));
		list.setSize(new Dimension(50, 100));
		scrollPane.setViewportView(list);
		list.setBackground(SystemColor.text);
		list.setModel(tempList);
		list.setCellRenderer(new DefaultListCellRenderer() {
			public Component getListCellRendererComponent(JList list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, false, false);
				return this;
			}});
		JLabel lblNewLabel = new JLabel("TEMP_Q");
		lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
		lblNewLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblNewLabel.setBorder(new EmptyBorder(0, 0, 0, 0));
		panel_1.add(lblNewLabel, BorderLayout.NORTH);

		JSeparator separator = new JSeparator();
		separator.setOpaque(true);
		separator.setBackground(SystemColor.windowBorder);
		separator.setForeground(SystemColor.windowBorder);
		separator.setPreferredSize(new Dimension(2, 2));
		panel_1.add(separator, BorderLayout.EAST);

		JPanel panel_2 = new JPanel();
		panel_2.setPreferredSize(new Dimension(150, 400));
		frame.getContentPane().add(panel_2, BorderLayout.CENTER);
		panel_2.setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane_1 = new JScrollPane();
		scrollPane_1
		.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane_1
		.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
		scrollPane_1.setPreferredSize(new Dimension(100, 300));
		panel_2.add(scrollPane_1, BorderLayout.CENTER);

		JList list_1 = new JList();
		list_1.setEnabled(true);
		list_1.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null,
				null));
		list_1.setPreferredSize(new Dimension(100, 300));
		list_1.setModel(deliveryList);
		list_1.setBackground(SystemColor.text);
		list_1.setSelectionBackground(new Color(255, 255, 255));
		list_1.setCellRenderer(new DefaultListCellRenderer() {
			public Component getListCellRendererComponent(JList list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, false, false);
				return this;
			}});
		scrollPane_1.setViewportView(list_1);
		JLabel lblDelivq = new JLabel("DELIV_Q");
		lblDelivq.setHorizontalAlignment(SwingConstants.CENTER);
		lblDelivq.setBorder(new EmptyBorder(0, 0, 0, 0));
		panel_2.add(lblDelivq, BorderLayout.NORTH);

		JSeparator separator_1 = new JSeparator();
		separator_1.setPreferredSize(new Dimension(2, 2));
		separator_1.setOpaque(true);
		separator_1.setForeground(SystemColor.windowBorder);
		separator_1.setBackground(SystemColor.windowBorder);
		panel_2.add(separator_1, BorderLayout.EAST);

		JPanel panel_3 = new JPanel();
		panel_3.setPreferredSize(new Dimension(150, 400));
		frame.getContentPane().add(panel_3, BorderLayout.EAST);
		panel_3.setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane_2 = new JScrollPane();
		scrollPane_2
		.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane_2
		.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		panel_3.add(scrollPane_2);

		JList list_2 = new JList();
		list_2.setEnabled(true);
		list_2.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null,
				null));
		list_2.setSelectionBackground(new Color(255, 255, 255));
		list_2.setBackground(SystemColor.text);
		list_2.setModel(processedList);
		list_2.setCellRenderer(new DefaultListCellRenderer() {
			public Component getListCellRendererComponent(JList list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, false, false);
				return this;
			}});
		scrollPane_2.setViewportView(list_2);

		JLabel lblProcq = new JLabel("PROC_Q");
		lblProcq.setHorizontalAlignment(SwingConstants.CENTER);
		lblProcq.setBorder(new EmptyBorder(0, 0, 0, 0));
		panel_3.add(lblProcq, BorderLayout.NORTH);

		JPanel panel_4 = new JPanel();
		panel_4.setPreferredSize(new Dimension(10, 80));
		frame.getContentPane().add(panel_4, BorderLayout.SOUTH);
		panel_4.setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane_3 = new JScrollPane();
		panel_4.add(scrollPane_3);

		JList list_3 = new JList();
		list_3.setEnabled(true);
		list_3.setSelectionBackground(new Color(255, 255, 255));
		list_3.setModel(log);
		list_3.setCellRenderer(new DefaultListCellRenderer() {
			public Component getListCellRendererComponent(JList list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, false, false);
				return this;
			}});
		scrollPane_3.setViewportView(list_3);

		JLabel lblNewLabel_1 = new JLabel("LOG");
		lblNewLabel_1.setHorizontalAlignment(SwingConstants.CENTER);
		panel_4.add(lblNewLabel_1, BorderLayout.NORTH);
	}

	private class BroadcastActionListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			broadcastMessage(genTag(), genM());
		}
	}

	private void updateTempQueueUI() {
		removeFromList(tempList,true);
		StringBuffer sb = new StringBuffer();
		for (QEntry q : tempQueue) {
			sb.append(q.getStringForm() + "::");
			addToList(tempList,q.getStringForm());
		}
		addToLog(sb.toString());
	}

	private void addToLog(final String s) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				log.addElement(s);
			}
		});
	}

	private void addToList(final DefaultListModel model,final String s){
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				model.addElement(s);
			}
		});
	}

	private void removeFromList(final DefaultListModel model,final boolean all){
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if(!all)
					model.remove(0);
				else
					model.removeAllElements();
			}
		});
	}

	private void updateClock() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				txtBaj.setText("Clock: " + clock);
			}
		});
	}

	@Override
	public void windowClosing(WindowEvent e) {
		if (frame.isDisplayable()) {
			frame.dispose();
		}
		try {
			overseer.nodeDead(nodeName);
		} catch (RemoteException e1) {
			e1.printStackTrace();
		}
	}

	@Override
	public void windowClosed(WindowEvent e) {
	}

	@Override
	public void windowActivated(WindowEvent arg0) {
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
	}
}
