import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GameServer {
    // 서버의 포트 번호 설정
    private static final int PORT = 30000;
    // 고정된 수(2)의 스레드를 가지는 스레드 풀 생성
    private static ExecutorService pool = Executors.newFixedThreadPool(2);
    // 클라이언트 핸들러를 저장하는 스레드 안전한 리스트
    private static List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    // 현재 연결된 클라이언트 수를 추적하기 위한 AtomicInteger
    private static AtomicInteger numberOfClients = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        // 서버 소켓 생성
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port " + PORT);

            // 무한 루프를 돌며 클라이언트의 연결을 기다림
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // 새로운 클라이언트 연결 시 카운트 증가
                int currentNumberOfClients = numberOfClients.incrementAndGet();
                System.out.println("Client entered: " + currentNumberOfClients);
                
                // 클라이언트 핸들러 객체 생성 및 클라이언트 목록에 추가
                ClientHandler clientThread = new ClientHandler(clientSocket, currentNumberOfClients);
                synchronized(clients) {
                    clients.add(clientThread);
                    // 첫 번째 클라이언트일 경우 대기 메시지 전송
                    if (currentNumberOfClients == 1) {
                        clientThread.sendMessageToClient("Waiting for another player...");
                    } else if (currentNumberOfClients == 2) {
                        // 두 번째 클라이언트일 경우 게임 시작 메시지 전송
                        for (ClientHandler client : clients) {
                            client.sendMessageToClient("Game starts now!");
                        }
                    }
                }
                // 클라이언트 핸들러 스레드를 스레드 풀에 제출
                pool.execute(clientThread);
            }
        }
    }

    // 클라이언트를 관리하는 핸들러 클래스
    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private static final AtomicInteger clientIdCounter = new AtomicInteger(0);
        private final int clientId;
        private final int currentNumberOfClients;
        
        // 클라이언트 핸들러 생성자
        public ClientHandler(Socket socket, int currentNumberOfClients) throws IOException {
            this.clientSocket = socket;
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.clientId = clientIdCounter.getAndIncrement();
            this.currentNumberOfClients = currentNumberOfClients;
        }
        
        // 클라이언트에게 메시지를 전송하는 메소드
        public void sendMessageToClient(String message) {
            out.println(message);
        }

        // 클라이언트와의 통신을 처리하는 메인 메소드
        public void run() {
            try {
                while (true) {
                    String inputLine = in.readLine();
                    if (inputLine != null) {
                        // 받은 메시지를 다른 클라이언트에게 전달
                        String sendToClient = clientId + " : " + inputLine;
                        for (ClientHandler client : clients) {
                            if (client != this) {
                                client.out.println(sendToClient);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Exception in ClientHandler " + e.getMessage());
                e.printStackTrace();
            } finally {
                // 예외 발생 시 리소스 정리
                try {
                    in.close();
                    out.close();
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
