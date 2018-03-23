package bhs.server.game.main;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;

import bhs.server.game.control.DataController;
import bhs.server.game.control.EnemySystem;
import game.protocol.Client;
import game.protocol.Player;

public class InputHandlingThread implements Runnable {
	private DatagramSocket socket;
	private DatagramPacket packet;
	private byte[] buf = new byte[8192];
	private Player temp;
	private DataController dataController;
	private ByteArrayInputStream bais;
	private ObjectInputStream is;
	private List<Client> clients;
	private EnemySystem enemySystem;
	private boolean run = true;

	public InputHandlingThread(DatagramSocket ioSocket, DataController dataController, EnemySystem enemySystem,
			List<Client> clients) {
		this.socket = ioSocket;
		this.dataController = dataController;
		this.enemySystem = enemySystem;
		this.clients = clients;
	}

	public void run() {
		while (shouldRun()) {
			try {
				packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);

				bais = new ByteArrayInputStream(packet.getData());
				is = new ObjectInputStream(bais);

				try {
					temp = (Player) is.readObject();
					dataController.updatePlayer(temp);

					if (dataController.isNewPlayer()) {
						addClient(temp.getID(), packet.getAddress(), packet.getPort(), System.currentTimeMillis());
						System.out.println("A new player has connected. Name of player: " +temp.getUsername());
					} else {
						for (Client c : clients) {
							if (c.getID() == temp.getID()) {
								c.setLastSentTime(System.currentTimeMillis());
								break;
							}
						}
					}
					for (Integer i : temp.getHitEnemies()) {
						enemySystem.getOriginals().get(i).getHit();
						if (enemySystem.getOriginals().get(i).isActive()) {
							enemySystem.changeShadowColor(i);
						} else {
							enemySystem.getShadows().get(i).setXY(-1000, -1000);
						}
						dataController.addExplosion(enemySystem.getOriginals().get(i).getLastKnownX(),
								enemySystem.getOriginals().get(i).getLastKnownY(),
								enemySystem.getShadows().get(i).getRGB(0), enemySystem.getShadows().get(i).getRGB(1),
								enemySystem.getShadows().get(i).getRGB(2));
					}
					if (temp.isHit()) {
						dataController.addExplosion(temp.getX(), temp.getY(), temp.getRGB(0), temp.getRGB(1),
								temp.getRGB(2));
					}
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
					System.exit(1);
				}
				is.close();
			} catch (SocketException e) {
				terminate();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void addClient(short id, InetAddress clientAddress, int clientPort, long lastSentTime) {
		clients.add(new Client(id, clientAddress, clientPort, lastSentTime));
	}

	public boolean shouldRun() {
		return run;
	}

	public void terminate() {
		run = false;
	}
}
