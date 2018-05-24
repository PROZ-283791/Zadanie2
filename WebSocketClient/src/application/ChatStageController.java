package application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;

public class ChatStageController {
	private static long MAX_FILE_SIZE = 1024 * 1024 * 4 - 1;
	private ByteBuffer fileBuffer;
	private String fileName;
	private boolean fileJustGot = false;
	private String user;
	private WebSocketClient webSocketClient;

	@FXML
	TextField userTextField;
	@FXML
	TextArea chatTextArea;
	@FXML
	TextField messageTextField;
	@FXML
	Button btnSet;
	@FXML
	Button btnSend;
	@FXML
	Label fileLabel;
	@FXML
	Label fileSaveLabel;
	@FXML
	Label fileSaveExit;

	@FXML
	public void initialize() {
		webSocketClient = new WebSocketClient();
		user = userTextField.getText();
	}

	@FXML
	private void onEnter(KeyEvent key) {
		if (key.getCode().equals(KeyCode.ENTER)) {
			btnSendClick();
		}
	}

	@FXML
	private void onEntered() {
		btnSet.getScene().setCursor(Cursor.HAND);
	}

	@FXML
	private void onExited() {
		btnSet.getScene().setCursor(Cursor.DEFAULT);
	}

	@FXML
	private void onEnterUser(KeyEvent key) {
		if (key.getCode().equals(KeyCode.ENTER)) {
			btnSetClick();
		}
	}

	@FXML
	private void fileDiscard() {
		fileBuffer = null;
		fileName = null;
		fileLabel.setVisible(false);
		fileSaveLabel.setVisible(false);
		fileSaveExit.setVisible(false);
		System.out.println("File discarded");
	}

	@FXML
	private void btnSetClick() {
		if (userTextField.getText().isEmpty()) {
			return;
		}
		user = userTextField.getText();
		messageTextField.requestFocus();
	}

	@FXML
	private void btnAddFileClick() throws IOException {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Open Resource File");
		File file = fileChooser.showOpenDialog(null);

		try {
			webSocketClient.sendMessage(ByteBuffer.wrap(getBytesFromFile(file)), file.getName());
		} catch (IOException e) {
			System.out.println(e.getMessage());
			Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Error");
			alert.setHeaderText("File is too big");
			alert.setContentText("Maximal file size is 4 MB");
			alert.showAndWait();
		}
	}

	@FXML
	private void btnSendClick() {
		webSocketClient.sendMessage(messageTextField.getText());
		chatTextArea.positionCaret(chatTextArea.getText().length());
		messageTextField.setText("");
	}

	@FXML
	private void saveFile() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Open Resource File");
		fileChooser.setInitialFileName(fileName);

		try {
			FileOutputStream fos = new FileOutputStream(fileChooser.showSaveDialog(null).getAbsolutePath());
			fos.write(fileBuffer.array());
			fos.close();
		} catch (Exception e) {

		}
	}

	private double calculateWidth(String name) {
		Text text = new Text("File received: " + name);
		text.applyCss();
		return text.getLayoutBounds().getWidth();
	}

	private byte[] getBytesFromFile(File file) throws IOException {
		long length = file.length();

		if (length > MAX_FILE_SIZE) {
			throw new IOException("File is too large!");
		}

		byte[] bytes = new byte[(int) length];

		int offset = 0;
		int numRead = 0;

		InputStream is = new FileInputStream(file);
		try {
			while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
				offset += numRead;
			}
		} finally {
			is.close();
		}

		if (offset < bytes.length) {
			throw new IOException("Could not completely read file " + file.getName());
		}
		return bytes;
	}

	public void closeSession(CloseReason closeReason) {
		try {
			webSocketClient.session.close(closeReason);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@ClientEndpoint
	public class WebSocketClient {
		private Session session;

		public WebSocketClient() {
			connectToWebSocket();
		}

		private void connectToWebSocket() {
			WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
			try {
				URI uri = URI.create("ws://localhost:8080/Proj2/websocketendpoint");
				webSocketContainer.connectToServer(this, uri);
			} catch (DeploymentException | IOException e) {
				e.printStackTrace();
			}
		}

		@OnOpen
		public void onOpen(Session session) {
			System.out.println("Connection is opened.");
			this.session = session;
		}

		@OnClose
		public void onClose(CloseReason closeReason) {
			System.out.println("Connection is closed: " + closeReason.getReasonPhrase());
		}

		@OnError
		public void onError(Throwable throwable) {
			System.out.println("Error occured");
			throwable.printStackTrace();
		}

		@OnMessage
		public void onMessage(String message, Session session) {
			if (fileJustGot) {
				fileJustGot = false;
				fileName = message;
				Platform.runLater(() -> {
					fileLabel.setText("File received: " + fileName);
					fileSaveExit.setLayoutX(fileLabel.getLayoutX() + calculateWidth(message));
				});
				fileSaveExit.setVisible(true);
				fileSaveLabel.setVisible(true);
			} else {
				System.out.println("Message was received");
				chatTextArea.setText(chatTextArea.getText() + message + "\n");
				chatTextArea.positionCaret(chatTextArea.getText().length());
			}
		}

		@OnMessage
		@FXML
		public void onMessage(ByteBuffer bufer, Session session) {
			System.out.println("File was received");
			fileLabel.setVisible(true);
			fileBuffer = bufer;
			fileJustGot = true;
		}

		public void sendMessage(String message) {
			try {
				System.out.println("Message was sent: " + message);
				session.getBasicRemote().sendText(user + ": " + message);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		public void sendMessage(ByteBuffer buf, String fileName) {
			try {
				session.getBasicRemote().sendBinary(buf);
				session.getBasicRemote().sendText(fileName);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	} // public class WebSocketClient
}
