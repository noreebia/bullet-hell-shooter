package bhs.server.main;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import bhs.server.game.main.Room;
import protocol.Message;

public class ClientHandler extends Thread {
	private Client client;
	private List<Client> clientList;
	private List<ClientHandler> clientHandlingThreads;
	private List<Room> rooms;
	private AtomicInteger uniqueRoomID;
	private ObjectOutputStream oos;
	private ObjectInputStream ois;
	volatile boolean run = true;

	public ClientHandler(Client client, List<Client> clientList,
			List<ClientHandler> clientHandlingThreads, List<Room> rooms,
			AtomicInteger uniqueRoomID) {
		this.client = client;
		this.clientList = clientList;
		this.clientHandlingThreads = clientHandlingThreads;
		this.rooms = rooms;
		this.uniqueRoomID = uniqueRoomID;

		try {
			oos = client.getOutputStream();
			ois = client.getInputStream();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("A client handler has just been created");
	}

	public void run() {
		Message incomingMessage = null;
		Message outgoingMessage = null;
		String request;
		String messageData;
		while (shouldRun()) {
			try {
				incomingMessage = (Message) ois.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				terminate();
				break;
			} catch (SocketException e) {
				terminate();
				break;
			} catch (Exception e) {
				e.printStackTrace();
				terminate();
				break;
			}
			if (incomingMessage == null) {
				terminate();
				break;
			}
			request = incomingMessage.getContents();
			System.out.println("Recevied message from client  " + client.getNickname());
			System.out.println("Client request: " + request);
			switch (request) {
			case "chat":
				messageData = (String) incomingMessage.getData();
				String modifiedMessage = client.getNickname() + ": " + messageData + "\n";
				System.out.println(modifiedMessage);
				outgoingMessage = new Message("chatboxUpdate", modifiedMessage);
				sendToAllClients(outgoingMessage);
				break;
			case "create game":
				int newRoomID = createRoom();
				handleRoomJoinRequest(newRoomID);
				refreshRoomListOfEveryClient();
				break;
			case "join game":
				int roomID = (int) incomingMessage.getData();
				handleRoomJoinRequest(roomID);
				break;
			case "refresh room list":
				refreshRoomListOfClient();
				break;
			case "exit":
				terminate();
				break;
			default:
			}
		}
		System.out.println("Client has disconnected. Terminating appointed client handler.");
		disconnectClient();
		removeSelfFromArray();
		return;
	}

	public int createRoom() {
		int newRoomID = uniqueRoomID.getAndIncrement();
		Room newRoom = new Room(newRoomID);
		rooms.add(newRoom);
		return newRoomID;
	}

	public void handleRoomJoinRequest(int roomID) {
		for (Room r : rooms) {
			if (r.getID() == roomID) {
				if (r.getState().equals("Dead") || r.getState().equals("Finished")) {
					refreshRoomListOfClient();
				} else {
					if (r.getPlayerCount() <= 3) {
						int[] roomInfo = { r.getPort(), r.getUniquePlayerID() };
						Message message = new Message("join game response", roomInfo);
						sendToClient(message);
					}
				}
				return;
			}
		}
		refreshRoomListOfClient();
	}

	public void refreshRoomListOfClient() {
		Message message = createRoomListRefreshmentMessage();
		sendToClient(message);
	}

	public void refreshRoomListOfEveryClient() {
		Message message = createRoomListRefreshmentMessage();
		sendToAllClients(message);
	}
	
	public Message createRoomListRefreshmentMessage() {
		List<String> listOfRooms = getActiveRooms();
		return new Message("refresh room list response", listOfRooms);
	}
	
	public List<String> getActiveRooms(){
		List<String> listOfRooms = new ArrayList<String>();
		for (Room r : rooms) {
			if (r.getState().equals("Dead")) {
				rooms.remove(r);
			} else {
				listOfRooms.add("Room " + r.getID() + "     Players:" + r.getPlayerCount() + "/4" + "     Stage: "
						+ r.getCurrentStage() + "    " + r.getState());
			}
		}
		return listOfRooms;
	}

	public void sendToClient(Message message) {
		try {
			oos.writeObject(message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendToAllClients(Message message) {
		for (ClientHandler ch : clientHandlingThreads) {
			ch.sendToClient(message);
		}
	}

	public void disconnectClient() {
		clientList.remove(client);
	}

	public boolean shouldRun() {
		return run;
	}

	public void removeSelfFromArray() {
		clientHandlingThreads.remove(this);
	}

	public void terminate() {
		this.run = false;
	}
}
