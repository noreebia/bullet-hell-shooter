package bhs.server.game.main;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;

import bhs.server.game.control.DataController;
import bhs.server.game.control.EnemySystem;
import game.protocol.Client;

public class OutputHandlingThread implements Runnable {
	private ByteArrayOutputStream baos = new ByteArrayOutputStream();
	private ObjectOutputStream os;
	private byte[] buf = new byte[8192];
	private DatagramSocket socket;
	private DatagramPacket packet;
	private DataController dataController;
	private List<Client> clients;
	private EnemySystem enemySystem;
	private Room room;

	public OutputHandlingThread(Room room, DatagramSocket socket, DataController dataController,
			List<Client> clients, EnemySystem enemySystem) {
		this.room = room;
		this.socket = socket;
		try {
			os = new ObjectOutputStream(baos);
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.clients = clients;
		this.dataController = dataController;
		this.enemySystem = enemySystem;
	}

	public void run() {
		if (clients.size() > 0) {
			try {
				enemySystem.run();
				if(enemySystem.isEveryPlayerDead()) {
					room.setState("Finished");
				}
				try {
					baos.reset();
					os = new ObjectOutputStream(baos);
					os.writeObject(dataController.getData());
				} catch (IOException e) {
					e.printStackTrace();
				}
				buf = baos.toByteArray();

				for (Client c : clients) {
					if (System.currentTimeMillis() - c.getLastSentTime() <= 2000) {
						packet = new DatagramPacket(buf, buf.length, c.getAddress(), c.getPort());
						try {
							socket.send(packet);
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {
						dataController.removePlayer((short) c.getID());
						clients.remove(c);
						System.out.println("A player has left the game. Player's in-game ID number: " + c.getID());
						if(clients.size() <= 0) {
							room.shutdown();
							room.setState("Dead");
						}
					}
				}

				if (dataController.getExplosions().size() > 0) {
					dataController.clearExplosions();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
