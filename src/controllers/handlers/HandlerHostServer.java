package controllers.handlers;

import controllers.frameControllers.MainFrame;
import org.w3c.dom.ls.LSOutput;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static controllers.Encoding.*;
import static controllers.frameControllers.HostServerFrame.messages;

public class HandlerHostServer extends Thread {
    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private static Set<PrintWriter> writers;
    private static ArrayList<String> connectedUsers;
    private static ArrayList<String> connectedIPs;

    public HandlerHostServer(Socket socket ,Set<PrintWriter> writers) {
        connectedIPs = new ArrayList<String>();
        connectedUsers = new ArrayList<String>();
        this.socket = socket;
        this.writers = writers;
    }

    @Override

    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            writers.add(writer);

            while (true) {

                String message = reader.readLine();
                if (message == null) {
                    return;
                }
                boolean isCommand = false;
                try {
                    if (message.split("/")[1].equals("requestHashedPassword")) {
                        writer.println("HashedPassword: " + MainFrame.hostHashedPassword);
                        isCommand = true;
                    }
                }catch(ArrayIndexOutOfBoundsException e){}
                if(!isCommand) {
                    message = decrypt(message, MainFrame.hostHashedPassword);
                    MainFrame.serverMessages.add("(" + socket.getInetAddress() + ")" + message);
                    broadcast(message, writer);
                    try {
                        if (message.split("/")[1].equals("list")) {
                            String users = "-- Connected users: ";
                            for (int i = 0; i < connectedUsers.size(); i++) {
                                users += "\n" + connectedUsers.get(i);
                            }
                            writer.println(encrypt(users, MainFrame.hostHashedPassword));
                        }
                    }catch(ArrayIndexOutOfBoundsException e){}
                    try {

                        if (message.split(" ")[2].equals("joined")) {
                            String ip = message.split(" ")[0];
                            String ipSplitted[] = ip.split("");
                            if ((ipSplitted[ipSplitted.length - 1] + ipSplitted[ipSplitted.length - 2]).equals("--")) {
                                connectedUsers.add(message.split(" ")[1]);
                                connectedIPs.add("127.0.0.1");
                            }
                        }if (message.split(" ")[2].equals("left")) {
                            String ip = message.split(" ")[0];
                            String ipSplitted[] = ip.split("");
                            if ((ipSplitted[ipSplitted.length - 1] + ipSplitted[ipSplitted.length - 2]).equals("--")) {
                                connectedUsers.remove(message.split(" ")[1]);
                                int slashIndex = message.indexOf('/');

                                if (slashIndex != -1) {
                                    // Encuentra la posición del primer ')'
                                    int closingParenthesisIndex = message.indexOf(')', slashIndex);

                                    if (closingParenthesisIndex != -1) {
                                        // Extrae la subcadena entre '/' y ')'
                                        String ipAddress = message.substring(slashIndex + 1, closingParenthesisIndex);
                                        connectedIPs.remove(ipAddress);
                                    }
                                }
                            }
                        }
                        if (message.split(" ")[2].equals("sent")) {
                            try {
                                ServerSocket imageSocketServer = new ServerSocket(2020);
                                Socket imageSocket = imageSocketServer.accept();
                                Thread handlerThread = new Thread(new ImageConnectionHandler(imageSocket, message.split(" ")[1]));
                                handlerThread.start();
                            }catch(SocketException e){
                                e.printStackTrace();
                            }
                        }
                    }catch (ArrayIndexOutOfBoundsException e){}
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (writer != null) {
                writers.remove(writer);
            }

            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private static void broadcast(String message, PrintWriter messageWriter) {
        for (PrintWriter writer : writers) {
            if(!writer.equals(messageWriter)){
                writer.println(encrypt(message, MainFrame.hostHashedPassword));
            }
        }
    }

    public static void broadcastServerMessage(String message){
        for (PrintWriter writer : writers) {
            writer.println(encrypt(message, MainFrame.hostHashedPassword));
        }
    }

    static class ImageConnectionHandler implements Runnable {
        private Socket socket;
        private String sender;

        public ImageConnectionHandler(Socket socket, String sender) {
            this.socket = socket;
            this.sender = sender;
        }

        @Override
        public void run() {
            try {
                InputStream inputStream = socket.getInputStream();
                String fileName = "src/files/server/image"+ sender +Date.from(Instant.now()).getDate()+Date.from(Instant.now()).getMonth()
                        +Date.from(Instant.now()).getYear()+"_"+Date.from(Instant.now()).getHours()+Date.from(Instant.now()).getMinutes()+Date.from(Instant.now()).getSeconds()+".jpg";
                FileOutputStream fileOutputStream = new FileOutputStream(fileName);

                byte[] receiveBuffer = new byte[1024];
                int receiveBytesRead;

                while ((receiveBytesRead = inputStream.read(receiveBuffer)) != -1) {
                    fileOutputStream.write(receiveBuffer, 0, receiveBytesRead);
                }
                for(int i = 0; i < connectedIPs.size(); i++) {
                    try (Socket imageSocket = new Socket(connectedIPs.get(i), 2021);
                         OutputStream outputStream = imageSocket.getOutputStream();
                         FileInputStream fileInputStream = new FileInputStream(fileName)) {
                        Thread.sleep(100);

                        byte[] sendBuffer = new byte[1024];
                        int sendBytesRead;

                        while ((sendBytesRead = fileInputStream.read(sendBuffer)) != -1) {
                            outputStream.write(sendBuffer, 0, sendBytesRead);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}